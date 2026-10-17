package com.sologo.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.sologo.app.monetization.AdsManager

/**
 * 只在首页/棋谱页使用。对局页和复盘页不要放这个组件。
 */
@Composable
fun BannerAd(ads: AdsManager, modifier: Modifier = Modifier) {
    val initialized by ads.initialized.collectAsStateWithLifecycle()
    if (!initialized) return
    var visible by remember { mutableStateOf(true) }
    if (!visible) return
    val adView = remember { mutableStateOf<AdView?>(null) }
    DisposableEffect(Unit) {
        onDispose { adView.value?.destroy() }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(
                "广告",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { visible = false }) { Text("关闭") }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = AdsManager.BANNER_UNIT_ID
                    loadAd(AdRequest.Builder().build())
                    adView.value = this
                }
            }
        )
    }
}
