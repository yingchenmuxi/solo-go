package com.sologo.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sologo.app.AppContainer
import com.sologo.app.data.AppSettings
import com.sologo.core.model.AiLevel
import com.sologo.core.model.GameRules
import com.sologo.core.model.StoneColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val container: AppContainer
) : ViewModel() {
    val settings: StateFlow<AppSettings> = container.settings.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    fun setBoardSize(value: Int) = viewModelScope.launch {
        container.settings.setBoardSize(value)
    }

    fun setHandicap(value: Int) = viewModelScope.launch {
        container.settings.setHandicap(value)
        if (value != 0) container.settings.setHumanColor(StoneColor.BLACK)
    }

    fun setRules(value: GameRules) = viewModelScope.launch {
        container.settings.setRules(value)
    }

    fun setAiLevel(value: AiLevel) = viewModelScope.launch {
        container.settings.setAiLevel(value)
    }

    fun setHumanColor(value: StoneColor) = viewModelScope.launch {
        container.settings.setHumanColor(value)
        if (value == StoneColor.WHITE) container.settings.setHandicap(0)
    }
}
