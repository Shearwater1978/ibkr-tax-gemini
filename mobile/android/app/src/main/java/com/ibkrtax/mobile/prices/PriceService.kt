package com.ibkrtax.mobile.prices

import com.ibkrtax.mobile.portfolio.Holding
import java.math.BigDecimal
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
    /** The chosen provider is None: nothing was requested from any provider. */
    data object NoProvider : RefreshOutcome

    /** The chosen provider has no key: nothing was requested (no other provider is asked). */
    data object NoKey : RefreshOutcome

    data object Updated : RefreshOutcome

    /**
     * Quotes retrieved before [startedAt] are stale; quotes received before the failure are not.
     * [source] is the provider that failed, so the message can name it (e.g. the Twelve Data daily limit).
     */
    data class Failed(val reason: PriceFailure, val startedAt: Instant, val source: PriceSource? = null) : RefreshOutcome
}

/** Where a price comes from. Yahoo Finance is a temporary internal source for European listings only. */
enum class PriceSource {
    FINNHUB,
    TWELVE_DATA,
    YAHOO,
    NONE,
}

/**
 * How far a running refresh has got: [done] of [total] symbols requested from [source];
 * [minutesLeft] is an estimate for slow quotas (Twelve Data's free plan), else null.
 */
data class RefreshProgress(val done: Int, val total: Int, val source: PriceSource, val minutesLeft: Int? = null)

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

/**
 * Prices by the provider the user chose (mobile-market-prices "Choose a price provider", "Never
 * switch providers automatically"): US listings only from the chosen provider when it has a key;
 * European listings from [europe] (Yahoo Finance, temporary and internal) only while a provider is
 * chosen and keyed; with None, no request at all. A failure never moves symbols to another provider.
 */
class PriceService(
    private val keys: ApiKeyStore,
    private val cache: QuoteCache,
    private val providerFor: (PriceProviderId, apiKey: String) -> MarketDataProvider,
    private val clock: Clock = Clock.systemUTC(),
    private val europe: MarketDataProvider? = null,
) {
    /** The chosen provider as a source, or NONE when nothing is chosen. */
    fun source(): PriceSource = keys.selected().toSource()

    /** The chosen provider and whether its key is set, for Settings and status lines. */
    fun choice(): Pair<PriceProviderId, Boolean> = keys.selected().let { it to (keys.get(it) != null) }

    /**
     * [exchanges] maps (ticker, ISIN) to the IBKR listing exchange. US listings go to the chosen
     * provider, European ones to Yahoo; a failure in one part does not stop the other. Symbols are
     * requested largest position first, so a cut-off refresh prices the biggest holdings.
     */
    suspend fun refresh(
        holdings: List<Holding>,
        exchanges: Map<Pair<String, String>, String> = emptyMap(),
        onProgress: (RefreshProgress) -> Unit = {},
    ): RefreshOutcome {
        val chosen = keys.selected()
        if (chosen == PriceProviderId.NONE) return RefreshOutcome.NoProvider
        val key = keys.get(chosen) ?: return RefreshOutcome.NoKey
        val usProvider = providerFor(chosen, key)
        val usSource = chosen.toSource()
        val startedAt = clock.instant()
        val ordered = symbolsByPriority(holdings, exchanges)
        val cached = cache.get(ordered.toSet())
        val (european, us) = ordered.filter { needsRequest(cached[it], startedAt, intervalFor(it, chosen)) }.partition(Markets::isEuropean)
        val europeRequest = if (europe != null) european else emptyList()
        val total = us.size + europeRequest.size
        if (total == 0) return RefreshOutcome.Updated

        var failure: Pair<PriceFailure, PriceSource>? = null
        fun keep(result: PriceResult, source: PriceSource) {
            when (result) {
                // Each cached quote records the provider that produced it, so the UI can name it.
                is PriceResult.Success -> cache.putAll(result.quotes.values.map { it.copy(source = source) })
                is PriceResult.Failure -> {
                    cache.putAll(result.partial.values.map { it.copy(source = source) })
                    if (failure == null) failure = result.reason to source
                }
            }
        }
        fun minutesLeft(done: Int): Int? =
            if (chosen == PriceProviderId.TWELVE_DATA) (us.size - done).coerceAtLeast(0).let { (it + TWELVE_DATA_PER_MINUTE - 1) / TWELVE_DATA_PER_MINUTE } else null

        onProgress(RefreshProgress(0, total, if (us.isNotEmpty()) usSource else PriceSource.YAHOO, if (us.isNotEmpty()) minutesLeft(0) else null))
        if (us.isNotEmpty()) {
            keep(usProvider.latestQuotes(us.toCollection(LinkedHashSet())) { onProgress(RefreshProgress(it, total, usSource, minutesLeft(it))) }, usSource)
        }
        if (europeRequest.isNotEmpty()) {
            keep(europe!!.latestQuotes(europeRequest.toCollection(LinkedHashSet())) { onProgress(RefreshProgress(us.size + it, total, PriceSource.YAHOO)) }, PriceSource.YAHOO)
        }
        return failure?.let { (reason, source) -> RefreshOutcome.Failed(reason, startedAt, source) } ?: RefreshOutcome.Updated
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

    /** Saves the provider quota: skip recently fetched quotes and closing prices while the market is closed. */
    private fun needsRequest(cached: Quote?, now: Instant, minInterval: Duration): Boolean {
        if (cached == null) return true
        if (Duration.between(cached.retrievedAt, now) < minInterval) return false
        val market = Markets.forSymbol(cached.symbol)
        val freshness = PriceFreshnessRules.classify(cached.quoteTime, now, lastRefreshFailed = false, market = market)
        return !(freshness == PriceFreshness.LATEST_CLOSE && !market.isOpen(now))
    }

    private fun intervalFor(symbol: String, chosen: PriceProviderId): Duration =
        if (!Markets.isEuropean(symbol) && chosen == PriceProviderId.TWELVE_DATA) TWELVE_DATA_INTERVAL else MIN_REQUEST_INTERVAL

    /** In-scope symbols, largest position first: by cached value when known, else by cost. */
    private fun symbolsByPriority(holdings: List<Holding>, exchanges: Map<Pair<String, String>, String>): List<String> {
        val symbolFor = holdings.associateWith { PriceScope.symbol(it, exchanges[it.ticker to it.isin]) }
        val cached = cache.get(symbolFor.values.filterNotNull().toSet())
        val weight = mutableMapOf<String, BigDecimal>()
        for ((holding, symbol) in symbolFor) {
            symbol ?: continue
            val price = cached[symbol]?.price ?: holding.averagePrice
            weight.merge(symbol, holding.quantity.abs().multiply(price), BigDecimal::add)
        }
        return weight.entries.sortedWith(compareByDescending<Map.Entry<String, BigDecimal>> { it.value }.thenBy { it.key }).map { it.key }
    }

    private fun symbolsInScope(holdings: List<Holding>, exchanges: Map<Pair<String, String>, String>): Set<String> =
        holdings.mapNotNull { PriceScope.symbol(it, exchanges[it.ticker to it.isin]) }.toSet()

    private companion object {
        val MIN_REQUEST_INTERVAL: Duration = Duration.ofMinutes(1)

        /** Twelve Data's free plan has 800 credits a day; a cached quote is not re-requested for 15 minutes. */
        val TWELVE_DATA_INTERVAL: Duration = Duration.ofMinutes(15)
        const val TWELVE_DATA_PER_MINUTE = RateLimiter.TWELVE_DATA_FREE_CREDITS
    }
}

private fun PriceProviderId.toSource(): PriceSource = when (this) {
    PriceProviderId.NONE -> PriceSource.NONE
    PriceProviderId.FINNHUB -> PriceSource.FINNHUB
    PriceProviderId.TWELVE_DATA -> PriceSource.TWELVE_DATA
}
