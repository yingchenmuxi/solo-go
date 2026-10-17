package com.sologo.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 读取电池温度。读取 ACTION_BATTERY_CHANGED 不需要额外权限。
 * 某些设备/模拟器不会提供温度，此时为 null，调用方不要误判为高温。
 */
class BatteryTemperatureMonitor(context: Context) {
    private val appContext = context.applicationContext
    private val _temperature = MutableStateFlow<Float?>(null)
    val temperature: StateFlow<Float?> = _temperature.asStateFlow()
    private var started = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateFromIntent(intent)
        }
    }

    fun start() {
        if (started) return
        started = true
        val sticky = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        updateFromIntent(sticky)
        runCatching {
            // ACTION_BATTERY_CHANGED 是系统广播；Android 13+ 需要显式指定导出标志。
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_EXPORTED
            )
        }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { appContext.unregisterReceiver(receiver) }
    }

    private fun updateFromIntent(intent: Intent?) {
        val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        if (tenths != Int.MIN_VALUE) {
            _temperature.value = tenths / 10f
        }
    }
}
