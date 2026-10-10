package com.ibkrtax.mobile.prices

import com.ibkrtax.mobile.portfolio.Holding
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** The user's provider API key; implementations must keep it in encrypted storage only. */
interface ApiKeyStore {
    fun get(): String?

    fun set(key: String)

    fun clear()
}

/** Last retrieved quote per symbol, with its timestamps. */
interface QuoteCache {
    fun get(symbols: Set<String>): Map<String, Quote>

    fun putAll(quotes: Collection<Quote>)
}

/** A cached quote and how it may be presented right now. */
data class PricedQuote(val quote: Quote, val freshness: PriceFreshness)

sealed interface RefreshOutcome {
    /** No provider key configured: nothing was requested. */
    data object NoKey : RefreshOutcome

    data object Updated : RefreshOutcome

    /** Quotes retrieved before [startedAt] are stale; quotes received before the failure are not. */
    data class Failed(val reason: PriceFailure, val startedAt: Instant) : RefreshOutcome
}

/** Where prices come from right now. */
enum class PriceSource {
    FINNHUB,
    YAHOO,
    NONE,
}

/** How far a running refresh has got: [done] of [total] symbols requested from [source]. */
data class RefreshProgress(val done: Int, val total: Int, val source: PriceSource)

/** MVP price scope (mobile-market-prices spec): USD holdings, treated as US listings. */
object PriceScope {
    fun includes(holding: Holding): Boolean = holding.currency == "USD"
}

class PriceService(
    private val keys: ApiKeyStore,
    private val cache: QuoteCache,
    private val providerFor: (apiKey: String) -> MarketDataProvider,
    private val clock: Clock = Clock.systemUTC(),
    /** Used while no key is set (mobile-market-prices spec); without it nothing is requested. */
    private val fallback: MarketDataProvider? = null,
) {
    fun source(): PriceSource =
        when {
            keys.get() != null -> PriceSource.FINNHUB
            fallback != null -> PriceSource.YAHOO
            else -> PriceSource.NONE
        }

    suspend fun refresh(holdings: List<Holding>, onProgress: (RefreshProgress) -> Unit = {}): RefreshOutcome {
        val key = keys.get()
        val provider = key?.let(providerFor) ?: fallback ?: return RefreshOutcome.NoKey
        val source = if (key != null) PriceSource.FINNHUB else PriceSource.YAHOO
        val startedAt = clock.instant()
        val inScope = symbolsInScope(holdings)
        val cached = cache.get(inScope)
        val symbols = inScope.filter { needsRequest(cached[it], startedAt) }.toSet()
        if (symbols.isEmpty()) return RefreshOutcome.Updated
        onProgress(RefreshProgress(0, symbols.size, source))
        val result = provider.latestQuotes(symbols) { onProgress(RefreshProgress(it, symbols.size, source)) }
        return when (result) {
            is PriceResult.Success -> {
                cache.putAll(result.quotes.values)
                RefreshOutcome.Updated
            }
            is PriceResult.Failure -> {
                cache.putAll(result.partial.values)
                RefreshOutcome.Failed(result.reason, startedAt)
            }
        }
    }

    /** Cached prices for in-scope holdings; after a failed refresh, quotes it did not update are stale. */
    fun prices(holdings: List<Holding>, lastRefresh: RefreshOutcome?): Map<String, PricedQuote> {
        val now = clock.instant()
        val failedSince = (lastRefresh as? RefreshOutcome.Failed)?.startedAt
        return cache.get(symbolsInScope(holdings)).mapValues { (_, quote) ->
            val notUpdated = failedSince != null && quote.retrievedAt.isBefore(failedSince)
            PricedQuote(quote, PriceFreshnessRules.classify(quote.quoteTime, now, notUpdated))
        }
    }

    /** Saves the provider quota: skip just-fetched quotes and closing prices while the market is closed. */
    private fun needsRequest(cached: Quote?, now: Instant): Boolean {
        if (cached == null) return true
        if (Duration.between(cached.retrievedAt, now) < MIN_REQUEST_INTERVAL) return false
        val freshness = PriceFreshnessRules.classify(cached.quoteTime, now, lastRefreshFailed = false)
        return !(freshness == PriceFreshness.LATEST_CLOSE && !UsMarketHours.isOpen(now))
    }

    private companion object {
        val MIN_REQUEST_INTERVAL: Duration = Duration.ofMinutes(1)
    }

    private fun symbolsInScope(holdings: List<Holding>): Set<String> =
        holdings.filter(PriceScope::includes).map { it.ticker }.toSet()
}
