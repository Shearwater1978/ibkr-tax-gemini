package com.ibkrtax.mobile.portfolio

import com.ibkrtax.mobile.fx.FxRates
import java.math.BigDecimal
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DividendsTest {
    // 1 USD = 4 PLN, 1 EUR = 4.4 PLN, so 1 EUR = 1.1 USD.
    private val rates = FxRates("2026-10-09", mapOf("USD" to BigDecimal("4"), "EUR" to BigDecimal("4.4")))

    private fun dividend(date: String, ticker: String, amount: String, currency: String = "USD") =
        CashEvent(date, "DIVIDEND", ticker, currency, BigDecimal(amount))

    private fun tax(date: String, ticker: String, amount: String, currency: String = "USD") =
        CashEvent(date, "TAX", ticker, currency, BigDecimal(amount))

    private fun assertAmount(expected: String, actual: BigDecimal) = assertEquals(0, BigDecimal(expected).compareTo(actual))

    @Test
    fun monthsHoldGrossTaxAndNet() {
        val events = listOf(
            dividend("2025-05-18", "AAPL", "3.60"),
            tax("2025-05-18", "AAPL", "-0.54"),
            dividend("2025-08-15", "MSFT", "10"),
        )
        val year = Dividends.year(events, 2025, rates)

        assertEquals(12, year.months.size)
        val may = year.months[4].byCurrency.getValue("USD")
        assertAmount("3.60", may.gross)
        assertAmount("-0.54", may.tax)
        assertAmount("3.06", may.net)
        assertAmount("10", year.months[7].usdNet.amount)
        assertAmount("0", year.months[0].usdNet.amount)
        assertAmount("13.06", year.usdNet.amount)
        assertAmount("-0.54", year.usdTax.amount)
        assertNull("no conversion needed for USD only", year.rateDate)
    }

    @Test
    fun aTaxCorrectionCountsInTheMonthOfItsOwnDate() {
        val events = listOf(
            dividend("2025-03-10", "AAPL", "10"),
            tax("2025-03-10", "AAPL", "-3"),
            tax("2025-06-02", "AAPL", "1.5"),
        )
        val year = Dividends.year(events, 2025, rates)

        assertAmount("7", year.months[2].usdNet.amount)
        assertAmount("1.5", year.months[5].usdNet.amount)
        assertAmount("8.5", year.positions.single().amounts.net)
    }

    @Test
    fun currenciesStayApartAndTotalInUsd() {
        val events = listOf(dividend("2025-04-01", "SAP", "10", "EUR"), dividend("2025-04-02", "AAPL", "5"))
        val year = Dividends.year(events, 2025, rates)

        assertEquals(setOf("EUR", "USD"), year.byCurrency.keys)
        assertAmount("10", year.byCurrency.getValue("EUR").net)
        assertAmount("16", year.usdNet.amount) // 10 EUR = 11 USD, plus 5 USD
        assertTrue(year.usdNet.isComplete)
        assertEquals("2026-10-09", year.rateDate)
        assertEquals(listOf("SAP", "AAPL"), year.positions.map { it.ticker }) // 11 USD before 5 USD
    }

    @Test
    fun aMissingRateMarksTheTotalIncomplete() {
        val events = listOf(dividend("2025-04-01", "NOVO", "100", "DKK"), dividend("2025-04-02", "AAPL", "5"))
        val year = Dividends.year(events, 2025, rates)

        assertAmount("5", year.usdNet.amount)
        assertFalse(year.usdNet.isComplete)
        assertEquals(setOf("DKK"), year.usdNet.missingCurrencies)
        assertAmount("100", year.byCurrency.getValue("DKK").net)
        assertEquals("NOVO", year.positions.last().ticker)
        assertNull(year.positions.last().usdNet)
    }

    @Test
    fun yearsCoverageAndComparisonUseTheMostRecentThreeYears() {
        val events = listOf(
            dividend("2022-01-05", "KO", "1"),
            dividend("2024-05-05", "KO", "2"),
            dividend("2025-05-05", "KO", "3"),
            dividend("2026-02-05", "KO", "4"),
        )
        assertEquals(listOf(2026, 2025, 2024, 2022), Dividends.years(events))
        assertEquals(YearMonth.of(2022, 1) to YearMonth.of(2026, 2), Dividends.coverage(events))

        val compare = Dividends.netByMonthForRecentYears(events, rates)
        assertEquals(listOf(2024, 2025, 2026), compare.keys.toList())
        assertAmount("3", compare.getValue(2025)[4].amount)
        assertAmount("4", compare.getValue(2026)[1].amount)
    }

    @Test
    fun usdNetEqualsUsdGrossPlusUsdTax() {
        // 1 EUR = 1.1 USD exactly here; with an awkward rate each part rounds on its own when shown.
        val awkward = FxRates("2026-10-09", mapOf("USD" to BigDecimal("4.0123"), "EUR" to BigDecimal("4.3987")))
        val events = listOf(dividend("2024-05-20", "SAP", "15.40", "EUR"), tax("2024-05-20", "SAP", "-4.06", "EUR"))
        val year = Dividends.year(events, 2024, awkward)
        fun cents(value: BigDecimal) = value.setScale(2, java.math.RoundingMode.HALF_EVEN)
        assertEquals(cents(year.usdGross.amount).add(cents(year.usdTax.amount)), cents(year.usdNet.amount))
        assertEquals(0, year.months[4].usdNet.amount.compareTo(year.usdNet.amount))
    }

    @Test
    fun rowsWithoutAnIsoDateAreLeftOut() {
        val events = listOf(dividend("bad", "KO", "1"), dividend("2025-01-02", "KO", "2"))
        assertEquals(listOf(2025), Dividends.years(events))
        assertAmount("2", Dividends.year(events, 2025, null).usdNet.amount)
    }
}
