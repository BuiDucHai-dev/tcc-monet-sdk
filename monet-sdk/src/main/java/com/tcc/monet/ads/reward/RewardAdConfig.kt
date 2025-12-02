package com.tcc.monet.ads.reward

data class RewardAdConfig(
    val key: String,
    val adUnitIds: List<String>,
    val enable: Boolean,
    val timeout: Long
)