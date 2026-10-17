package com.sologo.app.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.sologo.core.model.MoveAnalysis
import kotlin.math.max

/**
 * 复盘胜率曲线。用 Compose Canvas 绘制，避免为两张小图引入图表库。
 */
@Composable
fun WinrateChart(
    moves: List<MoveAnalysis>,
    modifier: Modifier = Modifier
) {
    AnalysisChartFrame(
        title = "黑棋胜率",
        moves = moves,
        modifier = modifier,
        valueOf = { it.winrateAfter },
        minValue = 0.0,
        maxValue = 1.0,
        valueFormatter = { "${(it * 100).toInt()}%" },
        color = Color(0xFF1565C0)
    )
}

/**
 * 目数差曲线。正数黑领先，负数白领先。
 */
@Composable
fun ScoreLeadChart(
    moves: List<MoveAnalysis>,
    modifier: Modifier = Modifier
) {
    AnalysisChartFrame(
        title = "目数差（黑 - 白）",
        moves = moves,
        modifier = modifier,
        valueOf = { it.scoreLeadAfter },
        minValue = null,
        maxValue = null,
        valueFormatter = { if (it >= 0) "+${"%.1f".format(it)}" else "%.1f".format(it) },
        color = Color(0xFFC62828)
    )
}

@Composable
private fun AnalysisChartFrame(
    title: String,
    moves: List<MoveAnalysis>,
    modifier: Modifier,
    valueOf: (MoveAnalysis) -> Double,
    minValue: Double?,
    maxValue: Double?,
    valueFormatter: (Double) -> String,
    color: Color
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        if (moves.isEmpty()) {
            Text(
                text = "暂无复盘数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val left = 42f
                val right = size.width - 10f
                val top = 10f
                val bottom = size.height - 24f
                val values = moves.map(valueOf)
                val lo = minValue ?: (values.minOrNull() ?: 0.0) - 2.0
                val hi = maxValue ?: (values.maxOrNull() ?: 1.0) + 2.0
                val span = max(0.001, hi - lo)
                fun y(value: Double) = bottom - ((value - lo) / span).toFloat() * (bottom - top)

                // 网格与 0 轴。
                repeat(4) { i ->
                    val yy = top + (bottom - top) * i / 3f
                    drawLine(Color(0x22000000), Offset(left, yy), Offset(right, yy), 1f)
                }
                if (lo < 0 && hi > 0) {
                    drawLine(Color(0x66000000), Offset(left, y(0.0)), Offset(right, y(0.0)), 1.2f)
                }
                val path = Path()
                values.forEachIndexed { index, value ->
                    val x = if (values.size == 1) left else left + (right - left) * index / (values.size - 1)
                    val yy = y(value)
                    if (index == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
                }
                drawPath(path, color, style = Stroke(width = 2.4f))
                values.forEachIndexed { index, value ->
                    val x = if (values.size == 1) left else left + (right - left) * index / (values.size - 1)
                    drawCircle(color, radius = 2.6f, center = Offset(x, y(value)))
                }
                val paint = Paint().apply {
                    isAntiAlias = true
                    textSize = 22f
                    setColor(android.graphics.Color.DKGRAY)
                }
                drawContext.canvas.nativeCanvas.drawText(
                    valueFormatter(lo),
                    2f,
                    bottom,
                    paint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    valueFormatter(hi),
                    2f,
                    top + paint.textSize,
                    paint
                )
            }
        }
    }
}
