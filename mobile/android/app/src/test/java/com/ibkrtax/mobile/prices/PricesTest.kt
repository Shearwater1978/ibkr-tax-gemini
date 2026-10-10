package com.ibkrtax.mobile.prices

import com.ibkrtax.mobile.portfolio.Holding
import com.ibkrtax.mobile.portfolio.Holdings
import com.ibkrtax.mobile.portfolio.OpenLot
import com.ibkrtax.mobile.testing.FakeMarketDataProvider
import java.io.IOException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PricesTest {
    // 2024-01-03 is a Wednesday; New York is UTC-5 in January.
    private val marketOpen = Instant.parse("2024-01-03T16:00:00Z") // 11:00 New York
    private val afterClose = Instant.parse("2024-01-03T23:00:00Z") // 18:00 New York
    private val saturday = Instant.parse("2024-01-06T16:00:00Z")

    private class RecordingTransport(private val respond: (String) -> HttpResponse) : HttpTransport {
        val calls = mutableListOf<Pair<String, Map<String, String>>>()

        override fun get(url: String, headers: Map<String, String>): HttpResponse {
            calls += url to headers
            return respond(url)
        }
    }

    private fun quoteJson(price: String, epochSeconds: Long) =
        HttpResponse(200, """{"c":$price,"d":1.2,"dp":0.6,"h":191,"l":188,"o":189,"pc":189.5,"t":$epochSeconds}""")

    private fun holding(ticker: String, currency: String) =
        Holdings.fromLots(listOf(OpenLot(ticker, "", "2023-01-01", BigDecimal.ONE, BigDecimal.TEN, currency))).single()

    /** Spark series for [symbol]: an empty bar (null close) after the last trade, as Yahoo sends. */
    private fun sparkSeries(symbol: String, price: String, epochSeconds: Long) =
        """"$symbol":{"symbol":"$symbol","timestamp":[${epochSeconds - 300},$epochSeconds,${epochSeconds + 300}],""" +
            """"close":[189.0,$price,null],"previousClose":189.5,"chartPreviousClose":189.5}"""

    private fun spark(vararg series: String) = HttpResponse(200, series.joinToString(",", "{", "}"))

    // --- Yahoo fallback ---

    @Test
    fun yahooSendsOnlyTheSymbolsAndNoKey() = runBlocking {
        val transport = RecordingTransport { spark(sparkSeries("AAPL", "190.1", marketOpen.epochSecond)) }
        val result = YahooProvider(transport, Clock.fixed(marketOpen, ZoneOffset.UTC)).latestQuotes(setOf("AAPL"))

        val (url, headers) = transport.calls.single()
        assertEquals("https://query1.finance.yahoo.com/v8/finance/spark?symbols=AAPL&range=1d&interval=5m", url)
        assertEquals(setOf("User-Agent"), headers.keys)

        val quote = (result as PriceResult.Success).quotes.getValue("AAPL")
        assertEquals(0, BigDecimal("190.1").compareTo(quote.price))
        assertEquals(0, BigDecimal("0.6").compareTo(quote.dailyChange))
        assertEquals("USD", quote.currency)
        assertEquals(marketOpen, quote.quoteTime)
    }

    @Test
    fun yahooAsksForTwentySymbolsPerRequest() = runBlocking {
        val symbols = (1..45).map { "S$it" }.toSet()
        val transport = RecordingTransport { url ->
            val requested = url.substringAfter("symbols=").substringBefore("&").split(",")
            spark(*requested.map { sparkSeries(it, "10", marketOpen.epochSecond) }.toTypedArray())
        }
        val result = YahooProvider(transport).latestQuotes(symbols) as PriceResult.Success

        assertEquals(listOf(20, 20, 5), transport.calls.map { it.first.substringAfter("symbols=").substringBefore("&").split(",").size })
        assertEquals(symbols, result.quotes.keys)
    }

    @Test
    fun yahooMapsShareClassSymbols() = runBlocking {
        assertEquals("BRK-B", YahooProvider.yahooSymbol("BRK B"))
        assertEquals("BRK-B", YahooProvider.yahooSymbol("BRK.B"))
        assertEquals("AAPL", YahooProvider.yahooSymbol("AAPL"))

        val transport = RecordingTransport { spark(sparkSeries("BRK-B", "515.62", marketOpen.epochSecond)) }
        val result = YahooProvider(transport).latestQuotes(setOf("BRK B")) as PriceResult.Success
        assertEquals(setOf("BRK B"), result.quotes.keys)
    }

    @Test
    fun yahooLeavesUnknownSymbolsUnavailable() = runBlocking {
        val some = YahooProvider(RecordingTransport { spark(sparkSeries("AAPL", "190.1", marketOpen.epochSecond)) })
            .latestQuotes(setOf("AAPL", "NOPE")) as PriceResult.Success
        assertEquals(setOf("AAPL"), some.quotes.keys)

        val none = YahooProvider(RecordingTransport { HttpResponse(404, """{"spark":{"result":null}}""") })
            .latestQuotes(setOf("NOPE")) as PriceResult.Success
        assertTrue(none.quotes.isEmpty())
    }

    @Test
    fun yahooReportsThrottlingAndOutages() = runBlocking {
        val limited = YahooProvider(RecordingTransport { HttpResponse(429, "") }).latestQuotes(setOf("AAPL"))
        assertEquals(PriceFailure.RATE_LIMITED, (limited as PriceResult.Failure).reason)
        val offline = YahooProvider({ _, _ -> throw IOException("offline") }).latestQuotes(setOf("AAPL"))
        assertEquals(PriceFailure.OFFLINE, (offline as PriceResult.Failure).reason)
    }

    @Test
    fun serviceUsesYahooOnlyWithoutAKey(): Unit = runBlocking {
        val finnhub = FakeMarketDataProvider()
        val yahoo = FakeMarketDataProvider()
        val holdings = listOf(holding("AAPL", "USD"))

        val noKey = PriceService(MemoryKeys(), MemoryCache(), { finnhub }, Clock.fixed(marketOpen, ZoneOffset.UTC), yahoo)
        assertEquals(PriceSource.YAHOO, noKey.source())
        noKey.refresh(holdings)
        assertEquals(1, yahoo.requests.size)
        assertEquals(0, finnhub.requests.size)

        val withKey = PriceService(MemoryKeys("k"), MemoryCache(), { finnhub }, Clock.fixed(marketOpen, ZoneOffset.UTC), yahoo)
        assertEquals(PriceSource.FINNHUB, withKey.source())
        withKey.refresh(holdings)
        assertEquals(1, yahoo.requests.size)
        assertEquals(1, finnhub.requests.size)
    }

    // --- Finnhub adapter ---

    @Test
    fun finnhubSendsOnlyTheSymbolAndTheKeyHeader() = runBlocking {
        val transport = RecordingTransport { quoteJson("190.1", marketOpen.epochSecond) }
        val result = FinnhubProvider("user-key", transport, Clock.fixed(marketOpen, ZoneOffset.UTC)).latestQuotes(setOf("AAPL"))

        val (url, headers) = transport.calls.single()
        assertEquals("https://finnhub.io/api/v1/quote?symbol=AAPL", url)
        assertEquals(mapOf("X-Finnhub-Token" to "user-key"), headers)
        assertFalse("key must not be in the URL", url.contains("user-key"))

        val quote = (result as PriceResult.Success).quotes.getValue("AAPL")
        assertEquals(0, BigDecimal("190.1").compareTo(quote.price))
        assertEquals("USD", quote.currency)
        assertEquals(marketOpen, quote.quoteTime)
    }

    @Test
    fun finnhubPreviousCloseGivesTheDailyChange() = runBlocking {
        val withClose = FinnhubProvider("k", RecordingTransport { quoteJson("190.1", marketOpen.epochSecond) })
            .latestQuotes(setOf("AAPL")) as PriceResult.Success
        val quote = withClose.quotes.getValue("AAPL")
        assertEquals(0, BigDecimal("189.5").compareTo(quote.previousClose))
        assertEquals(0, BigDecimal("0.6").compareTo(quote.dailyChange))

        val noClose = FinnhubProvider("k", RecordingTransport { HttpResponse(200, """{"c":190.1,"pc":0,"t":${marketOpen.epochSecond}}""") })
            .latestQuotes(setOf("AAPL")) as PriceResult.Success
        assertEquals(null, noClose.quotes.getValue("AAPL").dailyChange)
    }

    @Test
    fun finnhubUnknownSymbolIsLeftOut() = runBlocking {
        val transport = RecordingTransport { quoteJson("0", 0) }
        val result = FinnhubProvider("k", transport).latestQuotes(setOf("NOPE"))
        assertEquals(PriceResult.Success(emptyMap()), result)
    }

    @Test
    fun finnhubMapsErrors() = runBlocking {
        fun provider(response: () -> HttpResponse) = FinnhubProvider("k", RecordingTransport { response() })

        assertEquals(PriceResult.Failure(PriceFailure.INVALID_KEY), provider { HttpResponse(401, "{}") }.latestQuotes(setOf("AAPL")))
        assertEquals(PriceResult.Failure(PriceFailure.RATE_LIMITED), provider { HttpResponse(429, "{}") }.latestQuotes(setOf("AAPL")))
        assertEquals(PriceResult.Failure(PriceFailure.PROVIDER_ERROR), provider { HttpResponse(500, "") }.latestQuotes(setOf("AAPL")))
        assertEquals(PriceResult.Success(emptyMap()), provider { HttpResponse(403, "{}") }.latestQuotes(setOf("AAPL")))
        val offline = FinnhubProvider("k", { _, _ -> throw IOException("no network") })
        assertEquals(PriceResult.Failure(PriceFailure.OFFLINE), offline.latestQuotes(setOf("AAPL")))
    }

    // --- Freshness rules ---

    @Test
    fun liveForFifteenMinutesDuringMarketHours() {
        assertTrue(UsMarketHours.isOpen(marketOpen))
        assertEquals(PriceFreshness.LIVE, PriceFreshnessRules.classify(marketOpen.minusSeconds(15 * 60), marketOpen, false))
        assertEquals(PriceFreshness.STALE, PriceFreshnessRules.classify(marketOpen.minusSeconds(15 * 60 + 1), marketOpen, false))
    }

    @Test
    fun outsideMarketHoursTheLastSessionIsTheLatestClose() {
        val sessionClose = Instant.parse("2024-01-03T21:00:00Z") // 16:00 New York
        assertFalse(UsMarketHours.isOpen(afterClose))
        assertEquals(PriceFreshness.LATEST_CLOSE, PriceFreshnessRules.classify(sessionClose, afterClose, false))
        // Friday's close is still the latest close on Saturday; Thursday's is not.
        assertEquals(PriceFreshness.LATEST_CLOSE, PriceFreshnessRules.classify(Instant.parse("2024-01-05T21:00:00Z"), saturday, false))
        assertEquals(PriceFreshness.STALE, PriceFreshnessRules.classify(Instant.parse("2024-01-04T21:00:00Z"), saturday, false))
    }

    @Test
    fun failedRefreshMakesEveryPriceStale() {
        assertEquals(PriceFreshness.STALE, PriceFreshnessRules.classify(marketOpen, marketOpen, lastRefreshFailed = true))
    }

    // --- Price service ---

    private class MemoryKeys(var key: String? = null) : ApiKeyStore {
        override fun get() = key

        override fun set(key: String) {
            this.key = key
        }

        override fun clear() {
            key = null
        }
    }

    private class MemoryCache : QuoteCache {
        val quotes = mutableMapOf<String, Quote>()

        override fun get(symbols: Set<String>) = quotes.filterKeys { it in symbols }

        override fun putAll(quotes: Collection<Quote>) {
            quotes.forEach { this.quotes[it.symbol] = it }
        }
    }

    private val holdings: List<Holding> = listOf(holding("AAPL", "USD"), holding("SAP", "EUR"))

    @Test
    fun withoutAKeyNothingIsRequested() = runBlocking {
        val provider = FakeMarketDataProvider()
        val service = PriceService(MemoryKeys(), MemoryCache(), { provider })

        assertEquals(RefreshOutcome.NoKey, service.refresh(holdings))
        assertTrue(provider.requests.isEmpty())
    }

    @Test
    fun onlyInScopeSymbolsAreRequestedAndCached() = runBlocking {
        val quote = Quote("AAPL", BigDecimal("190"), "USD", marketOpen, marketOpen)
        val provider = FakeMarketDataProvider().apply { setQuote(quote) }
        val cache = MemoryCache()
        var usedKey: String? = null
        val service = PriceService(MemoryKeys("user-key"), cache, { usedKey = it; provider }, Clock.fixed(marketOpen, ZoneOffset.UTC))

        assertEquals(RefreshOutcome.Updated, service.refresh(holdings))
        assertEquals(listOf(setOf("AAPL")), provider.requests)
        assertEquals("user-key", usedKey)
        assertEquals(PriceFreshness.LIVE, service.prices(holdings, RefreshOutcome.Updated).getValue("AAPL").freshness)
    }

    @Test
    fun failedRefreshKeepsTheLastKnownPriceAsStale() = runBlocking {
        val earlier = marketOpen.minusSeconds(600)
        val cache = MemoryCache().apply { putAll(listOf(Quote("AAPL", BigDecimal("190"), "USD", earlier, earlier))) }
        val provider = FakeMarketDataProvider().apply { failure = PriceFailure.OFFLINE }
        val service = PriceService(MemoryKeys("k"), cache, { provider }, Clock.fixed(marketOpen, ZoneOffset.UTC))

        val outcome = service.refresh(holdings)
        assertEquals(RefreshOutcome.Failed(PriceFailure.OFFLINE, marketOpen), outcome)
        assertEquals(PriceFreshness.STALE, service.prices(holdings, outcome).getValue("AAPL").freshness)
    }

    // --- Rate limit and partial refreshes ---

    @Test
    fun rateLimiterWaitsOnceTheWindowIsFull() = runBlocking {
        var now = marketOpen
        val clock = object : Clock() {
            override fun instant() = now
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
        }
        val waits = mutableListOf<java.time.Duration>()
        val limiter = RateLimiter(2, java.time.Duration.ofMinutes(1), clock) { waits += it; now = now.plus(it) }

        limiter.acquire()
        now = now.plusSeconds(10)
        limiter.acquire()
        limiter.acquire() // third call must wait until the first leaves the window

        assertEquals(listOf(java.time.Duration.ofSeconds(50)), waits)
    }

    @Test
    fun rateLimitKeepsTheQuotesAlreadyReceived() = runBlocking {
        val transport = RecordingTransport { url ->
            if (url.endsWith("AAPL")) quoteJson("190.1", marketOpen.epochSecond) else HttpResponse(429, "{}")
        }
        val result = FinnhubProvider("k", transport).latestQuotes(setOf("AAPL", "MSFT"))

        result as PriceResult.Failure
        assertEquals(PriceFailure.RATE_LIMITED, result.reason)
        assertEquals(setOf("AAPL"), result.partial.keys)
    }

    @Test
    fun afterAPartialRefreshOnlyQuotesItDidNotUpdateAreStale() = runBlocking {
        val earlier = marketOpen.minusSeconds(600)
        val cache = MemoryCache().apply { putAll(listOf(Quote("MSFT", BigDecimal("400"), "USD", earlier, earlier))) }
        val fresh = Quote("AAPL", BigDecimal("190"), "USD", marketOpen, marketOpen)
        val provider = object : MarketDataProvider {
            override suspend fun latestQuotes(symbols: Set<String>) =
                PriceResult.Failure(PriceFailure.RATE_LIMITED, mapOf("AAPL" to fresh))
        }
        val both = holdings + holding("MSFT", "USD")
        val service = PriceService(MemoryKeys("k"), cache, { provider }, Clock.fixed(marketOpen, ZoneOffset.UTC))

        val outcome = service.refresh(both)
        val prices = service.prices(both, outcome)
        assertEquals(PriceFreshness.LIVE, prices.getValue("AAPL").freshness)
        assertEquals(PriceFreshness.STALE, prices.getValue("MSFT").freshness)
    }

    @Test
    fun refreshSkipsJustFetchedQuotesAndClosesWhileTheMarketIsClosed() = runBlocking {
        val sessionClose = Instant.parse("2024-01-03T21:00:00Z")
        val cache = MemoryCache().apply {
            putAll(listOf(Quote("AAPL", BigDecimal("190"), "USD", sessionClose, sessionClose.plusSeconds(60))))
        }
        val provider = FakeMarketDataProvider()

        // Evening: AAPL already has the closing price, so nothing is requested.
        PriceService(MemoryKeys("k"), cache, { provider }, Clock.fixed(afterClose, ZoneOffset.UTC)).refresh(holdings)
        assertTrue(provider.requests.isEmpty())

        // Market hours, quote fetched 30 seconds ago: skipped as well.
        cache.putAll(listOf(Quote("AAPL", BigDecimal("191"), "USD", marketOpen, marketOpen)))
        PriceService(MemoryKeys("k"), cache, { provider }, Clock.fixed(marketOpen.plusSeconds(30), ZoneOffset.UTC)).refresh(holdings)
        assertTrue(provider.requests.isEmpty())
    }

    @Test
    fun anIntradayQuoteSeenAfterTheCloseIsStaleNotTheClose() {
        val intraday = Instant.parse("2024-01-03T20:50:00Z") // 15:50 New York
        assertEquals(PriceFreshness.STALE, PriceFreshnessRules.classify(intraday, afterClose, false))
    }
}
