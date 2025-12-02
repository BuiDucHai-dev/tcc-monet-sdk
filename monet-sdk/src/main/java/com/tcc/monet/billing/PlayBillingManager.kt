package com.tcc.monet.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.android.billingclient.api.querySkuDetails
import com.tcc.monet.billing.mappers.toCommonProduct
import com.tcc.monet.billing.model.CommonProduct
import com.tcc.monet.preference.PreferenceUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.collections.firstOrNull
import kotlin.collections.orEmpty
import kotlin.coroutines.resume

object PlayBillingManager {

    private lateinit var billingClient: BillingClient

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var onPurchaseUpdated: () -> Unit = {}
    var onUserCancel: (Int) -> Unit = {}

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchaseList ->
        if (billingResult.responseCode == BillingResponseCode.OK) {
            purchaseList?.forEach {
                recheckPurchase(it)
            }
        } else if (billingResult.responseCode == BillingResponseCode.USER_CANCELED ||
            billingResult.responseCode == BillingResponseCode.ERROR ||
            billingResult.responseCode == BillingResponseCode.NETWORK_ERROR ||
            billingResult.responseCode == BillingResponseCode.DEVELOPER_ERROR ) {
            onUserCancel(billingResult.responseCode)
        }
    }

    fun init(context: Context, onComplete: () -> Unit = {}) {
        billingClient = BillingClient
            .newBuilder(context)
            .enablePendingPurchases(
                PendingPurchasesParams
                    .newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .setListener(purchasesUpdatedListener)
            .build()
        scope.launch {
            isBillingReady()
            PreferenceUtil.isPurchased = fetchPurchase().isNotEmpty()
            onComplete()
        }
    }

    private suspend fun isBillingReady(): Boolean = suspendCancellableCoroutine { continuation ->
        if (billingClient.isReady) {
            if (continuation.isActive) continuation.resume(true)
        } else {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingServiceDisconnected() {}

                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (continuation.isActive) {
                        continuation.resume(billingResult.responseCode == BillingResponseCode.OK)
                    }
                }
            })
        }
    }

    suspend fun getCommonProductList(ids: List<String>): List<CommonProduct?> {
        if (!isBillingReady()) return emptyList()
        return coroutineScope {
            val subsDeferred = async { getCommonProductListByType(ids, BillingClient.ProductType.SUBS) }
            val inAppDeferred = async { getCommonProductListByType(ids, BillingClient.ProductType.INAPP) }
            val allProduct = subsDeferred.await() + inAppDeferred.await()
            ids.map { id ->
                allProduct.find { it.productId == id }
            }
        }
    }

    suspend fun fetchPurchase(): List<Purchase> {
        if (!isBillingReady()) return emptyList()
        return coroutineScope {
            val subsDeferred = async { queryPurchases(BillingClient.ProductType.SUBS) }
            val inAppDeferred = async { queryPurchases(BillingClient.ProductType.INAPP) }
            subsDeferred.await() + inAppDeferred.await()
        }
    }

    suspend fun handlePurchase(
        activity: Activity,
        productId: String,
        withOfferIfHave: Boolean = true
    ) {
        if (!isBillingReady()) return
        scope.launch {
            val subsDeferred = async { getCommonProductListByType(listOf(productId), BillingClient.ProductType.SUBS) }
            val inAppDeferred = async { getCommonProductListByType(listOf(productId), BillingClient.ProductType.INAPP) }
            (subsDeferred.await() + inAppDeferred.await()).find { it.productId == productId }?.let {
                if (isProductDetailsSupported()) {
                    launchPurchaseWithProductDetails(activity, it, withOfferIfHave)
                } else {
                    launchPurchaseWithSkuDetails(activity, it)
                }
            }
        }
    }

    private fun recheckPurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val param = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(param) { result ->
                if (result.responseCode == BillingResponseCode.OK) {
                    onPurchaseUpdated()
                }
            }
        }
    }

    private fun isProductDetailsSupported(): Boolean {
        return billingClient.isFeatureSupported(BillingClient.FeatureType.PRODUCT_DETAILS).responseCode == BillingResponseCode.OK
    }

    private suspend fun queryPurchases(type: String): List<Purchase> {
        return if (isProductDetailsSupported()) {
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(type).build()
            ).purchasesList
        } else {
            billingClient.queryPurchasesAsync(type).purchasesList
        }
    }

    private suspend fun getCommonProductListByType(ids: List<String>, type: String): List<CommonProduct> {
        return if (isProductDetailsSupported()) {
            getProductListById(ids, type).map { it.toCommonProduct() }
        } else {
            getSkuDetailListById(ids, type).map { it.toCommonProduct() }
        }
    }

    private suspend fun getProductListById(
        idList: List<String>,
        type: String
    ): List<ProductDetails> {
        val productList = idList
            .map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(type)
                    .build()
            }
        val params = QueryProductDetailsParams
            .newBuilder()
            .setProductList(productList)
        return billingClient.queryProductDetails(params.build()).productDetailsList ?: emptyList()
    }

    private suspend fun getSkuDetailListById(
        ids: List<String>,
        productType: String
    ): List<SkuDetails> {
        val params = SkuDetailsParams
            .newBuilder()
            .setSkusList(ids)
            .setType(productType)
            .build()
        return billingClient.querySkuDetails(params).skuDetailsList ?: emptyList()
    }

    private fun launchPurchaseWithProductDetails(
        activity: Activity,
        iapProduct: CommonProduct,
        withOfferIfHave: Boolean
    ) {
        scope.launch {
            val productDetails = getProductListById(
                listOf(iapProduct.productId),
                iapProduct.type
            ).firstOrNull() ?: return@launch
            val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)

            if (productDetails.productType == BillingClient.ProductType.SUBS) {
                val offerDetailsList = productDetails.subscriptionOfferDetails.orEmpty()

                val chosenOffer = if (withOfferIfHave) {
                    offerDetailsList.firstOrNull { it.offerId != null }
                        ?: offerDetailsList.firstOrNull { it.offerId == null }
                } else {
                    offerDetailsList.firstOrNull { it.offerId == null }
                        ?: offerDetailsList.firstOrNull()
                }
                val offerToken = chosenOffer?.offerToken
                    ?: return@launch
                paramsBuilder.setOfferToken(offerToken)
            }
            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(paramsBuilder.build()))
                .build()
            withContext(Dispatchers.Main) {
                val responseCode = billingClient.launchBillingFlow(activity, billingFlowParams).responseCode
                when (responseCode) {
                    BillingResponseCode.OK -> {

                    }
                    else -> {
                        Log.e("HAI", "launchPurchaseWithProductDetails: $responseCode")
                    }
                }
            }
        }
    }

    private fun launchPurchaseWithSkuDetails(
        activity: Activity,
        iapProduct: CommonProduct
    ) {
        scope.launch {
            val skuDetails = getSkuDetailListById(
                listOf(iapProduct.productId),
                iapProduct.type
            ).firstOrNull() ?: return@launch
            val flowParams = BillingFlowParams.newBuilder()
                .setSkuDetails(skuDetails)
                .build()
            withContext(Dispatchers.Main) {
                billingClient.launchBillingFlow(activity, flowParams)
            }
        }
    }
}

