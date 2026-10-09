package com.ibkrtax.mobile.prices

import java.math.BigDecimal
import java.time.Instant

/**
 * Replaceable market-data adapter. Requests carry only instrument symbols (and the
 * user's provider key): never quantities, account data, names, or report contents
 * (mobile-market-prices spec).
 */
interface MarketDataProvider {
    suspend fun latestQuotes(symbols: Set<String>): PriceResult
}

data class Quote(
    val symbol: String,
    val price: BigDecimal,
    val currency: String,
    /** When the provider says the price was set (last trade or close). */
    val quoteTime: Instant,
    /** When this app retrieved it. */
    val retrievedAt: Instant,
    /** Previous session's close, for the daily change; null when the provider has none. */
    val previousClose: BigDecimal? = null,
) {
    val dailyChange: BigDecimal? get() = previousClose?.let { price.subtract(it) }
}

sealed interface PriceResult {
    /** Symbols the provider does not know are simply absent from [quotes]. */
    data class Success(val quotes: Map<String, Quote>) : PriceResult

    data class Failure(val reason: PriceFailure) : PriceResult
}

enum class PriceFailure {
    OFFLINE,
    PROVIDER_ERROR,
    RATE_LIMITED,
    INVALID_KEY,
}
