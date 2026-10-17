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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sologo.app.AppContainer
import com.sologo.app.ui.components.BannerAd

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onStartGame: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("SoloGo · 单机围棋") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "和 KataGo 下一盘",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "本地离线运行，不联网也能对弈。基础对局永久免费；高级胜率、目数、领地和整盘复盘可按需解锁。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = onStartGame, modifier = Modifier.fillMaxWidth()) {
                        Text("开始人机对局")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("合规与安全", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "• 切后台自动结束 KataGo 子进程，避免后台耗电\n" +
                            "• 电池温度超过 42℃ 自动暂停搜索\n" +
                            "• 棋谱使用标准 SGF FF[4]，分析数据单独存 JSON\n" +
                            "• 广告 SDK 仅在隐私同意后初始化",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            BannerAd(ads = container.ads)
        }
    }
}
