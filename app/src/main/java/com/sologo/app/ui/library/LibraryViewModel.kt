package com.sologo.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sologo.app.AppContainer
import com.sologo.app.data.SavedGame
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class LibraryViewModel(
    private val container: AppContainer
) : ViewModel() {
    private val _games = MutableStateFlow<List<SavedGame>>(emptyList())
    val games: StateFlow<List<SavedGame>> = _games.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _busy.value = true
            _games.value = runCatching { container.sgfRepository.list() }
                .onFailure { _messages.tryEmit("读取棋谱失败: ${it.message}") }
                .getOrDefault(emptyList())
            _busy.value = false
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            runCatching { container.sgfRepository.import(uri) }
                .onSuccess {
                    _messages.emit("已导入 ${it.name}")
                    refresh()
                }
                .onFailure { _messages.emit("导入失败: ${it.message}") }
            _busy.value = false
        }
    }

    fun export(file: File, uri: Uri) {
        viewModelScope.launch {
            runCatching { container.sgfRepository.export(file, uri) }
                .onSuccess { _messages.emit("已导出 ${file.name}") }
                .onFailure { _messages.emit("导出失败: ${it.message}") }
        }
    }

    fun delete(game: SavedGame) {
        viewModelScope.launch {
            runCatching { container.sgfRepository.delete(game.file) }
                .onSuccess { _messages.emit("已删除 ${game.file.name}") }
                .onFailure { _messages.emit("删除失败: ${it.message}") }
            refresh()
        }
    }
}
