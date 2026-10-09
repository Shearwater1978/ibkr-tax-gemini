package com.ibkrtax.mobile.portfolio

import com.ibkrtax.mobile.fx.FxRates
import com.ibkrtax.mobile.prices.PriceFreshness
import com.ibkrtax.mobile.prices.PriceScope
import com.ibkrtax.mobile.prices.PricedQuote
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** One row of the positions table. Every amount is in the row's own [currency]. */
data class PositionRow(
    val holding: Holding,
    val listingExchange: String,
    /** False when the MVP price scope does not cover this holding (no request is made). */
    val inPriceScope: Boolean,
    val price: PricedQuote?,
    val cost: BigDecimal,
    val marketValue: BigDecimal?,
    val dailyChange: BigDecimal?,
    val dailyChangePercent: BigDecimal?,
    /** Quantity × change since the previous close. */
    val dailyPnl: BigDecimal?,
    /** Market value minus FIFO cost. */
    val unrealizedPnl: BigDecimal?,
) {
    val ticker: String get() = holding.ticker
    val currency: String get() = holding.currency
    val isStale: Boolean get() = price?.freshness == PriceFreshness.STALE
}

/**
 * Approximate USD header (mobile-portfolio-overview). Positions whose price or NBP rate is
 * missing are left out of the sums and counted, so the UI can mark the total incomplete.
 */
data class UsdTotals(
    val marketValue: BigDecimal,
    val dailyPnl: BigDecimal,
    val dailyPnlPercent: BigDecimal?,
    val missingValues: Int,
    val missingDailyPnl: Int,
    /** NBP table date, when any non-USD amount was converted. */
    val rateDate: String?,
) {
    val isComplete: Boolean get() = missingValues == 0
}

enum class PnlMode { DAILY, UNREALIZED }

enum class SortColumn { SYMBOL, LAST_PRICE, CHANGE, POSITION, PNL }

data class SortOrder(val column: SortColumn = SortColumn.SYMBOL, val descending: Boolean = false) {
    /** Selecting the active column reverses it; another column starts ascending. */
    fun select(column: SortColumn) = if (column == this.column) copy(descending = !descending) else SortOrder(column)
}

object MainPage {
    private val PY = MathContext(28, RoundingMode.HALF_EVEN)
    private val HUNDRED = BigDecimal(100)

    /** [prices] are keyed by ticker; [exchanges] by (ticker, ISIN). */
    fun rows(
        holdings: List<Holding>,
        prices: Map<String, PricedQuote>,
        exchanges: Map<Pair<String, String>, String> = emptyMap(),
    ): List<PositionRow> = holdings.map { holding ->
        val inScope = PriceScope.includes(holding)
        val price = prices[holding.ticker]?.takeIf { inScope && it.quote.currency == holding.currency }
        val cost = holding.lots.fold(BigDecimal.ZERO) { sum, lot -> sum.add(lot.quantity.multiply(lot.price, PY), PY) }
        val marketValue = price?.quote?.price?.multiply(holding.quantity, PY)
        val change = price?.quote?.dailyChange
        val previousClose = price?.quote?.previousClose
        PositionRow(
            holding = holding,
            listingExchange = exchanges[holding.ticker to holding.isin].orEmpty(),
            inPriceScope = inScope,
            price = price,
            cost = cost,
            marketValue = marketValue,
            dailyChange = change,
            dailyChangePercent = if (change != null && previousClose != null) change.divide(previousClose, PY).multiply(HUNDRED, PY) else null,
            dailyPnl = change?.multiply(holding.quantity, PY),
            unrealizedPnl = marketValue?.subtract(cost, PY),
        )
    }

    fun usdTotals(rows: List<PositionRow>, rates: FxRates?): UsdTotals {
        fun toUsd(amount: BigDecimal, currency: String): BigDecimal? =
            if (currency == "USD") amount else rates?.toUsd(amount, currency)

        var value = BigDecimal.ZERO
        var daily = BigDecimal.ZERO
        var previousValue = BigDecimal.ZERO
        var missingValues = 0
        var missingDaily = 0
        var converted = false
        for (row in rows) {
            val usdValue = row.marketValue?.let { toUsd(it, row.currency) }
            if (usdValue == null) missingValues++ else value = value.add(usdValue, PY)

            val usdDaily = row.dailyPnl?.let { toUsd(it, row.currency) }
            if (usdDaily == null || usdValue == null) {
                missingDaily++
            } else {
                daily = daily.add(usdDaily, PY)
                previousValue = previousValue.add(usdValue.subtract(usdDaily, PY), PY)
            }
            if (row.currency != "USD" && usdValue != null) converted = true
        }
        return UsdTotals(
            marketValue = value,
            dailyPnl = daily,
            dailyPnlPercent = if (previousValue.signum() > 0) daily.divide(previousValue, PY).multiply(HUNDRED, PY) else null,
            missingValues = missingValues,
            missingDailyPnl = missingDaily,
            rateDate = rates?.effectiveDate?.takeIf { converted },
        )
    }

    /** Rows without a value for the sort column always go last, whatever the direction. */
    fun sorted(rows: List<PositionRow>, order: SortOrder, mode: PnlMode): List<PositionRow> {
        val bySymbol = compareBy<PositionRow>({ it.ticker }, { it.currency }, { it.holding.isin })
        if (order.column == SortColumn.SYMBOL) return rows.sortedWith(if (order.descending) bySymbol.reversed() else bySymbol)

        val key: (PositionRow) -> BigDecimal? = when (order.column) {
            SortColumn.LAST_PRICE -> { row -> row.price?.quote?.price }
            SortColumn.CHANGE -> { row -> row.dailyChangePercent }
            SortColumn.POSITION -> { row -> row.holding.quantity }
            SortColumn.PNL -> { row -> if (mode == PnlMode.DAILY) row.dailyPnl else row.unrealizedPnl }
            SortColumn.SYMBOL -> error("handled above")
        }
        val (present, missing) = rows.partition { key(it) != null }
        val byKey = compareBy<PositionRow> { key(it)!! }.thenComparing(bySymbol)
        return present.sortedWith(if (order.descending) byKey.reversed() else byKey) + missing.sortedWith(bySymbol)
    }
}
