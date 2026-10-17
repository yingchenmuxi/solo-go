package com.sologo.core.model

/**
 * 棋盘显示缓存。
 *
 * 重要边界：这不是围棋规则引擎，也不负责判定劫争/终局/胜负。
 * 它只负责把 KataGo 已经接受的落子同步成“能画出来的棋盘”，并处理提子显示。
 * 真正的合法性、提子、劫争和终局判断仍以 KataGo 为准。
 */
data class BoardPosition(
    val size: Int,
    val stones: Map<GoPoint, StoneColor>,
    val lastMove: GoPoint? = null,
    val moveNumber: Int = 0,
    val capturedByBlack: Int = 0,
    val capturedByWhite: Int = 0
) {
    fun stoneAt(point: GoPoint): StoneColor? = stones[point]

    fun isEmpty(point: GoPoint): Boolean = stones[point] == null

    /**
     * 在显示缓存上落子。返回 null 表示该点在缓存层面不可落（占用或自杀）。
     * 正常的劫争、打劫禁着等由 KataGo 在调用 play 时拒绝。
     */
    fun play(color: StoneColor, point: GoPoint?): BoardPosition? {
        if (point == null) {
            return copy(lastMove = null, moveNumber = moveNumber + 1)
        }
        if (point.x !in 0 until size || point.y !in 0 until size) return null
        if (stones.containsKey(point)) return null

        val next = stones.toMutableMap()
        next[point] = color
        var captured = 0

        // 先提掉四个方向上所有无气的对方棋块。
        for (neighbor in neighbors(point)) {
            val neighborColor = next[neighbor] ?: continue
            if (neighborColor == color) continue
            val group = collectGroup(next, neighbor, neighborColor)
            if (!hasLiberty(next, group)) {
                group.forEach { next.remove(it) }
                captured += group.size
            }
        }

        // 提子后自己的棋块仍然无气，说明是自杀手；KataGo 也会拒绝，这里不改变缓存。
        val ownGroup = collectGroup(next, point, color)
        if (!hasLiberty(next, ownGroup)) return null

        return copy(
            stones = next,
            lastMove = point,
            moveNumber = moveNumber + 1,
            capturedByBlack = capturedByBlack + if (color == StoneColor.BLACK) captured else 0,
            capturedByWhite = capturedByWhite + if (color == StoneColor.WHITE) captured else 0
        )
    }

    /** 回退显示缓存。调用方需要同时保证 KataGo 已执行 undo。 */
    fun undo(previous: BoardPosition): BoardPosition = previous

    private fun neighbors(point: GoPoint): Sequence<GoPoint> = sequence {
        if (point.x > 0) yield(GoPoint(point.x - 1, point.y))
        if (point.x < size - 1) yield(GoPoint(point.x + 1, point.y))
        if (point.y > 0) yield(GoPoint(point.x, point.y - 1))
        if (point.y < size - 1) yield(GoPoint(point.x, point.y + 1))
    }

    private fun collectGroup(
        board: Map<GoPoint, StoneColor>,
        start: GoPoint,
        color: StoneColor
    ): Set<GoPoint> {
        val visited = mutableSetOf<GoPoint>()
        val queue = ArrayDeque<GoPoint>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            // 用索引删除，避免依赖 Java 21 SequencedCollection 的新方法。
            val current = queue.removeAt(0)
            if (!visited.add(current)) continue
            if (board[current] != color) continue
            for (neighbor in neighbors(current)) {
                if (board[neighbor] == color && neighbor !in visited) queue.add(neighbor)
            }
        }
        return visited
    }

    private fun hasLiberty(board: Map<GoPoint, StoneColor>, group: Set<GoPoint>): Boolean =
        group.any { point -> neighbors(point).any { board[it] == null } }

    companion object {
        fun empty(size: Int): BoardPosition = BoardPosition(size = size, stones = emptyMap())

        fun fromSetup(
            size: Int,
            black: Set<GoPoint>,
            white: Set<GoPoint>
        ): BoardPosition {
            val stones = buildMap {
                black.forEach { put(it, StoneColor.BLACK) }
                white.forEach { put(it, StoneColor.WHITE) }
            }
            return BoardPosition(size = size, stones = stones)
        }
    }
}
