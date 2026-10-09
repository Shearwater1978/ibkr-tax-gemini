package com.ibkrtax.mobile.prices

import java.math.BigDecimal
import java.time.Instant

/**
 * Replaceable market-data adapter. Requests carry ISINs only: never quantities,
 * account data, names, or report contents (mobile-market-prices spec).
 */
interface MarketDataProvider {
    suspend fun latestQuotes(isins: Set<String>): PriceResult
}

data class Quote(
    val isin: String,
    val price: BigDecimal,
    val currency: String,
    val retrievedAt: Instant,
    /** True when the quote is the prior market close rather than a live price. */
    val isClose: Boolean,
)

sealed interface PriceResult {
    data class Success(val quotes: Map<String, Quote>) : PriceResult

    data class Failure(val reason: PriceFailure) : PriceResult
}

enum class PriceFailure {
    OFFLINE,
    PROVIDER_ERROR,
    RATE_LIMITED,
}
