package com.ibkrtax.mobile.debug

import android.content.Context
import com.ibkrtax.mobile.fx.FxRates
import com.ibkrtax.mobile.prices.Quote
import java.math.BigDecimal
import java.time.Instant

/**
 * Debug builds only: synthetic fixtures from mobile/fixtures, used to exercise the
 * portfolio screens while report import stays disabled. Release builds use a stub.
 */
object SampleReports {
    const val AVAILABLE = true

    val names: List<String> = listOf("valid_basic.csv", "valid_followup.csv")

    fun read(context: Context, name: String): ByteArray =
        context.assets.open("flex-query/$name").use { it.readBytes() }

    /** Made-up quotes dated in the past, so the app always labels them stale, never live. */
    fun samplePrices(): List<Quote> {
        val time = Instant.parse("2024-01-03T21:00:00Z")
        return listOf(Quote("AAPL", BigDecimal("190.00"), "USD", time, time, BigDecimal("185.00")))
    }

    /** Made-up NBP-style rates, for the USD total only. */
    fun sampleRates(): FxRates =
        FxRates("2024-01-03", mapOf("USD" to BigDecimal("4.0000"), "EUR" to BigDecimal("4.4000")))
}
