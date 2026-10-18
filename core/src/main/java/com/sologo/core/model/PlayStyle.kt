package com.sologo.core.model

/**
 * 轻量级下法变化档位。
 *
 * 这不等于 HumanSL 的“真实人类段位模仿”，只是利用 KataGo 的根节点温度、
 * 对称采样和 LCB 开关，让同一局面不必每次都选择完全相同的着法。
 * 数值刻意保守，避免 60 visits 的手机对局出现明显乱下。
 */
enum class PlayStyle(
    val displayName: String,
    val earlyTemperature: Double,
    val temperature: Double,
    val temperatureHalflife: Double,
    val onlyBelowProb: Double,
    val rootSymmetries: Int,
    val useLcb: Boolean
) {
    STABLE(
        displayName = "稳定",
        earlyTemperature = 0.0,
        temperature = 0.0,
        temperatureHalflife = 19.0,
        onlyBelowProb = 0.0,
        rootSymmetries = 1,
        useLcb = true
    ),
    NATURAL(
        displayName = "自然",
        earlyTemperature = 0.45,
        temperature = 0.12,
        temperatureHalflife = 19.0,
        onlyBelowProb = 0.02,
        rootSymmetries = 2,
        useLcb = false
    ),
    VARIED(
        displayName = "多变",
        earlyTemperature = 0.75,
        temperature = 0.28,
        temperatureHalflife = 80.0,
        onlyBelowProb = 0.01,
        rootSymmetries = 2,
        useLcb = false
    )
}
