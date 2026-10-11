package com.ibkrtax.mobile.ui.common

import android.content.Context

/** Small UI settings that are not sensitive (switch states, the chosen skin); never backed up. */
object UiPreferences {
    private const val FILE = "ui"
    private const val SHOW_SOLD = "show_sold_positions"
    private const val USD_VIEW = "usd_view"
    private const val SKIN = "skin"
    private const val SKIN_NOTICE = "skin_notice_pending"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun usdView(context: Context): Boolean = prefs(context).getBoolean(USD_VIEW, false)

    fun setUsdView(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(USD_VIEW, value).apply()
    }

    fun showSold(context: Context): Boolean = prefs(context).getBoolean(SHOW_SOLD, false)

    fun setShowSold(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(SHOW_SOLD, value).apply()
    }

    /** The stored skin name, or null before a skin was ever chosen (mobile-skins spec). */
    fun skin(context: Context): String? = prefs(context).getString(SKIN, null)

    fun setSkin(context: Context, name: String) {
        prefs(context).edit().putString(SKIN, name).apply()
    }

    /** True after an update to the first version with skins, until the user saw the notice. */
    fun skinNoticePending(context: Context): Boolean = prefs(context).getBoolean(SKIN_NOTICE, false)

    fun setSkinNoticePending(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(SKIN_NOTICE, value).apply()
    }
}
