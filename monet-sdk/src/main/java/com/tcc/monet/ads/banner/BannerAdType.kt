package com.tcc.monet.ads.banner

sealed interface BannerAdType {
    data object Adaptive: BannerAdType
    data object Inline: BannerAdType
    data class Collapsible(val isBottom: Boolean = true): BannerAdType
}