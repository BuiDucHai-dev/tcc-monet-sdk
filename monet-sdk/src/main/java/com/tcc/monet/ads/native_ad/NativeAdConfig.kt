package com.tcc.monet.ads.native_ad

data class NativeAdConfig(
    val key: String,
    val adUnitIds: List<String>,
    val enable: Boolean,
    val timeout: Long
)
