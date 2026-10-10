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
 * Keyless fallback used while the user has no Finnhub key: Yahoo Finance's public chart
 * endpoint. It is unofficial and not licensed for this use (mobile-market-prices spec), so
 * it can throttle or change without notice. The only data sent is the symbol.
 */
class YahooProvider(
    private val transport: HttpTransport = UrlConnectionTransport(),
    private val clock: Clock = Clock.systemUTC(),
    private val limiter: RateLimiter? = null,
) : MarketDataProvider {
    override suspend fun latestQuotes(symbols: Set<String>): PriceResult = withContext(Dispatchers.IO) {
        val quotes = mutableMapOf<String, Quote>()
        fun failure(reason: PriceFailure) = PriceResult.Failure(reason, quotes.toMap())
        for (symbol in symbols.sorted()) {
            limiter?.acquire()
            val response = try {
                transport.get("$BASE_URL/${URLEncoder.encode(yahooSymbol(symbol), "UTF-8")}?range=1d&interval=1d", HEADERS)
            } catch (e: IOException) {
                return@withContext failure(PriceFailure.OFFLINE)
            }
            when (response.status) {
                in 200..299 -> parse(symbol, response.body)?.let { quotes[symbol] = it }
                // Unknown symbol: leave it unavailable.
                404 -> Unit
                429 -> return@withContext failure(PriceFailure.RATE_LIMITED)
                else -> return@withContext failure(PriceFailure.PROVIDER_ERROR)
            }
        }
        PriceResult.Success(quotes)
    }

    private fun parse(symbol: String, body: String): Quote? =
        try {
            val meta = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
            val price = meta.decimal("regularMarketPrice")
            val time = meta.optLong("regularMarketTime")
            // The scope is USD holdings of US listings; another currency is a different listing.
            if (price == null || price.signum() <= 0 || time <= 0 || meta.optString("currency") != "USD") {
                null
            } else {
                val previousClose = (meta.decimal("previousClose") ?: meta.decimal("chartPreviousClose"))?.takeIf { it.signum() > 0 }
                Quote(symbol, price, "USD", Instant.ofEpochSecond(time), clock.instant(), previousClose)
            }
        } catch (e: JSONException) {
            null
        } catch (e: NumberFormatException) {
            null
        }

    private fun JSONObject.decimal(name: String): BigDecimal? =
        opt(name)?.takeIf { it != JSONObject.NULL }?.toString()?.let(::BigDecimal)

    companion object {
        private const val BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart"

        // Yahoo answers 429 to requests without a browser-like user agent.
        private val HEADERS = mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36")

        /** IBKR writes share classes as "BRK B" (Finnhub: "BRK.B"); Yahoo uses "BRK-B". */
        fun yahooSymbol(symbol: String): String = symbol.trim().replace(Regex("[ .]+"), "-")
    }
}
