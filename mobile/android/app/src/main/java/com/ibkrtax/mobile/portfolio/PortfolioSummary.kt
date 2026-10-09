package com.ibkrtax.mobile.portfolio

import com.ibkrtax.mobile.prices.Quote
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** A holding with its cost and, when a same-currency quote exists, its market value. */
data class HoldingValue(
    val holding: Holding,
    val cost: BigDecimal,
    val quote: Quote?,
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
    /** Sum of the values that are available; see [missingPrices]. */
    val marketValue: BigDecimal,
    val unrealizedGain: BigDecimal,
    val missingPrices: Int,
) {
    val isComplete: Boolean get() = missingPrices == 0
}

object PortfolioSummary {
    private val PY = MathContext(28, RoundingMode.HALF_EVEN)

    /** [quotes] are keyed by ISIN. A quote in another currency counts as unavailable: no FX conversion. */
    fun build(holdings: List<Holding>, quotes: Map<String, Quote>): List<CurrencySubtotal> =
        holdings.groupBy { it.currency }
            .toSortedMap()
            .map { (currency, group) ->
                val values = group.map { value(it, quotes[it.isin]?.takeIf { quote -> quote.currency == currency }) }
                val priced = values.filter { it.marketValue != null }
                CurrencySubtotal(
                    currency = currency,
                    holdings = values,
                    cost = values.sumOf { it.cost },
                    marketValue = priced.sumOf { it.marketValue!! },
                    unrealizedGain = priced.sumOf { it.unrealizedGain!! },
                    missingPrices = values.size - priced.size,
                )
            }

    private fun value(holding: Holding, quote: Quote?): HoldingValue {
        val cost = holding.lots.fold(BigDecimal.ZERO) { sum, lot -> sum.add(lot.quantity.multiply(lot.price, PY), PY) }
        val marketValue = quote?.price?.multiply(holding.quantity, PY)
        return HoldingValue(holding, cost, quote, marketValue, marketValue?.subtract(cost, PY))
    }
}
