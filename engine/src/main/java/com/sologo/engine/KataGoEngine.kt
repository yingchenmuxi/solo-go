package com.sologo.engine

import android.content.Context
import android.util.Log
import com.sologo.core.model.CandidateMove
import com.sologo.core.model.GoCoordinates
import com.sologo.core.model.GoPoint
import com.sologo.core.model.PositionAnalysis
import com.sologo.core.model.StoneColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class EngineState {
    IDLE,
    INSTALLING,
    STARTING,
    READY,
    ERROR
}

data class GeneratedMove(
    val point: GoPoint?,
    val resigned: Boolean,
    val raw: String
)

/**
 * KataGo 业务门面。UI/ViewModel 只依赖这个类，不直接碰 Process 或 stdin/stdout。
 *
 * 规则、提子、劫争、终局判断都由 KataGo 执行；本类负责命令编排和异常转换。
 */
class KataGoEngine(
    context: Context,
    private val config: EngineConfig = EngineConfig()
) {
    private val installer = KataGoInstaller(context, config)
    private val initMutex = Mutex()
    private var gtp: GtpClient? = null
    private var installed: InstalledEngine? = null
    private var boardSize = 19
    private var visits = config.defaultVisits
    private val _state = MutableStateFlow(EngineState.IDLE)
    val state: StateFlow<EngineState> = _state.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    suspend fun initialize() = initMutex.withLock {
        if (_state.value == EngineState.READY && gtp?.isAlive() == true) return@withLock
        try {
            _state.value = EngineState.INSTALLING
            val resources = installer.install()
            installed = resources
            _state.value = EngineState.STARTING
            val client = GtpClient(
                binary = resources.binary,
                arguments = listOf("gtp", "-config", resources.config.absolutePath, "-model", resources.model.absolutePath),
                workDir = resources.workDir,
                logger = { Log.d(TAG, it) }
            )
            gtp = client
            client.start()
            client.command("boardsize $boardSize", 15_000)
            client.command("komi 7.5", 15_000)
            client.command("clear_board", 15_000)
            setVisitsInternal(visits)
            _state.value = EngineState.READY
            _lastError.value = null
        } catch (t: Throwable) {
            _state.value = EngineState.ERROR
            _lastError.value = t.message ?: t.javaClass.simpleName
            gtp?.shutdown()
            gtp = null
            throw if (t is KataGoException) t else KataGoException("KataGo 初始化失败", t)
        }
    }

    suspend fun newGame(size: Int, komi: Float, maxVisits: Int = visits) {
        require(size in 2..19) { "不支持的棋盘大小: $size" }
        ensureReady()
        val client = gtp ?: throw EngineNotReadyException()
        boardSize = size
        visits = maxVisits.coerceIn(1, config.maxVisits)
        client.command("boardsize $size")
        client.command("komi $komi")
        client.command("clear_board")
        setVisitsInternal(visits)
    }

    suspend fun setVisits(value: Int) {
        visits = value.coerceIn(1, config.maxVisits)
        if (_state.value == EngineState.READY) setVisitsInternal(visits)
    }

    suspend fun play(color: StoneColor, point: GoPoint?): String {
        ensureReady()
        val coord = point?.let { GoCoordinates.toGtp(it, boardSize) } ?: "pass"
        return gtp!!.command("play ${color.gtp} $coord", config.commandTimeoutMs)
    }

    suspend fun genmove(color: StoneColor): GeneratedMove {
        ensureReady()
        val raw = gtp!!.genmoveCommand(color.gtp, config.genmoveTimeoutMs).trim()
        val normalized = raw.substringBefore('\n').trim()
        if (normalized.isBlank()) {
            throw KataGoException("KataGo genmove 返回空结果，可能是进程退出或 GTP 输出被污染")
        }
        if (normalized.equals("resign", ignoreCase = true)) {
            return GeneratedMove(point = null, resigned = true, raw = normalized)
        }
        if (normalized.equals("pass", ignoreCase = true)) {
            return GeneratedMove(point = null, resigned = false, raw = normalized)
        }
        val point = GoCoordinates.parseGtp(normalized, boardSize)
            ?: throw KataGoException("无法解析 KataGo 落子坐标: $normalized")
        return GeneratedMove(
            point = point,
            resigned = false,
            raw = normalized
        )
    }

    suspend fun undo() {
        ensureReady()
        gtp!!.command("undo")
    }

    suspend fun loadsgf(path: String, moveNumber: Int = 1, boardSizeHint: Int? = null) {
        ensureReady()
        if (boardSizeHint != null && boardSizeHint in 2..19) boardSize = boardSizeHint
        gtp!!.command("loadsgf \"${path.replace("\"", "\\\"")}\" $moveNumber", 60_000)
    }

    suspend fun printsgf(): String {
        ensureReady()
        return gtp!!.command("printsgf", 60_000)
    }

    suspend fun finalScore(): String {
        ensureReady()
        return gtp!!.command("final_score", 120_000).trim()
    }

    suspend fun finalStatusList(status: String = "alive"): String {
        ensureReady()
        return gtp!!.command("final_status_list $status", 120_000).trim()
    }

    /**
     * 对一个局面做短时分析。返回的 ownership 为 KataGo 原始顺序（从上到下、从左到右）。
     */
    suspend fun analyze(
        maxVisits: Int = visits,
        intervalMs: Int = config.analysisIntervalMs,
        durationMs: Long = config.analysisDurationMs,
        turn: StoneColor = StoneColor.BLACK
    ): PositionAnalysis {
        ensureReady()
        setVisitsInternal(maxVisits.coerceIn(1, config.maxVisits))
        val infoLines = gtp!!.analyze(intervalMs, durationMs)
        return parseAnalysis(infoLines, turn)
    }

    fun stopSearch() {
        gtp?.stopSearch()
    }

    /** 给 ViewModel 判断是否需要重建进程；状态 Flow 的 READY 可能略滞后于子进程退出。 */
    fun isRunning(): Boolean = _state.value == EngineState.READY && gtp?.isAlive() == true

    suspend fun shutdown() {
        gtp?.shutdown()
        gtp = null
        _state.value = EngineState.IDLE
    }

    fun shutdownNow() {
        gtp?.shutdownNow()
        gtp = null
        _state.value = EngineState.IDLE
    }

    fun resourcesDescription(): String {
        val r = installed ?: return "资源未安装"
        return "binary=${r.binary.name}, native=${r.usedNativeLibrary}, model=${r.model.name}"
    }

    private suspend fun ensureReady() {
        if (_state.value != EngineState.READY || gtp?.isAlive() != true) {
            initialize()
        }
    }

    private suspend fun setVisitsInternal(value: Int) {
        // KataGo 的 GTP 扩展；若某个旧版本不支持，保留 config 里的默认值即可。
        runCatching { gtp!!.command("kata-set-param maxVisits $value", 10_000) }
    }

    private fun parseAnalysis(lines: List<String>, turn: StoneColor): PositionAnalysis {
        // KataGo 的 kata-analyze 可能在同一行连续输出多个 "info" 记录，
        // 因此不能按“一行一个候选”解析；这里按 info 分段，并用 order 去重保留最新值。
        val candidateByOrder = linkedMapOf<Int, CandidateMove>()
        var ownership = emptyList<Float>()
        var totalVisits = 0
        for (line in lines) {
            line.split(" info ").forEachIndexed { index, segment ->
                val normalized = if (segment.startsWith("info ")) segment else "info $segment"
                val parsed = parseInfoLine(normalized, turn) ?: return@forEachIndexed
                candidateByOrder[parsed.order] = parsed
                if (parsed.ownership.isNotEmpty()) ownership = parsed.ownership
                totalVisits = maxOf(totalVisits, parsed.visits)
            }
        }
        val candidates = candidateByOrder.values.toList()
        return PositionAnalysis(
            turn = turn,
            candidates = candidates.sortedBy { it.order }.take(12),
            ownership = ownership,
            ownershipSize = boardSize,
            visits = totalVisits
        )
    }

    private fun parseInfoLine(line: String, turn: StoneColor): CandidateMove? {
        if (!line.startsWith("info ")) return null
        val tokens = line.removePrefix("info ").trim().split(Regex("\\s+"))
        var move: GoPoint? = null
        var winrate = 0.0
        var scoreLead = 0.0
        var visits = 0
        var order = Int.MAX_VALUE
        var pv = emptyList<GoPoint>()
        var ownership = emptyList<Float>()
        var i = 0
        while (i < tokens.size) {
            when (val key = tokens[i]) {
                "move" -> {
                    val value = tokens.getOrNull(++i).orEmpty()
                    move = GoCoordinates.parseGtp(value, boardSize)
                }
                "winrate" -> winrate = tokens.getOrNull(++i)?.toDoubleOrNull() ?: winrate
                "scoreLead", "scoreMean" -> scoreLead = tokens.getOrNull(++i)?.toDoubleOrNull() ?: scoreLead
                "visits" -> visits = tokens.getOrNull(++i)?.toIntOrNull() ?: visits
                "order" -> order = tokens.getOrNull(++i)?.toIntOrNull() ?: order
                "pv" -> {
                    val moves = mutableListOf<GoPoint>()
                    i++
                    while (i < tokens.size && tokens[i] !in ANALYSIS_KEYS) {
                        GoCoordinates.parseGtp(tokens[i], boardSize)?.let(moves::add)
                        i++
                    }
                    pv = moves
                    // 当前 i 可能停在 ownership/order 等下一个键上，交给下一轮处理。
                    continue
                }
                "ownership" -> {
                    val values = mutableListOf<Float>()
                    i++
                    while (i < tokens.size) {
                        tokens[i].toFloatOrNull()?.let(values::add)
                        i++
                    }
                    ownership = values
                    continue
                }
                else -> Unit
            }
            i++
        }
        // 没有 move 字段的 info 行（例如只有 ownership）不能作为候选着法。
        if (order == Int.MAX_VALUE && move == null && winrate == 0.0) return null
        return CandidateMove(
            move = move,
            winrate = winrate.coerceIn(0.0, 1.0),
            scoreLead = scoreLead,
            visits = visits,
            order = if (order == Int.MAX_VALUE) candidatesFallbackOrder(visits) else order,
            pv = pv,
            ownership = ownership
        )
    }

    private fun candidatesFallbackOrder(visits: Int): Int = 10_000 - visits.coerceAtMost(10_000)

    companion object {
        private const val TAG = "KataGoEngine"
        private val ANALYSIS_KEYS = setOf(
            "move", "winrate", "scoreLead", "scoreMean", "scoreStdev", "visits", "order",
            "pv", "ownership", "utility", "utilityLcb", "scoreSelfplay", "scoreUtility",
            "weight", "lcb", "prior", "edgeVisits"
        )
    }
}
