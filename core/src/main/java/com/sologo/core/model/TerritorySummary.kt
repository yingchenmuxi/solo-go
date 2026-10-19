package com.sologo.core.model

/**
 * 根据 KataGo 的 ownership 预测计算双方势力面积。
 * 传入的 ownership 已经由引擎统一成黑方视角。
 *
 * ownership 的每个值为 -1..1：
 * - 正数表示该交叉点预计归黑方；
 * - 负数表示预计归白方；
 * - 绝对值越大，归属越确定。
 *
 * 这里按置信度加权求和，而不是简单按正负计数，边界上的模糊点会按比例
 * 计入双方，避免 0.01 和 0.99 被当成同样的一目。当前应用使用中国规则，
 * 因此黑白面积包含棋子和预计领地。
 */
data class TerritorySummary(
    val blackArea: Double,
    val whiteArea: Double,
    val komi: Float,
    val lead: Double
) {
    /** 差距是否已经小到可以视为持平。 */
    val isEven: Boolean get() = kotlin.math.abs(lead) < 0.05

    companion object {
        /**
         * @param ownership KataGo 原始顺序的 ownership，长度至少为 boardSize²。
         * @param boardSize 棋盘路数。
         * @param komi 当前对局贴目，从黑方视角计算差距时需要扣除。
         */
        fun fromOwnership(
            ownership: List<Float>,
            boardSize: Int,
            komi: Float
        ): TerritorySummary? {
            val expectedSize = boardSize * boardSize
            if (boardSize !in 2..19 || ownership.size < expectedSize) return null

            var black = 0.0
            var white = 0.0
            for (index in 0 until expectedSize) {
                val value = ownership[index]
                if (!value.isFinite()) continue
                val clamped = value.coerceIn(-1f, 1f)
                if (clamped > 0f) {
                    black += clamped
                } else if (clamped < 0f) {
                    white -= clamped
                }
            }
            return TerritorySummary(
                blackArea = black,
                whiteArea = white,
                komi = komi,
                lead = black - white - komi
            )
        }
    }
}
