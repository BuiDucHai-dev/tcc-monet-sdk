package com.tcc.monet.ads.combine.splash

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object SplashAdManager {

    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val config: SplashAdConfig? = null
}