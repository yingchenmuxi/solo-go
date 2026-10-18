package com.sologo.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sologo.app.AppContainer
import com.sologo.app.data.SettingsRepository
import com.sologo.core.model.BoardPosition
import com.sologo.core.model.CandidateMove
import com.sologo.core.model.GameAnalysis
import com.sologo.core.model.GameRecord
import com.sologo.core.model.MoveAnalysis
import com.sologo.core.model.PositionAnalysis
import com.sologo.core.model.StoneColor
import com.sologo.sgf.AnalysisStore
import com.sologo.sgf.SgfCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

data class ReviewUiState(
    val file: File? = null,
    val record: GameRecord? = null,
    val positions: List<BoardPosition> = emptyList(),
    val selectedIndex: Int = 0,
    val board: BoardPosition = BoardPosition.empty(19),
    val analysis: GameAnalysis? = null,
    val selectedMove: MoveAnalysis? = null,
    val ownership: List<Float> = emptyList(),
    val candidates: List<CandidateMove> = emptyList(),
    val advancedAccess: Boolean = false,
    val showPaywall: Boolean = false,
    val loading: Boolean = false,
    val analyzing: Boolean = false,
    val highTemp: Boolean = false,
    val batteryTemp: Float? = null,
    val progress: Float = 0f,
    val message: String? = null,
    val error: String? = null
) {
    val moveCount: Int get() = record?.moves?.size ?: 0
    val currentMoveNumber: Int get() = selectedIndex
}

class ReviewViewModel(
    private val container: AppContainer,
    initialFile: File?
) : ViewModel() {
    private val _state = MutableStateFlow(ReviewUiState(file = initialFile))
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()
    private val settings: SettingsRepository = container.settings

    init {
        container.battery.start()
        viewModelScope.launch {
            container.battery.temperature.collect { temp ->
                val high = temp != null && temp > 42f
                val wasHigh = _state.value.highTemp
                _state.update { it.copy(batteryTemp = temp, highTemp = high) }
                if (high && !wasHigh) {
                    container.engine.stopSearch()
                    _state.update { it.copy(message = "电池温度超过 42℃，已暂停 AI 搜索") }
                }
            }
        }
        viewModelScope.launch {
            settings.settings.collect { s ->
                _state.update { it.copy(advancedAccess = s.hasAdvancedAccess()) }
            }
        }
        if (initialFile != null) load(initialFile)
    }

    fun load(file: File) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, message = null) }
            try {
                val text = withContext(Dispatchers.IO) { file.readText(Charsets.UTF_8) }
                val record = withContext(Dispatchers.IO) { SgfCodec.decode(text) }
                val positions = buildPositions(record)
                val hash = AnalysisStore.sha256(text)
                val analysis = withContext(Dispatchers.IO) { AnalysisStore.load(file) }
                    ?.takeIf { it.sgfHash.isBlank() || it.sgfHash == hash }
                _state.update {
                    it.copy(
                        file = file,
                        record = record,
                        positions = positions,
                        selectedIndex = 0,
                        board = positions.firstOrNull() ?: BoardPosition.empty(record.size),
                        analysis = analysis,
                        selectedMove = null,
                        ownership = emptyList(),
                        candidates = emptyList(),
                        loading = false
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(loading = false, error = "读取棋谱失败: ${t.message}")
                }
            }
        }
    }

    fun selectIndex(index: Int) {
        val state = _state.value
        if (state.positions.isEmpty()) return
        val safe = index.coerceIn(0, state.positions.lastIndex)
        val move = state.analysis?.moves?.getOrNull(safe - 1)
        _state.update {
            it.copy(
                selectedIndex = safe,
                board = state.positions[safe],
                selectedMove = move,
                ownership = move?.ownership.orEmpty(),
                candidates = move?.candidates.orEmpty()
            )
        }
    }

    /**
     * 整盘自动复盘：对每个局面执行一次 kata-analyze，再 play 下一手。
     * 单手亏损 = 该手前最佳胜率与该手后胜率的差（按行棋方视角）。
     */
    fun analyzeAll() {
        val state = _state.value
        if (state.file == null || state.record == null) return
        if (!state.advancedAccess) {
            _state.update { it.copy(showPaywall = true) }
            return
        }
        if (state.highTemp) {
            _state.update { it.copy(message = "电池温度过高，已暂停 AI 搜索") }
            return
        }
        if (state.analyzing) return
        val file = state.file
        val record = state.record
        viewModelScope.launch {
            _state.update {
                it.copy(analyzing = true, progress = 0f, error = null, message = "正在启动 KataGo…")
            }
            try {
                val visits = settings.settings.first().aiLevel.analysisVisits
                container.engine.initialize()
                // 优先让 KataGo 读取 SGF（可保留 AB/AW 摆子）；失败则退回空棋盘重放。
                val loadedSgf = runCatching {
                    container.engine.loadsgf(file.absolutePath, 0, record.size)
                }.isSuccess
                if (!loadedSgf) {
                    container.engine.newGame(record.size, record.komi, visits)
                }
                val positionAnalyses = mutableListOf<PositionAnalysis>()
                val total = record.moves.size + 1
                for (index in 0 until total) {
                    val turn = if (index < record.moves.size) {
                        record.moves[index].color
                    } else {
                        record.moves.lastOrNull()?.color?.opponent ?: StoneColor.BLACK
                    }
                    _state.update {
                        it.copy(
                            progress = index.toFloat() / total.coerceAtLeast(1),
                            message = "分析第 ${index + 1} / $total 个局面"
                        )
                    }
                    positionAnalyses.add(
                        container.engine.analyze(
                            maxVisits = visits,
                            intervalMs = 300,
                            durationMs = 450,
                            turn = turn
                        )
                    )
                    if (index < record.moves.size) {
                        val move = record.moves[index]
                        container.engine.play(move.color, move.point)
                    }
                }
                val moveAnalyses = buildMoveAnalyses(record, positionAnalyses)
                val result = GameAnalysis(
                    sgfHash = AnalysisStore.sha256(file.readText(Charsets.UTF_8)),
                    boardSize = record.size,
                    visits = visits,
                    moves = moveAnalyses
                )
                withContext(Dispatchers.IO) { AnalysisStore.save(file, result) }
                _state.update {
                    it.copy(
                        analysis = result,
                        analyzing = false,
                        progress = 1f,
                        message = "复盘完成，已保存配套 JSON",
                        selectedIndex = 1.coerceAtMost(it.positions.lastIndex),
                        board = it.positions.getOrNull(1) ?: it.positions.first(),
                        selectedMove = moveAnalyses.firstOrNull(),
                        ownership = moveAnalyses.firstOrNull()?.ownership.orEmpty(),
                        candidates = moveAnalyses.firstOrNull()?.candidates.orEmpty()
                    )
                }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        analyzing = false,
                        error = "自动复盘失败: ${t.message}",
                        message = null
                    )
                }
            }
        }
    }

    fun dismissPaywall() {
        _state.update { it.copy(showPaywall = false) }
    }

    fun unlockWithPurchase() {
        viewModelScope.launch {
            settings.setPremiumUnlocked(true)
            _state.update { it.copy(showPaywall = false, advancedAccess = true) }
        }
    }

    override fun onCleared() {
        container.engine.shutdownNow()
        container.battery.stop()
        super.onCleared()
    }

    private fun buildPositions(record: GameRecord): List<BoardPosition> {
        var board = if (record.setupBlack.isNotEmpty() || record.setupWhite.isNotEmpty()) {
            BoardPosition.fromSetup(
                size = record.size,
                black = record.setupBlack.toSet(),
                white = record.setupWhite.toSet()
            )
        } else {
            BoardPosition.empty(record.size)
        }
        val result = mutableListOf(board)
        record.moves.forEach { move ->
            board = board.play(move.color, move.point) ?: board
            result.add(board)
        }
        return result
    }

    private fun buildMoveAnalyses(
        record: GameRecord,
        analyses: List<PositionAnalysis>
    ): List<MoveAnalysis> {
        return record.moves.mapIndexed { index, move ->
            val before = analyses.getOrNull(index)?.best
            val after = analyses.getOrNull(index + 1)?.best
            val beforeWinrate = before?.winrate ?: 0.5
            val afterWinrate = after?.winrate ?: beforeWinrate
            val beforeScore = before?.scoreLead ?: 0.0
            val afterScore = after?.scoreLead ?: beforeScore
            val blackLoss = beforeWinrate - afterWinrate
            val loss = if (move.color == StoneColor.BLACK) blackLoss else -blackLoss
            val scoreLoss = if (move.color == StoneColor.BLACK) {
                beforeScore - afterScore
            } else {
                afterScore - beforeScore
            }
            MoveAnalysis(
                moveNumber = index + 1,
                color = move.color,
                playedMove = move.point,
                winrateBefore = beforeWinrate,
                scoreLeadBefore = beforeScore,
                winrateAfter = afterWinrate,
                scoreLeadAfter = afterScore,
                loss = loss,
                isBlunder = loss > BLUNDER_THRESHOLD || abs(scoreLoss) > 4.0,
                comment = if (loss > BLUNDER_THRESHOLD) "明显亏损的一手" else "",
                ownership = analyses.getOrNull(index + 1)?.ownership.orEmpty(),
                candidates = analyses.getOrNull(index)?.candidates.orEmpty()
            )
        }
    }

    companion object {
        private const val BLUNDER_THRESHOLD = 0.12
    }
}
