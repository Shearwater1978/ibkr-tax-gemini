package com.ibkrtax.mobile.portfolio

import com.ibkrtax.mobile.fx.FxRates
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/**
 * What one position has earned, in its own currency (mobile-position-performance spec).
 * A fully sold position has [row] null, quantity zero and no price gain.
 */
data class PositionPerformance(
    val ticker: String,
    val currency: String,
    val row: PositionRow?,
    /** FIFO cost of the open lots, excluding commissions. */
    val invested: BigDecimal,
    /** Market value minus invested; null without a price, zero when fully sold. */
    val priceGain: BigDecimal?,
    val realizedGain: BigDecimal,
    /** False when a sale closed lots bought in another currency; those gains are left out. */
    val realizedComplete: Boolean,
    val dividends: DividendAmounts,
) {
    val isSold: Boolean get() = row == null

    /** Price gain + gain from sales + net dividends; null when the price gain is unknown. */
    val total: BigDecimal? get() = priceGain?.add(realizedGain, PY)?.add(dividends.net, PY)

    /** [total] as a percentage of [invested]; null for sold positions and unknown totals. */
    val totalPercent: BigDecimal? get() = total?.takeIf { invested.signum() > 0 }?.divide(invested, PY)?.multiply(HUNDRED, PY)
}

/** An approximate USD amount; [isComplete] is false when a price, rate or gain had to be left out. */
data class UsdAmount(val amount: BigDecimal, val isComplete: Boolean)

/** The main-page summary cards, all in approximate USD. */
data class PortfolioSummary(
    val value: UsdAmount,
    val invested: UsdAmount,
    val totalProfit: UsdAmount,
    val totalProfitPercent: BigDecimal?,
    val dividends12Months: UsdAmount,
)

object Performance {
    /**
     * Held positions in [rows] order, then fully sold ones (by ticker). Gains from sales and
     * dividends are matched by ticker and currency; when one ticker has several held rows in a
     * currency (an ISIN change), they go to the first of them only, so nothing counts twice.
     */
    fun positions(rows: List<PositionRow>, realized: List<RealizedSale>, dividends: List<CashEvent>): List<PositionPerformance> {
        val sales = realized.groupBy { it.ticker to it.currency }
        val paid = dividends.groupBy { it.ticker to it.currency }
        val claimed = mutableSetOf<Pair<String, String>>()

        val held = rows.map { row ->
            val key = row.ticker to row.currency
            val first = claimed.add(key)
            performance(row.ticker, row.currency, row, row.cost, row.unrealizedPnl, if (first) sales[key] else null, if (first) paid[key] else null)
        }
        val sold = (sales.keys + paid.keys).filter { it !in claimed }.sortedWith(compareBy({ it.first }, { it.second })).map { key ->
            performance(key.first, key.second, null, BigDecimal.ZERO, BigDecimal.ZERO, sales[key], paid[key])
        }
        return held + sold
    }

    /**
     * [positions] must come from [positions] with every row, so gains and dividends of sold
     * positions are included whether or not the user shows them.
     */
    fun summary(positions: List<PositionPerformance>, dividends: List<CashEvent>, rates: FxRates?, today: LocalDate): PortfolioSummary {
        val value = UsdAccumulator(rates)
        val invested = UsdAccumulator(rates)
        val profit = UsdAccumulator(rates)
        for (position in positions) {
            val row = position.row
            if (row != null) {
                value.add(row.marketValue, row.currency)
                invested.add(position.invested, row.currency)
            }
            // Added part by part, so a missing price leaves out only the price gain.
            profit.add(position.priceGain, position.currency)
            profit.add(position.realizedGain, position.currency)
            profit.add(position.dividends.net, position.currency)
            if (!position.realizedComplete) profit.markIncomplete()
        }
        val since = today.minusMonths(12)
        val recent = UsdAccumulator(rates)
        dividends.filter { event -> event.localDate()?.let { it.isAfter(since) && !it.isAfter(today) } == true }
            .forEach { recent.add(it.amount, it.currency) }

        val investedUsd = invested.result()
        val profitUsd = profit.result()
        return PortfolioSummary(
            value = value.result(),
            invested = investedUsd,
            totalProfit = profitUsd,
            totalProfitPercent = investedUsd.amount.takeIf { it.signum() > 0 }?.let { profitUsd.amount.divide(it, PY).multiply(HUNDRED, PY) },
            dividends12Months = recent.result(),
        )
    }

    private fun performance(
        ticker: String,
        currency: String,
        row: PositionRow?,
        invested: BigDecimal,
        priceGain: BigDecimal?,
        sales: List<RealizedSale>?,
        dividends: List<CashEvent>?,
    ) = PositionPerformance(
        ticker = ticker,
        currency = currency,
        row = row,
        invested = invested,
        priceGain = priceGain,
        realizedGain = sales.orEmpty().mapNotNull { it.gain }.fold(BigDecimal.ZERO) { sum, gain -> sum.add(gain, PY) },
        realizedComplete = sales.orEmpty().all { it.gain != null },
        dividends = dividends.orEmpty().map(DividendAmounts::of).fold(DividendAmounts(), DividendAmounts::plus),
    )

    private fun CashEvent.localDate(): LocalDate? = runCatching { LocalDate.parse(date.take(10)) }.getOrNull()

    /** Adds amounts in USD; a missing amount or rate marks the result incomplete instead of guessing. */
    private class UsdAccumulator(private val rates: FxRates?) {
        private var sum = BigDecimal.ZERO
        private var complete = true

        fun add(amount: BigDecimal?, currency: String) {
            val usd = amount?.let { if (currency == "USD") it else rates?.toUsd(it, currency) }
            if (usd == null) complete = false else sum = sum.add(usd, PY)
        }

        fun markIncomplete() {
            complete = false
        }

        fun result() = UsdAmount(sum, complete)
    }
}

private val PY = MathContext(28, RoundingMode.HALF_EVEN)
private val HUNDRED = BigDecimal(100)
