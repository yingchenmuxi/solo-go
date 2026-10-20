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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("对局结束后自动保存", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "保存到私有棋谱库，复盘页会自动加载",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoSaveGames,
                        onCheckedChange = vm::setAutoSaveGames
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("高级复盘", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (FeatureFlags.unlockAllForTesting) "测试模式：全部高级功能已开放，release 恢复付费门禁。"
                        else if (settings.premiumUnlocked) "已买断，永久解锁。"
                        else "未解锁",
                        style = MaterialTheme.typography.labelLarge
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
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("关于与合规", style = MaterialTheme.typography.titleSmall)
                    TextButton(
                        onClick = onOpenPrivacy,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) { Text("隐私政策") }
                    TextButton(
                        onClick = onOpenAbout,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) { Text("关于 SoloGo / KataGo") }
                }
            }
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
