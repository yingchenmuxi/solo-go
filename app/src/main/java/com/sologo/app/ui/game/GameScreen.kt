package com.sologo.app.ui.game

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
import androidx.compose.material3.Card
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.sologo.app.ui.components.TerritoryBlackColor
import com.sologo.app.ui.components.TerritoryWhiteColor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val vm: GameViewModel = viewModel(factory = GameViewModelFactory(container))
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.startGame()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        vm.onForeground()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        vm.onBackground()
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
            CompactGameHeader(state)
            GoBoard(
                position = state.board,
                enabled = !state.busy && !state.gameOver && !state.highTemp &&
                    state.turn == state.humanColor,
                ownership = state.scoreMarkers,
                showOwnership = state.scoreVisible && state.scoreMarkers.isNotEmpty(),
                ownershipThreshold = 0.01f,
                onIntersectionTap = vm::onBoardTap
            )
            if (state.scoreVisible) {
                ScorePanel(state)
            }
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
            Spacer(Modifier.height(8.dp))
        }
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
private fun CompactGameHeader(state: GameUiState) {
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
private fun ScorePanel(state: GameUiState) {
    val summary = state.scoreSummary
    val result = state.scoreResult
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("数子结果", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (summary != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TerritoryLegendItem(
                        color = TerritoryBlackColor,
                        text = "黑地 ${summary.blackTerritory} 目"
                    )
                    TerritoryLegendItem(
                        color = TerritoryWhiteColor,
                        text = "白地 ${summary.whiteTerritory} 目"
                    )
                }
                Text(
                    text = "总占地（含子与贴目）：黑 ${summary.blackScore.formatScore()} · " +
                        "白 ${summary.whiteScore.formatScore()} 目",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = if (summary.isEven) {
                        "按标记计算：双方持平"
                    } else if (summary.lead > 0) {
                        "按标记计算：黑胜 ${summary.lead.formatScore()} 目"
                    } else {
                        "按标记计算：白胜 ${(-summary.lead).formatScore()} 目"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (summary.neutralPoints > 0) {
                    Text(
                        "未定 ${summary.neutralPoints} 点，以 KataGo 结果为准",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    "未能获取地盘标记，以 KataGo 数子结果为准",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            result?.let {
                Text(
                    "KataGo：${formatScoreResult(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
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

private fun formatScoreResult(result: String): String = when {
    result.startsWith("B+") -> scoreWinnerText("黑", result.removePrefix("B+"))
    result.startsWith("W+") -> scoreWinnerText("白", result.removePrefix("W+"))
    else -> result
}

private fun scoreWinnerText(winner: String, value: String): String =
    if (value.equals("Resign", ignoreCase = true)) {
        "${winner}胜（对方认输）"
    } else {
        "${winner}胜 $value 目"
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
