package com.ibkrtax.mobile.portfolio

import com.ibkrtax.mobile.prices.PriceFreshness

/**
 * Today's largest moves among held positions (mobile-top-movers spec), in each position's own
 * currency. A position with no change is in neither list, so the lists never overlap.
 */
data class TopMovers(
    /** Largest rise first. */
    val gainers: List<PositionRow>,
    /** Largest fall first. */
    val losers: List<PositionRow>,
    /** The worst freshness among the ranked prices: STALE, then LATEST_CLOSE, then DELAYED, then LIVE. */
    val freshness: PriceFreshness,
)

object TopMoversRanking {
    const val MAX_PER_LIST = 5

    /** Null when no position has a price with a previous close, so the section is hidden. */
    fun rank(rows: List<PositionRow>): TopMovers? {
        val ranked = rows.filter { it.dailyChangePercent != null && it.price != null }
        if (ranked.isEmpty()) return null
        val gainers = ranked.filter { it.dailyChangePercent!!.signum() > 0 }
            .sortedWith(compareByDescending<PositionRow> { it.dailyChangePercent }.thenBy { it.ticker })
            .take(MAX_PER_LIST)
        val losers = ranked.filter { it.dailyChangePercent!!.signum() < 0 }
            .sortedWith(compareBy<PositionRow> { it.dailyChangePercent }.thenBy { it.ticker })
            .take(MAX_PER_LIST)
        val freshness = ranked.map { it.price!!.freshness }.let { all ->
            when {
                PriceFreshness.STALE in all -> PriceFreshness.STALE
                PriceFreshness.LATEST_CLOSE in all -> PriceFreshness.LATEST_CLOSE
                PriceFreshness.DELAYED in all -> PriceFreshness.DELAYED
                else -> PriceFreshness.LIVE
            }
        }
        return TopMovers(gainers, losers, freshness)
    }
}
