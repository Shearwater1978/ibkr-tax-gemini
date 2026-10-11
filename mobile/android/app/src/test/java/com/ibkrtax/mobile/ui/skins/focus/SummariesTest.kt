package com.ibkrtax.mobile.ui.skins.focus

import com.ibkrtax.mobile.portfolio.CashEvent
import com.ibkrtax.mobile.portfolio.DividendAmounts
import com.ibkrtax.mobile.portfolio.Dividends
import com.ibkrtax.mobile.portfolio.Holding
import com.ibkrtax.mobile.portfolio.PortfolioSummary
import com.ibkrtax.mobile.portfolio.PositionPerformance
import com.ibkrtax.mobile.portfolio.PositionRow
import com.ibkrtax.mobile.portfolio.UsdAmount
import com.ibkrtax.mobile.portfolio.UsdTotals
import java.math.BigDecimal
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SummariesTest {
    private lateinit var savedLocale: Locale

    @Before
    fun useUsLocale() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private fun d(v: String) = BigDecimal(v)

    private fun row(ticker: String, dailyPnl: String?, value: String? = "1000", quantity: String = "10") = PositionRow(
        holding = Holding(ticker, "US0000000000", "USD", d(quantity), d("50"), emptyList()),
        listingExchange = "NASDAQ",
        inPriceScope = true,
        price = null,
        cost = d("500"),
        marketValue = value?.let(::d),
        dailyChange = null,
        dailyChangePercent = null,
        dailyPnl = dailyPnl?.let(::d),
        unrealizedPnl = null,
    )

    private fun totals(day: String, missingValues: Int = 0, missingDaily: Int = 0) =
        UsdTotals(d("4000"), d(day), null, missingValues, missingDaily, null)

    private fun summary(percent: String?) =
        PortfolioSummary(UsdAmount(d("4000"), true), UsdAmount(d("1600"), true), UsdAmount(d("2400"), true), percent?.let(::d), UsdAmount(d("0"), true))

    @Test
    fun upDayNamesTheTwoLargestContributors() {
        val rows = listOf(row("NVDA", "70"), row("SAP", "45"), row("AAPL", "5"), row("KO", "-10"))
        val s = Summaries.today(rows, totals("110"), summary("149.49"), null)
        assertEquals("You’re up $110 today, mostly from NVDA and SAP. Since you started, your money grew by 149%.", s.text)
        assertEquals(Tone.GAIN, s.parts[1].tone)
    }

    @Test
    fun downDayNamesTheLargestFallsAndAShrinkingReturn() {
        val rows = listOf(row("NVDA", "-80"), row("SAP", "20"), row("AAPL", "-30"))
        val s = Summaries.today(rows, totals("-90"), summary("-5.4"), null)
        assertEquals("You’re down $90 today, mostly from NVDA and AAPL. Since you started, your money shrank by 5%.", s.text)
        assertEquals(Tone.LOSS, s.parts[1].tone)
    }

    @Test
    fun flatDayAndUnknownReturn() {
        val s = Summaries.today(listOf(row("AAPL", "0.2")), totals("0.2"), summary(null), null)
        assertEquals("Your portfolio is flat today.", s.text)
    }

    @Test
    fun missingPricesAreNoted() {
        val rows = listOf(row("AAPL", "12"), row("SAP", null, value = null))
        val s = Summaries.today(rows, totals("12", missingValues = 1, missingDaily = 1), summary("10"), null)
        assertEquals(
            "You’re up $12 today, mostly from AAPL. Since you started, your money grew by 10%. Some prices are missing, so these figures are incomplete.",
            s.text,
        )
    }

    @Test
    fun noDailyChangeAtAll() {
        val s = Summaries.today(listOf(row("AAPL", null)), totals("0", missingDaily = 1), summary(null), null)
        assertEquals("Today’s change is not known yet. Some prices are missing, so these figures are incomplete.", s.text)
    }

    private val events = listOf(
        CashEvent("2024-05-16", "DIVIDEND", "AAPL", "USD", d("10")),
        CashEvent("2025-05-15", "DIVIDEND", "AAPL", "USD", d("12")),
        CashEvent("2025-05-15", "TAX", "AAPL", "USD", d("-1.80")),
    )

    @Test
    fun incomeChangeForTheCurrentAndAPastYear() {
        val y2025 = Dividends.period(events, 2025, null)
        assertEquals(
            "This year your companies paid you $10.20 after tax: 2% more than the same months last year.",
            Summaries.income(y2025, d("2.0"), IncomePeriod.Year(2025, isCurrent = true)).text,
        )
        assertEquals(
            "In 2025 your companies paid you $10.20 after tax: 15% less than the year before.",
            Summaries.income(y2025, d("-15.2"), IncomePeriod.Year(2025, isCurrent = false)).text,
        )
        assertEquals("In 2024 your companies paid you $10.00 after tax.", Summaries.income(Dividends.period(events, 2024, null), null, IncomePeriod.Year(2024, false)).text)
    }

    @Test
    fun incomeForAllYears() {
        assertEquals(
            "Since 2024 your companies paid you $20.20 after $1.80 tax.",
            Summaries.income(Dividends.period(events, null, null), null, IncomePeriod.AllYears(2024)).text,
        )
    }

    @Test
    fun storyTellsInvestedValueSalesDividendsAndTotal() {
        val p = PositionPerformance(
            ticker = "AAPL", currency = "USD", row = row("AAPL", "5", value = "3029.76", quantity = "9"),
            invested = d("1470"), priceGain = d("1559.76"), realizedGain = d("200"), realizedComplete = true,
            dividends = DividendAmounts(d("3.60"), d("-0.54")),
        )
        assertEquals(
            listOf(
                "You put in $1,470.00 for your 9 shares. They are now worth $3,029.76.",
                "On top of that, you earned $200.00 from shares you sold, and $3.06 in dividends after $0.54 tax.",
                "All in all: +$1,762.82 · +120%",
            ),
            Summaries.story(p).map { it.text },
        )
    }

    @Test
    fun storyWithoutPriceOrIncome() {
        val p = PositionPerformance(
            ticker = "SAP", currency = "EUR", row = row("SAP", null, value = null, quantity = "3"),
            invested = d("400"), priceGain = null, realizedGain = BigDecimal.ZERO, realizedComplete = true, dividends = DividendAmounts(),
        )
        assertEquals(
            listOf(
                "You put in €400.00 for your 3 shares. Their current price is not available.",
                "There were no sales or dividends so far.",
                "All in all: not known yet, because the price is missing.",
            ),
            Summaries.story(p).map { it.text },
        )
    }
}
