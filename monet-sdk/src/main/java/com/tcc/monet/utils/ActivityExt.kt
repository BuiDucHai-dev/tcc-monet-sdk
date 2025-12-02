package com.tcc.monet.utils

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.children

private const val ADS_OVERLAY_TAG = "interstitial_ads_overlay_tag"

fun Activity.showAdsOverlay(
    message: String = "Đang tải quảng cáo...",
    backgroundAlpha: Float = 0.7f,
    showProgressBar: Boolean = true
) {
    val rootView = window.decorView as ViewGroup

    if (rootView.findViewWithTag<View>(ADS_OVERLAY_TAG) != null) return

    val overlayView = FrameLayout(this).apply {
        tag = ADS_OVERLAY_TAG
        setBackgroundColor(Color.argb((255 * backgroundAlpha).toInt(), 0, 0, 0)) // Đen mờ
        isClickable = true
        isFocusable = true
    }

    val centerContainer = FrameLayout(this).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    if (showProgressBar) {
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleLarge).apply {
            indeterminateTintList = androidx.core.content.ContextCompat.getColorStateList(this@showAdsOverlay, android.R.color.white)
            layoutParams = FrameLayout.LayoutParams(80, 80).apply {
                gravity = Gravity.CENTER
            }
        }
        centerContainer.addView(progressBar)
    }

    if (message.isNotEmpty()) {
        val textView = TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(32, if (showProgressBar) 24 else 0, 32, 0)
            if (showProgressBar) {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 100
                }
            }
        }
        centerContainer.addView(textView)
    }

    overlayView.addView(centerContainer)
    rootView.addView(overlayView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
}

fun Activity.hideAdsOverlay() {
    val rootView = window.decorView as ViewGroup
    rootView.children.forEach { child ->
        if (child.tag == ADS_OVERLAY_TAG) {
            rootView.removeView(child)
        }
    }
}