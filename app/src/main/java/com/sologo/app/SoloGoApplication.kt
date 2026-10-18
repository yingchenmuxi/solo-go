package com.sologo.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SoloGoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // 隐私同意后才启动商店计费；拒绝时基础围棋功能不受影响。
        container.appScope.launch {
            container.settings.settings
                .map { it.privacyAccepted }
                .distinctUntilChanged()
                .collect { accepted ->
                    if (accepted) {
                        container.billing.start()
                    }
                }
        }

        // 市场审核硬性要求：切后台强制结束 KataGo，绝不让它继续占 CPU。
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // 回到前台后恢复商店连接。
                container.billing.start()
            }

            override fun onStop(owner: LifecycleOwner) {
                container.engine.shutdownNow()
                container.battery.stop()
            }
        })
    }
}
