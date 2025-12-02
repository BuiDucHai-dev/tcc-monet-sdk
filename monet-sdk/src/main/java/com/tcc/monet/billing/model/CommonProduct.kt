package com.tcc.monet.billing.model

data class CommonProduct(
    val productId: String,
    val type: String,
    val priceOrigin: String?,
    val priceOriginMicros: Long?,
    val priceOffer: String?,
    val priceOfferMicros: Long?,
    val isTrialProduct: Boolean,
    val isDiscountProduct: Boolean,
    val billingCycle: BillingCycle
)
