package com.sologo.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sologo.app.AppContainer
import com.sologo.app.ui.components.ZenChoiceChip
import com.sologo.app.ui.components.ZenSeal
import com.sologo.app.ui.components.ZenSurface
import com.sologo.app.ui.components.zenPageBackground
import com.sologo.app.ui.theme.ZenInk
import com.sologo.app.ui.theme.ZenWood
import com.sologo.core.model.AiLevel
import com.sologo.core.model.GameRules
import com.sologo.core.model.HandicapOptions
import com.sologo.core.model.StoneColor
import kotlin.math.min

@Composable
fun HomeScreen(
    container: AppContainer,
    onStartGame: () -> Unit
) {
    val vm: HomeViewModel = viewModel(factory = HomeViewModelFactory(container))
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        scrollState.scrollTo(0)
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .zenPageBackground()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeBrandHeader()

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = "一局一会",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "静心落子，与 AI 共弈",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "本地 KataGo · 无需联网 · 完整棋谱",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                AiLevel.entries.forEach { level ->
                    ZenChoiceChip(
                        label = level.displayName,
                        selected = settings.aiLevel == level,
                        onClick = { vm.setAiLevel(level) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            ZenBoardPreview(
                boardSize = settings.boardSize,
                rules = settings.rules,
                handicap = settings.handicap
            )

            Button(
                onClick = onStartGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 0.dp
                )
            ) {
                Text(
                    text = "开始一局",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            ZenSurface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "开局设置",
                        style = MaterialTheme.typography.titleSmall
                    )
                    CompactSettingLine(label = "规则") {
                        //GameRules.entries.forEach { rule ->
                            //ZenChoiceChip(
                            //    label = if (rule == GameRules.CHINESE) "中国" else "默认",
                            //    selected = settings.rules == rule,
                            //    onClick = { vm.setRules(rule) }
                            //)
                        //}
                        Text(
                            text = "贴目 ${settings.rules.formatKomi(settings.handicap)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SettingDivider()
                        listOf(9, 13, 19).forEach { size ->
                            ZenChoiceChip(
                                label = "$size 路",
                                selected = settings.boardSize == size,
                                onClick = { vm.setBoardSize(size) }
                            )
                        }
                    }
                    CompactSettingLine(label = "对局") {
                        ZenChoiceChip(
                            label = "执黑",
                            selected = settings.humanColor == StoneColor.BLACK,
                            onClick = { vm.setHumanColor(StoneColor.BLACK) }
                        )
                        ZenChoiceChip(
                            label = "执白",
                            selected = settings.humanColor == StoneColor.WHITE,
                            enabled = settings.handicap == 0,
                            onClick = { vm.setHumanColor(StoneColor.WHITE) }
                        )
                        SettingDivider()
                        HandicapOptions.ALLOWED.forEach { stones ->
                            ZenChoiceChip(
                                label = HandicapOptions.label(stones),
                                selected = settings.handicap == stones,
                                onClick = { vm.setHandicap(stones) }
                            )
                        }
                    }
                }
            }

            Text(
                text = "对局设置会用于下一盘棋",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun HomeBrandHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("独弈", style = MaterialTheme.typography.headlineSmall)
            Text(
                "离线围棋 · 单机学习",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ZenSeal(text = "弈", modifier = Modifier.size(34.dp))
    }
}

@Composable
private fun CompactSettingLine(
    label: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
private fun SettingDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(16.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * 品牌首页的装饰棋盘。它不是对局状态，只用于建立木纹棋盘的视觉识别。
 */
@Composable
private fun ZenBoardPreview(
    boardSize: Int,
    rules: GameRules,
    handicap: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(158.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFE6C78E), ZenWood, Color(0xFFC99960))
                )
            )
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            val side = min(size.width, size.height)
            val gridMargin = side * 0.07f
            val gridSize = side - gridMargin * 2f
            val startX = (size.width - gridSize) / 2f
            val startY = (size.height - gridSize) / 2f
            val cell = gridSize / (boardSize - 1).coerceAtLeast(1)
            val gridColor = ZenInk.copy(alpha = 0.72f)

            fun point(x: Int, y: Int) = Offset(
                startX + x * cell,
                startY + y * cell
            )

            repeat(boardSize) { index ->
                drawLine(
                    color = gridColor,
                    start = Offset(startX + index * cell, startY),
                    end = Offset(startX + index * cell, startY + gridSize),
                    strokeWidth = 1.1f
                )
                drawLine(
                    color = gridColor,
                    start = Offset(startX, startY + index * cell),
                    end = Offset(startX + gridSize, startY + index * cell),
                    strokeWidth = 1.1f
                )
            }

            val previewPoints = listOf(
                (boardSize * 0.28f).toInt() to (boardSize * 0.28f).toInt(),
                (boardSize * 0.72f).toInt() to (boardSize * 0.27f).toInt(),
                (boardSize * 0.58f).toInt() to (boardSize * 0.56f).toInt(),
                (boardSize * 0.43f).toInt() to (boardSize * 0.70f).toInt()
            )
            previewPoints.forEachIndexed { index, (x, y) ->
                val center = point(x, y)
                val radius = cell * 0.42f
                drawCircle(
                    brush = if (index % 2 == 0) {
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF5B5149), Color(0xFF11100F)),
                            center = center - Offset(radius * 0.3f, radius * 0.3f),
                            radius = radius * 1.4f
                        )
                    } else {
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFFFFCF5), Color(0xFFD8D1C5)),
                            center = center - Offset(radius * 0.3f, radius * 0.3f),
                            radius = radius * 1.4f
                        )
                    },
                    radius = radius,
                    center = center
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(11.dp),
            shape = RoundedCornerShape(999.dp),
            color = ZenInk.copy(alpha = 0.82f),
            contentColor = Color(0xFFFFF8ED)
        ) {
            Text(
                text = "${boardSize} 路",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(11.dp),
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFFFFF8ED).copy(alpha = 0.86f),
            contentColor = ZenInk
        ) {
            Text(
                text = "${rules.displayName} · 贴 ${rules.formatKomi(handicap)}",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private class HomeViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(container) as T
    }
}
