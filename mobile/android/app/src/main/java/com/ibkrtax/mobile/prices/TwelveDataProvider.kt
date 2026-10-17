package com.ibkrtax.mobile.prices

import java.io.IOException
import java.math.BigDecimal
import java.net.URLEncoder
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * Twelve Data `/quote` adapter for US-listed symbols (mobile-market-prices "Retrieve US prices
 * from Twelve Data"). The user's own key goes only into the `Authorization: apikey` header; the
 * URL carries only the comma-separated symbols. Each symbol costs one credit, so symbols are sent
 * in chunks of at most [chunkSize] and [limiter] is asked for one credit per symbol.
 *
 * Errors arrive as an HTTP status with a body `{"code", "message", "status": "error"}` (observed);
 * the same body with HTTP 200 is accepted too. A multi-symbol answer is an object keyed by symbol
 * whose values are quotes or such error objects; a single quote object is accepted for any chunk.
 */
class TwelveDataProvider(
    private val apiKey: String,
    private val transport: HttpTransport = UrlConnectionTransport(),
    private val clock: Clock = Clock.systemUTC(),
    private val limiter: CreditLimiter? = null,
    private val chunkSize: Int = RateLimiter.TWELVE_DATA_FREE_CREDITS,
) : MarketDataProvider {
    override suspend fun latestQuotes(symbols: Set<String>, onProgress: (Int) -> Unit): PriceResult = withContext(Dispatchers.IO) {
        val quotes = mutableMapOf<String, Quote>()
        var done = 0
        // Callers order symbols (largest position first); keep that order.
        for (chunk in symbols.toList().chunked(chunkSize)) {
            val failure = request(chunk, quotes)
            if (failure != null) return@withContext PriceResult.Failure(failure, quotes.toMap())
            done += chunk.size
            onProgress(done)
        }
        PriceResult.Success(quotes)
    }

    /** Requests [chunk] into [quotes]; returns a failure that stops the refresh, or null. */
    private suspend fun request(chunk: List<String>, quotes: MutableMap<String, Quote>): PriceFailure? {
        val wire = chunk.associateBy(::wireSymbol)
        limiter?.acquire(chunk.size)
        val response = try {
            transport.get(
                "$BASE_URL/quote?symbol=" + wire.keys.joinToString(",") { URLEncoder.encode(it, "UTF-8") },
                mapOf(AUTH_HEADER to "apikey $apiKey"),
            )
        } catch (e: IOException) {
            return PriceFailure.OFFLINE
        }
        val json = try {
            JSONObject(response.body)
        } catch (e: JSONException) {
            return if (response.status in 200..299) PriceFailure.PROVIDER_ERROR else failureFor(response.status, "", chunk, quotes)
        }
        errorCode(json)?.let { return failureFor(it, json.optString("message"), chunk, quotes) }
        if (response.status !in 200..299) return failureFor(response.status, "", chunk, quotes)

        if (json.has("symbol")) {
            // One quote object: match it to the requested symbol by its own symbol field.
            val requested = wire[json.optString("symbol")] ?: chunk.singleOrNull()
            requested?.let { symbol -> parseQuote(symbol, json)?.let { quotes[symbol] = it } }
            return null
        }
        var matched = false
        for ((wireName, symbol) in wire) {
            val item = json.optJSONObject(wireName) ?: continue
            matched = true
            when (val code = errorCode(item)) {
                null -> parseQuote(symbol, item)?.let { quotes[symbol] = it }
                401 -> return PriceFailure.INVALID_KEY
                429 -> return PriceFailure.RATE_LIMITED
                // 403 plan, 404 not found, 400 symbol, others: this symbol stays unpriced.
                else -> Unit
            }
        }
        // An object that is neither a quote, an error nor keyed by our symbols: unexpected shape.
        return if (matched || json.length() == 0) null else PriceFailure.PROVIDER_ERROR
    }

    /** Maps a whole-request error; null continues with the chunk left unpriced. */
    private suspend fun failureFor(code: Int, message: String, chunk: List<String>, quotes: MutableMap<String, Quote>): PriceFailure? =
        when {
            code == 401 -> PriceFailure.INVALID_KEY
            code == 429 -> PriceFailure.RATE_LIMITED
            code == 403 || code == 404 -> null
            code == 400 && message.contains("symbol", ignoreCase = true) -> null
            code == 414 && chunk.size > 1 -> {
                val (first, second) = chunk.chunked((chunk.size + 1) / 2).let { it[0] to it[1] }
                request(first, quotes) ?: request(second, quotes)
            }
            else -> PriceFailure.PROVIDER_ERROR
        }

    private fun errorCode(json: JSONObject): Int? =
        if (json.optString("status") == "error") json.optInt("code", 500) else null

    private fun parseQuote(symbol: String, json: JSONObject): Quote? =
        try {
            val price = BigDecimal(json.getString("close"))
            val epoch = json.optLong("last_quote_at", 0L).takeIf { it > 0 } ?: json.optLong("timestamp", 0L)
            if (price.signum() <= 0 || epoch <= 0) {
                null
            } else {
                val previousClose = json.optString("previous_close").takeIf { it.isNotBlank() }?.let(::BigDecimal)?.takeIf { it.signum() > 0 }
                // Only USD holdings are requested; the currency is still taken from the answer, and a
                // mismatch leaves the holding unpriced (MainPage compares it with the holding's).
                val currency = json.optString("currency").ifBlank { "USD" }
                Quote(symbol, price, currency, Instant.ofEpochSecond(epoch), clock.instant(), previousClose)
            }
        } catch (e: JSONException) {
            null
        } catch (e: NumberFormatException) {
            null
        }

    companion object {
        const val BASE_URL = "https://api.twelvedata.com"
        const val AUTH_HEADER = "Authorization"

        /** IBKR writes share classes with a space ("BRK B"); Twelve Data uses a dot ("BRK.B"). */
        fun wireSymbol(ticker: String): String = ticker.trim().replace(Regex("""[ \-/]+"""), ".")
    }
}
