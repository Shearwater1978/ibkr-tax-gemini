package com.ibkrtax.mobile.portfolio

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

class PortfolioSummaryTest {
    private fun holding(ticker: String, isin: String, currency: String, quantity: String, price: String): Holding {
        val lot = OpenLot(ticker, isin, "2023-01-01", BigDecimal(quantity), BigDecimal(price), currency)
        return Holdings.fromLots(listOf(lot)).single()
    }

    private fun priced(symbol: String, price: String, currency: String, freshness: PriceFreshness = PriceFreshness.LIVE): PricedQuote {
        val time = Instant.parse("2024-01-02T15:00:00Z")
        return PricedQuote(Quote(symbol, BigDecimal(price), currency, time, time), freshness)
    }

    private val usd = holding("AAPL", "US0378331005", "USD", "9", "150")
    private val msft = holding("MSFT", "US5949181045", "USD", "2", "300")
    private val eur = holding("SAP", "DE0007164600", "EUR", "5", "60")
    private val gbp = holding("SHEL", "GB00BP6MXD84", "GBP", "10", "24")

    @Test
    fun mixedCurrenciesGetSeparateSubtotalsWithoutConversion() {
        val subtotals = PortfolioSummary.build(listOf(usd, eur, gbp), mapOf("AAPL" to priced("AAPL", "190", "USD")))

        assertEquals(listOf("EUR", "GBP", "USD"), subtotals.map { it.currency })
        val usdTotal = subtotals.single { it.currency == "USD" }
        assertEquals(0, BigDecimal("1350").compareTo(usdTotal.cost))
        assertEquals(0, BigDecimal("1710").compareTo(usdTotal.marketValue))
        assertEquals(0, BigDecimal("360").compareTo(usdTotal.unrealizedGain))
        assertTrue(usdTotal.isComplete)
    }

    @Test
    fun holdingsOutsideThePriceScopeAreUnavailableEvenIfAQuoteExists() {
        val subtotals = PortfolioSummary.build(listOf(eur), mapOf("SAP" to priced("SAP", "70", "EUR")))
        val value = subtotals.single().holdings.single()

        assertFalse(value.inPriceScope)
        assertNull(value.marketValue)
        assertFalse(subtotals.single().isComplete)
    }

    @Test
    fun missingPriceMarksValueUnavailableAndSubtotalIncomplete() {
        val usdTotal = PortfolioSummary.build(listOf(usd, msft), mapOf("AAPL" to priced("AAPL", "190", "USD"))).single()

        assertNull(usdTotal.holdings.single { it.holding.ticker == "MSFT" }.marketValue)
        assertEquals(1, usdTotal.missingPrices)
        assertFalse(usdTotal.isComplete)
        assertEquals(0, BigDecimal("1710").compareTo(usdTotal.marketValue))
    }

    @Test
    fun quoteInAnotherCurrencyIsTreatedAsUnavailable() {
        val subtotal = PortfolioSummary.build(listOf(usd), mapOf("AAPL" to priced("AAPL", "175", "EUR"))).single()

        assertNull(subtotal.holdings.single().marketValue)
        assertFalse(subtotal.isComplete)
    }

    @Test
    fun stalePricesAreCounted() {
        val subtotal = PortfolioSummary.build(listOf(usd), mapOf("AAPL" to priced("AAPL", "190", "USD", PriceFreshness.STALE))).single()

        assertEquals(1, subtotal.stalePrices)
        assertTrue(subtotal.isComplete)
    }
}
