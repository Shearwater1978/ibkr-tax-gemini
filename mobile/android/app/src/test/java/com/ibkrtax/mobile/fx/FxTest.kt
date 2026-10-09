package com.ibkrtax.mobile.fx

import com.ibkrtax.mobile.prices.HttpResponse
import com.ibkrtax.mobile.prices.HttpTransport
import java.io.IOException
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FxTest {
    // Synthetic table in NBP's format; the rates are made up.
    private val table = """
        [{"table":"A","no":"196/A/NBP/2026","effectiveDate":"2026-10-08","rates":[
          {"currency":"dolar amerykański","code":"USD","mid":4.0000},
          {"currency":"euro","code":"EUR","mid":4.4000},
          {"currency":"funt szterling","code":"GBP","mid":5.0000}
        ]}]
    """.trimIndent()

    private class RecordingTransport(private val respond: () -> HttpResponse) : HttpTransport {
        val urls = mutableListOf<String>()

        override fun get(url: String, headers: Map<String, String>): HttpResponse {
            urls += url
            return respond()
        }
    }

    private val rates = FxRates("2026-10-08", mapOf("USD" to BigDecimal("4.0000"), "EUR" to BigDecimal("4.4000")))

    @Test
    fun nbpRequestNamesNoCurrencyOrHolding() = runBlocking {
        val transport = RecordingTransport { HttpResponse(200, table) }
        val result = NbpClient(transport).latestTableA()!!

        assertEquals(listOf("https://api.nbp.pl/api/exchangerates/tables/A/?format=json"), transport.urls)
        assertEquals("2026-10-08", result.effectiveDate)
        assertEquals(0, BigDecimal("4.4").compareTo(result.plnPerUnit.getValue("EUR")))
    }

    @Test
    fun nbpFailuresReturnNoRates() = runBlocking {
        assertNull(NbpClient(RecordingTransport { HttpResponse(404, "Not Found") }).latestTableA())
        assertNull(NbpClient(RecordingTransport { HttpResponse(200, "not json") }).latestTableA())
        assertNull(NbpClient({ _, _ -> throw IOException("offline") }).latestTableA())
    }

    @Test
    fun convertsThroughPlnCrossRates() {
        // 100 EUR x 4.40 PLN / 4.00 PLN per USD = 110 USD
        assertEquals(0, BigDecimal("110").compareTo(rates.toUsd(BigDecimal("100"), "EUR")))
        assertEquals(0, BigDecimal("25").compareTo(rates.toUsd(BigDecimal("100"), "PLN")))
        assertEquals(0, BigDecimal("100").compareTo(rates.toUsd(BigDecimal("100"), "USD")))
        assertNull(rates.toUsd(BigDecimal("100"), "GBP"))
        assertNull(FxRates("2026-10-08", mapOf("EUR" to BigDecimal("4.4"))).toUsd(BigDecimal.ONE, "EUR"))
    }

    @Test
    fun failedRefreshKeepsCachedRates() = runBlocking {
        val cache = object : FxRateCache {
            var stored: FxRates? = rates

            override fun get() = stored

            override fun put(rates: FxRates) {
                stored = rates
            }
        }
        val service = FxService(NbpClient({ _, _ -> throw IOException("offline") }), cache)

        assertEquals(FxRefreshOutcome.Failed, service.refresh())
        assertEquals(rates, service.rates())
    }
}
