package com.ibkrtax.mobile.prices

import com.ibkrtax.mobile.portfolio.Holding
import java.time.Clock

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

    data class Failed(val reason: PriceFailure) : RefreshOutcome
}

/** MVP price scope (mobile-market-prices spec): USD holdings, treated as US listings. */
object PriceScope {
    fun includes(holding: Holding): Boolean = holding.currency == "USD"
}

class PriceService(
    private val keys: ApiKeyStore,
    private val cache: QuoteCache,
    private val providerFor: (apiKey: String) -> MarketDataProvider,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun refresh(holdings: List<Holding>): RefreshOutcome {
        val key = keys.get() ?: return RefreshOutcome.NoKey
        val symbols = symbolsInScope(holdings)
        if (symbols.isEmpty()) return RefreshOutcome.Updated
        return when (val result = providerFor(key).latestQuotes(symbols)) {
            is PriceResult.Success -> {
                cache.putAll(result.quotes.values)
                RefreshOutcome.Updated
            }
            is PriceResult.Failure -> RefreshOutcome.Failed(result.reason)
        }
    }

    /** Cached prices for in-scope holdings; after a failed refresh every cached price is stale. */
    fun prices(holdings: List<Holding>, lastRefresh: RefreshOutcome?): Map<String, PricedQuote> {
        val now = clock.instant()
        val failed = lastRefresh is RefreshOutcome.Failed
        return cache.get(symbolsInScope(holdings)).mapValues { (_, quote) ->
            PricedQuote(quote, PriceFreshnessRules.classify(quote.quoteTime, now, failed))
        }
    }

    private fun symbolsInScope(holdings: List<Holding>): Set<String> =
        holdings.filter(PriceScope::includes).map { it.ticker }.toSet()
}
