package com.uiptv.mobile.android

import android.app.Activity
import android.content.Context
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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.uiptv.mobile.shared.logging.AndroidAppLog
import com.uiptv.mobile.shared.i18n.MobileMessages
import com.uiptv.mobile.shared.settings.AndroidPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class ProBillingState(
    val isPro: Boolean = false,
    val priceLabel: String = "",
    val billingAvailable: Boolean = true
)

class AndroidProBillingManager(
    context: Context,
    private val preferences: AndroidPreferencesRepository
) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ProBillingState())
    val state: StateFlow<ProBillingState> = _state.asStateFlow()

    private var productDetails: ProductDetails? = null
    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun start() {
        scope.launch {
            val savedPro = preferences.load().proUnlocked
            _state.value = _state.value.copy(isPro = savedPro)
            if (ensureConnected()) {
                queryProductDetailsInternal()
                syncPurchasesInternal()
            }
        }
    }

    fun destroy() {
        runCatching { billingClient.endConnection() }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch {
                handlePurchases(purchases)
            }
        }
    }

    suspend fun purchasePro(activity: Activity): String = withContext(Dispatchers.IO) {
        if (!ensureConnected()) {
            return@withContext MobileMessages.text("mobileProBillingUnavailable")
        }
        val details = productDetails ?: queryProductDetailsInternal()
        if (details == null) {
            return@withContext MobileMessages.text("mobileProNotAvailable")
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
        val launchResult = withContext(Dispatchers.Main.immediate) {
            billingClient.launchBillingFlow(activity, params)
        }
        if (launchResult.responseCode == BillingClient.BillingResponseCode.OK) {
            MobileMessages.text("mobileProOpeningPurchase")
        } else {
            MobileMessages.text("mobileProPurchaseFailed")
        }
    }

    suspend fun restorePurchases(): String = withContext(Dispatchers.IO) {
        if (!ensureConnected()) {
            return@withContext MobileMessages.text("mobileProBillingUnavailable")
        }
        val unlocked = syncPurchasesInternal()
        if (unlocked) {
            MobileMessages.text("mobileProRestored")
        } else {
            MobileMessages.text("mobileProNotFound")
        }
    }

    private suspend fun ensureConnected(): Boolean {
        if (billingClient.isReady) return true
        return suspendCancellableCoroutine { continuation ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                    _state.value = _state.value.copy(billingAvailable = ok)
                    if (!ok) {
                        AndroidAppLog.w("Billing", "Setup failed: ${result.debugMessage}")
                    }
                    if (continuation.isActive) continuation.resume(ok)
                }

                override fun onBillingServiceDisconnected() {
                    if (continuation.isActive) continuation.resume(false)
                }
            })
        }
    }

    private suspend fun queryProductDetailsInternal(): ProductDetails? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(BuildConfig.PRO_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        val result = billingClient.queryProductDetails(params)
        val details = result.productDetailsList?.firstOrNull()
        if (details != null) {
            productDetails = details
            val price = details.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty()
            _state.value = _state.value.copy(priceLabel = price)
        }
        return details
    }

    private suspend fun syncPurchasesInternal(): Boolean {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = billingClient.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return _state.value.isPro
        }
        return handlePurchases(result.purchasesList)
    }

    private suspend fun handlePurchases(purchases: List<Purchase>): Boolean {
        val proPurchase = purchases.firstOrNull { purchase ->
            purchase.products.contains(BuildConfig.PRO_PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        if (proPurchase != null && !proPurchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(proPurchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(ackParams)
        }
        val unlocked = proPurchase != null
        preferences.saveProUnlocked(unlocked)
        _state.value = _state.value.copy(isPro = unlocked)
        return unlocked
    }
}
