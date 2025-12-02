package com.tcc.monet.billing.mappers

import com.tcc.monet.billing.model.BillingCycle

fun getBillingCycle(timeCodeOrigin: String?, timeCodeOffer: String?): BillingCycle {
    val trialDay = when {
        timeCodeOffer?.equals("P3D") == true -> 3
        timeCodeOffer?.equals("P1W") == true -> 7
        else -> 0
    }
    return when {
        timeCodeOrigin?.contains("Y") == true -> BillingCycle.Yearly(trialDay)
        timeCodeOrigin?.contains("M") == true -> BillingCycle.Monthly(trialDay)
        timeCodeOrigin?.contains("W") == true -> BillingCycle.Weekly(trialDay)
        else -> BillingCycle.Lifetime
    }
}