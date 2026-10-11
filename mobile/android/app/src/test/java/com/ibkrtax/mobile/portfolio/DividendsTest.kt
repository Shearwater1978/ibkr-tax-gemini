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
    fun allYearsAddEveryYearMonthByMonth() {
        val events = listOf(dividend("2024-05-05", "KO", "2"), dividend("2025-05-05", "KO", "3"), dividend("2025-08-05", "KO", "1"))
        val all = Dividends.period(events, null, rates)

        assertNull(all.year)
        assertAmount("6", all.usdNet.amount)
        assertAmount("5", all.months[4].usdNet.amount)
        assertAmount("1", all.months[7].usdNet.amount)
        assertAmount("6", all.positions.single().amounts.net)
    }

    @Test
    fun changeFromYearBeforeComparesTheSameMonthsForTheRunningYear() {
        val events = listOf(
            dividend("2025-03-01", "KO", "10"),
            dividend("2025-11-01", "KO", "10"), // after today's month in 2026: left out of the comparison
            dividend("2026-03-01", "KO", "12"),
        )
        val today = java.time.LocalDate.of(2026, 10, 11)
        assertAmount("20", Dividends.changeFromYearBefore(events, 2026, rates, today)!!) // 12 vs 10
        // A finished year compares all twelve months: 2026 (12) against 2025 (10 + 10) is −40%.
        assertAmount("-40", Dividends.changeFromYearBefore(events, 2026, rates, java.time.LocalDate.of(2027, 1, 1))!!)
        assertNull(Dividends.changeFromYearBefore(events, 2025, rates, today)) // nothing in 2024
    }

    @Test
    fun rowsWithoutAnIsoDateAreLeftOut() {
        val events = listOf(dividend("bad", "KO", "1"), dividend("2025-01-02", "KO", "2"))
        assertEquals(listOf(2025), Dividends.years(events))
        assertAmount("2", Dividends.year(events, 2025, null).usdNet.amount)
    }

    @Test
    fun payerHistoryListsAllYearsNewestFirstWithTotals() {
        val events = listOf(
            dividend("2024-05-16", "AAPL", "1.20"), tax("2024-05-16", "AAPL", "-0.18"),
            dividend("2025-05-15", "AAPL", "1.30"), tax("2025-05-15", "AAPL", "-0.20"),
            dividend("2025-06-01", "KO", "5"),
        )
        val history = Dividends.history(events, "AAPL", "USD")
        assertEquals(listOf("2025-05-15", "2024-05-16"), history.lines.map { it.date })
        assertAmount("1.10", history.lines.first().amounts.net)
        assertAmount("2.50", history.total.gross)
        assertAmount("-0.38", history.total.tax)
        assertAmount("2.12", history.total.net)
    }

    @Test
    fun aLaterTaxCorrectionIsItsOwnLine() {
        val events = listOf(dividend("2025-05-15", "AAPL", "1.00"), tax("2025-05-15", "AAPL", "-0.30"), tax("2025-06-20", "AAPL", "0.15"))
        val history = Dividends.history(events, "AAPL", "USD")
        assertEquals(listOf("2025-06-20", "2025-05-15"), history.lines.map { it.date })
        assertAmount("0", history.lines.first().amounts.gross)
        assertAmount("0.15", history.lines.first().amounts.tax)
        assertAmount("-0.15", history.total.tax)
        assertAmount("0.85", history.total.net)
    }

    @Test
    fun payerHistoryKeepsCurrenciesApart() {
        val events = listOf(dividend("2025-03-01", "SHEL", "2", "GBP"), dividend("2025-04-01", "SHEL", "3", "EUR"))
        assertAmount("2", Dividends.history(events, "SHEL", "GBP").total.gross)
        assertAmount("3", Dividends.history(events, "SHEL", "EUR").total.gross)
        assertEquals(1, Dividends.history(events, "SHEL", "EUR").lines.size)
    }

    @Test
    fun paymentsOfAPeriodNewestFirstWithTaxOnTheSameDay() {
        val events = listOf(
            dividend("2024-12-02", "KO", "4"),
            dividend("2025-05-15", "AAPL", "1.00"), tax("2025-05-15", "AAPL", "-0.15"),
            dividend("2025-05-15", "KO", "2"),
            dividend("2025-06-03", "SHEL", "3", "EUR"),
        )
        val y2025 = Dividends.payments(events, 2025)
        assertEquals(listOf("SHEL", "AAPL", "KO"), y2025.map { it.ticker })
        assertAmount("0.85", y2025[1].amounts.net)
        assertEquals(YearMonth.of(2025, 6), y2025.first().month)
        assertEquals(4, Dividends.payments(events, null).size)
    }
}
