package com.sologo.app.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于 SoloGo") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("SoloGo", style = MaterialTheme.typography.headlineMedium)
            Text(
                "单机围棋学习工具。基础人机对局、SGF 棋谱管理和本地复盘可离线使用，" +
                    "高级分析通过应用商店一次性内购解锁。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text("KataGo", style = MaterialTheme.typography.titleLarge)
            Text(
                "本 APP 内置 KataGo 作为本地分析引擎。KataGo 以 MIT License 开源，版权归其作者所有。",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = { uriHandler.openUri("https://github.com/lightvector/KataGo") }) {
                Text("打开 KataGo 源码地址")
            }
            Text("MIT License 摘要", style = MaterialTheme.typography.titleMedium)
            Text(
                """
                Permission is hereby granted, free of charge, to any person obtaining a copy
                of this software and associated documentation files (the "Software"), to deal
                in the Software without restriction, including without limitation the rights
                to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
                copies of the Software, and to permit persons to whom the Software is
                furnished to do so, subject to the following conditions:

                The above copyright notice and this permission notice shall be included in all
                copies or substantial portions of the Software.

                THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
                IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
                FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
                AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
                LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
                OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
                SOFTWARE.
                """.trimIndent(),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "随包权重来自 katagotraining.org 的官方 kata1 网络，适用 KataGo Neural Network License；" +
                    "详见 assets/licenses/KataGo-Neural-Network-License.txt。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
