package com.tcc.monet.ads.native_ad

import com.google.android.gms.ads.nativead.NativeAd

internal data class NativeAdCache(
    val nativeAd: NativeAd,
    val isUse: Boolean
)
