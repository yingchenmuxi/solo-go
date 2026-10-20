package com.sologo.app.ui.review

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sologo.app.AppContainer
import com.sologo.app.data.SavedGame
import com.sologo.app.ui.components.GoBoard
import com.sologo.app.ui.components.PaywallDialog
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    container: AppContainer,
    filePath: String?,
    onBack: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    val file = remember(filePath) { filePath?.let(::File) }
    val vm: ReviewViewModel = viewModel(
        key = "review-${file?.absolutePath ?: "empty"}",
        factory = ReviewViewModelFactory(container, file)
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val billingState by container.billing.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current.findActivity()

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        container.battery.start()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("复盘分析") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    if (state.file != null) {
                        TextButton(onClick = vm::showSavedGames) { Text("换棋谱") }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val gameRecord = state.record
        if (state.loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            }
        } else if (state.file == null) {
            SavedGamePicker(
                games = state.savedGames,
                onSelect = vm::selectGame,
                onOpenLibrary = onOpenLibrary,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else if (gameRecord == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("选择一盘棋开始复盘", style = MaterialTheme.typography.titleLarge)
                Text(
                    "复盘页支持标准 SGF 主线浏览、逐手前后切换，以及当前局面的 KataGo 推荐点分析。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onOpenLibrary) { Text("打开棋谱库") }
            }
        } else {
            Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${gameRecord.blackName.ifBlank { "黑" }} vs " +
                            "${gameRecord.whiteName.ifBlank { "白" }}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "第 ${state.selectedIndex} / ${state.moveCount} 手 · " +
                            "${gameRecord.size} 路 · 贴目 ${gameRecord.komi}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val gameTime = state.selectedGame?.let(::savedGameTimeText)
                        ?: state.file?.let { file ->
                            val savedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(Date(file.lastModified()))
                            val playedAt = gameRecord.date.ifBlank {
                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                    .format(Date(file.lastModified()))
                            }
                            "$playedAt · 保存于 $savedAt"
                        }
                    gameTime?.let {
                        Text(
                            "对局时间：$it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = state.selectedIndex.toFloat(),
                        onValueChange = { vm.selectIndex(it.roundToInt()) },
                        valueRange = 0f..state.moveCount.coerceAtLeast(1).toFloat(),
                        steps = (state.moveCount - 1).coerceAtLeast(0)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedButton(
                            onClick = { vm.selectIndex(0) },
                            enabled = state.selectedIndex > 0,
                            modifier = Modifier.weight(1f)
                        ) { Text("开局") }
                        OutlinedButton(
                            onClick = { vm.selectIndex(state.selectedIndex - 1) },
                            enabled = state.selectedIndex > 0,
                            modifier = Modifier.weight(1f)
                        ) { Text("上一手") }
                        OutlinedButton(
                            onClick = { vm.selectIndex(state.selectedIndex + 1) },
                            enabled = state.selectedIndex < state.moveCount,
                            modifier = Modifier.weight(1f)
                        ) { Text("下一手") }
                        OutlinedButton(
                            onClick = { vm.selectIndex(state.moveCount) },
                            enabled = state.selectedIndex < state.moveCount,
                            modifier = Modifier.weight(1f)
                        ) { Text("末手") }
                    }
                }
            }
            GoBoard(
                position = state.board,
                enabled = false,
                ownership = state.ownership,
                showOwnership = state.advancedAccess && state.ownership.isNotEmpty(),
                candidates = if (state.advancedAccess) state.candidates else emptyList()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = vm::analyzeCurrent,
                    enabled = !state.analyzing && state.advancedAccess && !state.highTemp,
                    modifier = Modifier.weight(1f)
                ) { Text(if (state.advancedAccess) "分析当前手" else "解锁当前手分析") }
                Text(
                    text = when {
                        state.analyzing -> "分析中…"
                        state.highTemp -> "温度过高，已暂停"
                        state.candidates.isNotEmpty() -> "推荐 ${state.candidates.size} 个点"
                        else -> "尚未分析当前手"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (state.analyzing) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (state.advancedAccess && state.candidates.isNotEmpty()) {
                CandidateList(state)
            }
            }
        }
    }

    if (state.showPaywall) {
        PaywallDialog(
            priceText = billingState.priceText,
            onBuy = {
                val a = activity
                if (a == null) {
                    scope.launch { snackbar.showSnackbar("当前页面无法发起商店支付") }
                } else {
                    container.billing.launchPurchase(a)
                }
            },
            onDismiss = vm::dismissPaywall
        )
    }
}

@Composable
private fun SavedGamePicker(
    games: List<SavedGame>,
    onSelect: (SavedGame) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("已存储对局", style = MaterialTheme.typography.titleLarge)
                Text(
                    "选择一盘棋开始复盘；这里支持逐手浏览和本地 KataGo AI 分析。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onOpenLibrary) { Text("棋谱库") }
        }

        if (games.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("还没有已存储的对局", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "可以在设置中打开“对局结束后自动保存”，或在对局中手动保存 SGF。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onOpenLibrary) { Text("打开棋谱库") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(games, key = { it.file.absolutePath }) { game ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(game) }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                game.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "对局时间：${savedGameTimeText(game)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val details = buildList {
                                if (game.boardSize > 0) add("${game.boardSize} 路")
                                if (game.moveCount > 0) add("${game.moveCount} 手")
                                if (game.result.isNotBlank()) add("结果 ${game.result}")
                                if (game.hasAnalysis) add("已有 AI 分析")
                            }.joinToString(" · ")
                            if (details.isNotBlank()) {
                                Text(
                                    details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "开始复盘 →",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun savedGameTimeText(game: SavedGame): String {
    val savedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date(game.modifiedAt))
    val playedAt = game.playedAt.ifBlank { "未标注日期" }
    return "$playedAt · 保存于 $savedAt"
}

@Composable
private fun CandidateList(state: ReviewUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("当前局面推荐点", style = MaterialTheme.typography.titleSmall)
            state.candidates.take(5).forEachIndexed { index, candidate ->
                val columns = "ABCDEFGHJKLMNOPQRST"
                val boardSize = state.record?.size ?: 19
                val coordinate = candidate.move?.let {
                    "${columns[it.x]}${boardSize - it.y}"
                } ?: "pass"
                val pv = candidate.pv.take(4).joinToString(" ") { p ->
                    "${columns[p.x]}${boardSize - p.y}"
                }
                Text(
                    "${index + 1}. $coordinate · 黑胜率 ${(candidate.winrate * 100).toInt()}% · " +
                        "目数 ${"%.1f".format(candidate.scoreLead)}" + (if (pv.isBlank()) "" else "\nPV: $pv"),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private class ReviewViewModelFactory(
    private val container: AppContainer,
    private val file: File?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ReviewViewModel(container, file) as T
    }
}
