package com.sologo.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.TimeUnit

enum class GtpConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

/**
 * GTP 协议客户端。
 *
 * 设计要点：
 * - 所有普通命令串行执行，避免多个协程同时读写 stdout；
 * - 单独协程持续读取 stdout，解决 readLine 阻塞导致无法超时的问题；
 * - stderr 单独收集到日志，便于真机排查；
 * - 进程退出后命令立即失败，由上层决定是否重启。
 */
class GtpClient(
    private val binary: File,
    private val arguments: List<String>,
    private val workDir: File,
    private val startupTimeoutMs: Long = 180_000,
    private val logger: (String) -> Unit = {}
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()
    private val writeMutex = Mutex()
    private val lines = Channel<String>(Channel.UNLIMITED)
    private var process: Process? = null
    private var stdin: BufferedWriter? = null
    private var readerJob: Job? = null
    private var stderrJob: Job? = null
    private var waiterJob: Job? = null
    private val _state = MutableStateFlow(GtpConnectionState.DISCONNECTED)
    val state: StateFlow<GtpConnectionState> = _state.asStateFlow()

    suspend fun start() = withContext(Dispatchers.IO) {
        if (process?.isAlive == true) return@withContext
        _state.value = GtpConnectionState.CONNECTING
        try {
            val builder = ProcessBuilder(listOf(binary.absolutePath) + arguments)
                .directory(workDir)
                .redirectErrorStream(false)
            val p = builder.start()
            process = p
            stdin = p.outputStream.bufferedWriter(Charsets.UTF_8)
            readerJob = scope.launch { readStdout(p.inputStream.bufferedReader(Charsets.UTF_8)) }
            stderrJob = scope.launch { readStderr(p.errorStream.bufferedReader(Charsets.UTF_8)) }
            waiterJob = scope.launch {
                val code = p.waitFor()
                logger("KataGo 进程退出，code=$code")
                lines.close()
                _state.value = if (code == 0) GtpConnectionState.DISCONNECTED else GtpConnectionState.FAILED
            }
            // 用 name 命令做握手，顺便验证二进制/权限/配置是否正常。
            val name = command("name", timeoutMs = startupTimeoutMs)
            logger("KataGo 已连接: $name")
            _state.value = GtpConnectionState.CONNECTED
        } catch (t: Throwable) {
            _state.value = GtpConnectionState.FAILED
            shutdown()
            throw KataGoException(
                "无法启动 KataGo。请确认 arm64 二进制可执行，或改用 jniLibs/arm64-v8a/libkatago.so。" +
                    " 原始错误: ${t.message}",
                t
            )
        }
    }

    suspend fun command(command: String, timeoutMs: Long = 30_000): String = commandMutex.withLock {
        if (process?.isAlive != true) throw EngineNotReadyException()
        drainBufferedLines()
        write(command)
        readResponse(command, timeoutMs).trim()
    }

    /**
     * genmove 的容错读取。
     *
     * 如果高温/外部 stop 的空应答刚好夹在 genmove 前面，第一次读取可能只拿到
     * 一个空的 "="；此时不要立刻把它当成空落子，短暂等待真正的 genmove 应答。
     */
    suspend fun genmoveCommand(color: String, timeoutMs: Long): String = commandMutex.withLock {
        if (process?.isAlive != true) throw EngineNotReadyException()
        val command = "genmove $color"
        drainBufferedLines()
        write(command)
        var response = readResponse(command, timeoutMs)
        if (response.isBlank()) {
            // 只读一次补包；超时就返回空值交给上层报错，不在这里强杀进程。
            response = runCatching { readResponse(command, 1_500, destroyOnTimeout = false) }
                .getOrDefault("")
        }
        response.trim()
    }

    /**
     * 执行 kata-analyze。KataGo 的分析输出是流式的，不会自己结束，
     * 因此这里读固定时长后发送 stop，并排空剩余输出。
     *
     * @param intervalMs KataGo 的 interval 参数单位是 10ms（centisecond）。
     */
    suspend fun analyze(
        intervalMs: Int,
        durationMs: Long,
        ownership: Boolean = true
    ): List<String> = commandMutex.withLock {
        if (process?.isAlive != true) throw EngineNotReadyException()
        drainBufferedLines()
        // maxmoves 限制单次输出候选数，避免完整 361 点分析把 stdout 撑爆。
        val command = "kata-analyze interval ${(intervalMs / 10).coerceAtLeast(1)} " +
            "ownership $ownership maxmoves $MAX_ANALYSIS_MOVES"
        write(command)
        val first = receiveAck(command, 15_000)
        if (first.startsWith("?")) throw GtpCommandException(command, first.removePrefix("?").trim())
        val output = mutableListOf<String>()
        val firstValue = first.removePrefix("=").trim()
        if (firstValue.startsWith("info ")) output.add(firstValue)
        val deadline = System.currentTimeMillis() + durationMs
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) break
            val line = withTimeoutOrNull(remaining) { lines.receiveCatching().getOrNull() } ?: break
            if (line.isBlank()) break
            if (line.startsWith("info ")) output.add(line)
        }
        // 让 KataGo 停止当前分析；随后丢弃 stop 的应答和可能残留的分析行。
        runCatching { write("stop") }
        withTimeoutOrNull(2_000) {
            while (true) {
                val line = lines.receiveCatching().getOrNull() ?: break
                if (line.isBlank()) break
            }
        }
        drainBufferedLines()
        output
    }

    /** 从 UI/温度监控线程请求停止当前搜索；不阻塞。 */
    fun stopSearch() {
        scope.launch {
            writeMutex.withLock {
                runCatching {
                    stdin?.write("stop\n")
                    stdin?.flush()
                }
            }
        }
    }

    fun isAlive(): Boolean = process?.isAlive == true

    /** 不挂起地强制结束，供 Application.onStop 调用。 */
    fun shutdownNow() {
        runCatching { stdin?.close() }
        runCatching { process?.destroy() }
        runCatching {
            if (process?.waitFor(800, TimeUnit.MILLISECONDS) != true) process?.destroyForcibly()
        }
        scope.cancel()
        _state.value = GtpConnectionState.DISCONNECTED
    }

    suspend fun shutdown() = withContext(Dispatchers.IO) {
        runCatching { stdin?.close() }
        runCatching { process?.destroy() }
        runCatching {
            if (process?.waitFor(800, TimeUnit.MILLISECONDS) != true) process?.destroyForcibly()
        }
        readerJob?.cancel()
        stderrJob?.cancel()
        waiterJob?.cancel()
        scope.cancel()
        _state.value = GtpConnectionState.DISCONNECTED
    }

    private suspend fun write(command: String) = writeMutex.withLock {
        val writer = stdin ?: throw EngineNotReadyException()
        writer.write(command)
        writer.newLine()
        writer.flush()
    }

    private suspend fun readResponse(
        command: String,
        timeoutMs: Long,
        destroyOnTimeout: Boolean = true
    ): String {
        val first = receiveAck(command, timeoutMs, destroyOnTimeout)
        if (first.startsWith("?")) {
            throw GtpCommandException(command, first.removePrefix("?").trim())
        }
        val body = StringBuilder()
        // 标准 GTP 应答格式是 "= D4" / "= ..."，值可能就在第一行。
        val firstValue = first.removePrefix("=").trim()
        if (firstValue.isNotEmpty()) body.appendLine(firstValue)
        while (true) {
            val line = receiveLine(command, timeoutMs, destroyOnTimeout)
            if (line.isBlank()) break
            body.appendLine(line)
        }
        return body.toString()
    }

    /**
     * GTP 应答必须以 "=" 或 "?" 开头。KataGo 启动日志、分析输出和 stop 的
     * 尾包都可能先到达 stdout，不能把它们当成命令的应答。
     */
    private suspend fun receiveAck(
        command: String,
        timeoutMs: Long,
        destroyOnTimeout: Boolean = true
    ): String {
        while (true) {
            val line = receiveLine(command, timeoutMs, destroyOnTimeout)
            if (line.startsWith("=") || line.startsWith("?")) return line
        }
    }

    /** 清掉通道里已经到达但没有被消费的旧应答/分析行。 */
    private fun drainBufferedLines() {
        while (lines.tryReceive().isSuccess) {
            // 丢弃旧数据；调用点都在 commandMutex 内，不会误丢当前命令的应答。
        }
    }

    private suspend fun receiveLine(
        command: String,
        timeoutMs: Long,
        destroyOnTimeout: Boolean = true
    ): String {
        return try {
            withTimeout(timeoutMs) { lines.receive() }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.TimeoutCancellationException && destroyOnTimeout) {
                runCatching { process?.destroyForcibly() }
            }
            throw KataGoException("等待 GTP 响应超时/断开: $command", t)
        }
    }

    private suspend fun readStdout(reader: BufferedReader) {
        try {
            while (scope.isActive) {
                val line = reader.readLine() ?: break
                lines.send(line)
            }
        } catch (t: Throwable) {
            logger("读取 stdout 失败: ${t.message}")
        } finally {
            lines.close()
        }
    }

    private suspend fun readStderr(reader: BufferedReader) {
        try {
            while (scope.isActive) {
                val line = reader.readLine() ?: break
                logger("KataGo stderr: $line")
            }
        } catch (_: Throwable) {
            // stderr 只做诊断，不影响主流程。
        }
    }

    companion object {
        private const val MAX_ANALYSIS_MOVES = 12
    }
}
