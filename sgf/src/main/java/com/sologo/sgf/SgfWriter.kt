package com.sologo.sgf

/**
 * SGF FF[4] 写出器。输出 UTF-8 文本，不把 AI 分析塞进棋谱。
 */
object SgfWriter {
    fun write(collection: SgfCollection): String =
        collection.games.joinToString(separator = "\n") { writeGame(it) } + "\n"

    fun writeGame(root: SgfNode): String = buildString {
        append("(")
        writeNode(root)
        append(")")
    }

    private fun StringBuilder.writeNode(node: SgfNode) {
        append(";")
        node.properties.forEach { (key, values) ->
            append(key)
            values.forEach { value ->
                append("[")
                append(escape(value))
                append("]")
            }
        }
        node.children.forEach { child ->
            if (node.children.size > 1) append("(")
            writeNode(child)
            if (node.children.size > 1) append(")")
        }
    }

    private fun escape(value: String): String = buildString(value.length + 8) {
        value.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                ']' -> append("\\]")
                '\n' -> append("\\\n")
                '\r' -> Unit
                else -> append(c)
            }
        }
    }
}
