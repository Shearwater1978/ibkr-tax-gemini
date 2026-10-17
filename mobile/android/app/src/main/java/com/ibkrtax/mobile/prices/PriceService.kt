package com.ibkrtax.mobile.prices

import com.ibkrtax.mobile.portfolio.Holding
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The user's own key per provider and the chosen provider; implementations must keep keys in
 * encrypted storage only. [PriceProviderId.NONE] has no key.
 */
interface ApiKeyStore {
    fun get(provider: PriceProviderId): String?

    fun set(provider: PriceProviderId, key: String)

    fun clear(provider: PriceProviderId)

    /** The stored choice, or null when the user never chose (see [selected]). */
    fun storedChoice(): PriceProviderId?

    fun choose(provider: PriceProviderId)
}

/** The chosen provider; without a stored choice, Finnhub if its key exists, else None. */
fun ApiKeyStore.selected(): PriceProviderId =
    storedChoice() ?: PriceProviderId.default(hasFinnhubKey = get(PriceProviderId.FINNHUB) != null)

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

/**
 * Price scope (mobile-market-prices spec): holdings on a supported European exchange by their
 * Yahoo symbol (e.g. SAP.DE), any other USD holding as a US listing by its ticker.
 */
object PriceScope {
    /** The symbol a holding's price is requested and cached under, or null when it is out of scope. */
    fun symbol(holding: Holding, exchange: String?): String? {
        Markets.european(exchange.orEmpty())?.let { return Markets.europeanSymbol(holding.ticker, it) }
        return holding.ticker.takeIf { holding.currency == "USD" }
    }
}

class PriceService(
    private val keys: ApiKeyStore,
    private val cache: QuoteCache,
    private val providerFor: (apiKey: String) -> MarketDataProvider,
    private val clock: Clock = Clock.systemUTC(),
    /** Used for US listings while no key is set (mobile-market-prices spec). */
    private val fallback: MarketDataProvider? = null,
    /** Used for European listings, with or without a key (Finnhub's free plan has none). */
    private val europe: MarketDataProvider? = null,
) {
    fun source(): PriceSource =
        when {
            keys.get(PriceProviderId.FINNHUB) != null -> PriceSource.FINNHUB
            fallback != null -> PriceSource.YAHOO
            else -> PriceSource.NONE
        }

    /**
     * [exchanges] maps (ticker, ISIN) to the IBKR listing exchange. US listings go to Finnhub (or
     * the Yahoo fallback), European ones to Yahoo; a failure in one part does not stop the other.
     */
    suspend fun refresh(
        holdings: List<Holding>,
        exchanges: Map<Pair<String, String>, String> = emptyMap(),
        onProgress: (RefreshProgress) -> Unit = {},
    ): RefreshOutcome {
        val key = keys.get(PriceProviderId.FINNHUB)
        val usProvider = key?.let(providerFor) ?: fallback
        if (usProvider == null && europe == null) return RefreshOutcome.NoKey
        val usSource = if (key != null) PriceSource.FINNHUB else PriceSource.YAHOO
        val startedAt = clock.instant()
        val inScope = symbolsInScope(holdings, exchanges)
        val cached = cache.get(inScope)
        val (european, us) = inScope.filter { needsRequest(cached[it], startedAt) }.partition(Markets::isEuropean)
        val usRequest = if (usProvider != null) us.toSet() else emptySet()
        val europeRequest = if (europe != null) european.toSet() else emptySet()
        val total = usRequest.size + europeRequest.size
        if (total == 0) return RefreshOutcome.Updated

        var failure: PriceFailure? = null
        fun keep(result: PriceResult) {
            when (result) {
                is PriceResult.Success -> cache.putAll(result.quotes.values)
                is PriceResult.Failure -> {
                    cache.putAll(result.partial.values)
                    if (failure == null) failure = result.reason
                }
            }
        }
        onProgress(RefreshProgress(0, total, if (usRequest.isNotEmpty()) usSource else PriceSource.YAHOO))
        if (usRequest.isNotEmpty()) {
            keep(usProvider!!.latestQuotes(usRequest) { onProgress(RefreshProgress(it, total, usSource)) })
        }
        if (europeRequest.isNotEmpty()) {
            keep(europe!!.latestQuotes(europeRequest) { onProgress(RefreshProgress(usRequest.size + it, total, PriceSource.YAHOO)) })
        }
        return failure?.let { RefreshOutcome.Failed(it, startedAt) } ?: RefreshOutcome.Updated
    }

    /** Cached prices keyed by request symbol; after a failed refresh, quotes it did not update are stale. */
    fun prices(
        holdings: List<Holding>,
        lastRefresh: RefreshOutcome?,
        exchanges: Map<Pair<String, String>, String> = emptyMap(),
    ): Map<String, PricedQuote> {
        val now = clock.instant()
        val failedSince = (lastRefresh as? RefreshOutcome.Failed)?.startedAt
        return cache.get(symbolsInScope(holdings, exchanges)).mapValues { (_, quote) ->
            val notUpdated = failedSince != null && quote.retrievedAt.isBefore(failedSince)
            PricedQuote(quote, PriceFreshnessRules.classify(quote.quoteTime, now, notUpdated, Markets.forSymbol(quote.symbol)))
        }
    }

    /** Saves the provider quota: skip just-fetched quotes and closing prices while the market is closed. */
    private fun needsRequest(cached: Quote?, now: Instant): Boolean {
        if (cached == null) return true
        if (Duration.between(cached.retrievedAt, now) < MIN_REQUEST_INTERVAL) return false
        val market = Markets.forSymbol(cached.symbol)
        val freshness = PriceFreshnessRules.classify(cached.quoteTime, now, lastRefreshFailed = false, market = market)
        return !(freshness == PriceFreshness.LATEST_CLOSE && !market.isOpen(now))
    }

    private companion object {
        val MIN_REQUEST_INTERVAL: Duration = Duration.ofMinutes(1)
    }

    private fun symbolsInScope(holdings: List<Holding>, exchanges: Map<Pair<String, String>, String>): Set<String> =
        holdings.mapNotNull { PriceScope.symbol(it, exchanges[it.ticker to it.isin]) }.toSet()
}
