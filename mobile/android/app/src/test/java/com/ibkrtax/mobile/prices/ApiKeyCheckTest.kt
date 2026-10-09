package com.ibkrtax.mobile.prices

import com.ibkrtax.mobile.testing.FakeMarketDataProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiKeyCheckTest {
    @Test
    fun acceptedKeyIsValidAndOnlyTheTestSymbolIsSent() = runBlocking {
        val provider = FakeMarketDataProvider()

        assertEquals(KeyCheckResult.Valid, ApiKeyCheck.check(provider))
        assertEquals(listOf(setOf("AAPL")), provider.requests)
    }

    @Test
    fun rejectedKeyIsReported() = runBlocking {
        val provider = FakeMarketDataProvider().apply { failure = PriceFailure.INVALID_KEY }
        assertEquals(KeyCheckResult.Rejected, ApiKeyCheck.check(provider))
    }

    @Test
    fun offlineOrRateLimitedMeansUnverified() = runBlocking {
        for (reason in listOf(PriceFailure.OFFLINE, PriceFailure.RATE_LIMITED, PriceFailure.PROVIDER_ERROR)) {
            val provider = FakeMarketDataProvider().apply { failure = reason }
            assertEquals(KeyCheckResult.Unverified(reason), ApiKeyCheck.check(provider))
        }
    }
}
