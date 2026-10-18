package com.sologo.app.ui.game

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sologo.app.AppContainer
import com.sologo.app.ui.components.GoBoard
import com.sologo.app.ui.components.PaywallDialog
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val vm: GameViewModel = viewModel(factory = GameViewModelFactory(container))
    val state by vm.uiState.collectAsStateWithLifecycle()
    val billingState by container.billing.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context.findActivity()

    LaunchedEffect(Unit) {
        vm.startGame()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        vm.onForeground()
    }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is GameEvent.Saved -> snackbarHostState.showSnackbar("棋谱已保存: ${event.file.name}")
                is GameEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("人机对局") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusCard(state, billingState.priceText, vm::toggleAnalysis)
            if (state.highTemp) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "电池温度超过 42℃，AI 搜索已自动暂停以保护设备。温度降下来后可以继续。",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            GoBoard(
                position = state.board,
                enabled = !state.busy && !state.gameOver && !state.highTemp &&
                    state.turn == state.humanColor,
                ownership = state.ownership,
                showOwnership = state.analysisVisible && state.ownership.isNotEmpty(),
                candidates = if (state.analysisVisible) state.candidates else emptyList(),
                onIntersectionTap = vm::onBoardTap
            )
            AiThinkingTimer(
                active = state.busy && state.turn != state.humanColor
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = vm::undo,
                    enabled = !state.busy && state.moves.any { it.color == state.humanColor },
                    modifier = Modifier.weight(1f)
                ) { Text("悔棋") }
                OutlinedButton(
                    onClick = vm::pass,
                    enabled = !state.busy && !state.gameOver && state.turn == state.humanColor,
                    modifier = Modifier.weight(1f)
                ) { Text("停一手") }
                Button(
                    onClick = vm::analyzeNow,
                    enabled = !state.busy && !state.gameOver,
                    modifier = Modifier.weight(1f)
                ) { Text(if (state.advancedAccess) "AI 分析" else "解锁分析") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = vm::saveGame,
                    modifier = Modifier.weight(1f)
                ) { Text("保存 SGF") }
                OutlinedButton(
                    onClick = vm::countScore,
                    // 即使 KataGo 正在思考也允许点击，ViewModel 会给出“请稍后”的提示，
                    // 避免按钮看起来完全失效。
                    enabled = state.started,
                    modifier = Modifier.weight(1f)
                ) { Text("数子") }
                TextButton(
                    onClick = vm::resign,
                    enabled = !state.gameOver,
                    modifier = Modifier.weight(1f)
                ) { Text("认输") }
            }
            OutlinedButton(
                onClick = vm::restart,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("重新开局") }
            if (state.analysisVisible && state.candidates.isNotEmpty()) {
                CandidatePanel(state)
            }
            state.error?.let { error ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "错误：$error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (state.showPaywall) {
        PaywallDialog(
            priceText = billingState.priceText,
            onBuy = {
                val a = activity
                if (a == null) {
                    scope.launch { snackbarHostState.showSnackbar("当前页面无法发起商店支付") }
                } else {
                    container.billing.launchPurchase(a)
                }
            },
            onDismiss = vm::dismissPaywall
        )
    }
}

@Composable
private fun AiThinkingTimer(active: Boolean) {
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(active) {
        seconds = 0
        if (active) {
            while (true) {
                delay(1_000)
                seconds++
            }
        }
    }
    if (active) {
        Text(
            text = "AI 思考中 · ${seconds}s",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StatusCard(
    state: GameUiState,
    priceText: String,
    onToggleAnalysis: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.status,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${state.boardSize} 路 · 贴目 ${state.komi}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "你执${if (state.humanColor.name == "BLACK") "黑" else "白"} · 第 ${state.moves.size} 手",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                state.batteryTemp?.let {
                    Text(
                        text = "电池 ${"%.1f".format(it)}℃",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.highTemp) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.advancedAccess) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = state.winrate?.let { "黑胜率 ${(it * 100).formatPercent()}" } ?: "黑胜率 --",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = state.scoreLead?.let { "目数差 ${if (it >= 0) "+" else ""}${"%.1f".format(it)}" } ?: "目数差 --",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI 分析：${if (state.analysisVisible) "开" else "关"}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onToggleAnalysis) {
                        Text(if (state.analysisVisible) "关闭分析" else "开启分析")
                    }
                }
            } else {
                Text(
                    text = "高级复盘未解锁 · $priceText 买断解锁",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CandidatePanel(state: GameUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("KataGo 候选点", style = MaterialTheme.typography.titleSmall)
            state.candidates.take(6).forEachIndexed { index, candidate ->
                val columns = "ABCDEFGHJKLMNOPQRST"
                val coordinate = candidate.move?.let {
                    "${columns[it.x]}${state.boardSize - it.y}"
                } ?: "pass"
                val pv = candidate.pv.take(4).joinToString(" ") { p ->
                    "${columns[p.x]}${state.boardSize - p.y}"
                }
                Text(
                    text = "${index + 1}. $coordinate  胜率 ${(candidate.winrate * 100).formatPercent()}  " +
                        "目数 ${if (candidate.scoreLead >= 0) "+" else ""}${"%.1f".format(candidate.scoreLead)}  " +
                        "${candidate.visits} visits" + (if (pv.isBlank()) "" else "\nPV: $pv"),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun Double.formatPercent(): String =
    if (this >= 100.0) "100.0%" else "%.1f%%".format(this)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private class GameViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GameViewModel(
            engine = container.engine,
            settings = container.settings,
            sgfRepository = container.sgfRepository,
            battery = container.battery
        ) as T
    }
}
