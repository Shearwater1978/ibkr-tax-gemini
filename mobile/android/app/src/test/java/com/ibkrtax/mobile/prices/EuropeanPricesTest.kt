package com.ibkrtax.mobile.prices

import com.ibkrtax.mobile.portfolio.Holding
import com.ibkrtax.mobile.portfolio.Holdings
import com.ibkrtax.mobile.portfolio.MainPage
import com.ibkrtax.mobile.portfolio.OpenLot
import com.ibkrtax.mobile.testing.FakeMarketDataProvider
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EuropeanPricesTest {
    // Wednesday 2024-01-03: 11:00 in Berlin (Xetra open), 05:00 in New York (US closed).
    private val xetraOpen = Instant.parse("2024-01-03T10:00:00Z")
    private val xetraClosed = Instant.parse("2024-01-03T18:00:00Z") // 19:00 Berlin

    private fun holding(ticker: String, currency: String, isin: String = "") =
        Holdings.fromLots(listOf(OpenLot(ticker, isin, "2023-01-01", BigDecimal.ONE, BigDecimal.TEN, currency))).single()

    private fun d(value: String) = BigDecimal(value)

    private class Transport(private val respond: (String) -> HttpResponse) : HttpTransport {
        val urls = mutableListOf<String>()

        override fun get(url: String, headers: Map<String, String>): HttpResponse {
            urls += url
            return respond(url)
        }
    }

    private fun chart(currency: String, price: String, previousClose: String, time: Instant) = HttpResponse(
        200,
        """{"chart":{"result":[{"meta":{"currency":"$currency","regularMarketPrice":$price,"regularMarketTime":${time.epochSecond},""" +
            """"chartPreviousClose":$previousClose}}],"error":null}}""",
    )

    private class MemoryCache : QuoteCache {
        val quotes = mutableMapOf<String, Quote>()
        override fun get(symbols: Set<String>) = quotes.filterKeys { it in symbols }
        override fun putAll(quotes: Collection<Quote>) = quotes.forEach { this.quotes[it.symbol] = it }
    }

    // --- exchange table and symbols ---

    @Test
    fun ibkrExchangesMapToYahooSymbols() {
        assertEquals("SAP.DE", Markets.europeanSymbol("SAP", Markets.european("IBIS")!!))
        assertEquals("BP.L", Markets.europeanSymbol("BP.", Markets.european("LSE")!!))
        assertEquals("NOVO-B.CO", Markets.europeanSymbol("NOVO B", Markets.european("cph")!!))
        assertEquals("PKO.WA", Markets.europeanSymbol("PKO", Markets.european("WSE")!!))
        assertNull(Markets.european("NASDAQ"))
        assertNull(Markets.european("ISED"))
        assertTrue(Markets.isEuropean("SAP.DE"))
        assertFalse(Markets.isEuropean("BRK B"))
        assertFalse(Markets.isEuropean("AAPL"))
    }

    @Test
    fun scopeUsesTheListingExchangeBeforeTheCurrency() {
        assertEquals("SAP.DE", PriceScope.symbol(holding("SAP", "EUR"), "IBIS"))
        assertEquals("CSPX.L", PriceScope.symbol(holding("CSPX", "USD"), "LSE")) // USD fund on a European exchange
        assertEquals("AAPL", PriceScope.symbol(holding("AAPL", "USD"), "NASDAQ"))
        assertEquals("AAPL", PriceScope.symbol(holding("AAPL", "USD"), null))
        assertNull(PriceScope.symbol(holding("SHOP", "CAD"), "TSE"))
        assertNull(PriceScope.symbol(holding("SAP", "EUR"), null))
    }

    // --- Yahoo chart quotes ---

    @Test
    fun europeanSymbolsUseTheChartEndpointAndPenceBecomePounds() = runBlocking {
        val transport = Transport { url ->
            when {
                "chart/SHEL.L" in url -> chart("GBp", "3793.5", "3800", xetraOpen)
                "chart/SAP.DE" in url -> chart("EUR", "191.46", "190", xetraOpen)
                "chart/NOPE.DE" in url -> HttpResponse(404, "")
                else -> HttpResponse(200, """{"AAPL":{"timestamp":[${xetraOpen.epochSecond}],"close":[190.0],"previousClose":189.0}}""")
            }
        }
        val result = YahooProvider(transport).latestQuotes(setOf("SHEL.L", "SAP.DE", "NOPE.DE", "AAPL")) as PriceResult.Success

        assertEquals(1, transport.urls.count { "/spark?" in it })
        assertTrue(transport.urls.any { it.endsWith("/chart/SAP.DE?range=1d&interval=1d") })
        val shell = result.quotes.getValue("SHEL.L")
        assertEquals("GBP", shell.currency)
        assertEquals(0, d("37.935").compareTo(shell.price))
        assertEquals(0, d("38").compareTo(shell.previousClose))
        assertEquals("EUR", result.quotes.getValue("SAP.DE").currency)
        assertEquals(setOf("SHEL.L", "SAP.DE", "AAPL"), result.quotes.keys)
    }

    // --- freshness ---

    @Test
    fun europeanQuotesAreDelayedDuringTheirSessionAndCloseAfterIt() {
        val xetra = Markets.forSymbol("SAP.DE")
        assertTrue(xetra.isOpen(xetraOpen))
        assertFalse(Markets.US.isOpen(xetraOpen))

        assertEquals(PriceFreshness.DELAYED, PriceFreshnessRules.classify(xetraOpen.minusSeconds(20 * 60), xetraOpen, false, xetra))
        assertEquals(PriceFreshness.STALE, PriceFreshnessRules.classify(xetraOpen.minusSeconds(31 * 60), xetraOpen, false, xetra))
        val close = Instant.parse("2024-01-03T16:30:00Z") // 17:30 Berlin
        assertEquals(PriceFreshness.LATEST_CLOSE, PriceFreshnessRules.classify(close, xetraClosed, false, xetra))
        // A US quote is never "delayed".
        val usOpen = Instant.parse("2024-01-03T16:00:00Z")
        assertEquals(PriceFreshness.STALE, PriceFreshnessRules.classify(usOpen.minusSeconds(20 * 60), usOpen, false))
    }

    // --- routing in the service ---

    @Test
    fun withAKeyUsGoesToFinnhubAndEuropeToYahoo(): Unit = runBlocking {
        val finnhub = FakeMarketDataProvider()
        val yahooUs = FakeMarketDataProvider()
        val europe = FakeMarketDataProvider()
        val holdings = listOf(holding("AAPL", "USD"), holding("SAP", "EUR"))
        val exchanges = mapOf(("AAPL" to "") to "NASDAQ", ("SAP" to "") to "IBIS")
        val service = PriceService(MemoryKeys("k"), MemoryCache(), { _, _ -> finnhub }, Clock.fixed(xetraOpen, ZoneOffset.UTC), europe)

        service.refresh(holdings, exchanges)

        assertEquals(listOf(setOf("AAPL")), finnhub.requests)
        assertEquals(listOf(setOf("SAP.DE")), europe.requests)
        assertTrue(yahooUs.requests.isEmpty())
    }

    @Test
    fun aFailingFinnhubKeyStillLetsEuropeanPricesThrough(): Unit = runBlocking {
        val finnhub = FakeMarketDataProvider().apply { failure = PriceFailure.INVALID_KEY }
        val europe = FakeMarketDataProvider().apply { setQuote(Quote("SAP.DE", d("191"), "EUR", xetraOpen, xetraOpen, d("190"))) }
        val cache = MemoryCache()
        val holdings = listOf(holding("AAPL", "USD"), holding("SAP", "EUR"))
        val exchanges = mapOf(("SAP" to "") to "IBIS")
        val service = PriceService(MemoryKeys("k"), cache, { _, _ -> finnhub }, Clock.fixed(xetraOpen, ZoneOffset.UTC), europe)
        val progress = mutableListOf<RefreshProgress>()

        val outcome = service.refresh(holdings, exchanges) { progress += it }

        assertEquals(PriceFailure.INVALID_KEY, (outcome as RefreshOutcome.Failed).reason)
        assertTrue("SAP.DE" in cache.quotes)
        assertEquals(2, progress.first().total)
        assertEquals(PriceSource.YAHOO, progress.last().source)
    }

    // --- main page lookup by symbol ---

    @Test
    fun rowsUseTheSymbolSoSameTickersInOtherMarketsDoNotMix() {
        val sapEur = holding("SAP", "EUR", "DE0007164600")
        val sapAdr = holding("SAP", "USD", "US8030542042")
        val prices = mapOf(
            "SAP.DE" to PricedQuote(Quote("SAP.DE", d("191"), "EUR", xetraOpen, xetraOpen, d("190")), PriceFreshness.DELAYED),
            "SAP" to PricedQuote(Quote("SAP", d("210"), "USD", xetraOpen, xetraOpen, d("209")), PriceFreshness.LIVE),
        )
        val exchanges = mapOf(("SAP" to "DE0007164600") to "IBIS", ("SAP" to "US8030542042") to "NYSE")
        val rows = MainPage.rows(listOf(sapEur, sapAdr), prices, exchanges)

        assertEquals(0, d("191").compareTo(rows.first { it.currency == "EUR" }.price!!.quote.price))
        assertEquals(0, d("210").compareTo(rows.first { it.currency == "USD" }.price!!.quote.price))
    }
}
