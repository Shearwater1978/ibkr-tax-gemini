package com.ibkrtax.mobile.portfolio

import com.ibkrtax.mobile.fx.FxRates
import com.ibkrtax.mobile.prices.PriceFreshness
import com.ibkrtax.mobile.prices.PricedQuote
import com.ibkrtax.mobile.prices.Quote
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainPageTest {
    private fun holding(ticker: String, isin: String, currency: String, quantity: String, price: String): Holding =
        Holdings.fromLots(listOf(OpenLot(ticker, isin, "2023-01-01", BigDecimal(quantity), BigDecimal(price), currency))).single()

    private fun priced(symbol: String, price: String, previousClose: String?, freshness: PriceFreshness = PriceFreshness.LIVE): PricedQuote {
        val time = Instant.parse("2024-01-03T16:00:00Z")
        return PricedQuote(Quote(symbol, BigDecimal(price), "USD", time, time, previousClose?.let(::BigDecimal)), freshness)
    }

    private fun assertDecimal(expected: String, actual: BigDecimal?) =
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))

    // Synthetic positions and prices.
    private val aapl = holding("AAPL", "US0378331005", "USD", "10", "150")
    private val msft = holding("MSFT", "US5949181045", "USD", "2", "300")
    private val sap = holding("SAP", "DE0007164600", "EUR", "5", "60")
    private val prices = mapOf("AAPL" to priced("AAPL", "190", "185"), "MSFT" to priced("MSFT", "400", "410"))
    private val rates = FxRates("2026-10-08", mapOf("USD" to BigDecimal("4.0"), "EUR" to BigDecimal("4.4")))

    @Test
    fun rowsCarryDailyAndUnrealizedPnlInTheirOwnCurrency() {
        val rows = MainPage.rows(listOf(aapl, sap), prices, mapOf(("AAPL" to "US0378331005") to "NASDAQ"))
        val row = rows.first { it.ticker == "AAPL" }

        assertEquals("NASDAQ", row.listingExchange)
        assertDecimal("1900", row.marketValue)
        assertDecimal("5", row.dailyChange)
        assertDecimal("2.702702702702702702702702703", row.dailyChangePercent)
        assertDecimal("50", row.dailyPnl)
        assertDecimal("400", row.unrealizedPnl)

        val eurRow = rows.first { it.ticker == "SAP" }
        assertFalse(eurRow.inPriceScope)
        assertNull(eurRow.marketValue)
        assertDecimal("300", eurRow.cost)
    }

    @Test
    fun usdTotalAddsUsdPositionsAndMarksMissingPricesIncomplete() {
        val totals = MainPage.usdTotals(MainPage.rows(listOf(aapl, msft, sap), prices), rates)

        assertDecimal("2700", totals.marketValue) // 1900 + 800; SAP has no price
        assertDecimal("30", totals.dailyPnl) // +50 - 20
        // 30 / (2700 - 30)
        assertDecimal("1.123595505617977528089887640", totals.dailyPnlPercent)
        assertEquals(1, totals.missingValues)
        assertFalse(totals.isComplete)
        assertNull("no non-USD amount was converted", totals.rateDate)
    }

    @Test
    fun nonUsdValuesConvertAtNbpCrossRates() {
        val eurRow = MainPage.rows(listOf(aapl), prices).single().let { usdRow ->
            usdRow.copy(holding = sap, marketValue = BigDecimal("100"), dailyPnl = BigDecimal("10"))
        }
        val totals = MainPage.usdTotals(listOf(eurRow), rates)

        assertDecimal("110", totals.marketValue) // 100 EUR x 4.4 / 4.0
        assertDecimal("11", totals.dailyPnl)
        assertEquals("2026-10-08", totals.rateDate)
        assertTrue(totals.isComplete)
    }

    @Test
    fun missingRateMarksTheTotalIncomplete() {
        val eurRow = MainPage.rows(listOf(aapl), prices).single().copy(holding = sap, marketValue = BigDecimal("100"))
        val totals = MainPage.usdTotals(listOf(eurRow), rates = null)

        assertEquals(1, totals.missingValues)
        assertDecimal("0", totals.marketValue)
    }

    @Test
    fun sortsByEachColumnWithMissingValuesLast() {
        val rows = MainPage.rows(listOf(msft, sap, aapl), prices)
        fun order(column: SortColumn, descending: Boolean = false, mode: PnlMode = PnlMode.DAILY) =
            MainPage.sorted(rows, SortOrder(column, descending), mode).map { it.ticker }

        assertEquals(listOf("AAPL", "MSFT", "SAP"), order(SortColumn.SYMBOL))
        assertEquals(listOf("SAP", "MSFT", "AAPL"), order(SortColumn.SYMBOL, descending = true))
        assertEquals(listOf("AAPL", "MSFT", "SAP"), order(SortColumn.LAST_PRICE))
        assertEquals(listOf("MSFT", "AAPL", "SAP"), order(SortColumn.LAST_PRICE, descending = true))
        assertEquals(listOf("MSFT", "AAPL", "SAP"), order(SortColumn.CHANGE))
        assertEquals(listOf("MSFT", "SAP", "AAPL"), order(SortColumn.POSITION))
        assertEquals(listOf("MSFT", "AAPL", "SAP"), order(SortColumn.PNL, mode = PnlMode.DAILY))
        // Unrealized: MSFT +200, AAPL +400
        assertEquals(listOf("AAPL", "MSFT", "SAP"), order(SortColumn.PNL, descending = true, mode = PnlMode.UNREALIZED))
    }

    @Test
    fun selectingTheActiveColumnReversesIt() {
        val order = SortOrder().select(SortColumn.SYMBOL)
        assertTrue(order.descending)
        assertEquals(SortOrder(SortColumn.PNL), order.select(SortColumn.PNL))
    }

    @Test
    fun stalePricesAreFlagged() {
        val row = MainPage.rows(listOf(aapl), mapOf("AAPL" to priced("AAPL", "190", "185", PriceFreshness.STALE))).single()
        assertTrue(row.isStale)
    }
}
