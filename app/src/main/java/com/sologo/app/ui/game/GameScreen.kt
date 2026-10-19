package com.sologo.app.ui.game

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.sologo.app.ui.components.TerritoryBlackColor
import com.sologo.app.ui.components.TerritoryWhiteColor
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
            CompactGameHeader(state, billingState.priceText, vm::toggleAnalysis)
            GoBoard(
                position = state.board,
                enabled = !state.busy && !state.gameOver && !state.highTemp &&
                    state.turn == state.humanColor,
                ownership = state.ownership,
                showOwnership = state.analysisVisible && state.ownership.isNotEmpty(),
                candidates = if (state.analysisVisible) state.candidates else emptyList(),
                onIntersectionTap = vm::onBoardTap
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
                    onClick = vm::toggleAnalysis,
                    enabled = !state.busy && !state.gameOver,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when {
                            !state.advancedAccess -> "解锁分析"
                            state.analysisVisible -> "关闭分析"
                            else -> "局势分析"
                        }
                    )
                }
            }
            if (state.analysisVisible && state.analysis != null) {
                SituationPanel(state)
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
            Spacer(Modifier.height(8.dp))
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
private fun AiThinkingTimer(active: Boolean, lastSeconds: Int?) {
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
    val shownSeconds = if (active) seconds else (lastSeconds ?: 0)
    Text(
        text = if (active) "AI 思考 ${shownSeconds}s" else "AI 用时 ${shownSeconds}s",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun CompactGameHeader(
    state: GameUiState,
    priceText: String,
    onToggleAnalysis: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.status,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                AiThinkingTimer(
                    active = state.busy && state.turn != state.humanColor,
                    lastSeconds = state.lastAiSeconds
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "你执${if (state.humanColor.name == "BLACK") "黑" else "白"} · " +
                        "第 ${state.moves.size} 手 · ${state.rules.displayName} · " +
                        "${state.boardSize}路 · 贴目 ${state.komi}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                state.batteryTemp?.let {
                    Text(
                        text = "电池 ${"%.1f".format(it)}℃",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.highTemp) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.advancedAccess) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.winrate?.let { "黑胜率 ${(it * 100).formatPercent()}" } ?: "黑胜率 --",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = state.scoreLead?.let { "目数差 ${if (it >= 0) "+" else ""}${"%.1f".format(it)}" } ?: "目数差 --",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onToggleAnalysis, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text(
                            if (state.analysisVisible) "分析关" else "分析开",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            } else {
                Text(
                    text = "高级复盘未解锁 · $priceText 买断解锁",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.highTemp) {
                Text(
                    text = "温度超过 42℃，AI 搜索已暂停",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            state.error?.let { error ->
                Text(
                    text = "错误：$error",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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

@Composable
private fun SituationPanel(state: GameUiState) {
    val best = state.analysis?.best ?: return
    val columns = "ABCDEFGHJKLMNOPQRST"
    val bestCoordinate = best.move?.let { "${columns[it.x]}${state.boardSize - it.y}" } ?: "pass"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("局势分析", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "黑胜率 ${(best.winrate * 100).formatPercent()} · " +
                    "最佳点 $bestCoordinate",
                style = MaterialTheme.typography.labelSmall
            )
            val territory = state.territory
            if (territory != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TerritoryLegendItem(
                        color = TerritoryBlackColor,
                        text = "黑势力 ${territory.blackArea.formatScore()} 目"
                    )
                    TerritoryLegendItem(
                        color = TerritoryWhiteColor,
                        text = "白势力 ${territory.whiteArea.formatScore()} 目"
                    )
                }
                Text(
                    text = if (territory.isEven) {
                        "贴目 ${territory.komi.formatScore()}（白）· 双方持平"
                    } else if (territory.lead > 0) {
                        "贴目 ${territory.komi.formatScore()}（白）· " +
                            "黑领先 ${territory.lead.formatScore()} 目"
                    } else {
                        "贴目 ${territory.komi.formatScore()}（白）· " +
                            "白领先 ${(-territory.lead).formatScore()} 目"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = "目数差 ${if (best.scoreLead >= 0) "+" else ""}" +
                        "${best.scoreLead.formatScore()}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (best.pv.isNotEmpty()) {
                val pv = best.pv.take(5).joinToString(" ") {
                    "${columns[it.x]}${state.boardSize - it.y}"
                }
                Text(
                    "PV: $pv",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TerritoryLegendItem(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color.copy(alpha = 0.75f))
        )
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

private fun Float.formatScore(): String = "%.1f".format(this)

private fun Double.formatScore(): String = "%.1f".format(this)

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
