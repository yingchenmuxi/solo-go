package com.sologo.core.model

/**
 * 标准星位让子点。坐标是内部坐标：x 从左到右，y 从上到下。
 * 只生成展示和 GTP 初始摆子需要的点，不参与围棋规则判定。
 */
object HandicapStones {
    fun points(boardSize: Int, count: Int): List<GoPoint> {
        if (count <= 0) return emptyList()
        val low = when (boardSize) {
            9 -> 2
            13 -> 3
            else -> 3
        }
        val high = boardSize - 1 - low
        val mid = boardSize / 2
        val corners = listOf(
            GoPoint(high, low),   // 右上
            GoPoint(low, high),   // 左下
            GoPoint(low, low),    // 左上
            GoPoint(high, high)   // 右下
        )
        val sides = listOf(
            GoPoint(low, mid),
            GoPoint(high, mid),
            GoPoint(mid, low),
            GoPoint(mid, high)
        )
        return when {
            count <= 4 -> corners.take(count)
            count == 5 -> corners + GoPoint(mid, mid)
            else -> (corners + sides + GoPoint(mid, mid)).take(count.coerceAtMost(9))
        }
    }
}
