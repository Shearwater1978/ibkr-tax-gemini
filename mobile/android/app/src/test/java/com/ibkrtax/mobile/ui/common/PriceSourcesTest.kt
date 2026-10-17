package com.ibkrtax.mobile.ui.common

import com.ibkrtax.mobile.portfolio.Holdings
import com.ibkrtax.mobile.portfolio.MainPage
import com.ibkrtax.mobile.portfolio.OpenLot
import com.ibkrtax.mobile.prices.PriceFreshness
import com.ibkrtax.mobile.prices.PriceSource
import com.ibkrtax.mobile.prices.PricedQuote
import com.ibkrtax.mobile.prices.Quote
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/** The sources line names each actual source once, Twelve Data first (mobile-market-prices "Attribution"). */
class PriceSourcesTest {
    private val time = Instant.parse("2026-10-16T20:00:00Z")

    private fun quote(symbol: String, currency: String, source: PriceSource?) =
        PricedQuote(Quote(symbol, BigDecimal.TEN, currency, time, time, source = source), PriceFreshness.LATEST_CLOSE)

    @Test
    fun eachActualSourceOnceTwelveDataFirst() {
        val holdings = Holdings.fromLots(
            listOf(
                OpenLot("AAPL", "", "2023-01-01", BigDecimal.ONE, BigDecimal.TEN, "USD"),
                OpenLot("MSFT", "", "2023-01-01", BigDecimal.ONE, BigDecimal.TEN, "USD"),
                OpenLot("SAP", "", "2023-01-01", BigDecimal.ONE, BigDecimal.TEN, "EUR"),
                OpenLot("OLD", "", "2023-01-01", BigDecimal.ONE, BigDecimal.TEN, "USD"),
            ),
        )
        val prices = mapOf(
            "AAPL" to quote("AAPL", "USD", PriceSource.FINNHUB),
            "MSFT" to quote("MSFT", "USD", PriceSource.TWELVE_DATA),
            "SAP.DE" to quote("SAP.DE", "EUR", PriceSource.YAHOO),
            "OLD" to quote("OLD", "USD", null), // cached before sources were recorded
        )
        val rows = MainPage.rows(holdings, prices, mapOf(("SAP" to "") to "IBIS"))

        assertEquals(listOf(PriceSource.TWELVE_DATA, PriceSource.FINNHUB, PriceSource.YAHOO), priceSources(rows))
        assertEquals(emptyList<PriceSource>(), priceSources(MainPage.rows(holdings, emptyMap())))
    }
}
