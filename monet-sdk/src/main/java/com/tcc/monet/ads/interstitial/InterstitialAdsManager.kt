package com.tcc.monet.ads.interstitial

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.tcc.monet.ads.AdsManager
import com.tcc.monet.utils.hideAdsOverlay
import com.tcc.monet.utils.showAdsOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object InterstitialAdsManager {

    private const val TAG = "InterstitialAdsManager"

    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val configMap = mutableMapOf<String, InterstitialAdConfig>()

    private val adCacheMap = mutableMapOf<String, InterstitialAdCache>()

    private val preloadJobMap = mutableMapOf<String, Job>()

    fun loadInterstitial(
        context: Context,
        interstitialConfig: InterstitialAdConfig,
    ) {
        val key = interstitialConfig.key
        configMap[key] = interstitialConfig
        preloadJobMap[key]?.cancel()
        preloadJobMap[key] = preloadScope.launch {
            loadInterstitialAdSequential(context, interstitialConfig) ?: return@launch
        }
    }

    fun showInterstitial(
        activity: Activity,
        key: String,
        onClose: ((Boolean) -> Unit)? = null
    ) {
        val job = preloadJobMap[key] ?: run {
            onClose?.invoke(false)
            return
        }
        val interstitialConfig = configMap[key] ?: run {
            onClose?.invoke(false)
            return
        }
        if (AdsManager.isInRelaxTime()) {
            onClose?.invoke(false)
            return
        }
        activity.showAdsOverlay()
        if (job.isCompleted) {
            val interstitialAd = interstitialConfig.adUnitIds.firstNotNullOfOrNull { adCacheMap[it]?.interstitialAd } ?: run {
                onClose?.invoke(false)
                return
            }
            interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    super.onAdFailedToShowFullScreenContent(p0)
                    Log.d(TAG, "onAdFailedToShowFullScreenContent: ${p0.message}")
                    CoroutineScope(Dispatchers.Main).launch {
                        activity.hideAdsOverlay()
                        onClose?.invoke(false)
                    }
                }

                override fun onAdShowedFullScreenContent() {
                    super.onAdShowedFullScreenContent()
                    Log.d(TAG, "onAdShowedFullScreenContent: ")
                    AdsManager.lastAdDisplayTime = System.currentTimeMillis()
                    AdsManager.isFullScreenAdShowing = true
                }

                override fun onAdDismissedFullScreenContent() {
                    super.onAdDismissedFullScreenContent()
                    Log.d(TAG, "onAdDismissedFullScreenContent: ")
                    CoroutineScope(Dispatchers.Main).launch {
                        AdsManager.isFullScreenAdShowing = false
                        activity.hideAdsOverlay()
                        onClose?.invoke(true)
                    }
                }
            }
            interstitialAd.show(activity)
        } else {
            job.invokeOnCompletion {
                CoroutineScope(Dispatchers.Main).launch {
                    showInterstitial(activity, key, onClose)
                }
            }
        }
    }

    private suspend fun loadInterstitialAdSequential(
        context: Context,
        bannerConfig: InterstitialAdConfig
    ) = withContext(Dispatchers.Main) {
        return@withContext withTimeoutOrNull(bannerConfig.timeout * 1000) {
            for ((_, adId) in bannerConfig.adUnitIds.withIndex()) {
                val interstitialAd = loadSingleInterstitialAd(context, adId)
                if (interstitialAd != null) {
                    return@withTimeoutOrNull interstitialAd
                }
            }
            return@withTimeoutOrNull null
        }
    }

    private suspend fun loadSingleInterstitialAd(
        context: Context,
        adUnitId: String,
    ) : InterstitialAd? = suspendCancellableCoroutine { cont ->
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Google Ad loaded successfully")
                    ad.setImmersiveMode(true)
                    adCacheMap[ad.adUnitId] = InterstitialAdCache(ad, false)
                    if (cont.isActive) cont.resume(ad)
                }

                override fun onAdFailedToLoad(e: LoadAdError) {
                    Log.d(TAG, "Google Ad failed to load: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
            }
        )
    }
}