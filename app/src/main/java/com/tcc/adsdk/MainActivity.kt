package com.tcc.adsdk

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
//import com.tcc.ads.banner.BannerAdsManager
//import com.tcc.ads.native_ad.NativeAdsManager
import com.tcc.adsdk.databinding.ActivityMainBinding

class MainActivity: AppCompatActivity() {

    val binding: ActivityMainBinding by lazy {
        DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main).apply {
            lifecycleOwner = this@MainActivity
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        loadAd()
//        BannerAdsManager.showBanner(this, "Main", binding.banner)
//        NativeAdsManager.showNativeAd(this, "Main", binding.nativeAdContainer)
    }
}