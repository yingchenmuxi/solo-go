package com.sologo.app

/**
 * 仅用于开发/真机测试阶段的开关。
 *
 * debug APK 开放全部高级功能，避免测试时被买断/激励视频弹窗打断；
 * release 构建仍走正式的付费与广告解锁逻辑。
 */
object FeatureFlags {
    val unlockAllForTesting: Boolean = BuildConfig.DEBUG
}
