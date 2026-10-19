package com.sologo.core.model

/**
 * 手机端的“近似人类水平”档位。
 *
 * 说明：等级同时对应 HumanSL 的 rank profile 和普通搜索参数。
 * HumanSL 负责人类着法选择，visits/温度用于分析、pass 判断和兜底搜索。
 * 所有档位都受 gtp.cfg 的 maxTime 限制。
 */
enum class AiLevel(
    val displayName: String,
    val visits: Int,
    val analysisVisits: Int,
    val playStyle: PlayStyle,
    val humanProfile: String
) {
    TEN_K("10K", visits = 5, analysisVisits = 60, playStyle = PlayStyle.VARIED, humanProfile = "rank_10k"),
    FIVE_K("5K", visits = 8, analysisVisits = 80, playStyle = PlayStyle.VARIED, humanProfile = "rank_5k"),
    ONE_K("1K", visits = 10, analysisVisits = 120, playStyle = PlayStyle.NATURAL, humanProfile = "rank_1k"),
    ONE_DAN("1段", visits = 15, analysisVisits = 240, playStyle = PlayStyle.NATURAL, humanProfile = "rank_1d");

    companion object {
        val DEFAULT = FIVE_K
    }
}
