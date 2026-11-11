package com.sologo.app.ui.review

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.sologo.app.ui.components.TerritoryBlackColor
import com.sologo.app.ui.components.TerritoryWhiteColor
import com.sologo.app.ui.components.ZenSurface
import com.sologo.app.ui.components.zenPageBackground
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
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("复盘分析", style = MaterialTheme.typography.titleLarge) },
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
                    .zenPageBackground()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ZenSurface(modifier = Modifier.fillMaxWidth()) {
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
                ZenSurface(modifier = Modifier.fillMaxWidth()) {
                    GoBoard(
                        position = state.board,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(7.dp)
                            .clip(RoundedCornerShape(15.dp)),
                        enabled = false,
                        ownership = state.ownership,
                        showOwnership = state.advancedAccess && state.ownership.isNotEmpty(),
                        candidates = if (state.advancedAccess) state.candidates else emptyList()
                    )
                }
                Slider(
                    value = state.selectedIndex.toFloat(),
                    onValueChange = { vm.selectIndex(it.roundToInt()) },
                    valueRange = 0f..state.moveCount.coerceAtLeast(1).toFloat(),
                    steps = (state.moveCount - 1).coerceAtLeast(0)
                )
                ZenSurface(
                    modifier = Modifier.fillMaxWidth(),
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = vm::togglePositionAnalysis,
                        enabled = !state.analyzing && !state.highTemp,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            when {
                                state.analysisMode == ReviewAnalysisMode.POSITION -> "判断中…"
                                state.ownership.isNotEmpty() -> "关闭形势"
                                state.advancedAccess -> "形势判断"
                                else -> "解锁形势判断"
                            },
                            maxLines = 1
                        )
                    }
                    Button(
                        onClick = vm::analyzeAi,
                        enabled = !state.analyzing && !state.highTemp,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            when {
                                state.analysisMode == ReviewAnalysisMode.AI -> "分析中…"
                                state.advancedAccess -> "AI 分析"
                                else -> "解锁 AI 分析"
                            },
                            maxLines = 1
                        )
                    }
                }
                Text(
                    text = when {
                        state.analyzing && state.analysisMode == ReviewAnalysisMode.POSITION ->
                            "正在使用当前局面判断黑白势力…"
                        state.analyzing && state.analysisMode == ReviewAnalysisMode.AI ->
                            "AI 正在计算下一手候选位置…"
                        state.highTemp -> "温度过高，已暂停分析"
                        state.candidates.isNotEmpty() ->
                            "AI 推荐 ${state.candidates.size} 个下一手候选位置"
                        state.ownership.isNotEmpty() -> "形势判断已显示"
                        else -> "可分别进行形势判断或 AI 下一手分析"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.advancedAccess && state.territorySummary != null) {
                    TerritorySummaryPanel(state)
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
private fun TerritorySummaryPanel(state: ReviewUiState) {
    val summary = state.territorySummary ?: return
    ZenSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TerritoryLegend(TerritoryBlackColor, "黑势力")
                TerritoryLegend(TerritoryWhiteColor, "白势力")
            }
            Text(
                text = "估计占地：黑 ${summary.blackArea} 目 · " +
                    "白 ${summary.whiteArea} 目" +
                    if (summary.neutralPoints > 0) {
                        " · 未定 ${summary.neutralPoints} 点"
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = when {
                    summary.isEven -> "贴目后双方持平"
                    summary.lead > 0 ->
                        "贴目后黑领先 ${"%.1f".format(summary.lead)} 目"
                    else ->
                        "贴目后白领先 ${"%.1f".format(kotlin.math.abs(summary.lead))} 目"
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TerritoryLegend(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color.copy(alpha = 0.78f))
        )
        Text(text, style = MaterialTheme.typography.labelSmall)
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
    ZenSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("AI 推荐的下一手", style = MaterialTheme.typography.titleSmall)
            Text(
                "按 AI 首手倾向排序，棋盘上的数字对应下列位置。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.candidates.take(5).forEachIndexed { index, candidate ->
                val columns = "ABCDEFGHJKLMNOPQRST"
                val boardSize = state.record?.size ?: 19
                val coordinate = candidate.move?.let {
                    "${columns[it.x]}${boardSize - it.y}"
                } ?: "pass"
                Text(
                    "${index + 1}. $coordinate",
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
