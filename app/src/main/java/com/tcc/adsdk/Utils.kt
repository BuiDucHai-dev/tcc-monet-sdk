package com.tcc.adsdk

import android.os.Handler
import android.os.Looper

fun runDelay(delay: Long, block: () -> Unit) {
    Handler(Looper.getMainLooper()).postDelayed({
        block()
    }, delay)
}
