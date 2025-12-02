package com.tcc.monet.ads.banner

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.util.Log
import android.widget.FrameLayout
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object BannerAdsManager {

    private const val TAG = "BannerAdsManager"

    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val configMap = mutableMapOf<String, BannerAdConfig>()

    private val adCacheMap = mutableMapOf<String, BannerAdCache>()

    private val preloadJobMap = mutableMapOf<String, Job>()

    fun loadBanner(
        context: Context,
        bannerConfig: BannerAdConfig,
    ) {
        val key = bannerConfig.key
        configMap[key] = bannerConfig
        preloadJobMap[key]?.cancel()
        preloadJobMap[key] = preloadScope.launch {
            val adView = loadBannerSequential(context, bannerConfig) ?: return@launch
            val tempAd = adCacheMap[adView.adUnitId]?.adView
            adCacheMap[adView.adUnitId] = BannerAdCache(adView, false)
            withContext(Dispatchers.Main) {
                tempAd?.destroy()
            }
        }
    }

    fun showBanner(
        context: Context,
        key: String,
        container: ViewGroup
    ) {
        val job = preloadJobMap[key] ?: return
        val bannerConfig = configMap[key] ?: return
        container.post {
            if (job.isCompleted) {
                checkAndBindAd(bannerConfig, container)
                startAutoReload(key, bannerConfig, context)
            } else {
                checkAndBindAd(bannerConfig, container)
            }
            job.invokeOnCompletion {
                showBanner(context, key, container)
            }
        }
    }

    private suspend fun loadBannerSequential(
        context: Context,
        bannerConfig: BannerAdConfig
    ): AdView? = withContext(Dispatchers.Main) {

        return@withContext withTimeoutOrNull(bannerConfig.timeout * 1000) {
            for ((_, adId) in bannerConfig.adUnitIds.withIndex()) {
                val adView = loadSingleBanner(context, adId, bannerConfig.bannerType)
                if (adView != null) {
                    return@withTimeoutOrNull adView
                }
            }
            return@withTimeoutOrNull null
        }
    }

    private suspend fun loadSingleBanner(
        context: Context,
        adId: String,
        bannerType: BannerAdType
    ) : AdView? = suspendCancellableCoroutine { cont ->
        val cacheAd = adCacheMap[adId]
        if (cacheAd?.adView != null && !cacheAd.isUse) {
            if (cont.isActive) cont.resume(cacheAd.adView)
            return@suspendCancellableCoroutine
        }

        val adView = AdView(context)
        adView.adUnitId = adId

        val adRequestBuilder = AdRequest.Builder()

        when(bannerType) {
            BannerAdType.Adaptive -> adView.setAdSize(AdSize.BANNER)
            is BannerAdType.Collapsible -> {
                adView.setAdSize(AdSize.BANNER)
                adRequestBuilder.addNetworkExtrasBundle(
                    AdMobAdapter::class.java,
                    Bundle().apply {
                        putString("collapsible", if (bannerType.isBottom) "bottom" else "top")
                    }
                )
            }
            BannerAdType.Inline -> adView.setAdSize(AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(context, 320))
        }

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                Log.d(TAG, "onAdLoaded: ")
                if (cont.isActive) cont.resume(adView)
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.d(TAG, "onAdFailedToLoad: ${error.message}")
                if (cont.isActive) cont.resume(null)
            }
        }
        adView.loadAd(adRequestBuilder.build())
    }

    private fun checkAndBindAd(
        bannerConfig: BannerAdConfig,
        container: ViewGroup
    ) {
        val adView = bannerConfig.adUnitIds.firstNotNullOfOrNull { adCacheMap[it]?.adView } ?: run {
            container.removeAllViews()
            return
        }
        (adView.parent as? ViewGroup)?.removeView(adView)

        container.removeAllViews()
        container.addView(
            adView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        adCacheMap[adView.adUnitId] = BannerAdCache(adView, true)
    }

    private fun startAutoReload(
        key: String,
        bannerConfig: BannerAdConfig,
        context: Context
    ) {
        preloadJobMap[key]?.cancel()

        val interval = bannerConfig.refreshIntervalSec
        if (interval <= 0) return

        preloadJobMap[key] = preloadScope.launch {
            delay(interval * 1000)
            loadBanner(
                context = context,
                bannerConfig = bannerConfig
            )
        }
    }
}