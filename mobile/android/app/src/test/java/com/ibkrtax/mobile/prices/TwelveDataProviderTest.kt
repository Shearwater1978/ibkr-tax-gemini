package com.ibkrtax.mobile.prices

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

class TwelveDataProviderTest {
    private val now = Instant.parse("2026-10-17T09:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private class Call(val url: String, val headers: Map<String, String>) {
        val symbols: List<String> get() = java.net.URLDecoder.decode(url.substringAfter("symbol="), "UTF-8").split(",")
    }

    /** Answers each request with the next scripted response (or throws it). */
    private class ScriptedTransport(vararg responses: Any) : HttpTransport {
        private val queue = ArrayDeque(responses.toList())
        val calls = mutableListOf<Call>()

        override fun get(url: String, headers: Map<String, String>): HttpResponse {
            calls += Call(url, headers)
            return when (val next = queue.removeFirstOrNull() ?: error("unexpected request $url")) {
                is HttpResponse -> next
                is IOException -> throw next
                else -> error("bad script $next")
            }
        }
    }

    /** Answers every request by building a quote for each requested symbol. */
    private class EchoTransport : HttpTransport {
        val calls = mutableListOf<Call>()

        override fun get(url: String, headers: Map<String, String>): HttpResponse {
            val call = Call(url, headers).also { calls += it }
            val body = call.symbols.joinToString(",", "{", "}") { "\"$it\":${quote(it, "10.00")}" }
            return HttpResponse(200, body)
        }
    }

    private class RecordingLimiter : CreditLimiter {
        val credits = mutableListOf<Int>()

        override suspend fun acquire(credits: Int) {
            this.credits += credits
        }
    }

    private fun fixture(name: String) =
        javaClass.classLoader!!.getResource("twelve-data/$name")!!.readText()

    private fun provider(transport: HttpTransport, limiter: CreditLimiter? = null) =
        TwelveDataProvider("user-key-123", transport, clock, limiter)

    private fun ok(body: String) = HttpResponse(200, body)

    private fun error(code: Int, message: String = "synthetic") = """{"code":$code,"message":"$message","status":"error"}"""

    @Test
    fun singleQuoteUsesCloseLastQuoteTimeCurrencyAndPreviousClose() = runBlocking {
        val result = provider(ScriptedTransport(ok(fixture("synthetic-quote-single.json")))).latestQuotes(setOf("AAPL")) as PriceResult.Success
        val quote = result.quotes.getValue("AAPL")
        assertEquals(0, BigDecimal("204.50").compareTo(quote.price))
        assertEquals("USD", quote.currency)
        // last_quote_at (1760644800) wins over timestamp (1760572800).
        assertEquals(Instant.ofEpochSecond(1760644800), quote.quoteTime)
        assertEquals(now, quote.retrievedAt)
        assertEquals(0, BigDecimal("3.25").compareTo(quote.dailyChange))
    }

    @Test
    fun timestampIsUsedWithoutLastQuoteTime() = runBlocking {
        val body = """{"symbol":"AAPL","currency":"USD","close":"5","timestamp":1760572800}"""
        val quote = (provider(ScriptedTransport(ok(body))).latestQuotes(setOf("AAPL")) as PriceResult.Success).quotes.getValue("AAPL")
        assertEquals(Instant.ofEpochSecond(1760572800), quote.quoteTime)
        assertEquals(null, quote.previousClose)
    }

    @Test
    fun multiSymbolAnswerKeyedBySymbolWithPerSymbolErrors() = runBlocking {
        val result = provider(ScriptedTransport(ok(fixture("synthetic-quote-multi.json"))))
            .latestQuotes(linkedSetOf("AAPL", "SHEL", "PRIV", "NOPE")) as PriceResult.Success
        assertEquals(setOf("AAPL", "SHEL"), result.quotes.keys) // 403 PRIV and 404 NOPE stay unpriced
        assertEquals("GBp", result.quotes.getValue("SHEL").currency) // taken from the answer, not assumed
    }

    @Test
    fun twentySymbolsGoInChunksOfEightEightAndFourWithOneCreditPerSymbol() = runBlocking {
        val transport = EchoTransport()
        val limiter = RecordingLimiter()
        val symbols = (1..20).map { "S$it" }
        val progress = mutableListOf<Int>()
        val result = provider(transport, limiter).latestQuotes(symbols.toCollection(LinkedHashSet())) { progress += it } as PriceResult.Success
        assertEquals(listOf(8, 8, 4), transport.calls.map { it.symbols.size })
        assertEquals(listOf(8, 8, 4), limiter.credits)
        assertEquals(symbols, transport.calls.flatMap { it.symbols }) // the caller's order is kept
        assertEquals(listOf(8, 16, 20), progress)
        assertEquals(20, result.quotes.size)
    }

    @Test
    fun theKeyIsOnlyInTheAuthorizationHeader() = runBlocking {
        val transport = EchoTransport()
        provider(transport).latestQuotes(setOf("AAPL", "MSFT"))
        val call = transport.calls.single()
        assertEquals("https://api.twelvedata.com/quote?symbol=AAPL,MSFT", call.url)
        assertFalse(call.url.contains("user-key-123"))
        assertFalse(call.url.contains("apikey"))
        assertEquals(mapOf("Authorization" to "apikey user-key-123"), call.headers)
    }

    @Test
    fun shareClassTickersAreSentWithADotAndMappedBack() = runBlocking {
        val transport = EchoTransport()
        val result = provider(transport).latestQuotes(setOf("BRK B")) as PriceResult.Success
        assertEquals(listOf("BRK.B"), transport.calls.single().symbols)
        assertTrue("BRK B" in result.quotes)
    }

    @Test
    fun errorsInAnHttp200BodyAreMapped() = runBlocking {
        assertEquals(PriceFailure.INVALID_KEY, failure(ScriptedTransport(ok(error(401)))))
        assertEquals(PriceFailure.RATE_LIMITED, failure(ScriptedTransport(ok(error(429)))))
        assertEquals(PriceFailure.PROVIDER_ERROR, failure(ScriptedTransport(ok(error(500)))))
    }

    @Test
    fun theSameErrorsAsHttpStatusesAreMapped() = runBlocking {
        // Observed: the invalid key comes back as HTTP 401 with the error object.
        assertEquals(PriceFailure.INVALID_KEY, failure(ScriptedTransport(HttpResponse(401, fixture("observed-401-invalid-key.json")))))
        assertEquals(PriceFailure.INVALID_KEY, failure(ScriptedTransport(HttpResponse(401, fixture("observed-401-demo-multi-symbol.json")))))
        assertEquals(PriceFailure.RATE_LIMITED, failure(ScriptedTransport(HttpResponse(429, error(429)))))
        assertEquals(PriceFailure.PROVIDER_ERROR, failure(ScriptedTransport(HttpResponse(500, error(500)))))
        assertEquals(PriceFailure.PROVIDER_ERROR, failure(ScriptedTransport(HttpResponse(503, "<html>down</html>"))))
        assertEquals(PriceFailure.RATE_LIMITED, failure(ScriptedTransport(HttpResponse(429, "too many"))))
    }

    @Test
    fun aRateLimitKeepsTheQuotesReceivedBefore() = runBlocking {
        val transport = ScriptedTransport(ok(quotes((1..8).map { "S$it" })), HttpResponse(429, error(429, "out of API credits for the day")))
        val result = provider(transport).latestQuotes((1..12).map { "S$it" }.toCollection(LinkedHashSet())) as PriceResult.Failure
        assertEquals(PriceFailure.RATE_LIMITED, result.reason)
        assertEquals(8, result.partial.size)
    }

    @Test
    fun aWholeRequest403LeavesThatChunkUnpricedAndContinues() = runBlocking {
        val transport = ScriptedTransport(HttpResponse(403, error(403)), ok(quotes((9..10).map { "S$it" })))
        val result = provider(transport).latestQuotes((1..10).map { "S$it" }.toCollection(LinkedHashSet())) as PriceResult.Success
        assertEquals(setOf("S9", "S10"), result.quotes.keys)
    }

    @Test
    fun symbolNotFoundIsUnpricedButOtherBadRequestsFail() = runBlocking {
        val notFound = provider(ScriptedTransport(HttpResponse(404, error(404)))).latestQuotes(setOf("NOPE")) as PriceResult.Success
        assertTrue(notFound.quotes.isEmpty())
        val badSymbol = provider(ScriptedTransport(ok(error(400, "**symbol** not found: NOPE")))).latestQuotes(setOf("NOPE"))
        assertTrue(badSymbol is PriceResult.Success)
        assertEquals(PriceFailure.PROVIDER_ERROR, failure(ScriptedTransport(ok(error(400, "invalid interval parameter")))))
    }

    @Test
    fun malformedOrUnexpectedAnswersAreProviderErrors() = runBlocking {
        assertEquals(PriceFailure.PROVIDER_ERROR, failure(ScriptedTransport(ok("{not json"))))
        assertEquals(PriceFailure.PROVIDER_ERROR, failure(ScriptedTransport(ok("""{"data":[1,2,3]}"""))))
    }

    @Test
    fun missingZeroOrNegativeCloseLeavesTheSymbolUnpriced() = runBlocking {
        val body = """{"A":{"symbol":"A","currency":"USD","timestamp":1760572800},
            "B":${quote("B", "0")},"C":${quote("C", "-1.5")},"D":${quote("D", "12.5")}}"""
        val result = provider(ScriptedTransport(ok(body))).latestQuotes(linkedSetOf("A", "B", "C", "D")) as PriceResult.Success
        assertEquals(setOf("D"), result.quotes.keys)
    }

    @Test
    fun parameterTooLongSplitsTheChunkAndRetries() = runBlocking {
        val limiter = RecordingLimiter()
        val transport = ScriptedTransport(HttpResponse(414, error(414)), ok(quotes(listOf("A", "B"))), ok(quotes(listOf("C", "D"))))
        val result = provider(transport, limiter).latestQuotes(linkedSetOf("A", "B", "C", "D")) as PriceResult.Success
        assertEquals(listOf(listOf("A", "B", "C", "D"), listOf("A", "B"), listOf("C", "D")), transport.calls.map { it.symbols })
        assertEquals(listOf(4, 2, 2), limiter.credits)
        assertEquals(4, result.quotes.size)
        // A single symbol that is still too long fails.
        assertEquals(PriceFailure.PROVIDER_ERROR, failure(ScriptedTransport(HttpResponse(414, error(414)))))
    }

    @Test
    fun offlineKeepsTheQuotesReceivedBefore() = runBlocking {
        val transport = ScriptedTransport(ok(quotes((1..8).map { "S$it" })), IOException("offline"))
        val result = provider(transport).latestQuotes((1..9).map { "S$it" }.toCollection(LinkedHashSet())) as PriceResult.Failure
        assertEquals(PriceFailure.OFFLINE, result.reason)
        assertEquals(8, result.partial.size)
    }

    private suspend fun failure(transport: HttpTransport): PriceFailure =
        (provider(transport).latestQuotes(setOf("AAPL")) as PriceResult.Failure).reason

    private fun quotes(symbols: List<String>) = symbols.joinToString(",", "{", "}") { "\"$it\":${quote(it, "10.00")}" }

    private companion object {
        fun quote(symbol: String, close: String) =
            """{"symbol":"$symbol","currency":"USD","close":"$close","previous_close":"9.50","timestamp":1760572800,"last_quote_at":1760644800}"""
    }
}
