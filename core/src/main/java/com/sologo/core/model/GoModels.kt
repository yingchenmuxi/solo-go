package com.sologo.core.model

import kotlinx.serialization.Serializable

/**
 * 棋盘上的一个交叉点。
 *
 * 坐标约定（整个项目统一）：
 * - x = 0 表示最左列，向右递增；
 * - y = 0 表示最上行，向下递增；
 * - 坐标转换全部集中在本包的 GoCoordinates.kt，UI 和引擎层不要各自实现一套。
 */
@Serializable
data class GoPoint(val x: Int, val y: Int) {
    init {
        require(x in 0..18) { "x 越界: $x" }
        require(y in 0..18) { "y 越界: $y" }
    }
}

@Serializable
enum class StoneColor {
    BLACK,
    WHITE;

    val opponent: StoneColor
        get() = if (this == BLACK) WHITE else BLACK

    /** GTP 命令里使用的颜色字母。 */
    val gtp: String
        get() = if (this == BLACK) "B" else "W"
}

/**
 * 一手棋。point 为 null 表示 pass。
 *
 * 这里只记录“发生了什么”，不承担合法性判断；合法性由 KataGo 的 play/genmove 返回结果决定。
 */
@Serializable
data class MoveRecord(
    val color: StoneColor,
    val point: GoPoint?,
    val comment: String = "",
    val moveNumber: Int = 0
)

/**
 * 与 UI 无关的一盘棋业务数据。SGF 导出的根节点就是由它生成的。
 */
@Serializable
data class GameRecord(
    val size: Int = 19,
    val komi: Float = 7.5f,
    val rules: String = "Chinese",
    val handicap: Int = 0,
    val blackName: String = "",
    val whiteName: String = "",
    val result: String = "",
    val date: String = "",
    /** SGF AB/AW 摆子，主要供复盘显示使用。 */
    val setupBlack: List<GoPoint> = emptyList(),
    val setupWhite: List<GoPoint> = emptyList(),
    val moves: List<MoveRecord> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
) {
    fun withMove(move: MoveRecord): GameRecord = copy(
        moves = moves + move.copy(moveNumber = moves.size + 1)
    )

    fun withMoves(newMoves: List<MoveRecord>): GameRecord = copy(
        moves = newMoves.mapIndexed { index, move -> move.copy(moveNumber = index + 1) }
    )
}

@Serializable
data class ScoreEstimate(
    val blackScore: Double,
    val whiteScore: Double,
    val lead: Double
) {
    val displayText: String
        get() = if (lead >= 0) "黑 +${"%.1f".format(lead)}" else "白 +${"%.1f".format(-lead)}"
}
