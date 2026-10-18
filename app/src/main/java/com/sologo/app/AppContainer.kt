package com.sologo.app

import android.app.Application
import com.sologo.app.data.SettingsRepository
import com.sologo.app.data.SgfRepository
import com.sologo.app.monetization.BillingRepository
import com.sologo.engine.BatteryTemperatureMonitor
import com.sologo.engine.KataGoEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 极简手写依赖容器，避免为一个小型单机 App 引入 DI 框架。
 */
class AppContainer(application: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsRepository(application)
    val sgfRepository = SgfRepository(application)
    val engine = KataGoEngine(application)
    val battery = BatteryTemperatureMonitor(application)
    val billing = BillingRepository(application) { unlocked ->
        appScope.launch { settings.setPremiumUnlocked(unlocked) }
    }

    init {
        appScope.launch {
            val settingsState = settings.settings.first()
            if (settingsState.privacyAccepted) {
                billing.start()
            }
        }
    }
}
