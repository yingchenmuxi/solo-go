package com.sologo.core.model

/**
 * 数子时根据 KataGo ownership 统计的终局占地。
 *
 * 中国规则按“子空皆地”计算，所以 blackPoints/whitePoints 统计所有交叉点；
 * blackTerritory/whiteTerritory 只统计空点，方便在棋盘上标出黑白地盘。
 * ownership 已由引擎统一为黑方视角：正数黑、负数白。
 */
data class ScoreSummary(
    val blackPoints: Int,
    val whitePoints: Int,
    val blackTerritory: Int,
    val whiteTerritory: Int,
    val neutralPoints: Int,
    val komi: Float
) {
    val blackScore: Double get() = blackPoints.toDouble()
    val whiteScore: Double get() = whitePoints.toDouble() + komi
    val lead: Double get() = blackScore - whiteScore
    val isEven: Boolean get() = kotlin.math.abs(lead) < 0.05

    companion object {
        /**
         * @param scoringBoard 已移除死子的棋盘；用于区分空地和棋子。
         */
        fun fromOwnership(
            ownership: List<Float>,
            scoringBoard: BoardPosition,
            komi: Float
        ): ScoreSummary? {
            val size = scoringBoard.size
            val expectedSize = size * size
            if (size !in 2..19 || ownership.size < expectedSize) return null

            var blackPoints = 0
            var whitePoints = 0
            var blackTerritory = 0
            var whiteTerritory = 0
            var neutralPoints = 0
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val value = ownership[y * size + x]
                    val isEmpty = scoringBoard.isEmpty(GoPoint(x, y))
                    when {
                        !value.isFinite() -> neutralPoints++
                        value > 0f -> {
                            blackPoints++
                            if (isEmpty) blackTerritory++
                        }
                        value < 0f -> {
                            whitePoints++
                            if (isEmpty) whiteTerritory++
                        }
                        else -> neutralPoints++
                    }
                }
            }
            return ScoreSummary(
                blackPoints = blackPoints,
                whitePoints = whitePoints,
                blackTerritory = blackTerritory,
                whiteTerritory = whiteTerritory,
                neutralPoints = neutralPoints,
                komi = komi
            )
        }

        /**
         * 棋盘叠加层只画空点，棋子本身已经用黑白子显示。
         */
        fun emptyPointMarkers(
            ownership: List<Float>,
            scoringBoard: BoardPosition
        ): List<Float> {
            val size = scoringBoard.size
            val expectedSize = size * size
            if (ownership.size < expectedSize) return emptyList()
            return List(expectedSize) { index ->
                val point = GoPoint(index % size, index / size)
                if (!scoringBoard.isEmpty(point)) {
                    0f
                } else {
                    val value = ownership[index]
                    if (value.isFinite()) value.coerceIn(-1f, 1f) else 0f
                }
            }
        }
    }
}
