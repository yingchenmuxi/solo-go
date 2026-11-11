package com.sologo.app.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.sologo.app.ui.components.ZenSurface
import com.sologo.app.ui.components.zenPageBackground
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
                is GameEvent.Saved -> snackbarHostState.showSnackbar(
                    if (event.automatic) "棋谱已自动保存: ${event.file.name}"
                    else "棋谱已保存: ${event.file.name}"
                )
                is GameEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("对局", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .zenPageBackground()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactGameHeader(state, vm::toggleAnalysis)
            ZenSurface(modifier = Modifier.fillMaxWidth()) {
                GoBoard(
                    position = state.board,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(7.dp)
                        .clip(RoundedCornerShape(15.dp)),
                    enabled = !state.busy && !state.gameOver && !state.highTemp &&
                        state.turn == state.humanColor,
                    ownership = state.analysisOwnership,
                    showOwnership = state.analysisVisible && state.analysisOwnership.isNotEmpty(),
                    ownershipThreshold = 0.08f,
                    onIntersectionTap = vm::onBoardTap
                )
            }
            GameActionBar(
                state = state,
                onUndo = vm::undo,
                onPass = vm::pass,
                onScore = vm::countScore,
                onSave = vm::saveGame,
                onResign = vm::resign,
                onRestart = vm::restart
            )
            Spacer(Modifier.height(2.dp))
        }
    }

    if (state.showEndGamePrompt) {
        EndGamePrompt(
            onCount = vm::confirmEndGameCount,
            onDismiss = vm::dismissEndGamePrompt
        )
    }
}

@Composable
private fun EndGamePrompt(
    onCount: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("双方连续停一手") },
        text = {
            Text("对局已结束，是否使用当前形势判断进行数子？")
        },
        confirmButton = {
            Button(onClick = onCount) { Text("数子") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("暂不数子") }
        }
    )
}

/**
 * 对局工具栏采用“高频操作 + 次要操作”两行布局。
 * 高频按钮使用紧凑色块，次要按钮使用低高度文字按钮，
 * 在不隐藏功能的前提下显著减少纵向占用。
 */
@Composable
private fun GameActionBar(
    state: GameUiState,
    onUndo: () -> Unit,
    onPass: () -> Unit,
    onScore: () -> Unit,
    onSave: () -> Unit,
    onResign: () -> Unit,
    onRestart: () -> Unit
) {
    ZenSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                CompactActionButton(
                    text = "悔棋",
                    onClick = onUndo,
                    enabled = !state.busy && state.moves.any { it.color == state.humanColor },
                    modifier = Modifier.weight(1f)
                )
                CompactActionButton(
                    text = "停一手",
                    onClick = onPass,
                    enabled = !state.busy && !state.gameOver && state.turn == state.humanColor,
                    modifier = Modifier.weight(1f)
                )
                CompactActionButton(
                    text = "数子",
                    onClick = onScore,
                    // 即使 AI 正在思考也允许点击，ViewModel 会提示稍后再执行。
                    enabled = state.started,
                    emphasized = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CompactTextAction(
                    text = "保存棋谱",
                    onClick = onSave,
                    enabled = state.moves.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
                CompactTextAction(
                    text = "认输",
                    onClick = onResign,
                    enabled = !state.gameOver,
                    modifier = Modifier.weight(1f)
                )
                CompactTextAction(
                    text = "重新开局",
                    onClick = onRestart,
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CompactActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false
) {
    val shape: Shape = RoundedCornerShape(9.dp)
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        emphasized -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        emphasized -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(30.dp),
        shape = shape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CompactTextAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    Box(
        modifier = modifier
            .height(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1
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
    onToggleAnalysis: () -> Unit
) {
    ZenSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.status,
                    style = MaterialTheme.typography.titleSmall,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                val analysisEnabled =
                    state.analysisVisible || state.analysisRunning || !state.busy
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                            RoundedCornerShape(999.dp)
                        )
                        .clip(RoundedCornerShape(7.dp))
                        .clickable(
                            enabled = analysisEnabled,
                            role = Role.Button,
                            onClick = onToggleAnalysis
                        )
                        .padding(horizontal = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            state.analysisRunning -> "分析中…"
                            state.analysisVisible -> "关闭形势"
                            else -> "形势分析"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (analysisEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        },
                        maxLines = 1
                    )
                }
                if (state.analysisVisible) {
                    TerritoryLegendItem(TerritoryBlackColor, "黑势力")
                    TerritoryLegendItem(TerritoryWhiteColor, "白势力")
                    if (state.analysisVisits > 0) {
                        Text(
                            "${state.analysisVisits}v",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (state.analysisVisible) {
                state.analysisSummary?.let { summary ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "估计占地：黑 ${summary.blackArea} 目 · " +
                                "白 ${summary.whiteArea} 目" +
                                if (summary.neutralPoints > 0) {
                                    " · 未定 ${summary.neutralPoints} 点"
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = when {
                                summary.isEven -> "双方持平"
                                summary.lead > 0 ->
                                    "黑领先 ${summary.lead.formatScore()} 目"
                                else ->
                                    "白领先 ${(-summary.lead).formatScore()} 目"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
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

private fun Double.formatScore(): String = "%.1f".format(this)

private class GameViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GameViewModel(
            engine = container.engine,
            settings = container.settings,
            sgfRepository = container.sgfRepository,
            battery = container.battery,
            moveSound = container.moveSound
        ) as T
    }
}
