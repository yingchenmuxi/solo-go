package com.sologo.app.ui.review

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sologo.app.AppContainer
import com.sologo.app.R
import com.sologo.app.ui.components.GoBoard
import com.sologo.app.ui.components.PaywallDialog
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 独立复盘页面。返回按钮和系统返回都会回到复盘棋谱列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewDetailScreen(
    container: AppContainer,
    filePath: String?,
    onBack: () -> Unit
) {
    val file = remember(filePath) { filePath?.let(::File) }
    val vm: ReviewViewModel = viewModel(
        key = "review-detail-${file?.absolutePath ?: "missing"}",
        factory = ReviewDetailViewModelFactory(container, file)
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
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
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
        } else if (gameRecord == null || state.file == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("无法打开复盘", style = MaterialTheme.typography.titleLarge)
                Text(
                    "棋谱可能已被删除或无法解析。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onBack) { Text("返回棋谱列表") }
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
                            ?: state.file?.let { openedFile ->
                                val savedAt = SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm",
                                    Locale.getDefault()
                                ).format(Date(openedFile.lastModified()))
                                val playedAt = gameRecord.date.ifBlank {
                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                        .format(Date(openedFile.lastModified()))
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
                    }
                }
                GoBoard(
                    position = state.board,
                    enabled = false,
                    ownership = state.ownership,
                    showOwnership = state.advancedAccess && state.ownership.isNotEmpty(),
                    candidates = if (state.advancedAccess) state.candidates else emptyList()
                )
                Slider(
                    value = state.selectedIndex.toFloat(),
                    onValueChange = { vm.selectIndex(it.roundToInt()) },
                    valueRange = 0f..state.moveCount.coerceAtLeast(1).toFloat(),
                    steps = (state.moveCount - 1).coerceAtLeast(0)
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MoveControl(
                            iconRes = R.drawable.ic_first_move,
                            description = "开局",
                            enabled = state.selectedIndex > 0,
                            onClick = { vm.selectIndex(0) }
                        )
                        MoveControl(
                            iconRes = R.drawable.ic_prev_move,
                            description = "上一手",
                            enabled = state.selectedIndex > 0,
                            onClick = { vm.selectIndex(state.selectedIndex - 1) }
                        )
                        MoveControl(
                            iconRes = R.drawable.ic_next_move,
                            description = "下一手",
                            enabled = state.selectedIndex < state.moveCount,
                            onClick = { vm.selectIndex(state.selectedIndex + 1) }
                        )
                        MoveControl(
                            iconRes = R.drawable.ic_last_move,
                            description = "末手",
                            enabled = state.selectedIndex < state.moveCount,
                            onClick = { vm.selectIndex(state.moveCount) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = vm::analyzeCurrent,
                        enabled = !state.analyzing && state.advancedAccess && !state.highTemp,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(if (state.advancedAccess) "分析当前手" else "解锁当前手分析")
                    }
                    Text(
                        text = when {
                            state.analyzing -> "分析中…"
                            state.highTemp -> "温度过高，已暂停"
                            state.candidates.isNotEmpty() -> "推荐 ${state.candidates.size} 个点"
                            else -> "尚未分析当前手"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
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
                val currentActivity = activity
                if (currentActivity == null) {
                    scope.launch { snackbar.showSnackbar("当前页面无法发起商店支付") }
                } else {
                    container.billing.launchPurchase(currentActivity)
                }
            },
            onDismiss = vm::dismissPaywall
        )
    }
}

@Composable
private fun MoveControl(
    iconRes: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
            }
        )
    }
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
                val pv = candidate.pv.take(4).joinToString(" ") { point ->
                    "${columns[point.x]}${boardSize - point.y}"
                }
                Text(
                    "${index + 1}. $coordinate · 黑胜率 ${(candidate.winrate * 100).toInt()}% · " +
                        "目数 ${"%.1f".format(candidate.scoreLead)}" +
                        (if (pv.isBlank()) "" else "\nPV: $pv"),
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

private class ReviewDetailViewModelFactory(
    private val container: AppContainer,
    private val file: File?
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ReviewViewModel(container, file) as T
    }
}
