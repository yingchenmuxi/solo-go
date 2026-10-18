package com.sologo.core.model

/**
 * 手机端的“近似人类水平”档位。
 *
 * 说明：这里用普通 KataGo 模型的 visits + 着法温度组合来近似水平，
 * 不是 HumanSL 的真实人类棋谱模仿。等级越高，搜索越多、温度越低；
 * 等级越低，搜索更少、着法更随机。所有档位都受 gtp.cfg 的 maxTime 限制。
 */
enum class AiLevel(
    val displayName: String,
    val visits: Int,
    val analysisVisits: Int,
    val playStyle: PlayStyle
) {
    TEN_K("10K", visits = 20, analysisVisits = 60, playStyle = PlayStyle.VARIED),
    FIVE_K("5K", visits = 35, analysisVisits = 80, playStyle = PlayStyle.VARIED),
    ONE_K("1K", visits = 60, analysisVisits = 120, playStyle = PlayStyle.NATURAL),
    ONE_DAN("1段", visits = 120, analysisVisits = 240, playStyle = PlayStyle.NATURAL),
    THREE_DAN("3段", visits = 250, analysisVisits = 400, playStyle = PlayStyle.STABLE);

    companion object {
        val DEFAULT = FIVE_K
    }
}
