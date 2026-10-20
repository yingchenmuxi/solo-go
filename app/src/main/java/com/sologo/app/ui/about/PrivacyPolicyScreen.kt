package com.sologo.app.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("隐私政策") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                """
                生效日期：2026-10-17

                一、我们处理什么
                独弈围棋的核心功能在本地运行。棋谱文件保存在应用私有目录；KataGo 的进程、权重和配置也只在设备本地使用。基础围棋功能不需要账号，不上传棋谱，不读取通讯录、定位、相册或麦克风。

                二、广告与第三方 SDK
                当前版本不包含广告 SDK，也不会为了广告收集设备标识符或广告交互信息。

                三、应用内购买
                高级复盘买断通过 Google Play 官方计费完成。APP 不接触你的银行卡信息，也不提供任何外部收款方式。订单和退款由应用商店处理。

                四、设备温度
                APP 会读取系统提供的电池温度。该信息只用于在温度超过 42℃ 时暂停本地 AI 搜索，不会上传。

                五、进程与后台
                APP 切到后台或离开对局/复盘页面时，会主动结束 KataGo 子进程，避免持续占用 CPU 和耗电。你重新进入对局时可以重新启动引擎。

                六、你的选择
                你可以拒绝隐私同意并继续使用基础功能；也可以在系统设置中清除应用数据，撤回此前的同意。撤回后如需再次启用商店购买，需要重新启动 APP 并作出选择。

                七、联系我们
                正式上架前请在此处补充开发者联系邮箱和隐私政策公网地址。应用商店通常要求可公开访问的隐私政策 URL。
                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
