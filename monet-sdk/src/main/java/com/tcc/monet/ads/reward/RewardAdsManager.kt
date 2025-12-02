package com.tcc.monet.ads.reward

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
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

object RewardAdsManager {

    private const val TAG = "RewardAdsManager"

    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val configMap = mutableMapOf<String, RewardAdConfig>()

    private val adCacheMap = mutableMapOf<String, RewardAdCache>()

    private val preloadJobMap = mutableMapOf<String, Job>()

    fun loadRewardAd(
        context: Context,
        rewardConfig: RewardAdConfig,
    ) {
        val key = rewardConfig.key
        configMap[key] = rewardConfig
        preloadJobMap[key]?.cancel()
        preloadJobMap[key] = preloadScope.launch {
            loadRewardAdSequential(context, rewardConfig) ?: return@launch
        }
    }

    fun showRewardAd(
        activity: Activity,
        key: String,
        onClose: ((RewardItem?) -> Unit)? = null
    ) {
        var rewardItemReceive: RewardItem? = null
        val job = preloadJobMap[key] ?: run {
            onClose?.invoke(rewardItemReceive)
            return
        }
        val config = configMap[key] ?: run {
            onClose?.invoke(rewardItemReceive)
            return
        }
        if (AdsManager.isInRelaxTime()) {
            onClose?.invoke(rewardItemReceive)
            return
        }
        activity.showAdsOverlay()
        if (job.isCompleted) {
            val rewardAd = config.adUnitIds.firstNotNullOfOrNull { adCacheMap[it]?.interstitialAd } ?: run {
                onClose?.invoke(rewardItemReceive)
                return
            }
            rewardAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdFailedToShowFullScreenContent(p0: AdError) {
                    super.onAdFailedToShowFullScreenContent(p0)
                    Log.d(TAG, "onAdFailedToShowFullScreenContent: ${p0.message}")
                    CoroutineScope(Dispatchers.Main).launch {
                        activity.hideAdsOverlay()
                        onClose?.invoke(rewardItemReceive)
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
                        onClose?.invoke(rewardItemReceive)
                    }
                }
            }
            rewardAd.show(
                activity
            ) { rewardItem ->
                Log.d(TAG, "onEarReward: ")
                rewardItemReceive = rewardItem
            }
        } else {
            job.invokeOnCompletion {
                CoroutineScope(Dispatchers.Main).launch {
                    showRewardAd(activity, key)
                }
            }
        }
    }

    private suspend fun loadRewardAdSequential(
        context: Context,
        rewardConfig: RewardAdConfig
    ) = withContext(Dispatchers.Main) {
        return@withContext withTimeoutOrNull(rewardConfig.timeout * 1000) {
            for ((_, adId) in rewardConfig.adUnitIds.withIndex()) {
                val rewardAd = loadSingleRewardAd(context, adId)
                if (rewardAd != null) {
                    return@withTimeoutOrNull rewardAd
                }
            }
            return@withTimeoutOrNull null
        }
    }

    private suspend fun loadSingleRewardAd(
        context: Context,
        adUnitId: String,
    ) : RewardedAd? = suspendCancellableCoroutine { cont ->

        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    super.onAdLoaded(ad)
                    Log.d(TAG, "Google Ad loaded successfully")
                    ad.setImmersiveMode(true)
                    adCacheMap[ad.adUnitId] = RewardAdCache(ad, false)
                    if (cont.isActive) cont.resume(ad)
                }

                override fun onAdFailedToLoad(e: LoadAdError) {
                    super.onAdFailedToLoad(e)
                    Log.d(TAG, "Google Ad failed to load: ${e.message}")
                    if (cont.isActive) cont.resume(null)
                }
            }
        )
    }
}