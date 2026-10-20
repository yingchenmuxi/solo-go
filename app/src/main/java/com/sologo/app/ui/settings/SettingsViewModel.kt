package com.sologo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sologo.app.AppContainer
import com.sologo.app.data.AppSettings
import com.sologo.core.model.StoneColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
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

    fun setHumanColor(value: StoneColor) = viewModelScope.launch {
        container.settings.setHumanColor(value)
    }

    fun restorePurchases() {
        container.billing.start()
    }
}
