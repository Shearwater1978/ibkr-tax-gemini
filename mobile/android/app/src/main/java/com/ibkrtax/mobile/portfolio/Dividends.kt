package com.ibkrtax.mobile.portfolio

import com.ibkrtax.mobile.fx.FxRates
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.YearMonth

/** A DIVIDEND or TAX (withholding tax) row as stored; amounts keep their sign, so tax is usually negative. */
data class CashEvent(val date: String, val eventType: String, val ticker: String, val currency: String, val amount: BigDecimal) {
    /** Null when the stored date is not ISO; such rows are left out rather than guessed. */
    val month: YearMonth? get() = runCatching { YearMonth.parse(date.take(7)) }.getOrNull()
}

/** Gross dividends and withholding tax in one currency; net = gross + tax. */
data class DividendAmounts(val gross: BigDecimal = BigDecimal.ZERO, val tax: BigDecimal = BigDecimal.ZERO) {
    val net: BigDecimal get() = gross.add(tax, PY)

    operator fun plus(other: DividendAmounts) = DividendAmounts(gross.add(other.gross, PY), tax.add(other.tax, PY))

    companion object {
        fun of(event: CashEvent) =
            if (event.eventType == Dividends.TAX) DividendAmounts(tax = event.amount) else DividendAmounts(gross = event.amount)
    }
}

/** An approximate USD sum; [missingCurrencies] had no NBP rate and are left out, so the sum is incomplete. */
data class UsdSum(val amount: BigDecimal, val missingCurrencies: Set<String> = emptySet()) {
    val isComplete: Boolean get() = missingCurrencies.isEmpty()
}

data class MonthDividends(val month: Int, val byCurrency: Map<String, DividendAmounts>, val usdNet: UsdSum)

data class PositionDividends(val ticker: String, val currency: String, val amounts: DividendAmounts, val usdNet: BigDecimal?)

data class YearDividends(
    val year: Int,
    /** Always twelve entries, January first. */
    val months: List<MonthDividends>,
    val byCurrency: Map<String, DividendAmounts>,
    val usdGross: UsdSum,
    val usdTax: UsdSum,
    val usdNet: UsdSum,
    /** Largest USD net first; positions without a rate go last. */
    val positions: List<PositionDividends>,
    /** NBP table date, when any non-USD amount was converted. */
    val rateDate: String?,
)

/**
 * Dividend views (mobile-dividends spec). Rows count in the month of their own date, so a
 * withholding-tax correction lands in the month it was booked. Currencies are only added
 * together after conversion to USD at the latest NBP cross rates.
 */
object Dividends {
    const val DIVIDEND = "DIVIDEND"
    const val TAX = "TAX"
    private const val USD = "USD"

    /** Years that contain dividend or tax rows, newest first. */
    fun years(events: List<CashEvent>): List<Int> = events.mapNotNull { it.month?.year }.distinct().sortedDescending()

    /** First and last month with dividend data, or null when there is none. */
    fun coverage(events: List<CashEvent>): Pair<YearMonth, YearMonth>? {
        val months = events.mapNotNull { it.month }
        return if (months.isEmpty()) null else months.min() to months.max()
    }

    fun year(events: List<CashEvent>, year: Int, rates: FxRates?): YearDividends {
        val inYear = events.filter { it.month?.year == year }
        val byCurrency = sumByCurrency(inYear)
        val months = (1..12).map { month ->
            val amounts = sumByCurrency(inYear.filter { it.month?.monthValue == month })
            MonthDividends(month, amounts, usdNet(amounts, rates))
        }
        val positions = inYear.groupBy { it.ticker to it.currency }.map { (key, rows) ->
            val amounts = rows.map(DividendAmounts::of).reduce(DividendAmounts::plus)
            PositionDividends(key.first, key.second, amounts, toUsd(amounts.net, key.second, rates))
        }.sortedWith(compareBy<PositionDividends> { it.usdNet == null }.thenByDescending { it.usdNet }.thenBy { it.ticker })
        return YearDividends(
            year = year,
            months = months,
            byCurrency = byCurrency,
            usdGross = usd(byCurrency, rates) { it.gross },
            usdTax = usd(byCurrency, rates) { it.tax },
            usdNet = usdNet(byCurrency, rates),
            positions = positions,
            rateDate = rates?.effectiveDate?.takeIf { byCurrency.keys.any { it != USD && rates.toUsd(BigDecimal.ONE, it) != null } },
        )
    }

    /** Net USD per month (twelve entries) for each of up to [count] most recent years, oldest year first. */
    fun netByMonthForRecentYears(events: List<CashEvent>, rates: FxRates?, count: Int = 3): Map<Int, List<UsdSum>> =
        years(events).take(count).sorted().associateWith { year -> year(events, year, rates).months.map { it.usdNet } }

    private fun sumByCurrency(events: List<CashEvent>): Map<String, DividendAmounts> =
        events.groupBy { it.currency }.mapValues { (_, rows) -> rows.map(DividendAmounts::of).reduce(DividendAmounts::plus) }.toSortedMap()

    private fun usd(byCurrency: Map<String, DividendAmounts>, rates: FxRates?, pick: (DividendAmounts) -> BigDecimal): UsdSum {
        var sum = BigDecimal.ZERO
        val missing = mutableSetOf<String>()
        for ((currency, amounts) in byCurrency) {
            val usd = toUsd(pick(amounts), currency, rates)
            if (usd == null) missing += currency else sum = sum.add(usd, PY)
        }
        return UsdSum(sum, missing)
    }

    /** USD gross plus USD tax, each in cents, so the net shown always equals the gross and tax shown beside it. */
    private fun usdNet(byCurrency: Map<String, DividendAmounts>, rates: FxRates?): UsdSum {
        val gross = usd(byCurrency, rates) { it.gross }
        val tax = usd(byCurrency, rates) { it.tax }
        return UsdSum(gross.amount.cents().add(tax.amount.cents()), gross.missingCurrencies + tax.missingCurrencies)
    }

    private fun BigDecimal.cents(): BigDecimal = setScale(2, RoundingMode.HALF_EVEN)

    private fun toUsd(amount: BigDecimal, currency: String, rates: FxRates?): BigDecimal? =
        if (currency == USD) amount else rates?.toUsd(amount, currency)
}

private val PY = MathContext(28, RoundingMode.HALF_EVEN)
