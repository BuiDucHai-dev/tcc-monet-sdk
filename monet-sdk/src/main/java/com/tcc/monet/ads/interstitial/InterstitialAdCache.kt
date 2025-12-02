package com.tcc.monet.ads.interstitial

import com.google.android.gms.ads.interstitial.InterstitialAd

internal data class InterstitialAdCache(
    val interstitialAd: InterstitialAd,
    val isUse: Boolean
)
