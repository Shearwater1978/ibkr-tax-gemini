package com.ibkrtax.mobile.prices

import com.ibkrtax.mobile.portfolio.Holding
import com.ibkrtax.mobile.portfolio.Holdings
import com.ibkrtax.mobile.portfolio.OpenLot
import com.ibkrtax.mobile.testing.FakeMarketDataProvider
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Provider choice and keys (mobile-market-prices "Choose a price provider and use the user's own key"). */
class PriceProviderSelectionTest {
    @Test
    fun withoutAStoredChoiceAFinnhubKeyKeepsFinnhub() {
        assertEquals(PriceProviderId.FINNHUB, MemoryKeys(key = "finnhub-key").selected())
    }

    @Test
    fun withoutAStoredChoiceOrKeyNothingIsChosen() {
        assertEquals(PriceProviderId.NONE, MemoryKeys().selected())
        // A Twelve Data key alone does not choose Twelve Data.
        assertEquals(PriceProviderId.NONE, MemoryKeys().apply { set(PriceProviderId.TWELVE_DATA, "td") }.selected())
    }

    @Test
    fun aStoredChoiceWins() {
        assertEquals(PriceProviderId.NONE, MemoryKeys(key = "finnhub-key", choice = PriceProviderId.NONE).selected())
        assertEquals(PriceProviderId.TWELVE_DATA, MemoryKeys(choice = PriceProviderId.TWELVE_DATA).selected())
    }

    @Test
    fun keysAreKeptPerProvider() {
        val keys = MemoryKeys(key = "finnhub-key")
        keys.set(PriceProviderId.TWELVE_DATA, "twelve-key")
        assertEquals("finnhub-key", keys.get(PriceProviderId.FINNHUB))
        assertEquals("twelve-key", keys.get(PriceProviderId.TWELVE_DATA))
        keys.clear(PriceProviderId.FINNHUB)
        assertNull(keys.get(PriceProviderId.FINNHUB))
        assertEquals("twelve-key", keys.get(PriceProviderId.TWELVE_DATA))
        assertNull(keys.get(PriceProviderId.NONE))
    }

    @Test
    fun storedNamesResolveExactlyAndKeySettingsStayStable() {
        assertEquals(PriceProviderId.TWELVE_DATA, PriceProviderId.resolve("TWELVE_DATA"))
        assertNull(PriceProviderId.resolve("YAHOO"))
        assertNull(PriceProviderId.resolve(null))
        // The Finnhub row name predates the choice; changing it would lose existing keys.
        assertEquals("finnhub_api_key", PriceProviderId.FINNHUB.keySetting)
        assertEquals("twelvedata_api_key", PriceProviderId.TWELVE_DATA.keySetting)
    }

    // --- Routing (mobile-market-prices "Never switch providers automatically") ---

    /** Thursday: US and Xetra sessions both open. */
    private val open = Instant.parse("2026-10-15T14:00:00Z")

    private class MemoryCache : QuoteCache {
        val quotes = mutableMapOf<String, Quote>()

        override fun get(symbols: Set<String>) = quotes.filterKeys { it in symbols }

        override fun putAll(quotes: Collection<Quote>) {
            quotes.forEach { this.quotes[it.symbol] = it }
        }
    }

    private class Providers {
        val finnhub = FakeMarketDataProvider()
        val twelveData = FakeMarketDataProvider()
        val yahoo = FakeMarketDataProvider()
        val keysUsed = mutableListOf<Pair<PriceProviderId, String>>()

        fun service(keys: ApiKeyStore, cache: QuoteCache = MemoryCache(), clock: Clock) =
            PriceService(keys, cache, { id, key ->
                keysUsed += id to key
                if (id == PriceProviderId.TWELVE_DATA) twelveData else finnhub
            }, clock, yahoo)

        val called: Int get() = finnhub.requests.size + twelveData.requests.size + yahoo.requests.size
    }

    private fun holding(ticker: String, currency: String, quantity: String = "1", price: String = "10") =
        Holdings.fromLots(listOf(OpenLot(ticker, "", "2023-01-01", BigDecimal(quantity), BigDecimal(price), currency))).single()

    private val usAndEurope: List<Holding> = listOf(holding("AAPL", "USD"), holding("SAP", "EUR"))
    private val exchanges = mapOf(("SAP" to "") to "IBIS")

    private fun clockAt(instant: Instant) = Clock.fixed(instant, ZoneOffset.UTC)

    private fun keysFor(choice: PriceProviderId, finnhub: String? = "finnhub-key", twelveData: String? = "td-key") =
        MemoryKeys(key = finnhub, choice = choice).apply { twelveData?.let { set(PriceProviderId.TWELVE_DATA, it) } }

    @Test
    fun twelveDataWithAKeyGetsUsListingsAndYahooTheEuropeanOnes() = runBlocking {
        val providers = Providers()
        val service = providers.service(keysFor(PriceProviderId.TWELVE_DATA, finnhub = null), clock = clockAt(open))

        assertEquals(PriceSource.TWELVE_DATA, service.source())
        service.refresh(usAndEurope, exchanges)

        assertEquals(listOf(setOf("AAPL")), providers.twelveData.requests)
        assertEquals(listOf(setOf("SAP.DE")), providers.yahoo.requests)
        assertTrue(providers.finnhub.requests.isEmpty())
        assertEquals(listOf(PriceProviderId.TWELVE_DATA to "td-key"), providers.keysUsed)
    }

    @Test
    fun aChosenProviderWithoutAKeyRequestsNothing() = runBlocking {
        val providers = Providers()
        // A Finnhub key is stored, but Twelve Data is chosen without its own key: no fallback.
        val outcome = providers.service(keysFor(PriceProviderId.TWELVE_DATA, twelveData = null), clock = clockAt(open))
            .refresh(usAndEurope, exchanges)
        assertEquals(RefreshOutcome.NoKey, outcome)
        assertEquals(0, providers.called)
        assertTrue(providers.keysUsed.isEmpty())
    }

    @Test
    fun noneMakesNoRequestAtAllAndShowsCachedPricesStale() = runBlocking {
        val providers = Providers()
        val earlier = open.minusSeconds(7200)
        val cache = MemoryCache().apply { putAll(listOf(Quote("AAPL", BigDecimal("190"), "USD", earlier, earlier))) }
        val service = providers.service(keysFor(PriceProviderId.NONE), cache, clockAt(open))

        val outcome = service.refresh(usAndEurope, exchanges)

        assertEquals(RefreshOutcome.NoProvider, outcome)
        assertEquals(0, providers.called)
        assertEquals(PriceFreshness.STALE, service.prices(usAndEurope, outcome, exchanges).getValue("AAPL").freshness)
    }

    @Test
    fun aFailingProviderIsNeverReplacedByAnother() = runBlocking {
        val aapl = listOf(holding("AAPL", "USD"))
        for (failure in PriceFailure.entries) {
            val providers = Providers().apply { twelveData.failure = failure }
            val earlier = open.minusSeconds(3600)
            val cache = MemoryCache().apply { putAll(listOf(Quote("AAPL", BigDecimal("190"), "USD", earlier, earlier))) }
            val service = providers.service(keysFor(PriceProviderId.TWELVE_DATA), cache, clockAt(open))

            val outcome = service.refresh(aapl) as RefreshOutcome.Failed

            assertEquals(failure, outcome.reason)
            assertEquals(PriceSource.TWELVE_DATA, outcome.source)
            assertTrue(providers.finnhub.requests.isEmpty())
            assertTrue(providers.yahoo.requests.isEmpty())
            assertEquals(PriceFreshness.STALE, service.prices(aapl, outcome).getValue("AAPL").freshness)
        }
    }

    @Test
    fun europeUsesYahooOnlyWhileFinnhubOrTwelveDataIsChosen() = runBlocking {
        val europeOnly = listOf(holding("SAP", "EUR"))
        val cases = listOf(PriceProviderId.NONE to false, PriceProviderId.FINNHUB to true, PriceProviderId.TWELVE_DATA to true)
        for ((choice, expectYahoo) in cases) {
            val providers = Providers()
            providers.service(keysFor(choice), clock = clockAt(open)).refresh(europeOnly, exchanges)
            assertEquals(choice.name, expectYahoo, providers.yahoo.requests.isNotEmpty())
        }
    }

    @Test
    fun twelveDataWaitsFifteenMinutesAndFinnhubOneBeforeAskingAgain() = runBlocking {
        val aapl = listOf(holding("AAPL", "USD"))
        val cases = listOf(PriceProviderId.TWELVE_DATA to Duration.ofMinutes(15), PriceProviderId.FINNHUB to Duration.ofMinutes(1))
        for ((choice, interval) in cases) {
            val providers = Providers()
            val fetched = open.minusSeconds(interval.seconds - 1)
            val cache = MemoryCache().apply { putAll(listOf(Quote("AAPL", BigDecimal("190"), "USD", fetched, fetched))) }

            providers.service(keysFor(choice), cache, clockAt(open)).refresh(aapl)
            assertEquals(choice.name + " inside the interval", 0, providers.called)

            providers.service(keysFor(choice), cache, clockAt(open.plusSeconds(2))).refresh(aapl)
            assertEquals(choice.name + " after the interval", 1, providers.called)
        }
    }

    @Test
    fun largestPositionsArePricedFirst() = runBlocking {
        val providers = Providers()
        val dayBefore = open.minusSeconds(86400)
        // A cached price beats the cost basis: SMALL cost 10 but is now worth 900.
        val cache = MemoryCache().apply { putAll(listOf(Quote("SMALL", BigDecimal("900"), "USD", dayBefore, dayBefore))) }
        val holdings = listOf(
            holding("MID", "USD", quantity = "10", price = "50"),
            holding("SMALL", "USD", quantity = "1", price = "10"),
            holding("BIG", "USD", quantity = "100", price = "20"),
            holding("TINY", "USD", quantity = "1", price = "1"),
        )
        val progress = mutableListOf<RefreshProgress>()

        providers.service(keysFor(PriceProviderId.TWELVE_DATA), cache, clockAt(open)).refresh(holdings) { progress += it }

        assertEquals(listOf("BIG", "SMALL", "MID", "TINY"), providers.twelveData.requests.single().toList())
        assertEquals(PriceSource.TWELVE_DATA, progress.first().source)
        assertEquals(1, progress.first().minutesLeft) // 4 symbols at 8 per minute
    }
}
