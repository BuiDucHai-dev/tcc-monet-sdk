package com.tcc.monet.ads.reward

import com.google.android.gms.ads.rewarded.RewardedAd

internal data class RewardAdCache(
    val interstitialAd: RewardedAd,
    val isUse: Boolean
)
