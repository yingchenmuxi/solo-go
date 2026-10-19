package com.sologo.core.model

/**
 * 对局规则选项。KataGo 的默认规则来自 gtp.cfg；中国规则显式通过 GTP 设置。
 */
enum class GameRules(
    val displayName: String,
    val sgfName: String,
    val gtpName: String?
) {
    CHINESE("中国规则", "Chinese", "chinese"),
    KATAGO_DEFAULT("KataGo 默认", "Chinese", null);

    /**
     * 贴目由规则和让子方式自动决定，不开放给用户调整。
     * 当前随包 gtp.cfg 的默认规则也是中国规则，因此两种规则的分先贴目均为 7.5。
     */
    fun komiFor(handicap: Int): Float = when {
        handicap > 0 -> 0.5f   // 让子棋：白方仅保留 0.5 目，避免和棋
        handicap == -1 -> 0f   // 让先：黑方先走且不贴目
        else -> 7.5f           // 分先
    }

    /** 贴目展示文本，去掉无意义的小数零。 */
    fun formatKomi(handicap: Int): String {
        val value = komiFor(handicap)
        return if (value % 1f == 0f) value.toInt().toString() else value.toString()
    }
}
