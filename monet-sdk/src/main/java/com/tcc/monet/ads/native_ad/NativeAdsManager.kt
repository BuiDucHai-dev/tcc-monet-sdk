package com.tcc.monet.ads.native_ad

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.tcc.monet.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object NativeAdsManager {

    private const val TAG = "NativeAdsManager"

    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val configMap = mutableMapOf<String, NativeAdConfig>()

    private val adsCacheMap = mutableMapOf<String, NativeAdCache>()

    private val preloadJobs = mutableMapOf<String, Job>()

    fun loadNative(
        context: Context,
        nativeConfig: NativeAdConfig
    ) {
        val key = nativeConfig.key
        configMap[key] = nativeConfig
        preloadJobs[key]?.cancel()
        preloadJobs[key] = preloadScope.launch {
            val nativeAd = loadInterstitialSequential(context, nativeConfig) ?: return@launch
            adsCacheMap[key] = NativeAdCache(nativeAd, false)
        }
    }

    fun showNativeAd(
        context: Context,
        key: String,
        container: ViewGroup
    ) {
        val job = preloadJobs[key] ?: return
        val bannerConfig = configMap[key] ?: return
        if (job.isCompleted) {
            container.post {
                val nativeAd = adsCacheMap[key]?.nativeAd ?: return@post
                val inflater = container.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                val adView = inflater.inflate(R.layout.ad_unified_320_176, null) as NativeAdView
                val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
                headlineView.text = nativeAd.headline
                adView.headlineView = headlineView

                // Repeat the process for the other assets in the NativeAd using
                // additional view objects (Buttons, ImageViews, etc).

                val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
                adView.mediaView = mediaView

                // Call the NativeAdView's setNativeAd method to register the
                // NativeAdObject.
                adView.setNativeAd(nativeAd)

                // Ensure that the parent view doesn't already contain an ad view.
                container.removeAllViews()

                // Place the AdView into the parent.
                container.addView(adView)
            }
        } else {
            job.invokeOnCompletion {
                    showNativeAd(context, key, container)
            }
        }
    }

    private suspend fun loadInterstitialSequential(
        context: Context,
        nativeConfig: NativeAdConfig
    ) = withContext(Dispatchers.Main) {
        return@withContext withTimeoutOrNull(nativeConfig.timeout * 1000) {
            for ((_, adId) in nativeConfig.adUnitIds.withIndex()) {
                val interstitialAd = loadSingleNative(context, adId)
                if (interstitialAd != null) {
                    return@withTimeoutOrNull interstitialAd
                }
            }
            return@withTimeoutOrNull null
        }
    }

    private suspend fun loadSingleNative(
        context: Context,
        adUnitId: String
    ): NativeAd? = suspendCancellableCoroutine { cont ->
        val builder = AdLoader.Builder(context, adUnitId)
        val adLoader = builder
            .forNativeAd { nativeAd ->
                if (cont.isActive) cont.resume(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    Log.d(TAG, "onAdLoaded: ")
                }

                override fun onAdFailedToLoad(p0: LoadAdError) {
                    super.onAdFailedToLoad(p0)
                    Log.d(TAG, "onAdFailedToLoad: ")
                    if (cont.isActive) cont.resume(null)
                }
            })
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
    }
}