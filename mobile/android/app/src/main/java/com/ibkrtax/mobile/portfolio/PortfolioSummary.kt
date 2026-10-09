package com.ibkrtax.mobile.portfolio

import com.ibkrtax.mobile.prices.PriceFreshness
import com.ibkrtax.mobile.prices.PriceScope
import com.ibkrtax.mobile.prices.PricedQuote
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** A holding with its cost and, when a same-currency quote exists, its market value. */
data class HoldingValue(
    val holding: Holding,
    val cost: BigDecimal,
    /** False when the MVP price scope does not cover this holding (no request is made). */
    val inPriceScope: Boolean,
    val price: PricedQuote?,
    val marketValue: BigDecimal?,
    val unrealizedGain: BigDecimal?,
)

/**
 * Totals for one currency. There is deliberately no cross-currency total anywhere:
 * the MVP shows no converted grand total (mobile-portfolio-overview spec).
 */
data class CurrencySubtotal(
    val currency: String,
    val holdings: List<HoldingValue>,
    val cost: BigDecimal,
    /** Sum of the values that are available; see [missingPrices] and [stalePrices]. */
    val marketValue: BigDecimal,
    val unrealizedGain: BigDecimal,
    val missingPrices: Int,
    val stalePrices: Int,
) {
    val isComplete: Boolean get() = missingPrices == 0
}

object PortfolioSummary {
    private val PY = MathContext(28, RoundingMode.HALF_EVEN)

    /** [prices] are keyed by ticker. A quote in another currency counts as unavailable: no FX conversion. */
    fun build(holdings: List<Holding>, prices: Map<String, PricedQuote>): List<CurrencySubtotal> =
        holdings.groupBy { it.currency }
            .toSortedMap()
            .map { (currency, group) ->
                val values = group.map { holding ->
                    val inScope = PriceScope.includes(holding)
                    val price = prices[holding.ticker]?.takeIf { inScope && it.quote.currency == currency }
                    value(holding, inScope, price)
                }
                val priced = values.filter { it.marketValue != null }
                CurrencySubtotal(
                    currency = currency,
                    holdings = values,
                    cost = values.sumOf { it.cost },
                    marketValue = priced.sumOf { it.marketValue!! },
                    unrealizedGain = priced.sumOf { it.unrealizedGain!! },
                    missingPrices = values.size - priced.size,
                    stalePrices = priced.count { it.price?.freshness == PriceFreshness.STALE },
                )
            }

    private fun value(holding: Holding, inScope: Boolean, price: PricedQuote?): HoldingValue {
        val cost = holding.lots.fold(BigDecimal.ZERO) { sum, lot -> sum.add(lot.quantity.multiply(lot.price, PY), PY) }
        val marketValue = price?.quote?.price?.multiply(holding.quantity, PY)
        return HoldingValue(holding, cost, inScope, price, marketValue, marketValue?.subtract(cost, PY))
    }
}
