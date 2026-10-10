package com.ibkrtax.mobile.portfolio

import com.ibkrtax.mobile.fx.FxRates
import com.ibkrtax.mobile.prices.PriceFreshness
import com.ibkrtax.mobile.prices.PricedQuote
import com.ibkrtax.mobile.prices.Quote
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceTest {
    // 1 USD = 4 PLN, 1 EUR = 4.4 PLN, so 1 EUR = 1.1 USD.
    private val rates = FxRates("2026-10-09", mapOf("USD" to BigDecimal("4"), "EUR" to BigDecimal("4.4")))
    private val today = LocalDate.of(2026, 10, 10)

    private fun d(value: String) = BigDecimal(value)

    private fun assertAmount(expected: String, actual: BigDecimal?) = assertEquals(0, d(expected).compareTo(actual))

    private var order = 0L

    private fun event(date: String, type: String, ticker: String, quantity: String, price: String, currency: String = "USD", ratio: String? = null) =
        PortfolioEvent(order++, date, type, ticker, d(quantity), d(price), currency, "", splitRatio = ratio?.let(::d))

    private fun sales(vararg events: PortfolioEvent) = (FifoInventory.openLots(events.toList()) as FifoResult.Success).realized

    private fun rows(events: List<PortfolioEvent>, prices: Map<String, String>): Pair<List<PositionRow>, List<RealizedSale>> {
        val result = FifoInventory.openLots(events) as FifoResult.Success
        val quotes = prices.mapValues { (ticker, price) ->
            PricedQuote(Quote(ticker, d(price), "USD", Instant.EPOCH, Instant.EPOCH, previousClose = d(price)), PriceFreshness.LATEST_CLOSE)
        }
        return MainPage.rows(Holdings.fromLots(result.lots), quotes) to result.realized
    }

    private fun dividend(date: String, ticker: String, amount: String, type: String = "DIVIDEND", currency: String = "USD") =
        CashEvent(date, type, ticker, currency, d(amount))

    // --- gains from sales in the FIFO pass ---

    @Test
    fun partialSaleClosesTheOldestLotsFirst() {
        val sale = sales(
            event("2024-01-02", "BUY", "AAPL", "10", "100"),
            event("2024-02-02", "BUY", "AAPL", "10", "120"),
            event("2024-03-02", "SELL", "AAPL", "-15", "130"),
        ).single()
        assertAmount("1950", sale.proceeds)
        assertAmount("1600", sale.cost)
        assertAmount("350", sale.gain)
    }

    @Test
    fun saleAfterASplitUsesSplitAdjustedLots() {
        val sale = sales(
            event("2024-01-02", "BUY", "SAP", "4", "120", "EUR"),
            event("2024-02-02", "SPLIT", "SAP", "4", "0", "EUR", ratio = "2"),
            event("2024-03-02", "SELL", "SAP", "-8", "70", "EUR"),
        ).single()
        assertAmount("80", sale.gain) // 8 × (70 − 60)
        assertEquals("EUR", sale.currency)
    }

    @Test
    fun saleClosingLotsInAnotherCurrencyHasNoGain() {
        val sale = sales(
            event("2024-01-02", "BUY", "SHEL", "10", "24", "GBP"),
            event("2024-03-02", "SELL", "SHEL", "-5", "30", "EUR"),
        ).single()
        assertNull(sale.cost)
        assertNull(sale.gain)
    }

    @Test
    fun openLotsAreUnchangedBySaleTracking() {
        val events = listOf(
            event("2024-01-02", "BUY", "AAPL", "10", "100"),
            event("2024-03-02", "SELL", "AAPL", "-4", "130"),
        )
        val result = FifoInventory.openLots(events) as FifoResult.Success
        assertAmount("6", result.lots.single().quantity)
        assertAmount("100", result.lots.single().price)
    }

    // --- per-position breakdown ---

    @Test
    fun heldPositionCombinesPriceGainSalesAndDividends() {
        val (rows, realized) = rows(
            listOf(
                event("2024-01-02", "BUY", "AAPL", "10", "100"),
                event("2024-03-02", "SELL", "AAPL", "-4", "130"),
            ),
            mapOf("AAPL" to "150"),
        )
        val divs = listOf(dividend("2024-05-01", "AAPL", "5"), dividend("2024-05-01", "AAPL", "-0.75", type = "TAX"))
        val aapl = Performance.positions(rows, realized, divs).single()

        assertFalse(aapl.isSold)
        assertAmount("600", aapl.invested) // 6 × 100
        assertAmount("300", aapl.priceGain) // 6 × (150 − 100)
        assertAmount("120", aapl.realizedGain) // 4 × (130 − 100)
        assertAmount("4.25", aapl.dividends.net)
        assertAmount("424.25", aapl.total)
        assertTrue(aapl.totalPercent!! > d("70.70") && aapl.totalPercent!! < d("70.71")) // 424.25 / 600
    }

    @Test
    fun fullySoldPositionsComeAfterHeldOnes() {
        val (rows, realized) = rows(
            listOf(
                event("2024-01-02", "BUY", "MSFT", "3", "250"),
                event("2024-02-02", "SELL", "MSFT", "-3", "300"),
                event("2024-01-03", "BUY", "AAPL", "1", "100"),
            ),
            mapOf("AAPL" to "110"),
        )
        val all = Performance.positions(rows, realized, listOf(dividend("2023-12-01", "KO", "2")))

        assertEquals(listOf("AAPL", "KO", "MSFT"), all.map { it.ticker })
        val msft = all.last()
        assertTrue(msft.isSold)
        assertAmount("150", msft.realizedGain)
        assertAmount("150", msft.total)
        assertNull(msft.totalPercent)
        assertAmount("2", all[1].total) // dividends of a never-held (or earlier sold) ticker
    }

    @Test
    fun positionWithoutAPriceStillShowsSalesAndDividends() {
        val (rows, realized) = rows(
            listOf(event("2024-01-02", "BUY", "AAPL", "10", "100"), event("2024-03-02", "SELL", "AAPL", "-4", "130")),
            prices = emptyMap(),
        )
        val aapl = Performance.positions(rows, realized, listOf(dividend("2024-05-01", "AAPL", "5"))).single()
        assertNull(aapl.priceGain)
        assertNull(aapl.total)
        assertAmount("120", aapl.realizedGain)
        assertAmount("5", aapl.dividends.net)
    }

    // --- summary cards ---

    @Test
    fun summaryAddsEverythingInUsd() {
        val (rows, realized) = rows(
            listOf(
                event("2024-01-02", "BUY", "AAPL", "10", "100"),
                event("2024-03-02", "SELL", "AAPL", "-4", "130"),
                event("2024-01-02", "BUY", "MSFT", "3", "250"),
                event("2024-02-02", "SELL", "MSFT", "-3", "300"),
            ),
            mapOf("AAPL" to "150"),
        )
        val divs = listOf(
            dividend("2026-01-15", "AAPL", "5"),
            dividend("2026-01-15", "AAPL", "-0.75", type = "TAX"),
            dividend("2025-06-01", "SAP", "10", currency = "EUR"), // older than 12 months
        )
        val positions = Performance.positions(rows, realized, divs)
        val summary = Performance.summary(positions, divs, rates, today)

        assertAmount("900", summary.value.amount)
        assertAmount("600", summary.invested.amount)
        // 300 price gain + 120 + 150 from sales + 4.25 net AAPL dividends + 11 USD (10 EUR) SAP dividends
        assertAmount("585.25", summary.totalProfit.amount)
        assertTrue(summary.totalProfit.isComplete)
        assertAmount("4.25", summary.dividends12Months.amount)
        assertTrue(summary.totalProfitPercent!! > d("97.5") && summary.totalProfitPercent!! < d("97.6"))
    }

    @Test
    fun missingPriceMarksValueAndProfitIncompleteOnly() {
        val (rows, realized) = rows(
            listOf(event("2024-01-02", "BUY", "AAPL", "10", "100"), event("2024-03-02", "SELL", "AAPL", "-4", "130")),
            prices = emptyMap(),
        )
        val divs = listOf(dividend("2026-02-01", "AAPL", "5"))
        val summary = Performance.summary(Performance.positions(rows, realized, divs), divs, rates, today)

        assertFalse(summary.value.isComplete)
        assertFalse(summary.totalProfit.isComplete)
        assertAmount("125", summary.totalProfit.amount) // sales and dividends still count
        assertTrue(summary.invested.isComplete)
        assertTrue(summary.dividends12Months.isComplete)
    }

    @Test
    fun aSaleAcrossCurrenciesMarksProfitIncomplete() {
        val (rows, realized) = rows(
            listOf(event("2024-01-02", "BUY", "SHEL", "10", "24", "GBP"), event("2024-03-02", "SELL", "SHEL", "-10", "30", "EUR")),
            prices = emptyMap(),
        )
        val positions = Performance.positions(rows, realized, emptyList())
        assertFalse(positions.single().realizedComplete)
        assertFalse(Performance.summary(positions, emptyList(), rates, today).totalProfit.isComplete)
    }
}
