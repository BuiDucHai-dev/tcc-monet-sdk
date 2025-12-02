package com.tcc.monet.ads.app_open

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
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

object AppOpenAdsManager {

    private const val TAG = "AppOpenAdsManager"

    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val configMap = mutableMapOf<String, AppOpenAdConfig>()

    private val adCacheMap = mutableMapOf<String, AppOpenAdCache>()

    private val preloadJobMap = mutableMapOf<String, Job>()

    fun loadAppOpenAd(
        context: Context,
        appOpenConfig: AppOpenAdConfig
    ) {
        val key = appOpenConfig.key
        configMap[key] = appOpenConfig
        preloadJobMap[key]?.cancel()
        preloadJobMap[key] = preloadScope.launch {
            loadAppOpenAdSequential(context, appOpenConfig) ?: return@launch
        }
    }

    fun showAppOpenAd(
        activity: Activity,
        key: String,
        onClose: ((Boolean) -> Unit)? = null
    ) {
        val job = preloadJobMap[key] ?: run {
            onClose?.invoke(false)
            return
        }
        val appOpenAdConfig = configMap[key] ?: run {
            onClose?.invoke(false)
            return
        }
        if (AdsManager.isInRelaxTime()) {
            onClose?.invoke(false)
            return
        }
        activity.showAdsOverlay()
        if (job.isCompleted) {
            val appOpenAd = appOpenAdConfig.adUnitIds.firstNotNullOfOrNull { adCacheMap[it]?.appOpenAd } ?: run {
                onClose?.invoke(false)
                return
            }
            appOpenAd.fullScreenContentCallback = object : FullScreenContentCallback() {
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
            appOpenAd.show(activity)
        } else {
            job.invokeOnCompletion {
                CoroutineScope(Dispatchers.Main).launch {
                    showAppOpenAd(activity, key, onClose)
                }
            }
        }
    }

    private suspend fun loadAppOpenAdSequential(
        context: Context,
        appOpenConfig: AppOpenAdConfig
    ) = withContext(Dispatchers.Main) {
        return@withContext withTimeoutOrNull(appOpenConfig.timeout * 1000) {
            for ((_, adId) in appOpenConfig.adUnitIds.withIndex()) {
                val interstitialAd = loadSingleAppOpenAd(context, adId)
                if (interstitialAd != null) {
                    return@withTimeoutOrNull interstitialAd
                }
            }
            return@withTimeoutOrNull null
        }
    }

    private suspend fun loadSingleAppOpenAd(
        context: Context,
        adUnitId: String,
    ): AppOpenAd? = suspendCancellableCoroutine { cont ->
        val adRequest = AdRequest.Builder().build()
        AppOpenAd.load(
            context,
            adUnitId,
            adRequest,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    super.onAdLoaded(ad)
                    Log.d(TAG, "Google Ad loaded successfully")
                    ad.setImmersiveMode(true)
                    adCacheMap[ad.adUnitId] = AppOpenAdCache(ad, false)
                    if (cont.isActive) cont.resume(ad)
                }

                override fun onAdFailedToLoad(p0: LoadAdError) {
                    super.onAdFailedToLoad(p0)
                    Log.d(TAG, "Google Ad failed to load: ${p0.message}")
                    if (cont.isActive) cont.resume(null)
                }
            }
        )
    }
}