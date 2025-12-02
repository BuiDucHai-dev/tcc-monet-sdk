package com.tcc.monet.billing.model

sealed interface BillingCycle {
    data object Lifetime: BillingCycle
    data class Yearly(val trialDay: Int = 0): BillingCycle
    data class Monthly(val trialDay: Int = 0): BillingCycle
    data class Weekly(val trialDay: Int = 0): BillingCycle
}