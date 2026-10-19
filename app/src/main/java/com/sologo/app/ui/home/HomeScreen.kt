package com.sologo.app.ui.home

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sologo.app.AppContainer
import com.sologo.core.model.AiLevel
import com.sologo.core.model.StoneColor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onStartGame: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModelFactory(container))
    val settings by vm.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("SoloGo · 单机围棋") }) }
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
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "和 KataGo 下一盘",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "本地离线运行 · 基础对局免费 · 高级分析可解锁",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("AI 水平（近似）", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AiLevel.entries.forEach { level ->
                            FilterChip(
                                selected = settings.aiLevel == level,
                                onClick = { vm.setAiLevel(level) },
                                label = { Text(level.displayName) }
                            )
                        }
                    }
                    Text(
                        "等级会自动调整搜索量、思考时间和着法变化；手机默认 5K。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("棋盘", style = MaterialTheme.typography.labelSmall)
                        listOf(9, 13, 19).forEach { size ->
                            FilterChip(
                                selected = settings.boardSize == size,
                                onClick = { vm.setBoardSize(size) },
                                label = { Text("$size") }
                            )
                        }
                        Text("执子", style = MaterialTheme.typography.labelSmall)
                        FilterChip(
                            selected = settings.humanColor == StoneColor.BLACK,
                            onClick = { vm.setHumanColor(StoneColor.BLACK) },
                            label = { Text("黑") }
                        )
                        FilterChip(
                            selected = settings.humanColor == StoneColor.WHITE,
                            enabled = settings.handicap == 0,
                            onClick = { vm.setHumanColor(StoneColor.WHITE) },
                            label = { Text("白") }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("让子", style = MaterialTheme.typography.labelSmall)
                        listOf(0, 2, 3, 4, 5).forEach { stones ->
                            FilterChip(
                                selected = settings.handicap == stones,
                                onClick = { vm.setHandicap(stones) },
                                label = { Text(if (stones == 0) "无" else "$stones") }
                            )
                        }
                    }
                    Text("贴目：${settings.komi}", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = settings.komi,
                        onValueChange = { vm.setKomi((it * 2).roundToInt() / 2f) },
                        valueRange = 0f..15f,
                        steps = 29
                    )
                    Button(onClick = onStartGame, modifier = Modifier.fillMaxWidth()) {
                        Text("开始人机对局")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("继续学习", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenLibrary, modifier = Modifier.weight(1f)) {
                            Text("棋谱库")
                        }
                        OutlinedButton(onClick = onOpenReview, modifier = Modifier.weight(1f)) {
                            Text("复盘")
                        }
                    }
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Text("设置与高级功能")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("合规与安全", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "• 切后台自动结束 KataGo 子进程，避免后台耗电\n" +
                            "• 电池温度超过 42℃ 自动暂停搜索\n" +
                            "• 棋谱使用标准 SGF FF[4]，分析数据单独存 JSON\n" +
                            "• 无广告 SDK，对局界面不受广告打断",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private class HomeViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(container) as T
    }
}
