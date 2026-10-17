package com.sologo.sgf

/**
 * SGF 树节点。
 *
 * properties 保留 SGF 原始属性名和值，children 对应多分支变化。
 * 这样导入一盘带变化的棋谱后，导出时不会只能保留主分支。
 */
data class SgfNode(
    val properties: MutableMap<String, MutableList<String>> = linkedMapOf(),
    val children: MutableList<SgfNode> = mutableListOf()
) {
    fun firstValue(key: String): String? = properties[key]?.firstOrNull()

    fun values(key: String): List<String> = properties[key].orEmpty()

    fun setProperty(key: String, vararg values: String) {
        properties[key] = values.toMutableList()
    }

    fun addChild(child: SgfNode): SgfNode {
        children.add(child)
        return child
    }
}

data class SgfCollection(val games: List<SgfNode>)

class SgfParseException(
    message: String,
    val position: Int = -1
) : IllegalArgumentException(if (position >= 0) "$message（位置 $position）" else message)
