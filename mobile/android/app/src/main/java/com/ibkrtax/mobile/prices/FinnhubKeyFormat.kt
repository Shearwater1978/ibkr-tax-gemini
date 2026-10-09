package com.ibkrtax.mobile.prices

/**
 * Recognises text that could be a Finnhub API key (a single alphanumeric token), so
 * the app can offer a just-copied key. It never decides validity: the provider does.
 */
object FinnhubKeyFormat {
    private val TOKEN = Regex("^[A-Za-z0-9]{16,64}$")

    fun candidate(clipboardText: String?): String? = clipboardText?.trim()?.takeIf(TOKEN::matches)
}
