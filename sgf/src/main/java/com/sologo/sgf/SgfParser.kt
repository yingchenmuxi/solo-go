package com.sologo.sgf

/**
 * 一个轻量、无反射、无第三方依赖的 SGF FF[4] 解析器。
 *
 * 支持：
 * - 单局/多局 collection；
 * - 属性值转义（\\] 、\\\\、反斜杠换行）；
 * - 节点、主分支、多分支；
 * - AP/CA/FF 等未知属性原样保留。
 *
 * 注意：解析器只负责语法，不解释围棋规则。
 */
object SgfParser {
    fun parse(source: String): SgfCollection {
        val text = source.removePrefix("\uFEFF")
        val parser = Parser(text)
        return parser.parseCollection()
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun parseCollection(): SgfCollection {
            val games = mutableListOf<SgfNode>()
            skipWhitespace()
            // 兼容不规范的、没有外层括号的单个 game tree。
            if (peek() == ';') {
                games.add(parseNode())
                skipWhitespace()
                return SgfCollection(games)
            }
            while (true) {
                skipWhitespace()
                if (index >= text.length) break
                expect('(')
                skipWhitespace()
                expect(';')
                val root = parseNode(afterSemicolon = true)
                games.add(root)
                skipWhitespace()
                expect(')')
            }
            if (games.isEmpty()) throw SgfParseException("SGF 中没有找到棋局")
            return SgfCollection(games)
        }

        /**
         * 解析一个节点及其后续节点/分支。
         * afterSemicolon=true 表示调用方已经消费了分号。
         */
        private fun parseNode(afterSemicolon: Boolean = false): SgfNode {
            if (!afterSemicolon) expect(';')
            val node = SgfNode()
            while (true) {
                skipWhitespace()
                val c = peek() ?: return node
                when {
                    c == ';' -> {
                        index++
                        val child = parseNode(afterSemicolon = true)
                        node.children.add(child)
                        return node
                    }
                    c == '(' -> {
                        index++
                        skipWhitespace()
                        expect(';')
                        node.children.add(parseNode(afterSemicolon = true))
                        skipWhitespace()
                        expect(')')
                        // 一个节点后面可以跟多个并列分支。
                        while (true) {
                            skipWhitespace()
                            if (peek() == '(') {
                                index++
                                skipWhitespace()
                                expect(';')
                                node.children.add(parseNode(afterSemicolon = true))
                                skipWhitespace()
                                expect(')')
                            } else {
                                break
                            }
                        }
                        return node
                    }
                    c == ')' -> return node
                    c.isLetter() -> parseProperty(node)
                    else -> throw SgfParseException("无法识别的字符 '$c'", index)
                }
            }
        }

        private fun parseProperty(node: SgfNode) {
            val keyStart = index
            while (peek()?.isLetter() == true) index++
            val key = text.substring(keyStart, index).uppercase()
            if (key.isEmpty()) throw SgfParseException("属性名为空", index)
            val values = mutableListOf<String>()
            do {
                skipWhitespace()
                expect('[')
                values.add(parseValue())
            } while (peek() == '[')
            node.properties.getOrPut(key) { mutableListOf() }.addAll(values)
        }

        private fun parseValue(): String {
            val result = StringBuilder()
            while (true) {
                if (index >= text.length) throw SgfParseException("属性值缺少 ]", index)
                val c = text[index++]
                when (c) {
                    ']' -> return result.toString()
                    '\\' -> {
                        if (index >= text.length) {
                            throw SgfParseException("转义符后缺少字符", index)
                        }
                        val escaped = text[index++]
                        // SGF 中反斜杠 + 换行表示删除换行。
                        if (escaped != '\n' && escaped != '\r') result.append(escaped)
                    }
                    else -> result.append(c)
                }
            }
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        private fun peek(): Char? = text.getOrNull(index)

        private fun expect(expected: Char) {
            if (peek() != expected) {
                throw SgfParseException("期望 '$expected'，实际为 '${peek() ?: "EOF"}'", index)
            }
            index++
        }
    }
}
