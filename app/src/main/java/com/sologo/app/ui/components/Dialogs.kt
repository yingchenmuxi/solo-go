package com.sologo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PrivacyDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* 必须明确二选一 */ },
        title = { Text("隐私与数据使用") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "SoloGo 是单机围棋学习工具。基础对弈、SGF 棋谱管理和本地复盘不需要账号，也不会上传棋谱。"
                )
                Text(
                    "同意后才会初始化广告 SDK（AdMob），用于展示底部 Banner 和用户主动观看的激励视频。" +
                        "拒绝不影响基础围棋功能，但广告解锁和商店购买相关入口将不可用。"
                )
                Text(
                    "KataGo 在本机以子进程运行。切到后台或退出对局页时，APP 会立即结束该进程，避免持续占用 CPU。"
                )
                Text(
                    "详细说明见「设置 → 隐私政策」。你可以随时在系统设置中清除应用数据，撤回同意。"
                )
            }
        },
        confirmButton = {
            Button(onClick = onAccept) { Text("同意并继续") }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text("仅使用基础功能") }
        }
    )
}

@Composable
fun PaywallDialog(
    priceText: String,
    onBuy: () -> Unit,
    onWatchAd: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("解锁高级复盘") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("高级复盘包含：", fontWeight = FontWeight.SemiBold)
                Text("• 实时胜率与目数差\n• 领地热力图与多候选 PV\n• 整盘逐手亏损、恶手标记\n• 更高 visits 的局部深度分析")
                Text(
                    "基础对弈永久免费。买断后无需订阅，所有分析仍在本机离线完成。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onBuy) { Text("买断 · $priceText") }
        },
        dismissButton = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                OutlinedButton(onClick = onWatchAd) { Text("看广告解锁 24 小时") }
                TextButton(onClick = onDismiss) { Text("以后再说") }
            }
        }
    )
}
