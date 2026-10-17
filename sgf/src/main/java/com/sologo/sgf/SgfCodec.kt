package com.sologo.sgf

import com.sologo.core.model.GameRecord
import com.sologo.core.model.GoCoordinates
import com.sologo.core.model.GoPoint
import com.sologo.core.model.MoveRecord
import com.sologo.core.model.StoneColor

/**
 * GameRecord（业务模型）与 SGF 树之间的转换。
 *
 * 目前 App 的对局记录是主线；导入时如果遇到多分支，SgfParser/SgfWriter
 * 能完整保留树结构，后续复盘 UI 可以逐个分支浏览。
 */
object SgfCodec {
    fun parse(source: String): SgfCollection = SgfParser.parse(source)

    fun encode(record: GameRecord): String {
        val root = SgfNode()
        root.setProperty("GM", "1")
        root.setProperty("FF", "4")
        root.setProperty("CA", "UTF-8")
        root.setProperty("AP", "SoloGo:1.0")
        root.setProperty("SZ", record.size.toString())
        root.setProperty("KM", record.komi.toString())
        root.setProperty("RU", record.rules)
        if (record.handicap > 0) root.setProperty("HA", record.handicap.toString())
        if (record.blackName.isNotBlank()) root.setProperty("PB", record.blackName)
        if (record.whiteName.isNotBlank()) root.setProperty("PW", record.whiteName)
        if (record.result.isNotBlank()) root.setProperty("RE", record.result)
        if (record.date.isNotBlank()) root.setProperty("DT", record.date)
        if (record.setupBlack.isNotEmpty()) {
            root.setProperty("AB", *record.setupBlack.map(GoCoordinates::toSgf).toTypedArray())
        }
        if (record.setupWhite.isNotEmpty()) {
            root.setProperty("AW", *record.setupWhite.map(GoCoordinates::toSgf).toTypedArray())
        }

        var cursor = root
        record.moves.forEach { move ->
            val node = SgfNode()
            val key = if (move.color == StoneColor.BLACK) "B" else "W"
            node.setProperty(key, move.point?.let(GoCoordinates::toSgf) ?: "")
            if (move.comment.isNotBlank()) node.setProperty("C", move.comment)
            cursor.addChild(node)
            cursor = node
        }
        return SgfWriter.writeGame(root)
    }

    /** 读取主分支。遇到变化时只跟随第一个子节点。 */
    fun decode(source: String): GameRecord {
        val root = parse(source).games.firstOrNull()
            ?: throw SgfParseException("SGF 中没有棋局")
        return decode(root)
    }

    fun decode(root: SgfNode): GameRecord {
        val size = parseBoardSize(root.firstValue("SZ"))
        val komi = root.firstValue("KM")?.toFloatOrNull() ?: 7.5f
        val moves = mutableListOf<MoveRecord>()
        var node: SgfNode? = root.children.firstOrNull()
        var number = 1
        while (node != null) {
            val black = node.firstValue("B")
            val white = node.firstValue("W")
            if (black != null || white != null) {
                val color = if (black != null) StoneColor.BLACK else StoneColor.WHITE
                val raw = black ?: white.orEmpty()
                val point = if (raw.isBlank() || (size <= 19 && raw.equals("tt", true))) {
                    null
                } else {
                    GoCoordinates.parseSgf(raw)
                }
                moves.add(
                    MoveRecord(
                        color = color,
                        point = point,
                        comment = node.firstValue("C").orEmpty(),
                        moveNumber = number
                    )
                )
                number++
            }
            node = node.children.firstOrNull()
        }
        return GameRecord(
            size = size,
            komi = komi,
            rules = root.firstValue("RU").orEmpty().ifBlank { "Chinese" },
            handicap = root.firstValue("HA")?.toIntOrNull() ?: 0,
            blackName = root.firstValue("PB").orEmpty(),
            whiteName = root.firstValue("PW").orEmpty(),
            result = root.firstValue("RE").orEmpty(),
            date = root.firstValue("DT").orEmpty(),
            setupBlack = root.values("AB").mapNotNull(GoCoordinates::parseSgf),
            setupWhite = root.values("AW").mapNotNull(GoCoordinates::parseSgf),
            moves = moves
        )
    }

    fun mainLine(root: SgfNode): List<SgfNode> {
        val result = mutableListOf<SgfNode>()
        var node: SgfNode? = root
        while (node != null) {
            result.add(node)
            node = node.children.firstOrNull()
        }
        return result
    }

    fun nodeMove(node: SgfNode, size: Int): Pair<StoneColor, GoPoint?>? {
        val black = node.firstValue("B")
        val white = node.firstValue("W")
        val raw = black ?: white ?: return null
        val point = if (raw.isBlank() || (size <= 19 && raw.equals("tt", true))) {
            null
        } else {
            GoCoordinates.parseSgf(raw)
        }
        return (if (black != null) StoneColor.BLACK else StoneColor.WHITE) to point
    }

    private fun parseBoardSize(value: String?): Int {
        val first = value?.substringBefore(':')?.toIntOrNull() ?: 19
        return first.coerceIn(2, 19)
    }
}
