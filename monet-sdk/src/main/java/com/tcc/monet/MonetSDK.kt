package com.tcc.monet

import android.content.Context
import com.tcc.monet.billing.PlayBillingManager
import com.tcc.monet.ump.GoogleConsent

object MonetSDK {

    fun init(context: Context) {
        GoogleConsent.initialize(context.applicationContext)
        PlayBillingManager.init(context.applicationContext)
    }
}