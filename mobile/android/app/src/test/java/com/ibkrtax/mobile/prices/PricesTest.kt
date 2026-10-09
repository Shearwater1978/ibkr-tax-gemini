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
        val cache = MemoryCache().apply { putAll(listOf(Quote("AAPL", BigDecimal("190"), "USD", marketOpen, marketOpen))) }
        val provider = FakeMarketDataProvider().apply { failure = PriceFailure.OFFLINE }
        val service = PriceService(MemoryKeys("k"), cache, { provider }, Clock.fixed(marketOpen, ZoneOffset.UTC))

        val outcome = service.refresh(holdings)
        assertEquals(RefreshOutcome.Failed(PriceFailure.OFFLINE), outcome)
        assertEquals(PriceFreshness.STALE, service.prices(holdings, outcome).getValue("AAPL").freshness)
    }
}
