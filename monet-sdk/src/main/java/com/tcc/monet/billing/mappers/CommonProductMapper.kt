package com.tcc.monet.billing.mappers

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.SkuDetails
import com.tcc.monet.billing.model.CommonProduct

fun ProductDetails.toCommonProduct(): CommonProduct {
    val a1 = if (productType == BillingClient.ProductType.INAPP) {
        oneTimePurchaseOfferDetails?.formattedPrice
    } else {
        subscriptionOfferDetails?.find { offerDetails -> offerDetails.offerId == null }?.pricingPhases?.pricingPhaseList?.first()?.formattedPrice
    }

    val a2 = if (productType == BillingClient.ProductType.INAPP) {
        oneTimePurchaseOfferDetails?.priceAmountMicros
    } else {
        subscriptionOfferDetails?.find { offerDetails -> offerDetails.offerId == null }?.pricingPhases?.pricingPhaseList?.first()?.priceAmountMicros
    }

    val b1 = if (productType == BillingClient.ProductType.INAPP) {
        null
    } else {
        subscriptionOfferDetails?.find { offerDetails -> offerDetails.offerId != null }?.pricingPhases?.pricingPhaseList?.first()?.formattedPrice
    }

    val b2 = if (productType == BillingClient.ProductType.INAPP) {
        null
    } else {
        subscriptionOfferDetails?.find { offerDetails -> offerDetails.offerId != null }?.pricingPhases?.pricingPhaseList?.first()?.priceAmountMicros
    }

    val isTrial = if (productType == BillingClient.ProductType.INAPP) {
        false
    } else {
        subscriptionOfferDetails?.any { offerDetails -> offerDetails.offerId != null && offerDetails.pricingPhases.pricingPhaseList.first().priceAmountMicros == 0L } ?: false
    }
    val isDiscount = if (productType == BillingClient.ProductType.INAPP) {
        false
    } else {
        subscriptionOfferDetails?.any { offerDetails -> offerDetails.offerId != null && offerDetails.pricingPhases.pricingPhaseList.first().priceAmountMicros != 0L } ?: false
    }

    val timeCodeOrigin = if (productType == BillingClient.ProductType.INAPP) {
        null
    } else {
        subscriptionOfferDetails?.find { offerDetails -> offerDetails.offerId == null }?.pricingPhases?.pricingPhaseList?.first()?.billingPeriod
    }
    val timeCodeOffer = if (productType == BillingClient.ProductType.INAPP) {
        null
    } else {
        subscriptionOfferDetails?.find { offerDetails -> offerDetails.offerId != null }?.pricingPhases?.pricingPhaseList?.first()?.billingPeriod
    }

    return CommonProduct(
        productId = productId,
        type = productType,
        priceOrigin = a1,
        priceOriginMicros = a2,
        priceOffer = b1,
        priceOfferMicros = b2,
        isTrialProduct = isTrial,
        isDiscountProduct = isDiscount,
        billingCycle = getBillingCycle(timeCodeOrigin, timeCodeOffer)
    )
}

fun SkuDetails.toCommonProduct(): CommonProduct {
    val a1 = if (type == BillingClient.SkuType.INAPP) {
        null
    } else {
        introductoryPrice
    }
    val a2 = if (type == BillingClient.SkuType.INAPP) {
        null
    } else {
        introductoryPriceAmountMicros
    }

    val timeCodeOrigin = if (type == BillingClient.SkuType.INAPP) {
        null
    } else {
        subscriptionPeriod
    }
    val timeCodeOffer = if (type == BillingClient.SkuType.INAPP) {
        null
    } else {
        introductoryPricePeriod
    }
    return CommonProduct(
        productId = sku,
        type = type,
        priceOrigin = price,
        priceOriginMicros = priceAmountMicros,
        priceOffer = a1,
        priceOfferMicros = a2,
        isTrialProduct = freeTrialPeriod.isNotEmpty(),
        isDiscountProduct = false,
        billingCycle = getBillingCycle(timeCodeOrigin, timeCodeOffer)
    )
}