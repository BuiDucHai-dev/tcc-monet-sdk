package com.tcc.monet.ads.test

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import java.security.MessageDigest

object GenerateTestDevice {

    @SuppressLint("HardwareIds")
    fun generateDeviceId(context: Context) {
//        if (!BuildConfig.DEBUG) return
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val googleDeviceId = md5(androidId).uppercase()
        val testId = mutableListOf(googleDeviceId)
        val configuration = RequestConfiguration.Builder()
            .setTestDeviceIds(testId)
            .build()
        MobileAds.setRequestConfiguration(configuration)
    }

    private fun md5(androidId: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val array = md.digest(androidId.toByteArray())
            val builder = StringBuilder()
            val var6 = array.size
            for (var7 in 0 until var6) {
                val b = array[var7]
                builder.append(Integer.toHexString(b.toInt() and 255 or 256).substring(1, 3))
            }
            return builder.toString()
        } catch (e: Exception) {
            ""
        }
    }
}