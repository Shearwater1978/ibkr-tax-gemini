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
 * TEMPORARY, INTERNAL source: Yahoo Finance's public endpoints. It is unofficial and not
 * licensed for this use (mobile-market-prices spec), so it can throttle or change without notice,
 * and it is never offered as a price provider. Since the provider choice it only prices European
 * listings, and only while Finnhub or Twelve Data is chosen; it will be replaced (whether to remove
 * it before a Play release is decided separately). The only data sent is the symbols.
 *
 * US symbols (no longer requested by PriceService): one spark request covers up to [BATCH_SIZE] of them. Spark has no currency field;
 * symbols without an exchange suffix are US listings, which Yahoo quotes in USD.
 * European symbols (e.g. SAP.DE): one chart request each, because the chart reports the currency.
 */
class YahooProvider(
    private val transport: HttpTransport = UrlConnectionTransport(),
    private val clock: Clock = Clock.systemUTC(),
    private val limiter: RateLimiter? = null,
) : MarketDataProvider {
    override suspend fun latestQuotes(symbols: Set<String>, onProgress: (Int) -> Unit): PriceResult = withContext(Dispatchers.IO) {
        val quotes = mutableMapOf<String, Quote>()
        fun failure(reason: PriceFailure) = PriceResult.Failure(reason, quotes.toMap())
        var requested = 0
        val (european, us) = symbols.sorted().partition(Markets::isEuropean)
        for (batch in us.chunked(BATCH_SIZE)) {
            limiter?.acquire()
            val query = batch.map(::yahooSymbol).distinct().joinToString(",") { URLEncoder.encode(it, "UTF-8") }
            val response = try {
                transport.get("$BASE_URL?symbols=$query&range=1d&interval=5m", HEADERS)
            } catch (e: IOException) {
                return@withContext failure(PriceFailure.OFFLINE)
            }
            when (response.status) {
                in 200..299 -> quotes += parse(batch, response.body)
                // None of the symbols is known: leave them unavailable.
                404 -> Unit
                429 -> return@withContext failure(PriceFailure.RATE_LIMITED)
                else -> return@withContext failure(PriceFailure.PROVIDER_ERROR)
            }
            requested += batch.size
            onProgress(requested)
        }
        for (symbol in european) {
            limiter?.acquire()
            val response = try {
                transport.get("$CHART_URL/${URLEncoder.encode(symbol, "UTF-8")}?range=1d&interval=1d", HEADERS)
            } catch (e: IOException) {
                return@withContext failure(PriceFailure.OFFLINE)
            }
            when (response.status) {
                in 200..299 -> chartQuote(symbol, response.body)?.let { quotes[symbol] = it }
                404 -> Unit
                429 -> return@withContext failure(PriceFailure.RATE_LIMITED)
                else -> return@withContext failure(PriceFailure.PROVIDER_ERROR)
            }
            requested++
            onProgress(requested)
        }
        PriceResult.Success(quotes)
    }

    /** A European quote in its own currency; pence (GBp) become pounds so they match GBP holdings. */
    private fun chartQuote(symbol: String, body: String): Quote? =
        try {
            val meta = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
            val pence = meta.optString("currency") in PENCE
            fun amount(name: String) = meta.decimal(name)?.let { if (pence) it.movePointLeft(2) else it }
            val price = amount("regularMarketPrice")
            val time = meta.optLong("regularMarketTime")
            val currency = if (pence) "GBP" else meta.optString("currency")
            if (price == null || price.signum() <= 0 || time <= 0 || currency.isEmpty()) {
                null
            } else {
                val previousClose = (amount("previousClose") ?: amount("chartPreviousClose"))?.takeIf { it.signum() > 0 }
                Quote(symbol, price, currency, Instant.ofEpochSecond(time), clock.instant(), previousClose)
            }
        } catch (e: JSONException) {
            null
        } catch (e: NumberFormatException) {
            null
        }

    /** Unknown symbols are absent from the response and stay unavailable. */
    private fun parse(batch: List<String>, body: String): Map<String, Quote> {
        val json = try {
            JSONObject(body)
        } catch (e: JSONException) {
            return emptyMap()
        }
        val retrievedAt = clock.instant()
        return batch.mapNotNull { symbol ->
            json.optJSONObject(yahooSymbol(symbol))?.let { quote(symbol, it, retrievedAt) }?.let { symbol to it }
        }.toMap()
    }

    private fun quote(symbol: String, series: JSONObject, retrievedAt: Instant): Quote? =
        try {
            val times = series.getJSONArray("timestamp")
            val closes = series.getJSONArray("close")
            // The latest 5-minute bar with a trade; bars without trades have a null close.
            val last = (minOf(times.length(), closes.length()) - 1 downTo 0).firstOrNull { !closes.isNull(it) }
            val price = last?.let { BigDecimal(closes.get(it).toString()) }
            val time = last?.let { times.getLong(it) } ?: 0
            if (price == null || price.signum() <= 0 || time <= 0) {
                null
            } else {
                val previousClose = (series.decimal("previousClose") ?: series.decimal("chartPreviousClose"))?.takeIf { it.signum() > 0 }
                Quote(symbol, price, "USD", Instant.ofEpochSecond(time), retrievedAt, previousClose)
            }
        } catch (e: JSONException) {
            null
        } catch (e: NumberFormatException) {
            null
        }

    private fun JSONObject.decimal(name: String): BigDecimal? =
        opt(name)?.takeIf { it != JSONObject.NULL }?.toString()?.let(::BigDecimal)

    companion object {
        private const val BASE_URL = "https://query1.finance.yahoo.com/v8/finance/spark"
        private const val CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart"
        private val PENCE = setOf("GBp", "GBX")

        /** Yahoo refuses spark requests with more than 20 symbols. */
        const val BATCH_SIZE = 20

        // Yahoo answers 429 to requests without a browser-like user agent.
        private val HEADERS = mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36")

        /** IBKR writes share classes as "BRK B" (Finnhub: "BRK.B"); Yahoo uses "BRK-B". */
        fun yahooSymbol(symbol: String): String = symbol.trim().replace(Regex("[ .]+"), "-")
    }
}
