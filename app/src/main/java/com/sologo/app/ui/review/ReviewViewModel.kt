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

data class ReviewUiState(
    val file: File? = null,
    val selectedGame: SavedGame? = null,
    val savedGames: List<SavedGame> = emptyList(),
    val record: GameRecord? = null,
    val positions: List<BoardPosition> = emptyList(),
    val selectedIndex: Int = 0,
    val board: BoardPosition = BoardPosition.empty(19),
    val ownership: List<Float> = emptyList(),
    val candidates: List<CandidateMove> = emptyList(),
    val analysisVisits: Int = 0,
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
                candidates = emptyList(),
                analysisVisits = 0,
                analyzing = false,
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
                candidates = emptyList(),
                analysisVisits = 0,
                analyzing = false,
                progress = 0f
            )
        }
    }

    /**
     * 只分析当前选中的局面，避免整盘逐手分析耗时。
     * KataGo 至少加载到 selectedIndex 手，再用一次 kata-analyze 取前几个推荐点。
     */
    fun analyzeCurrent() {
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
                    progress = 0f,
                    error = null,
                    message = "正在分析当前局面…",
                    ownership = emptyList(),
                    candidates = emptyList(),
                    analysisVisits = 0
                )
            }
            try {
                // 当前局面分析保持轻量：限制 visits，单次分析受 maxTime 约束。
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
                val analysis = container.engine.analyze(
                    maxVisits = visits,
                    intervalMs = 250,
                    durationMs = 900,
                    turn = turn,
                    includeOwnership = false
                )
                if (_state.value.selectedIndex != selectedIndex) return@launch
                _state.update {
                    it.copy(
                        analyzing = false,
                        progress = 1f,
                        candidates = analysis.candidates.take(5),
                        ownership = analysis.ownership,
                        analysisVisits = analysis.visits,
                        message = "当前局面分析完成（${analysis.visits} visits）"
                    )
                }
            } catch (t: Throwable) {
                if (_state.value.selectedIndex != selectedIndex) return@launch
                _state.update {
                    it.copy(
                        analyzing = false,
                        error = "当前局面分析失败: ${t.message}",
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
