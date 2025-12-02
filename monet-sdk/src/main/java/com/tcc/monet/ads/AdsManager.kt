package com.tcc.monet.ads

import android.util.Log

internal object AdsManager {

    private const val TAG = "AdsManager"

    var lastAdDisplayTime: Long = 0L
    var isFullScreenAdShowing: Boolean = false

    fun isInRelaxTime(): Boolean {
        val isInRelaxTime = System.currentTimeMillis() - lastAdDisplayTime < 5000
        Log.d(TAG, "isInRelaxTime: $isInRelaxTime")
        if (isInRelaxTime) {
            Log.d(TAG, "In relax time, do not show ad")
        }
        if (isFullScreenAdShowing) {
            Log.d(TAG, "FullScreen ad is showing, do not show ad")
        }
        return isInRelaxTime && isFullScreenAdShowing
    }
}