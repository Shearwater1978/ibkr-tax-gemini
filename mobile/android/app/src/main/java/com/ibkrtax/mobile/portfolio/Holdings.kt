package com.ibkrtax.mobile.portfolio

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * One currency subposition of an instrument (mobile-portfolio-overview spec). Lots in
 * different currencies are never averaged together.
 */
data class Holding(
    val ticker: String,
    val isin: String,
    val currency: String,
    val quantity: BigDecimal,
    /** Quantity-weighted lot price, excluding commissions (like the desktop `cost_per_share`). */
    val averagePrice: BigDecimal,
    val lots: List<OpenLot>,
)

object Holdings {
    private val PY = MathContext(28, RoundingMode.HALF_EVEN)
    private val EPSILON = BigDecimal("0.00000001")

    fun fromLots(lots: List<OpenLot>): List<Holding> =
        lots.groupBy { Triple(it.ticker, it.isin, it.currency) }
            .mapNotNull { (key, group) ->
                val quantity = group.fold(BigDecimal.ZERO) { sum, lot -> sum.add(lot.quantity, PY) }
                // FIFO can leave dust below the matcher tolerance; that is not a holding.
                if (quantity <= EPSILON) return@mapNotNull null
                val cost = group.fold(BigDecimal.ZERO) { sum, lot -> sum.add(lot.quantity.multiply(lot.price, PY), PY) }
                Holding(
                    ticker = key.first,
                    isin = key.second,
                    currency = key.third,
                    quantity = quantity,
                    averagePrice = cost.divide(quantity, PY),
                    lots = group,
                )
            }
            .sortedWith(compareBy({ it.ticker }, { it.currency }, { it.isin }))
}
