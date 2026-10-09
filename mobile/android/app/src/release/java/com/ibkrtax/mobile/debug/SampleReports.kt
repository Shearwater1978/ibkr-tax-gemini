package com.ibkrtax.mobile.debug

import android.content.Context
import com.ibkrtax.mobile.fx.FxRates
import com.ibkrtax.mobile.prices.Quote

/** Release stub: sample data is not packaged and the debug import action is hidden. */
object SampleReports {
    const val AVAILABLE = false

    val names: List<String> = emptyList()

    fun read(context: Context, name: String): ByteArray =
        throw UnsupportedOperationException("Sample reports exist only in debug builds")

    fun samplePrices(): List<Quote> = emptyList()

    fun sampleRates(): FxRates? = null
}
