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
 * 目数按整点统计：置信度达到阈值的交叉点整点计入一方，其余点视为未定。
 * 阈值与棋盘势力色块使用同一个值，避免“数字有小数、色块却是整点”的歧义。
 */
data class TerritorySummary(
    val blackArea: Int,
    val whiteArea: Int,
    val neutralPoints: Int,
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
            komi: Float,
            threshold: Float = DEFAULT_THRESHOLD
        ): TerritorySummary? {
            val expectedSize = boardSize * boardSize
            if (boardSize !in 2..19 || ownership.size < expectedSize) return null

            var black = 0
            var white = 0
            var neutral = 0
            for (index in 0 until expectedSize) {
                val value = ownership[index]
                when {
                    !value.isFinite() -> neutral++
                    value >= threshold -> black++
                    value <= -threshold -> white++
                    else -> neutral++
                }
            }
            return TerritorySummary(
                blackArea = black,
                whiteArea = white,
                neutralPoints = neutral,
                komi = komi,
                lead = black.toDouble() - white.toDouble() - komi
            )
        }

        const val DEFAULT_THRESHOLD = 0.08f
    }
}
