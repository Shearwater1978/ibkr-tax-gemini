package com.ibkrtax.mobile.portfolio

import com.ibkrtax.mobile.prices.PriceFreshness
import com.ibkrtax.mobile.prices.PricedQuote
import com.ibkrtax.mobile.prices.Quote
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopMoversTest {
    /** A held position priced at [price] after closing at [previousClose] (null: no previous close). */
    private fun row(ticker: String, price: String, previousClose: String?, freshness: PriceFreshness = PriceFreshness.LIVE): PositionRow {
        val holding = Holdings.fromLots(listOf(OpenLot(ticker, "", "2024-01-01", BigDecimal.ONE, BigDecimal.ONE, "USD"))).single()
        val quote = PricedQuote(Quote(ticker, BigDecimal(price), "USD", Instant.EPOCH, Instant.EPOCH, previousClose?.let(::BigDecimal)), freshness)
        return MainPage.rows(listOf(holding), mapOf(ticker to quote)).single()
    }

    @Test
    fun ranksByPercentWithUpToFivePerList() {
        val rows = listOf(
            row("A", "101", "100"), // +1%
            row("B", "110", "100"), // +10%
            row("C", "103", "100"),
            row("D", "104", "100"),
            row("E", "105", "100"),
            row("F", "102", "100"),
            row("G", "95", "100"), // -5%
            row("H", "99", "100"), // -1%
            row("I", "100", "100"), // unchanged: in neither list
        )
        val movers = TopMoversRanking.rank(rows)!!

        assertEquals(listOf("B", "E", "D", "C", "F"), movers.gainers.map { it.ticker })
        assertEquals(listOf("G", "H"), movers.losers.map { it.ticker })
        assertTrue(movers.gainers.none { it in movers.losers })
    }

    @Test
    fun onlyGainsLeavesTheLosersEmpty() {
        val movers = TopMoversRanking.rank(listOf(row("A", "101", "100"), row("B", "102", "100")))!!
        assertEquals(2, movers.gainers.size)
        assertTrue(movers.losers.isEmpty())
    }

    @Test
    fun hiddenWithoutPricesOrPreviousCloses() {
        assertNull(TopMoversRanking.rank(emptyList()))
        assertNull(TopMoversRanking.rank(listOf(row("A", "101", null))))
        val unpriced = MainPage.rows(Holdings.fromLots(listOf(OpenLot("SAP", "", "2024-01-01", BigDecimal.ONE, BigDecimal.ONE, "EUR"))), emptyMap())
        assertNull(TopMoversRanking.rank(unpriced))
    }

    @Test
    fun reportsTheWorstFreshness() {
        assertEquals(PriceFreshness.LIVE, TopMoversRanking.rank(listOf(row("A", "101", "100")))!!.freshness)
        val close = TopMoversRanking.rank(listOf(row("A", "101", "100"), row("B", "99", "100", PriceFreshness.LATEST_CLOSE)))!!
        assertEquals(PriceFreshness.LATEST_CLOSE, close.freshness)
        val stale = TopMoversRanking.rank(listOf(row("A", "101", "100", PriceFreshness.STALE), row("B", "99", "100", PriceFreshness.LATEST_CLOSE)))!!
        assertEquals(PriceFreshness.STALE, stale.freshness)
    }
}
