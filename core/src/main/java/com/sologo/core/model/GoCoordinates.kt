package com.sologo.core.model

/**
 * 三种坐标的互转：
 * - GTP：列字母跳过 I，行号从棋盘底部开始；
 * - SGF：两字母，aa 是左上角；
 * - 内部 GoPoint：x 从左到右，y 从上到下。
 *
 * 19 路 GTP 的列是 A-T（跳过 I），9 路是 A-J（跳过 I）。
 */
object GoCoordinates {
    private const val GTP_COLUMNS = "ABCDEFGHJKLMNOPQRSTUVWXYZ"

    fun toGtp(point: GoPoint, boardSize: Int): String {
        require(boardSize in 2..19) { "不支持的棋盘大小: $boardSize" }
        require(point.x in 0 until boardSize && point.y in 0 until boardSize) {
            "坐标越界: $point / $boardSize"
        }
        val column = GTP_COLUMNS[point.x]
        val row = boardSize - point.y
        return "$column$row"
    }

    fun parseGtp(text: String, boardSize: Int): GoPoint? {
        val value = text.trim().uppercase()
        if (value == "PASS" || value == "RESIGN") return null
        if (value.length < 2) return null
        val x = GTP_COLUMNS.indexOf(value.first())
        if (x !in 0 until boardSize) return null
        val row = value.drop(1).toIntOrNull() ?: return null
        if (row !in 1..boardSize) return null
        return GoPoint(x, boardSize - row)
    }

    fun toSgf(point: GoPoint): String =
        buildString(2) {
            append('a' + point.x)
            append('a' + point.y)
        }

    fun parseSgf(value: String): GoPoint? {
        val text = value.trim()
        if (text.isEmpty()) return null
        if (text.length != 2) return null
        val x = text[0] - 'a'
        val y = text[1] - 'a'
        if (x !in 0..18 || y !in 0..18) return null
        return GoPoint(x, y)
    }
}
