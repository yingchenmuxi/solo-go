package com.sologo.app.monetization

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BillingUiState(
    val connected: Boolean = false,
    val productReady: Boolean = false,
    val priceText: String = "¥18",
    val message: String? = null
)

/**
 * Google Play 一次性内购。只使用应用商店官方支付，不接任何外部收款。
 *
 * 注意：测试时需要使用 Play Console 内测轨道 + 许可测试账号；
 * 本地 debug 构建无法完成真实扣款，这是平台行为。
 */
class BillingRepository(
    context: Context,
    private val onPremiumPurchased: (Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state.asStateFlow()
    private var productDetails: ProductDetails? = null
    private var connecting = false

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            _state.value = _state.value.copy(message = "支付未完成: ${result.debugMessage}")
        }
    }

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    fun start() {
        if (billingClient.isReady) {
            queryPurchases()
            queryProduct()
            return
        }
        if (connecting) return
        connecting = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                _state.value = _state.value.copy(connected = ok)
                if (ok) {
                    queryPurchases()
                    queryProduct()
                } else {
                    _state.value = _state.value.copy(message = "商店连接失败: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
                _state.value = _state.value.copy(connected = false)
            }
        })
    }

    fun launchPurchase(activity: Activity) {
        val details = productDetails
        if (details == null) {
            _state.value = _state.value.copy(message = "商品信息尚未加载，请稍后重试")
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        billingClient.launchBillingFlow(activity, params)
    }

    fun close() {
        connecting = false
        runCatching { billingClient.endConnection() }
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        billingClient.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = details.firstOrNull()
                val price = productDetails?.oneTimePurchaseOfferDetails?.formattedPrice
                _state.value = _state.value.copy(productReady = productDetails != null, priceText = price ?: "¥18")
            } else {
                _state.value = _state.value.copy(message = "读取商品失败: ${result.debugMessage}")
            }
        }
    }

    private fun queryPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val owned = purchases.any { purchase ->
            purchase.products.contains(PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        // 查询成功时以商店当前状态为准，这样退款后重新进入 APP 会撤销本地买断标记。
        onPremiumPurchased(owned)
        if (owned) {
            purchases.filter { it.products.contains(PRODUCT_ID) && !it.isAcknowledged }
                .forEach(::acknowledge)
        }
    }

    private fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            Log.i(TAG, "确认订单: ${result.responseCode} ${result.debugMessage}")
        }
    }

    companion object {
        private const val TAG = "BillingRepository"
        const val PRODUCT_ID = "premium_review_unlock"
    }
}
