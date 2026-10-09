package com.ibkrtax.mobile.fx

import com.ibkrtax.mobile.prices.HttpTransport
import com.ibkrtax.mobile.prices.UrlConnectionTransport
import java.io.IOException
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

/**
 * Reads the latest NBP table A. The request names no currency, account, or holding:
 * the whole public table is fetched, and no key is needed.
 */
class NbpClient(private val transport: HttpTransport = UrlConnectionTransport()) {
    suspend fun latestTableA(): FxRates? = withContext(Dispatchers.IO) {
        val response = try {
            transport.get(URL, mapOf("Accept" to "application/json"))
        } catch (e: IOException) {
            return@withContext null
        }
        if (response.status !in 200..299) return@withContext null
        parse(response.body)
    }

    private fun parse(body: String): FxRates? =
        try {
            val table = JSONArray(body).getJSONObject(0)
            val rates = table.getJSONArray("rates")
            val mids = (0 until rates.length()).associate { i ->
                val rate = rates.getJSONObject(i)
                rate.getString("code") to BigDecimal(rate.get("mid").toString())
            }.filterValues { it.signum() > 0 }
            FxRates(table.getString("effectiveDate"), mids).takeIf { mids.isNotEmpty() }
        } catch (e: JSONException) {
            null
        } catch (e: NumberFormatException) {
            null
        }

    private companion object {
        const val URL = "https://api.nbp.pl/api/exchangerates/tables/A/?format=json"
    }
}
