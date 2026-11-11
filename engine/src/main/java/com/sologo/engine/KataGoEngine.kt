package com.sologo.engine

import android.content.Context
import android.util.Log
import com.sologo.core.model.CandidateMove
import com.sologo.core.model.GoCoordinates
import com.sologo.core.model.GoPoint
import com.sologo.core.model.GameRules
import com.sologo.core.model.HandicapStones
import com.sologo.core.model.PlayStyle
import com.sologo.core.model.PositionAnalysis
import com.sologo.core.model.StoneColor
import com.sologo.core.model.normalizeOwnershipToBlack
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
    private var playStyle = PlayStyle.NATURAL
    private var humanProfile = "rank_5k"
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
            val arguments = mutableListOf(
                "gtp",
                "-config", resources.config.absolutePath,
                "-model", resources.model.absolutePath
            )
            resources.humanModel?.let {
                arguments += listOf("-human-model", it.absolutePath)
            }
            val client = GtpClient(
                binary = resources.binary,
                arguments = arguments,
                workDir = resources.workDir,
                startupTimeoutMs = config.startupTimeoutMs,
                logger = { Log.d(TAG, it) }
            )
            gtp = client
            client.start()
            client.command("boardsize $boardSize", 15_000)
            client.command("komi 7.5", 15_000)
            client.command("clear_board", 15_000)
            setVisitsInternal(visits)
            setPlayStyleInternal(playStyle)
            setHumanStyleInternal()
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

    suspend fun newGame(
        size: Int,
        komi: Float,
        maxVisits: Int = visits,
        handicap: Int = 0
    ) {
        require(size in 2..19) { "不支持的棋盘大小: $size" }
        ensureReady()
        val client = gtp ?: throw EngineNotReadyException()
        boardSize = size
        visits = maxVisits.coerceIn(1, config.maxVisits)
        client.command("boardsize $size")
        client.command("komi $komi")
        client.command("clear_board")
        if (handicap > 0) {
            val points = HandicapStones.points(size, handicap)
            if (points.size != handicap) {
                throw KataGoException("棋盘 $size 路不支持 $handicap 子让子")
            }
            val coordinates = points.joinToString(" ") { GoCoordinates.toGtp(it, size) }
            client.command("set_free_handicap $coordinates")
        }
        setVisitsInternal(visits)
        setPlayStyleInternal(playStyle)
        setHumanStyleInternal()
    }

    /** 选择下法变化档位；引擎已启动时立即生效，下一局也会沿用。 */
    suspend fun setPlayStyle(style: PlayStyle) {
        playStyle = style
        if (_state.value == EngineState.READY) {
            setPlayStyleInternal(style)
        }
    }

    /** 设置 HumanSL 的人类水平 profile，例如 rank_10k / rank_1k / rank_1d。 */
    suspend fun setHumanProfile(profile: String) {
        humanProfile = profile
        if (_state.value == EngineState.READY) {
            setHumanStyleInternal()
        }
    }

    /** 中国规则显式设置；KataGo 默认沿用 gtp.cfg 中的默认规则。 */
    suspend fun setRules(rules: GameRules) {
        val gtpName = rules.gtpName ?: return
        if (_state.value == EngineState.READY) {
            runCatching { gtp!!.command("kata-set-rules $gtpName", 10_000) }
        }
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
     * 对一个局面做短时分析。返回的 ownership 为从上到下、从左到右的黑方视角值
     * （正数黑、负数白）；KataGo 原始输出是以当前行棋方为正方向，这里已统一翻转。
     */
    suspend fun analyze(
        maxVisits: Int = visits,
        intervalMs: Int = config.analysisIntervalMs,
        durationMs: Long = config.analysisDurationMs,
        turn: StoneColor = StoneColor.BLACK,
        includeOwnership: Boolean = true
    ): PositionAnalysis {
        ensureReady()
        // HumanSL 会让 kata-analyze 的首次输出延迟到几十秒，且 maxVisits 对该
        // 命令并不总是立即生效。形势图只需要当前局面的神经网络预测，因此直接
        // 读取普通模型的整盘输出，避免为了 81/169/361 个领地值长时间占用 CPU。
        if (includeOwnership) {
            return analyzeOwnershipFromRawNeuralNet(turn)
        }
        setVisitsInternal(maxVisits.coerceIn(1, config.maxVisits))
        val infoLines = gtp!!.analyze(intervalMs, durationMs, ownership = false)
        return parseAnalysis(infoLines, turn)
    }

    /**
     * 复盘页的快速下一手候选分析。
     *
     * HumanSL 下 kata-analyze 的首包可能等待很久，因此这里读取普通模型的
     * policy 概率，列出最可能的若干落点。它用于“下一手在哪里”，不用于
     * 连续 PV 或逐手深度分析。
     */
    suspend fun analyzePolicy(turn: StoneColor = StoneColor.BLACK): PositionAnalysis {
        ensureReady()
        var lastError: Throwable? = null
        repeat(2) { attempt ->
            try {
                val raw = readRawNeuralNet(requireOwnership = false)
                return parsePolicyFromRawNeuralNet(raw, turn)
            } catch (t: KataGoException) {
                lastError = t
                Log.w(TAG, "读取 raw policy 失败，第 ${attempt + 1} 次: ${t.message}")
            }
        }
        throw lastError ?: KataGoException("KataGo 未返回 policy 数据")
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
        return "binary=${r.binary.name}, native=${r.usedNativeLibrary}, model=${r.model.name}, human=${r.humanModel?.name ?: "none"}"
    }

    private suspend fun ensureReady() {
        if (_state.value != EngineState.READY || gtp?.isAlive() != true) {
            initialize()
        }
    }

    private suspend fun setVisitsInternal(value: Int) {
        // KataGo 的 GTP 扩展；若某个旧版本不支持，保留 config 里的默认值即可。
        runCatching { gtp!!.command("kata-set-param maxVisits $value", 10_000) }
        // HumanSL 的模型推理时间不一定受 maxVisits 约束，显式再限制一次。
        runCatching { gtp!!.command("kata-set-param maxTime 1.5", 10_000) }
    }

    private suspend fun setPlayStyleInternal(style: PlayStyle) {
        val params = listOf(
            "chosenMoveTemperatureEarly" to style.earlyTemperature,
            "chosenMoveTemperature" to style.temperature,
            "chosenMoveTemperatureHalflife" to style.temperatureHalflife,
            "chosenMoveTemperatureOnlyBelowProb" to style.onlyBelowProb,
            "rootNumSymmetriesToSample" to style.rootSymmetries,
            "useLcbForSelection" to style.useLcb
        )
        params.forEach { (key, value) ->
            // 参数兼容性按 KataGo 版本处理；不支持时保留默认值，不影响对局。
            runCatching { gtp!!.command("kata-set-param $key $value", 10_000) }
        }
    }

    private suspend fun setHumanStyleInternal() {
        // 没有 HumanSL 权重时保持普通 KataGo 行为。
        if (installed?.humanModel == null) return
        val params = listOf(
            "humanSLProfile" to humanProfile,
            "humanSLChosenMoveProp" to 1.0,
            "humanSLChosenMoveIgnorePass" to true,
            "humanSLChosenMovePiklLambda" to 100000000,
            "chosenMoveTemperatureEarly" to 0.85,
            "chosenMoveTemperature" to 0.70,
            "chosenMoveTemperatureHalflife" to 80,
            "chosenMoveTemperatureOnlyBelowProb" to 0.01,
            "rootNumSymmetriesToSample" to 2,
            "useLcbForSelection" to false,
            "useUncertainty" to false,
            "useNoisePruning" to false,
            "subtreeValueBiasFactor" to 0.0,
            "ignorePreRootHistory" to false
        )
        params.forEach { (key, value) ->
            runCatching { gtp!!.command("kata-set-param $key $value", 10_000) }
        }
    }

    private fun parseAnalysis(lines: List<String>, turn: StoneColor): PositionAnalysis {
        // KataGo 的 kata-analyze 可能在同一行连续输出多个 "info" 记录，
        // 因此不能按“一行一个候选”解析；这里按 info 分段，并用 order 去重保留最新值。
        val candidateByOrder = linkedMapOf<Int, CandidateMove>()
        var ownership = parseFullOwnership(lines.joinToString(" "), turn).orEmpty()
        var totalVisits = 0
        for (line in lines) {
            line.split(" info ").forEachIndexed { index, segment ->
                val normalized = if (segment.startsWith("info ")) segment else "info $segment"
                val parsed = parseInfoLine(normalized, turn) ?: return@forEachIndexed
                candidateByOrder[parsed.order] = parsed
                if (ownership.isEmpty() && parsed.ownership.isNotEmpty()) {
                    ownership = parsed.ownership
                }
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
                    ownership = parseFullOwnership(line, turn).orEmpty()
                    i = tokens.size
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

    /**
     * kata-raw-nn 只执行一次普通神经网络推理，不进入 HumanSL 的慢速搜索。
     * 返回值固定使用白方视角：whiteOwnership 为正表示白方势力，因此这里
     * 统一取反后保存为 App 内部的黑方视角。
     */
    private suspend fun analyzeOwnershipFromRawNeuralNet(
        turn: StoneColor
    ): PositionAnalysis {
        val raw = readRawNeuralNet(requireOwnership = true)
        val tokens = raw.trim().split(Regex("\\s+"))
        val expected = boardSize * boardSize
        val ownershipStart = tokens.indexOf("whiteOwnership")
        if (ownershipStart < 0) {
            throw KataGoException("KataGo 未返回完整领地数据：缺少 whiteOwnership")
        }

        val whiteOwnership = ArrayList<Float>(expected)
        var index = ownershipStart + 1
        while (index < tokens.size && whiteOwnership.size < expected) {
            val value = parseFloatToken(tokens[index]) ?: break
            whiteOwnership.add(value)
            index++
        }
        if (whiteOwnership.size < expected) {
            throw KataGoException(
                "KataGo 未返回完整领地数据：${whiteOwnership.size}/$expected"
            )
        }

        val blackOwnership = whiteOwnership.map { value ->
            if (value.isFinite()) (-value).coerceIn(-1f, 1f) else 0f
        }
        val whiteWin = valueAfter(tokens, "whiteWin")?.toDoubleOrNull()
        val whiteLead = valueAfter(tokens, "whiteLead")?.toDoubleOrNull()
        val rootCandidate = if (whiteWin != null && whiteLead != null) {
            CandidateMove(
                move = null,
                winrate = (1.0 - whiteWin).coerceIn(0.0, 1.0),
                scoreLead = -whiteLead,
                visits = 0,
                order = 0
            )
        } else {
            null
        }

        return PositionAnalysis(
            turn = turn,
            candidates = listOfNotNull(rootCandidate),
            ownership = blackOwnership,
            ownershipSize = boardSize,
            visits = 0
        )
    }

    private fun valueAfter(tokens: List<String>, key: String): String? {
        val index = tokens.indexOf(key)
        return if (index >= 0) tokens.getOrNull(index + 1) else null
    }

    private suspend fun readRawNeuralNet(requireOwnership: Boolean): String =
        gtp?.rawNeuralNet(
            expectedPoints = boardSize * boardSize,
            requireOwnership = requireOwnership
        ) ?: throw EngineNotReadyException()

    private fun parseFloatToken(token: String): Float? =
        if (token.equals("NAN", ignoreCase = true)) {
            Float.NaN
        } else {
            token.toFloatOrNull()
        }

    private fun parsePolicyFromRawNeuralNet(
        raw: String,
        turn: StoneColor
    ): PositionAnalysis {
        val tokens = raw.trim().split(Regex("\\s+"))
        val expected = boardSize * boardSize
        val policyStart = tokens.indexOf("policy")
        if (policyStart < 0) {
            throw KataGoException("KataGo 未返回 policy 数据")
        }
        val policy = ArrayList<Float>(expected)
        var index = policyStart + 1
        while (index < tokens.size && policy.size < expected) {
            val value = parseFloatToken(tokens[index]) ?: break
            policy.add(value)
            index++
        }
        if (policy.size < expected) {
            throw KataGoException("KataGo 返回的 policy 不完整：${policy.size}/$expected")
        }

        val whiteWin = valueAfter(tokens, "whiteWin")?.toDoubleOrNull() ?: 0.5
        val whiteLead = valueAfter(tokens, "whiteLead")?.toDoubleOrNull() ?: 0.0
        val blackWinrate = (1.0 - whiteWin).coerceIn(0.0, 1.0)
        val blackLead = -whiteLead
        val candidates = policy.mapIndexedNotNull { pointIndex, probability ->
            if (!probability.isFinite() || probability <= 0f) {
                null
            } else {
                val point = GoPoint(
                    x = pointIndex % boardSize,
                    y = pointIndex / boardSize
                )
                probability to CandidateMove(
                    move = point,
                    winrate = blackWinrate,
                    scoreLead = blackLead,
                    visits = 0,
                    order = 0,
                    pv = listOf(point)
                )
            }
        }
            .sortedByDescending { it.first }
            .take(12)
            .mapIndexed { order, (_, candidate) -> candidate.copy(order = order) }
        Log.d(TAG, "raw policy parsed=$expected candidates=${candidates.size}")

        return PositionAnalysis(
            turn = turn,
            candidates = candidates,
            ownership = emptyList(),
            ownershipSize = boardSize,
            visits = 0
        )
    }

    private fun parseFullOwnership(line: String, turn: StoneColor): List<Float>? {
        val tokens = line.trim().split(Regex("\\s+"))
        val expected = boardSize * boardSize
        var result: List<Float>? = null
        for (start in tokens.indices) {
            if (tokens[start] != "ownership") continue
            val values = ArrayList<Float>(expected)
            var index = start + 1
            while (index < tokens.size && values.size < expected) {
                val value = tokens[index].toFloatOrNull() ?: break
                values.add(value)
                index++
            }
            if (values.size == expected) {
                result = normalizeOwnershipToBlack(values, turn)
            }
        }
        return result
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
