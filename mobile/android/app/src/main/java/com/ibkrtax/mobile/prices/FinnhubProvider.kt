package com.ibkrtax.mobile.prices

import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

data class HttpResponse(val status: Int, val body: String)

/** Minimal HTTPS GET seam so tests never touch the network. Throws [IOException] when offline. */
fun interface HttpTransport {
    fun get(url: String, headers: Map<String, String>): HttpResponse
}

class UrlConnectionTransport(private val timeoutMillis: Int = 10_000) : HttpTransport {
    override fun get(url: String, headers: Map<String, String>): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            headers.forEach(connection::setRequestProperty)
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            return HttpResponse(status, stream?.use { it.readBytes().decodeToString() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Finnhub `/quote` adapter for US-listed symbols (quoted in USD). The user's key is
 * sent as a header so it never appears in URLs; the only query parameter is the symbol.
 */
class FinnhubProvider(
    private val apiKey: String,
    private val transport: HttpTransport = UrlConnectionTransport(),
    private val clock: Clock = Clock.systemUTC(),
    private val limiter: RateLimiter? = null,
) : MarketDataProvider {
    override suspend fun latestQuotes(symbols: Set<String>, onProgress: (Int) -> Unit): PriceResult = withContext(Dispatchers.IO) {
        val quotes = mutableMapOf<String, Quote>()
        fun failure(reason: PriceFailure) = PriceResult.Failure(reason, quotes.toMap())
        for ((index, symbol) in symbols.sorted().withIndex()) {
            limiter?.acquire()
            val response = try {
                transport.get(
                    "$BASE_URL/quote?symbol=${URLEncoder.encode(symbol, "UTF-8")}",
                    mapOf(TOKEN_HEADER to apiKey),
                )
            } catch (e: IOException) {
                return@withContext failure(PriceFailure.OFFLINE)
            }
            when (response.status) {
                in 200..299 -> parse(symbol, response.body)?.let { quotes[symbol] = it }
                401 -> return@withContext failure(PriceFailure.INVALID_KEY)
                // Symbol not covered by the user's plan: leave it unavailable.
                403 -> Unit
                429 -> return@withContext failure(PriceFailure.RATE_LIMITED)
                else -> return@withContext failure(PriceFailure.PROVIDER_ERROR)
            }
            onProgress(index + 1)
        }
        PriceResult.Success(quotes)
    }

    private fun parse(symbol: String, body: String): Quote? =
        try {
            val json = JSONObject(body)
            val price = BigDecimal(json.get("c").toString())
            val time = json.getLong("t")
            // Finnhub answers unknown symbols with zeros instead of an error.
            if (price.signum() <= 0 || time <= 0) {
                null
            } else {
                val previousClose = json.opt("pc")?.toString()?.let(::BigDecimal)?.takeIf { it.signum() > 0 }
                Quote(symbol, price, "USD", Instant.ofEpochSecond(time), clock.instant(), previousClose)
            }
        } catch (e: JSONException) {
            null
        } catch (e: NumberFormatException) {
            null
        }

    private companion object {
        const val BASE_URL = "https://finnhub.io/api/v1"
        const val TOKEN_HEADER = "X-Finnhub-Token"
    }
}
