package com.sologo.core.model

import kotlinx.serialization.Serializable

/**
 * KataGo kata-analyze 返回的一条候选着法。
 *
 * 约定：
 * - winrate 是“黑棋胜率”，范围 0..1；
 * - scoreLead 是“黑棋目数领先”，正数黑好、负数白好；
 * - pv 是 Principal Variation，包含当前候选着法及其后的推演。
 */
@Serializable
data class CandidateMove(
    val move: GoPoint?,
    val winrate: Double,
    val scoreLead: Double,
    val visits: Int,
    val order: Int,
    val pv: List<GoPoint> = emptyList(),
    val ownership: List<Float> = emptyList()
) {
    val winratePercent: Double get() = (winrate * 100.0).coerceIn(0.0, 100.0)
}

@Serializable
data class PositionAnalysis(
    val turn: StoneColor,
    val candidates: List<CandidateMove> = emptyList(),
    val ownership: List<Float> = emptyList(),
    val ownershipSize: Int = 0,
    val visits: Int = 0
) {
    val best: CandidateMove? get() = candidates.minByOrNull { it.order }

    /** 黑棋视角胜率；没有候选时返回 null。 */
    val blackWinrate: Double? get() = best?.winrate

    /** 黑棋视角目数领先；没有候选时返回 null。 */
    val blackScoreLead: Double? get() = best?.scoreLead
}

/**
 * 单手价值分析。loss 是“如果下这手，黑棋胜率相对上一手最佳点的下降”。
 * 负值表示黑棋实际下得比最佳点更好（通常出现在白棋视角的转换里）。
 */
@Serializable
data class MoveAnalysis(
    val moveNumber: Int,
    val color: StoneColor,
    val playedMove: GoPoint?,
    val winrateBefore: Double,
    val scoreLeadBefore: Double,
    val winrateAfter: Double,
    val scoreLeadAfter: Double,
    val loss: Double,
    val isBlunder: Boolean = false,
    val comment: String = "",
    /** 该手之后的领地热力，长度 = boardSize * boardSize；空表示未保存。 */
    val ownership: List<Float> = emptyList(),
    val candidates: List<CandidateMove> = emptyList()
)

/**
 * 与 SGF 同名的 companion JSON 内容。标准 SGF 不写入这些字段，
 * 从而保证外部棋谱软件仍可正常打开导出的 .sgf 文件。
 */
@Serializable
data class GameAnalysis(
    val schemaVersion: Int = 1,
    val sgfHash: String = "",
    val engineName: String = "KataGo",
    val engineVersion: String = "",
    val boardSize: Int = 19,
    val visits: Int = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val moves: List<MoveAnalysis> = emptyList()
)
