package com.sologo.app.monetization

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AdMob 封装。当前使用 Google 官方测试 ID，发布前替换为你的正式 ID。
 *
 * 只允许在用户同意隐私政策后调用 initialize()。
 * 对局页不展示插屏/激励视频；激励视频由用户在复盘页主动点击。
 */
class AdsManager(private val context: Context) {
    private val _initialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()
    private var rewardedAd: RewardedAd? = null
    private var initializing = false

    val isInitialized: Boolean get() = _initialized.value

    fun initialize() {
        if (_initialized.value || initializing) return
        initializing = true
        MobileAds.initialize(context) {
            initializing = false
            _initialized.value = true
            Log.i(TAG, "AdMob 初始化完成")
            preloadRewarded()
        }
    }

    fun preloadRewarded() {
        if (!_initialized.value) return
        RewardedAd.load(
            context,
            REWARDED_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    Log.w(TAG, "激励视频加载失败: ${error.message}")
                }
            }
        )
    }

    fun showRewarded(
        activity: Activity,
        onReward: () -> Unit,
        onUnavailable: (String) -> Unit
    ) {
        if (!_initialized.value) {
            onUnavailable("请先同意隐私政策，广告功能才会启用")
            return
        }
        val ad = rewardedAd
        if (ad == null) {
            onUnavailable("激励视频暂未准备好，请稍后再试")
            preloadRewarded()
            return
        }
        ad.show(activity) { _ ->
            onReward()
            rewardedAd = null
            preloadRewarded()
        }
    }

    companion object {
        private const val TAG = "AdsManager"

        // Google 官方测试广告位；发布前必须替换。
        const val BANNER_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        const val REWARDED_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }
}
