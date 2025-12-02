package com.tcc.monet.ads.combine.splash

data class SplashAdConfig(
    val enable: Boolean,
    val appOpenAdUnitIds: List<String>,
    val interstitialAdUnitIds: List<String>,
    val nativeAdUnitIds: List<String>,
    val priority: Int,
    val timeout: Int
)