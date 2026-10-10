package com.ibkrtax.mobile.testing

import com.ibkrtax.mobile.prices.PriceFailure
import com.ibkrtax.mobile.prices.PriceResult
import com.ibkrtax.mobile.prices.Quote
import java.io.IOException
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TestInfrastructureTest {
    @Test
    fun allSharedFixturesAreAvailable() {
        Fixtures.ALL.forEach { Fixtures.flexQuery(it) }
        assertTrue(Fixtures.expectedJson(Fixtures.VALID_BASIC).contains("\"trades\""))
        assertTrue(Fixtures.expectedJson(Fixtures.VALID_FOLLOWUP).contains("\"trades\""))
        assertEquals(0, Fixtures.flexQuery(Fixtures.EMPTY).size)
    }

    @Test
    fun fixturesContainOnlySyntheticAccountIdentifiers() {
        val accountLike = Regex("""\b[UF]\d{7,8}\b""")
        Fixtures.ALL.forEach { name ->
            val text = Fixtures.flexQuery(name).decodeToString()
            accountLike.findAll(text).forEach { assertTrue(name, it.value.matches(Regex("U0000000\\d"))) }
        }
    }

    @Test
    fun unitTestsCannotOpenNetworkConnections() {
        // Gradle routes test sockets through a closed local SOCKS port (see app/build.gradle.kts).
        assertThrows(IOException::class.java) {
            Socket().use { it.connect(InetSocketAddress("1.1.1.1", 443), 2_000) }
        }
    }

    @Test
    fun fakeMarketDataRecordsRequestedSymbolsAndFailures() = runBlocking {
        val time = Instant.parse("2024-02-12T15:00:00Z")
        val quote = Quote("AAPL", BigDecimal("190.10"), "USD", time, time)
        val prices = FakeMarketDataProvider().apply { setQuote(quote) }

        val result = prices.latestQuotes(setOf("AAPL", "MSFT"))
        assertEquals(PriceResult.Success(mapOf("AAPL" to quote)), result)
        assertEquals(listOf(setOf("AAPL", "MSFT")), prices.requests)

        prices.failure = PriceFailure.OFFLINE
        assertEquals(PriceResult.Failure(PriceFailure.OFFLINE), prices.latestQuotes(setOf("AAPL")))
    }
}
