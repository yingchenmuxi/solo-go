package com.sologo.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sologo.app.AppContainer
import com.sologo.app.FeatureFlags
import com.sologo.app.ui.components.BannerAd
import com.sologo.core.model.PlayStyle
import com.sologo.core.model.StoneColor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(container))
    val settings by vm.settings.collectAsStateWithLifecycle()
    val billing by container.billing.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("对局", style = MaterialTheme.typography.titleMedium)
                    Text("棋盘路数", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(9, 13, 19).forEach { size ->
                            FilterChip(
                                selected = settings.boardSize == size,
                                onClick = { vm.setBoardSize(size) },
                                label = { Text("${size} 路") }
                            )
                        }
                    }
                    Text("执子颜色", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.humanColor == StoneColor.BLACK,
                            onClick = { vm.setHumanColor(StoneColor.BLACK) },
                            label = { Text("执黑") }
                        )
                        FilterChip(
                            selected = settings.humanColor == StoneColor.WHITE,
                            onClick = { vm.setHumanColor(StoneColor.WHITE) },
                            label = { Text("执白") }
                        )
                    }
                    Text("贴目：${settings.komi}", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = settings.komi,
                        onValueChange = { vm.setKomi((it * 2).roundToInt() / 2f) },
                        valueRange = 0f..15f,
                        steps = 29
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("AI 搜索", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "最大 visits：${settings.maxVisits}。数值越高越强，也越耗电。高温保护会在 42℃ 暂停搜索。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("速度预设", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(40, 60, 120, 300).forEach { value ->
                            FilterChip(
                                selected = settings.maxVisits == value,
                                onClick = { vm.setVisits(value) },
                                label = { Text("$value") }
                            )
                        }
                    }
                    Slider(
                        value = settings.maxVisits.toFloat(),
                        onValueChange = { vm.setVisits(it.roundToInt()) },
                        valueRange = 20f..4000f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            "每次 AI 落子后自动分析",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = settings.autoAnalyze,
                            onClick = { vm.setAutoAnalyze(!settings.autoAnalyze) },
                            label = { Text(if (settings.autoAnalyze) "开" else "关") }
                        )
                    }
                    Text("下法风格", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlayStyle.entries.forEach { style ->
                            FilterChip(
                                selected = settings.playStyle == style,
                                onClick = { vm.setPlayStyle(style) },
                                label = { Text(style.displayName) }
                            )
                        }
                    }
                    Text(
                        "自然/多变会使用温度和根节点采样，让每局着法更有差异；不是 HumanSL 的真实段位模仿。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (FeatureFlags.unlockAllForTesting) {
                            "测试模式：visits 不设免费上限；默认 60，40 更快，120/300 更深。"
                        } else {
                            "默认 60，兼顾速度和棋力；40 更快，120/300 更深。免费对局最高按 120 visits 执行。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("高级复盘", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (FeatureFlags.unlockAllForTesting) "测试模式：全部高级功能已开放，release 恢复付费门禁。"
                        else if (settings.premiumUnlocked) "已买断，永久解锁。"
                        else if (settings.adUnlockUntilEpochMs > System.currentTimeMillis()) {
                            "已通过激励视频临时解锁。"
                        } else "未解锁",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (!FeatureFlags.unlockAllForTesting && !settings.premiumUnlocked) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val activity = context.findActivity()
                                if (activity != null) container.billing.launchPurchase(activity)
                            }) { Text("买断 · ${billing.priceText}") }
                            OutlinedButton(onClick = vm::restorePurchases) { Text("恢复购买") }
                        }
                    }
                    if (billing.message != null) {
                        Text(
                            billing.message.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("关于与合规", style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    TextButton(onClick = onOpenPrivacy) { Text("隐私政策") }
                    TextButton(onClick = onOpenAbout) { Text("关于 SoloGo / KataGo 开源协议") }
                    Text(
                        "本 APP 为教育/工具类单机围棋学习工具，不提供联网对战，不申请通讯录、定位等无关权限。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            BannerAd(ads = container.ads)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private class SettingsViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(container) as T
    }
}
