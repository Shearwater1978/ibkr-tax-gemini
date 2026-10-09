package com.ibkrtax.mobile.portfolio

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

    private fun quote(isin: String, price: String, currency: String) =
        Quote(isin, BigDecimal(price), currency, Instant.parse("2024-01-02T15:00:00Z"), isClose = false)

    private val usd = holding("AAPL", "US0378331005", "USD", "9", "150")
    private val eur = holding("SAP", "DE0007164600", "EUR", "5", "60")
    private val gbp = holding("SHEL", "GB00BP6MXD84", "GBP", "10", "24")

    @Test
    fun mixedCurrenciesGetSeparateSubtotalsWithoutConversion() {
        val subtotals = PortfolioSummary.build(
            listOf(usd, eur, gbp),
            mapOf(usd.isin to quote(usd.isin, "190", "USD"), eur.isin to quote(eur.isin, "70", "EUR")),
        )

        assertEquals(listOf("EUR", "GBP", "USD"), subtotals.map { it.currency })
        val usdTotal = subtotals.single { it.currency == "USD" }
        assertEquals(0, BigDecimal("1350").compareTo(usdTotal.cost))
        assertEquals(0, BigDecimal("1710").compareTo(usdTotal.marketValue))
        assertEquals(0, BigDecimal("360").compareTo(usdTotal.unrealizedGain))
        assertTrue(usdTotal.isComplete)
        assertEquals(0, BigDecimal("350").compareTo(subtotals.single { it.currency == "EUR" }.marketValue))
    }

    @Test
    fun missingPriceMarksValueUnavailableAndSubtotalIncomplete() {
        val gbpTotal = PortfolioSummary.build(listOf(gbp), emptyMap()).single()

        assertNull(gbpTotal.holdings.single().marketValue)
        assertEquals(1, gbpTotal.missingPrices)
        assertFalse(gbpTotal.isComplete)
        assertEquals(0, BigDecimal("240").compareTo(gbpTotal.cost))
    }

    @Test
    fun quoteInAnotherCurrencyIsTreatedAsUnavailable() {
        val subtotal = PortfolioSummary.build(listOf(eur), mapOf(eur.isin to quote(eur.isin, "75", "USD"))).single()

        assertNull(subtotal.holdings.single().marketValue)
        assertFalse(subtotal.isComplete)
    }
}
