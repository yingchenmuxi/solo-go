package com.sologo.app.ui.game

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sologo.app.audio.MoveSoundPlayer
import com.sologo.app.data.SettingsRepository
import com.sologo.app.data.SgfRepository
import com.sologo.core.model.BoardPosition
import com.sologo.core.model.GameRecord
import com.sologo.core.model.GameRules
import com.sologo.core.model.GoPoint
import com.sologo.core.model.HandicapOptions
import com.sologo.core.model.HandicapStones
import com.sologo.core.model.MoveRecord
import com.sologo.core.model.PositionAnalysis
import com.sologo.core.model.StoneColor
import com.sologo.core.model.TerritorySummary
import com.sologo.engine.BatteryTemperatureMonitor
import com.sologo.engine.EngineConnectionLostException
import com.sologo.engine.EngineState
import com.sologo.engine.KataGoEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GameUiState(
    val boardSize: Int = 19,
    val komi: Float = 7.5f,
    val rules: GameRules = GameRules.CHINESE,
    val handicap: Int = 0,
    val setupBlack: List<GoPoint> = emptyList(),
    val humanColor: StoneColor = StoneColor.BLACK,
    val board: BoardPosition = BoardPosition.empty(19),
    val turn: StoneColor = StoneColor.BLACK,
    val moves: List<MoveRecord> = emptyList(),
    val status: String = "准备开始",
    val busy: Boolean = true,
    val lastAiSeconds: Int? = null,
    val started: Boolean = false,
    val gameOver: Boolean = false,
    val scoreResult: String? = null,
    val analysisVisible: Boolean = false,
    val analysisRunning: Boolean = false,
    val analysisOwnership: List<Float> = emptyList(),
    val analysisSummary: TerritorySummary? = null,
    val analysisVisits: Int = 0,
    val showEndGamePrompt: Boolean = false,
    val batteryTemp: Float? = null,
    val highTemp: Boolean = false,
    val error: String? = null
)

sealed interface GameEvent {
    data class Saved(val file: File, val automatic: Boolean = false) : GameEvent
    data class Message(val text: String) : GameEvent
}

/**
 * 人机对局 ViewModel。Compose 只消费 StateFlow，不直接调用引擎。
 */
class GameViewModel(
    private val engine: KataGoEngine,
    private val settings: SettingsRepository,
    private val sgfRepository: SgfRepository,
    private val battery: BatteryTemperatureMonitor,
    private val moveSound: MoveSoundPlayer
) : ViewModel() {
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private val positionHistory = mutableListOf<BoardPosition>()
    private var isForeground = false
    private var autoSavedForGame = false
    private var analysisJob: Job? = null

    init {
        battery.start()
        viewModelScope.launch {
            engine.state.collect { state ->
                when (state) {
                    EngineState.ERROR -> _uiState.update {
                        it.copy(busy = false, status = "引擎异常", error = engine.lastError.value)
                    }
                    EngineState.READY -> Unit
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            battery.temperature.collect { temp ->
                val high = temp != null && temp > HIGH_TEMPERATURE_C
                val wasHigh = _uiState.value.highTemp
                _uiState.update { it.copy(batteryTemp = temp, highTemp = high) }
                if (high && !wasHigh) {
                    // 不在 genmove 进行中直接发送 stop：stop 的空应答会与 genmove 的
                    // 应答混在同一 stdout 流里，导致下一次读到空结果。当前搜索由
                    // maxTime=2s 兜底，结束后不再发起新搜索。
                    _uiState.update {
                        it.copy(status = "电池温度 ${"%.1f".format(temp)}℃，已暂停 AI 搜索")
                    }
                    _events.tryEmit(GameEvent.Message("电池温度过高，AI 已暂停"))
                } else if (!high && wasHigh) {
                    _uiState.update { it.copy(status = "温度已恢复，可以继续对局") }
                    val recovered = _uiState.value
                    if (recovered.started && !recovered.gameOver && !recovered.busy &&
                        recovered.turn != recovered.humanColor
                    ) {
                        maybeAiMove()
                    }
                }
            }
        }
    }

    /** 由对局页在进入时调用；重复调用不会重复开新局。 */
    fun startGame(
        size: Int? = null,
        humanColor: StoneColor? = null
    ) {
        if (_uiState.value.started && !_uiState.value.gameOver) return
        viewModelScope.launch {
            analysisJob?.cancel()
            val currentSettings = settings.settings.first()
            autoSavedForGame = false
            val resolvedSize = size ?: currentSettings.boardSize
            val resolvedRules = currentSettings.rules
            val resolvedHandicap = HandicapOptions.normalize(currentSettings.handicap)
            val resolvedKomi = resolvedRules.komiFor(resolvedHandicap)
            // 让子棋默认由人类执黑，AI 执白。
            val resolvedHumanColor = if (resolvedHandicap != 0) {
                StoneColor.BLACK
            } else {
                humanColor ?: currentSettings.humanColor
            }
            val setupPoints = HandicapStones.points(resolvedSize, resolvedHandicap)
            _uiState.update {
                it.copy(
                    busy = true,
                    status = "正在准备 KataGo…",
                    error = null,
                    boardSize = resolvedSize,
                    komi = resolvedKomi,
                    rules = resolvedRules,
                    handicap = resolvedHandicap,
                    setupBlack = setupPoints,
                    humanColor = resolvedHumanColor,
                    board = BoardPosition.fromSetup(
                        size = resolvedSize,
                        black = setupPoints.toSet(),
                        white = emptySet()
                    ),
                    // 让子棋白棋先行；分先/让先仍由黑棋先行。
                    turn = if (resolvedHandicap > 0) StoneColor.WHITE else StoneColor.BLACK,
                    moves = emptyList(),
                    started = true,
                    gameOver = false,
                    lastAiSeconds = null,
                    scoreResult = null,
                    analysisVisible = false,
                    analysisRunning = false,
                    analysisOwnership = emptyList(),
                    analysisSummary = null,
                    analysisVisits = 0,
                    showEndGamePrompt = false
                )
            }
            positionHistory.clear()
            positionHistory.add(
                BoardPosition.fromSetup(
                    size = resolvedSize,
                    black = setupPoints.toSet(),
                    white = emptySet()
                )
            )
            try {
                engine.initialize()
                engine.setPlayStyle(currentSettings.aiLevel.playStyle)
                engine.setHumanProfile(currentSettings.aiLevel.humanProfile)
                engine.setRules(resolvedRules)
                val effectiveVisits = currentSettings.aiLevel.visits
                engine.newGame(
                    resolvedSize,
                    resolvedKomi,
                    effectiveVisits,
                    resolvedHandicap.coerceAtLeast(0)
                )
                _uiState.update {
                    it.copy(
                        busy = false,
                        status = if (resolvedHandicap > 0) {
                            "让 $resolvedHandicap 子，KataGo 执白先行"
                        } else if (resolvedHandicap == -1) {
                            "让先，你执黑，请落子"
                        } else {
                            "你执${resolvedHumanColor.chineseName}，请落子"
                        }
                    )
                }
                if (resolvedHumanColor == StoneColor.WHITE || resolvedHandicap > 0) {
                    maybeAiMove()
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        status = "引擎启动失败",
                        error = t.message ?: "未知错误"
                    )
                }
            }
        }
    }

    fun onBoardTap(point: GoPoint) {
        val state = _uiState.value
        if (!state.started || state.busy || state.gameOver || state.highTemp) return
        if (state.turn != state.humanColor) return
        if (!state.board.isEmpty(point)) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, status = "正在落子…") }
            try {
                engine.play(state.humanColor, point)
                applyMove(state.humanColor, point)
                maybeAiMove()
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(busy = false, status = "这手棋未能执行", error = t.message)
                }
            }
        }
    }

    fun pass() {
        val state = _uiState.value
        if (!state.started || state.busy || state.gameOver || state.highTemp) return
        if (state.turn != state.humanColor) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, status = "你选择停一手") }
            try {
                engine.play(state.humanColor, null)
                applyMove(state.humanColor, null)
                if (!_uiState.value.showEndGamePrompt) {
                    maybeAiMove()
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(busy = false, error = t.message) }
            }
        }
    }

    /** 悔棋：一次退回“人类落子前”，如果 AI 已经回应则连续 undo 两手。 */
    fun undo() {
        val state = _uiState.value
        if (!state.started || state.busy) return
        val lastHumanIndex = state.moves.indexOfLast { it.color == state.humanColor }
        if (lastHumanIndex < 0) {
            _events.tryEmit(GameEvent.Message("还没有你的落子，无法悔棋"))
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, status = "正在悔棋…") }
            try {
                val moves = state.moves.toMutableList()
                // 回退到最后一手人类落子之前，连同其后的 AI 应手一起撤销。
                val removeCount = moves.size - lastHumanIndex
                repeat(removeCount) {
                    engine.undo()
                    moves.removeAt(moves.lastIndex)
                    if (positionHistory.size > 1) {
                        positionHistory.removeAt(positionHistory.lastIndex)
                    }
                }
                val restored = positionHistory.lastOrNull() ?: BoardPosition.empty(state.boardSize)
                autoSavedForGame = false
                analysisJob?.cancel()
                _uiState.update {
                    it.copy(
                        busy = false,
                        board = restored,
                        moves = moves,
                        turn = state.humanColor,
                        gameOver = false,
                        lastAiSeconds = null,
                        status = "已悔棋，请重新落子",
                        scoreResult = null,
                        analysisVisible = false,
                        analysisRunning = false,
                        analysisOwnership = emptyList(),
                        analysisSummary = null,
                        analysisVisits = 0,
                        showEndGamePrompt = false
                    )
                }
            } catch (t: Throwable) {
                _uiState.update { it.copy(busy = false, status = "悔棋失败", error = t.message) }
            }
        }
    }

    fun saveGame() {
        val state = _uiState.value
        if (state.moves.isEmpty()) {
            _events.tryEmit(GameEvent.Message("还没有落子"))
            return
        }
        viewModelScope.launch {
            try {
                val file = sgfRepository.save(buildGameRecord(state))
                if (state.gameOver) autoSavedForGame = true
                _events.emit(GameEvent.Saved(file))
            } catch (t: Throwable) {
                _events.emit(GameEvent.Message("保存失败: ${t.message}"))
            }
        }
    }

    /** 对局结束路径统一调用；开关关闭或本局已自动保存时直接跳过。 */
    private suspend fun autoSaveGameIfEnabled() {
        val state = _uiState.value
        if (!state.started || !state.gameOver || state.moves.isEmpty() || autoSavedForGame) return
        val current = settings.settings.first()
        if (!current.autoSaveGames) return
        autoSavedForGame = true
        try {
            val file = sgfRepository.save(buildGameRecord(state))
            _events.emit(GameEvent.Saved(file, automatic = true))
            _uiState.update {
                it.copy(status = "${it.status} · 已自动保存棋谱")
            }
        } catch (t: Throwable) {
            autoSavedForGame = false
            _events.emit(GameEvent.Message("自动保存失败: ${t.message}"))
        }
    }

    private fun buildGameRecord(state: GameUiState): GameRecord {
        val blackName = if (state.humanColor == StoneColor.BLACK) "你" else "KataGo"
        val whiteName = if (state.humanColor == StoneColor.WHITE) "你" else "KataGo"
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return GameRecord(
            size = state.boardSize,
            komi = state.komi,
            rules = state.rules.sgfName,
            handicap = state.handicap.coerceAtLeast(0),
            blackName = blackName,
            whiteName = whiteName,
            result = resultText(state),
            date = date,
            setupBlack = state.setupBlack,
            moves = state.moves
        )
    }

    private fun resultText(state: GameUiState): String {
        state.scoreResult?.let { return it }
        if (!state.gameOver) return ""
        return when {
            state.status.contains("你已认输") ->
                if (state.humanColor == StoneColor.BLACK) "W+R" else "B+R"
            state.status.contains("KataGo 认输") ->
                if (state.humanColor == StoneColor.BLACK) "B+R" else "W+R"
            else -> "?"
        }
    }

    /**
     * 形势分析开关。分析命令显式请求 ownership true，
     * 返回数组按行优先排列，长度必须覆盖整个棋盘。
     */
    fun toggleAnalysis() {
        val state = _uiState.value
        if (!state.started) return
        if (state.analysisVisible || state.analysisRunning) {
            analysisJob?.cancel()
            _uiState.update {
                it.copy(
                    busy = false,
                    analysisVisible = false,
                    analysisRunning = false,
                    analysisOwnership = emptyList(),
                    analysisSummary = null,
                    analysisVisits = 0,
                    status = "形势分析已关闭"
                )
            }
            return
        }
        if (state.busy) {
            _events.tryEmit(GameEvent.Message("KataGo 正在思考，请稍后再分析"))
            return
        }
        if (state.highTemp) {
            _events.tryEmit(GameEvent.Message("电池温度过高，已暂停 AI 搜索"))
            return
        }

        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    busy = true,
                    analysisRunning = true,
                    analysisVisible = false,
                    analysisOwnership = emptyList(),
                    analysisSummary = null,
                    analysisVisits = 0,
                    error = null,
                    status = "正在分析形势…"
                )
            }
            try {
                val visits = settings.settings.first().aiLevel.analysisVisits.coerceIn(80, 200)
                if (!engine.isRunning()) {
                    restoreEngineForPosition(state)
                }
                val analysis = analyzeOwnershipWithRecovery(visits)
                val expected = state.boardSize * state.boardSize
                if (analysis.ownership.size < expected) {
                    throw IllegalStateException(
                        "KataGo 未返回完整领地数据：${analysis.ownership.size}/$expected"
                    )
                }
                val summary = TerritorySummary.fromOwnership(
                    ownership = analysis.ownership,
                    boardSize = state.boardSize,
                    komi = state.komi
                )
                _uiState.update {
                    it.copy(
                        busy = false,
                        analysisRunning = false,
                        analysisVisible = true,
                        analysisOwnership = analysis.ownership,
                        analysisSummary = summary,
                        analysisVisits = analysis.visits,
                        status = summary?.leadText() ?: "形势分析完成"
                    )
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        analysisRunning = false,
                        analysisVisible = false,
                        analysisOwnership = emptyList(),
                        analysisSummary = null,
                        analysisVisits = 0,
                        status = "形势分析失败",
                        error = t.message
                    )
                }
            }
        }
    }

    private suspend fun analyzeOwnershipWithRecovery(visits: Int): PositionAnalysis {
        suspend fun request() = engine.analyze(
            maxVisits = visits,
            intervalMs = 200,
            durationMs = 2_000,
            turn = _uiState.value.turn,
            includeOwnership = true
        )
        return try {
            request()
        } catch (t: EngineConnectionLostException) {
            if (!isForeground) throw t
            restoreEngineForPosition(_uiState.value)
            request()
        }
    }

    fun resign() {
        val state = _uiState.value
        if (!state.started || state.gameOver) return
        _uiState.update {
            it.copy(
                gameOver = true,
                status = "你已认输"
            )
        }
        viewModelScope.launch { autoSaveGameIfEnabled() }
    }

    /**
     * 使用当前局面的形势判断结果进行数子。
     *
     * 不再调用 final_score / final_status_list：这两个 GTP 命令在局面尚未
     * 正式终局或引擎状态异常时容易超时。这里直接复用 whiteOwnership，
     * 按中国规则统计黑白双方预计占地并计算贴目后的差距。
     */
    fun countScore() {
        countScoreInternal(endGame = false)
    }

    /** 连续两次停一手后，由确认弹窗进入正式数子。 */
    fun confirmEndGameCount() {
        _uiState.update { it.copy(showEndGamePrompt = false) }
        countScoreInternal(endGame = true)
    }

    /** 用户选择暂不数子时结束对局，保留棋盘和悔棋能力。 */
    fun dismissEndGamePrompt() {
        _uiState.update {
            it.copy(
                showEndGamePrompt = false,
                busy = false,
                gameOver = true,
                status = "双方连续停一手，对局结束"
            )
        }
        viewModelScope.launch { autoSaveGameIfEnabled() }
    }

    private fun countScoreInternal(endGame: Boolean) {
        val state = _uiState.value
        if (!state.started) {
            _events.tryEmit(GameEvent.Message("请先开始一盘棋"))
            return
        }
        if (state.busy) {
            _events.tryEmit(GameEvent.Message("KataGo 正在思考，请稍后再点数子"))
            return
        }
        analysisJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    busy = true,
                    status = "KataGo 正在数子…",
                    error = null,
                    scoreResult = null,
                    analysisVisible = false,
                    analysisRunning = true,
                    analysisOwnership = emptyList(),
                    analysisSummary = null,
                    analysisVisits = 0,
                    showEndGamePrompt = false
                )
            }
            try {
                if (!engine.isRunning()) {
                    restoreEngineForPosition(state)
                }
                val analysis = analyzeOwnershipWithRecovery(
                    settings.settings.first().aiLevel.analysisVisits
                )
                val expected = state.boardSize * state.boardSize
                if (analysis.ownership.size < expected) {
                    throw IllegalStateException(
                        "KataGo 未返回完整领地数据：${analysis.ownership.size}/$expected"
                    )
                }
                val summary = TerritorySummary.fromOwnership(
                    ownership = analysis.ownership,
                    boardSize = state.boardSize,
                    komi = state.komi
                ) ?: throw IllegalStateException("无法根据形势判断计算数子结果")

                _uiState.update {
                    it.copy(
                        busy = false,
                        gameOver = it.gameOver || endGame,
                        analysisVisible = true,
                        analysisRunning = false,
                        analysisOwnership = analysis.ownership,
                        analysisSummary = summary,
                        analysisVisits = analysis.visits,
                        scoreResult = summary.toResultText(),
                        status = summary.leadText()
                    )
                }
                autoSaveGameIfEnabled()
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        analysisRunning = false,
                        analysisVisible = false,
                        gameOver = it.gameOver || endGame,
                        status = if (endGame) "对局结束，数子失败" else "数子失败",
                        error = t.message
                    )
                }
            }
        }
    }

    fun restart() {
        engine.shutdownNow()
        _uiState.update { it.copy(started = false) }
        startGame()
    }

    /**
     * 从后台回到前台时，进程已被 Application 强制结束。
     * 这里按已有棋谱重放一遍，既保证“后台不耗电”，也尽量不丢当前对局。
     */
    fun onForeground() {
        isForeground = true
        battery.start()
        val state = _uiState.value
        if (!state.started || state.gameOver) return
        if (state.showEndGamePrompt) return
        if (engine.isRunning()) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, status = "正在恢复引擎…") }
            try {
                restoreEngineForPosition(state)
                _uiState.update {
                    it.copy(
                        busy = false,
                        status = if (it.turn == it.humanColor) "引擎已恢复，请继续" else "AI 继续思考…",
                        error = null
                    )
                }
                if (_uiState.value.turn != _uiState.value.humanColor) {
                    maybeAiMove()
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(busy = false, status = "恢复引擎失败，请重新开局", error = t.message)
                }
            }
        }
    }

    /** App 进入后台时禁止重建引擎；KataGo 进程本身由 Application 统一销毁。 */
    fun onBackground() {
        isForeground = false
        analysisJob?.cancel()
        if (_uiState.value.analysisRunning) {
            _uiState.update {
                it.copy(
                    busy = false,
                    analysisRunning = false,
                    analysisVisible = false,
                    analysisOwnership = emptyList(),
                    analysisSummary = null,
                    analysisVisits = 0
                )
            }
        }
    }

    /**
     * 按当前棋谱重建 GTP 进程。
     *
     * 切后台、进程崩溃或 stdin 被关闭后，旧客户端不能再复用；
     * 必须重新 initialize，并用 play/让子摆子把局面恢复到当前手。
     */
    private suspend fun restoreEngineForPosition(state: GameUiState) {
        if (!isForeground) {
            throw EngineConnectionLostException("应用不在前台，已跳过引擎重建")
        }
        engine.shutdownNow()
        val current = settings.settings.first()
        engine.initialize()
        engine.setPlayStyle(current.aiLevel.playStyle)
        engine.setHumanProfile(current.aiLevel.humanProfile)
        engine.setRules(state.rules)
        engine.newGame(
            state.boardSize,
            state.komi,
            current.aiLevel.visits,
            state.handicap.coerceAtLeast(0)
        )
        state.moves.forEach { move ->
            if (!move.comment.contains("认输")) {
                engine.play(move.color, move.point)
            }
        }
    }

    private suspend fun maybeAiMove() {
        val state = _uiState.value
        if (state.gameOver) return
        if (state.showEndGamePrompt) {
            _uiState.update { it.copy(busy = false) }
            return
        }
        if (state.highTemp) {
            // 高温暂停可能发生在 applyMove 之后；必须清除 busy，否则 UI 会一直禁用。
            _uiState.update {
                it.copy(busy = false, status = "电池温度过高，AI 已暂停，温度恢复后继续")
            }
            _events.tryEmit(GameEvent.Message("电池温度过高，AI 已暂停"))
            return
        }
        val aiColor = state.humanColor.opponent
        val thinkingStartedAt = SystemClock.elapsedRealtime()
        _uiState.update {
            it.copy(
                busy = true,
                lastAiSeconds = null,
                turn = aiColor,
                status = "KataGo 正在思考…"
            )
        }
        try {
            val generated = engine.genmove(aiColor)
            val elapsedSeconds = ((SystemClock.elapsedRealtime() - thinkingStartedAt) / 1_000).toInt()
            _uiState.update { it.copy(lastAiSeconds = elapsedSeconds) }
            if (generated.resigned) {
                _uiState.update {
                    it.copy(busy = false, gameOver = true, status = "KataGo 认输，你赢了")
                }
                _events.tryEmit(GameEvent.Message("KataGo 认输，你赢了"))
                autoSaveGameIfEnabled()
                return
            }
            if (generated.point == null) {
                // pass 是合法动作，但必须明确告诉用户，而不是看起来“没有落子”。
                applyMove(aiColor, null)
                _uiState.update {
                    it.copy(
                        busy = false,
                        status = "KataGo 选择停一手（pass），轮到你"
                    )
                }
                _events.tryEmit(GameEvent.Message("KataGo 选择停一手（pass）"))
                return
            }
            applyMove(aiColor, generated.point)
            _uiState.update {
                it.copy(busy = false, status = "轮到你落子")
            }
        } catch (t: Throwable) {
            val elapsedSeconds = ((SystemClock.elapsedRealtime() - thinkingStartedAt) / 1_000).toInt()
            _uiState.update {
                it.copy(
                    busy = false,
                    lastAiSeconds = elapsedSeconds,
                    status = "AI 落子失败",
                    error = t.message
                )
            }
            _events.tryEmit(GameEvent.Message("AI 落子失败: ${t.message ?: "未知错误"}"))
        }
    }

    private fun applyMove(color: StoneColor, point: GoPoint?) {
        val current = _uiState.value
        val nextBoard = current.board.play(color, point) ?: current.board
        positionHistory.add(nextBoard)
        val move = MoveRecord(color, point, moveNumber = current.moves.size + 1)
        val nextMoves = current.moves + move
        val consecutivePasses = hasTwoConsecutivePasses(nextMoves)
        if (point != null && nextBoard.stoneAt(point) == color) {
            moveSound.play(color)
        }
        analysisJob?.cancel()
        _uiState.update {
            it.copy(
                board = nextBoard,
                moves = nextMoves,
                turn = color.opponent,
                busy = !consecutivePasses,
                scoreResult = null,
                analysisVisible = false,
                analysisRunning = false,
                analysisOwnership = emptyList(),
                analysisSummary = null,
                analysisVisits = 0,
                showEndGamePrompt = consecutivePasses,
                status = when {
                    consecutivePasses -> "双方连续停一手，等待确认数子"
                    color == it.humanColor -> "KataGo 正在思考…"
                    else -> "轮到你落子"
                }
            )
        }
    }

    override fun onCleared() {
        analysisJob?.cancel()
        engine.shutdownNow()
        battery.stop()
        super.onCleared()
    }

    companion object {
        const val HIGH_TEMPERATURE_C = 42f
    }
}

private val StoneColor.chineseName: String
    get() = if (this == StoneColor.BLACK) "黑" else "白"

internal fun hasTwoConsecutivePasses(moves: List<MoveRecord>): Boolean =
    moves.size >= 2 && moves.takeLast(2).all { it.point == null }

private fun TerritorySummary.toResultText(): String = when {
    isEven -> "Draw"
    lead > 0 -> "B+${"%.1f".format(lead)}"
    else -> "W+${"%.1f".format(kotlin.math.abs(lead))}"
}

private fun TerritorySummary.leadText(): String {
    val leader = if (lead >= 0) "黑" else "白"
    return "形势：黑 $blackArea 目 · " +
        "白 $whiteArea 目 · " +
        if (isEven) "双方持平" else "${leader}领先 ${"%.1f".format(kotlin.math.abs(lead))} 目"
}
