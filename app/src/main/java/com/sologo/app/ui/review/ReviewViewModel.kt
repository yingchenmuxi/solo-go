package com.sologo.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sologo.app.AppContainer
import com.sologo.app.data.SavedGame
import com.sologo.app.data.SettingsRepository
import com.sologo.core.model.BoardPosition
import com.sologo.core.model.CandidateMove
import com.sologo.core.model.GameRecord
import com.sologo.core.model.StoneColor
import com.sologo.core.model.TerritorySummary
import com.sologo.sgf.SgfCodec
import kotlinx.coroutines.Job
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

enum class ReviewAnalysisMode {
    POSITION,
    AI
}

data class ReviewUiState(
    val file: File? = null,
    val selectedGame: SavedGame? = null,
    val savedGames: List<SavedGame> = emptyList(),
    val record: GameRecord? = null,
    val positions: List<BoardPosition> = emptyList(),
    val selectedIndex: Int = 0,
    val board: BoardPosition = BoardPosition.empty(19),
    val ownership: List<Float> = emptyList(),
    val territorySummary: TerritorySummary? = null,
    val candidates: List<CandidateMove> = emptyList(),
    val analysisVisits: Int = 0,
    val advancedAccess: Boolean = false,
    val showPaywall: Boolean = false,
    val loading: Boolean = false,
    val analyzing: Boolean = false,
    val analysisMode: ReviewAnalysisMode? = null,
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
    private val _state = MutableStateFlow(
        ReviewUiState(file = initialFile, loading = initialFile != null)
    )
    val state: StateFlow<ReviewUiState> = _state.asStateFlow()
    private val settings: SettingsRepository = container.settings
    private var analysisJob: Job? = null

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
        if (initialFile != null) {
            load(initialFile)
        } else {
            refreshSavedGames()
        }
    }

    /** 复盘页没有指定文件时，自动读取私有棋谱目录并展示可选对局。 */
    fun refreshSavedGames() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val games = runCatching { container.sgfRepository.list() }
                .onFailure {
                    _state.update { state ->
                        state.copy(error = "读取已存储对局失败: ${it.message}")
                    }
                }
                .getOrDefault(emptyList())
            _state.update { it.copy(savedGames = games, loading = false) }
        }
    }

    fun selectGame(game: SavedGame) {
        load(game.file)
    }

    fun showSavedGames() {
        analysisJob?.cancel()
        _state.update {
            it.copy(
                file = null,
                selectedGame = null,
                record = null,
                positions = emptyList(),
                ownership = emptyList(),
                territorySummary = null,
                candidates = emptyList(),
                analysisVisits = 0,
                analyzing = false,
                analysisMode = null,
                progress = 0f,
                selectedIndex = 0,
                board = BoardPosition.empty(19),
                error = null,
                message = null
            )
        }
        refreshSavedGames()
    }

    fun load(file: File) {
        analysisJob?.cancel()
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    analyzing = false,
                    analysisMode = null,
                    progress = 0f,
                    error = null,
                    message = null
                )
            }
            try {
                val text = withContext(Dispatchers.IO) { file.readText(Charsets.UTF_8) }
                val record = withContext(Dispatchers.IO) { SgfCodec.decode(text) }
                val positions = buildPositions(record)
                _state.update {
                    it.copy(
                        file = file,
                        selectedGame = it.savedGames.firstOrNull { saved ->
                            saved.file.absolutePath == file.absolutePath
                        },
                        record = record,
                        positions = positions,
                        selectedIndex = 0,
                        board = positions.firstOrNull() ?: BoardPosition.empty(record.size),
                        ownership = emptyList(),
                        territorySummary = null,
                        candidates = emptyList(),
                        analysisVisits = 0,
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
        if (safe != state.selectedIndex) {
            state.record?.moves?.getOrNull(safe - 1)?.color?.let(container.moveSound::play)
        }
        analysisJob?.cancel()
        _state.update {
            it.copy(
                selectedIndex = safe,
                board = state.positions[safe],
                ownership = emptyList(),
                territorySummary = null,
                candidates = emptyList(),
                analysisVisits = 0,
                analyzing = false,
                analysisMode = null,
                progress = 0f
            )
        }
    }

    /** 形势判断与对局页一致：使用当前局面的 ownership 计算领地和差距。 */
    fun togglePositionAnalysis() {
        val state = _state.value
        if (state.analyzing) return
        if (state.ownership.isNotEmpty()) {
            _state.update {
                it.copy(
                    ownership = emptyList(),
                    territorySummary = null,
                    analysisMode = null,
                    progress = 0f,
                    message = "形势判断已关闭"
                )
            }
            return
        }
        runAnalysis(ReviewAnalysisMode.POSITION)
    }

    /** AI 分析当前局面，给出若干下一手候选位置。 */
    fun analyzeAi() {
        runAnalysis(ReviewAnalysisMode.AI)
    }

    private fun runAnalysis(mode: ReviewAnalysisMode) {
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
        val selectedIndex = state.selectedIndex
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    analyzing = true,
                    analysisMode = mode,
                    progress = 0f,
                    error = null,
                    message = when (mode) {
                        ReviewAnalysisMode.POSITION -> "正在判断形势…"
                        ReviewAnalysisMode.AI -> "AI 正在分析下一手…"
                    },
                    ownership = if (mode == ReviewAnalysisMode.POSITION) {
                        emptyList()
                    } else {
                        it.ownership
                    },
                    territorySummary = if (mode == ReviewAnalysisMode.POSITION) {
                        null
                    } else {
                        it.territorySummary
                    },
                    candidates = if (mode == ReviewAnalysisMode.AI) {
                        emptyList()
                    } else {
                        it.candidates
                    }
                )
            }
            try {
                val visits = settings.settings.first().aiLevel.analysisVisits.coerceIn(60, 160)
                container.engine.initialize()
                val loadedSgf = runCatching {
                    container.engine.loadsgf(file.absolutePath, selectedIndex, record.size)
                }.isSuccess
                if (!loadedSgf) {
                    container.engine.newGame(
                        record.size,
                        record.komi,
                        visits,
                        record.handicap.coerceAtLeast(0)
                    )
                    record.moves.take(selectedIndex).forEach { move ->
                        container.engine.play(move.color, move.point)
                    }
                }
                val turn = if (selectedIndex < record.moves.size) {
                    record.moves[selectedIndex].color
                } else {
                    record.moves.lastOrNull()?.color?.opponent ?: StoneColor.BLACK
                }
                if (_state.value.selectedIndex != selectedIndex) return@launch

                when (mode) {
                    ReviewAnalysisMode.POSITION -> {
                        val analysis = container.engine.analyze(
                            maxVisits = visits,
                            turn = turn,
                            includeOwnership = true
                        )
                        val expected = record.size * record.size
                        if (analysis.ownership.size < expected) {
                            throw IllegalStateException(
                                "KataGo 未返回完整领地数据：${analysis.ownership.size}/$expected"
                            )
                        }
                        val summary = TerritorySummary.fromOwnership(
                            ownership = analysis.ownership,
                            boardSize = record.size,
                            komi = record.komi
                        ) ?: throw IllegalStateException("无法计算当前形势")
                        _state.update {
                            it.copy(
                                analyzing = false,
                                analysisMode = null,
                                progress = 1f,
                                ownership = analysis.ownership,
                                territorySummary = summary,
                                analysisVisits = analysis.visits,
                                message = summary.leadText()
                            )
                        }
                    }

                    ReviewAnalysisMode.AI -> {
                        val analysis = container.engine.analyzePolicy(turn)
                        val candidates = analysis.candidates.take(5)
                        _state.update {
                            it.copy(
                                analyzing = false,
                                analysisMode = null,
                                progress = 1f,
                                candidates = candidates,
                                analysisVisits = analysis.visits,
                                message = if (candidates.isEmpty()) {
                                    "AI 分析完成，暂未找到推荐点"
                                } else {
                                    "AI 分析完成，推荐 ${candidates.size} 个下一手"
                                }
                            )
                        }
                    }
                }
            } catch (t: Throwable) {
                if (_state.value.selectedIndex != selectedIndex) return@launch
                _state.update {
                    it.copy(
                        analyzing = false,
                        analysisMode = null,
                        error = when (mode) {
                            ReviewAnalysisMode.POSITION -> "形势判断失败: ${t.message}"
                            ReviewAnalysisMode.AI -> "AI 分析失败: ${t.message}"
                        },
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

}

private fun TerritorySummary.leadText(): String {
    val leader = if (lead >= 0) "黑" else "白"
    return "形势：黑 $blackArea 目 · " +
        "白 $whiteArea 目 · " +
        if (isEven) {
            "双方持平"
        } else {
            "${leader}领先 ${"%.1f".format(kotlin.math.abs(lead))} 目"
        }
}
