package com.sologo.app.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.sologo.core.model.BoardPosition
import com.sologo.core.model.CandidateMove
import com.sologo.core.model.GoPoint
import com.sologo.core.model.StoneColor
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Compose Canvas 棋盘。
 *
 * 这个组件只做绘制和把触摸点换算成交叉点，不包含任何围棋规则。
 * ownership 为引擎统一转换后的黑棋视角领地值（正数黑、负数白），用于势力色块。
 */
@Composable
fun GoBoard(
    position: BoardPosition,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showCoordinates: Boolean = true,
    ownership: List<Float> = emptyList(),
    showOwnership: Boolean = false,
    candidates: List<CandidateMove> = emptyList(),
    onIntersectionTap: (GoPoint) -> Unit = {}
) {
    val boardSize = position.size
    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(boardSize, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { tap ->
                    val minSide = minOf(size.width, size.height).toFloat()
                    val margin = minSide * BOARD_MARGIN_RATIO
                    val cell = (minSide - margin * 2f) / (boardSize - 1).coerceAtLeast(1)
                    val x = ((tap.x - margin) / cell).roundToInt()
                    val y = ((tap.y - margin) / cell).roundToInt()
                    if (x in 0 until boardSize && y in 0 until boardSize) {
                        val center = Offset(margin + x * cell, margin + y * cell)
                        // 离交叉点太远就不落子，避免误触。
                        if ((tap - center).getDistance() <= cell * 0.62f) {
                            onIntersectionTap(GoPoint(x, y))
                        }
                    }
                }
            }
    ) {
        val margin = size.minDimension * BOARD_MARGIN_RATIO
        val cell = (size.minDimension - margin * 2f) / (boardSize - 1).coerceAtLeast(1)
        fun point(x: Int, y: Int) = Offset(margin + x * cell, margin + y * cell)

        // 木纹底色（用渐变模拟，不引入图片资源）。
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFE8C98E), Color(0xFFD8AE6B))
            )
        )

        // 势力层放在格线下面、棋子上面。蓝色表示黑方影响，红色表示白方影响，
        // 颜色深浅跟随 ownership 的置信度，便于同时看清边界和确定区域。
        if (showOwnership && ownership.size >= boardSize * boardSize) {
            for (y in 0 until boardSize) {
                for (x in 0 until boardSize) {
                    val value = ownership[y * boardSize + x]
                    if (abs(value) < 0.08f) continue
                    val center = point(x, y)
                    val half = cell * 0.46f
                    val strength = abs(value).coerceIn(0f, 1f)
                    val color = if (value > 0) TerritoryBlackColor else TerritoryWhiteColor
                    drawRoundRect(
                        color = color.copy(alpha = 0.12f + strength * 0.34f),
                        topLeft = Offset(center.x - half, center.y - half),
                        size = Size(half * 2f, half * 2f),
                        cornerRadius = CornerRadius(cell * 0.16f)
                    )
                }
            }
        }

        val gridColor = Color(0xFF4A3420)
        for (i in 0 until boardSize) {
            val horizontalStart = point(0, i)
            val horizontalEnd = point(boardSize - 1, i)
            drawLine(gridColor, horizontalStart, horizontalEnd, strokeWidth = 1.4f)
            val verticalStart = point(i, 0)
            val verticalEnd = point(i, boardSize - 1)
            drawLine(gridColor, verticalStart, verticalEnd, strokeWidth = 1.4f)
        }

        // 星位。
        starPoints(boardSize).forEach { (x, y) ->
            drawCircle(gridColor, radius = cell * 0.075f, center = point(x, y))
        }

        // 棋子。
        position.stones.forEach { (p, color) ->
            val center = point(p.x, p.y)
            val radius = cell * 0.46f
            drawCircle(
                brush = if (color == StoneColor.BLACK) {
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF666666), Color(0xFF050505)),
                        center = center - Offset(radius * 0.3f, radius * 0.3f),
                        radius = radius * 1.3f
                    )
                } else {
                    Brush.radialGradient(
                        colors = listOf(Color.White, Color(0xFFD6D6D6)),
                        center = center - Offset(radius * 0.3f, radius * 0.3f),
                        radius = radius * 1.3f
                    )
                },
                radius = radius,
                center = center
            )
            if (color == StoneColor.WHITE) {
                drawCircle(
                    color = Color(0xFF8B8B8B),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1f)
                )
            }
        }

        // 最后一手标记。
        position.lastMove?.let { last ->
            val center = point(last.x, last.y)
            val lastColor = position.stoneAt(last)
            drawCircle(
                color = if (lastColor == StoneColor.BLACK) Color.White else Color(0xFFE74C3C),
                radius = cell * 0.12f,
                center = center
            )
        }

        // 推荐候选点：只画前 3 个，避免遮挡棋盘。
        candidates.filter { it.move != null }.take(3).forEachIndexed { index, candidate ->
            val p = candidate.move ?: return@forEachIndexed
            if (p.x !in 0 until boardSize || p.y !in 0 until boardSize) return@forEachIndexed
            val center = point(p.x, p.y)
            val markerColor = when (index) {
                0 -> Color(0xFF1E88E5)
                1 -> Color(0xFF43A047)
                else -> Color(0xFFFB8C00)
            }
            drawCircle(
                color = markerColor.copy(alpha = 0.9f),
                radius = cell * 0.30f,
                center = center,
                style = Stroke(width = cell * 0.07f)
            )
            val paint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = cell * 0.30f
                color = markerColor.toArgb()
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            drawContext.canvas.nativeCanvas.drawText(
                "${index + 1}",
                center.x,
                center.y + paint.textSize * 0.35f,
                paint
            )
        }

        if (showCoordinates) {
            val paint = Paint().apply {
                isAntiAlias = true
                textSize = (cell * 0.28f).coerceAtLeast(8f)
                color = Color(0xFF4A3420).toArgb()
                textAlign = Paint.Align.CENTER
            }
            val columns = "ABCDEFGHJKLMNOPQRST"
            for (i in 0 until boardSize) {
                val top = point(i, 0)
                drawContext.canvas.nativeCanvas.drawText(
                    columns[i].toString(),
                    top.x,
                    margin * 0.55f,
                    paint
                )
                // 底部也标一次横坐标，方便从对面方向阅读。
                drawContext.canvas.nativeCanvas.drawText(
                    columns[i].toString(),
                    top.x,
                    size.height - margin * 0.25f,
                    paint
                )
                // 纵坐标必须使用左侧每一行的 y，而不是所有行都画在顶部。
                val left = point(0, i)
                val row = boardSize - i
                drawContext.canvas.nativeCanvas.drawText(
                    row.toString(),
                    margin * 0.45f,
                    left.y + paint.textSize * 0.35f,
                    paint
                )
            }
        }

        // 棋盘边框。
        drawRect(
            color = Color(0xFF3D2A16).copy(alpha = 0.7f),
            topLeft = Offset(margin * 0.5f, margin * 0.5f),
            size = Size(
                size.width - margin,
                size.height - margin
            ),
            style = Stroke(width = 2f, pathEffect = PathEffect.cornerPathEffect(4f))
        )
    }
}

private fun starPoints(boardSize: Int): List<Pair<Int, Int>> {
    val values = when (boardSize) {
        9 -> listOf(2, 4, 6)
        13 -> listOf(3, 6, 9)
        19 -> listOf(3, 9, 15)
        else -> emptyList()
    }
    if (values.isEmpty()) return emptyList()
    return buildList {
        for (x in values) for (y in values) add(x to y)
    }
}

private const val BOARD_MARGIN_RATIO = 0.09f

/** 与局势面板图例共用，避免棋盘颜色和说明颜色不一致。 */
val TerritoryBlackColor = Color(0xFF1565C0)
val TerritoryWhiteColor = Color(0xFFE53935)
