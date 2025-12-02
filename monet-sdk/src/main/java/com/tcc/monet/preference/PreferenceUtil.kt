package com.tcc.monet.preference

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object PreferenceUtil {

    private const val PREF = "PreferenceUtil"
    private const val PURCHASED = "purchased"

    private lateinit var preference: SharedPreferences

    fun init(context: Context) {
        preference = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }

    var isPurchased: Boolean
        get() = preference.getBoolean(PURCHASED, false)
        set(value) {
            preference.edit { putBoolean(PURCHASED, value) }
        }

}