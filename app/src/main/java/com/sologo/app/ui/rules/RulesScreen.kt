package com.sologo.app.ui.rules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sologo.app.ui.components.ZenSurface
import com.sologo.app.ui.components.zenPageBackground
import kotlin.math.min

/**
 * 面向零基础用户的围棋规则速查页。
 *
 * 这里只做规则说明，不参与对局判定；实际落子、提子、劫争和终局
 * 均由本地 KataGo 处理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen() {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("围棋规则", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .zenPageBackground()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "几分钟了解围棋",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "围棋的目标是围出比对手更多的地盘。双方轮流在棋盘交叉点上放下一颗棋子，黑棋先行。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            RuleDiagramCard(
                title = "棋子在交叉点上",
                caption = "● 黑棋先行，○ 白棋随后；棋子落下后不再移动。",
                type = RuleDiagramType.PLACEMENT
            )
            RuleSection(
                title = "1. 棋盘与落子",
                lines = listOf(
                    "常用棋盘有 9 路、13 路和 19 路，棋子放在线与线的交叉点上。",
                    "黑棋先下，之后黑白双方轮流落子。",
                    "棋子落下后不能移动到别的位置；除非被提掉，否则会一直留在棋盘上。",
                    "交叉点已有棋子时，不能再次落子。"
                )
            )
            RuleDiagramCard(
                title = "气：棋子四周的空点",
                caption = "红圈标出的四个点就是黑棋的气，斜对角不算。",
                type = RuleDiagramType.LIBERTIES
            )
            RuleSection(
                title = "2. 气与提子",
                lines = listOf(
                    "棋子相邻的上、下、左、右空点叫作“气”。斜对角不算相邻。",
                    "同色棋子上下左右相连，会组成一块棋，共享所有气。",
                    "一块棋没有气时，会从棋盘上被提掉。",
                    "如果落子后让对方的棋块失去最后一口气，可以立即提掉对方棋子。"
                )
            )
            RuleDiagramCard(
                title = "提子：白棋已经没有气",
                caption = "白棋上下左右都被黑棋占据，因此会被从棋盘上提掉。",
                type = RuleDiagramType.CAPTURE
            )
            RuleSection(
                title = "3. 不能落子的地方",
                lines = listOf(
                    "不能下在已有棋子的交叉点上。",
                    "不能下一手让自己整块棋立即没有气，除非这一手同时提掉了对方棋子。",
                    "劫争中，不能立即在同一个位置提回，必须先到别处落子；这条规则由 KataGo 自动判断。"
                )
            )
            RuleSection(
                title = "4. 停一手与结束",
                lines = listOf(
                    "轮到自己时可以停一手（pass）。",
                    "双方连续停一手后，通常进入终局计算。",
                    "如果认为自己已经无法获胜，可以选择认输。"
                )
            )
            RuleSection(
                title = "5. 中国规则怎样计算胜负",
                lines = listOf(
                    "中国规则采用“子空皆地”：棋盘上的活棋和围住的空点都计入面积。",
                    "双方完成终局后，比较黑棋面积、白棋面积以及贴目。",
                    "本 App 的分先对局默认贴目为 7.5 目；让先和让子棋会按规则自动调整。",
                    "“数子”使用 KataGo 对当前局面的形势判断，适合学习和快速估算，不替代正式比赛裁判。"
                )
            )
            Text(
                "小提示：初学时先关注棋子有没有气、哪里能被提子，不必急着记住所有术语。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

private enum class RuleDiagramType {
    PLACEMENT,
    LIBERTIES,
    CAPTURE
}

/**
 * 用 Compose Canvas 绘制示意图，避免额外位图资源，并保证高分辨率屏幕清晰。
 */
@Composable
private fun RuleDiagramCard(
    title: String,
    caption: String,
    type: RuleDiagramType
) {
    ZenSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            GoRuleDiagram(type)
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GoRuleDiagram(type: RuleDiagramType) {
    val boardColor = Color(0xFFE4BE83)
    val lineColor = Color(0xFF4B351E)
    val blackStone = Color(0xFF181818)
    val whiteStone = Color(0xFFF8F5ED)
    val highlightColor = MaterialTheme.colorScheme.error

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.9f)
            .clip(RoundedCornerShape(8.dp))
            .background(boardColor)
            .padding(10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val interval = min(size.width, size.height) / 5f
            val span = interval * 4f
            val startX = (size.width - span) / 2f
            val startY = (size.height - span) / 2f
            val stoneRadius = interval * 0.38f

            fun point(x: Int, y: Int) = Offset(
                startX + x * interval,
                startY + y * interval
            )

            repeat(5) { index ->
                drawLine(
                    color = lineColor,
                    start = Offset(startX + index * interval, startY),
                    end = Offset(startX + index * interval, startY + span),
                    strokeWidth = 1.4.dp.toPx()
                )
                drawLine(
                    color = lineColor,
                    start = Offset(startX, startY + index * interval),
                    end = Offset(startX + span, startY + index * interval),
                    strokeWidth = 1.4.dp.toPx()
                )
            }

            fun stone(x: Int, y: Int, isBlack: Boolean) {
                drawCircle(
                    color = if (isBlack) blackStone else whiteStone,
                    radius = stoneRadius,
                    center = point(x, y)
                )
                if (!isBlack) {
                    drawCircle(
                        color = lineColor.copy(alpha = 0.55f),
                        radius = stoneRadius,
                        center = point(x, y),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            when (type) {
                RuleDiagramType.PLACEMENT -> {
                    stone(1, 1, true)
                    stone(3, 1, false)
                    stone(2, 3, true)
                }

                RuleDiagramType.LIBERTIES -> {
                    stone(2, 2, true)
                    listOf(
                        2 to 1,
                        1 to 2,
                        3 to 2,
                        2 to 3
                    ).forEach { (x, y) ->
                        drawCircle(
                            color = highlightColor,
                            radius = interval * 0.16f,
                            center = point(x, y),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                RuleDiagramType.CAPTURE -> {
                    stone(2, 2, false)
                    stone(2, 1, true)
                    stone(1, 2, true)
                    stone(3, 2, true)
                    stone(2, 3, true)
                    val center = point(2, 2)
                    val crossRadius = interval * 0.2f
                    drawLine(
                        color = highlightColor,
                        start = Offset(center.x - crossRadius, center.y - crossRadius),
                        end = Offset(center.x + crossRadius, center.y + crossRadius),
                        strokeWidth = 2.5.dp.toPx()
                    )
                    drawLine(
                        color = highlightColor,
                        start = Offset(center.x + crossRadius, center.y - crossRadius),
                        end = Offset(center.x - crossRadius, center.y + crossRadius),
                        strokeWidth = 2.5.dp.toPx()
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleSection(title: String, lines: List<String>) {
    ZenSurface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            lines.forEach { line ->
                Text(
                    text = "• $line",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
