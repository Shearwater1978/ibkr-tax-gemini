package com.ibkrtax.mobile.prices

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RateLimiterTest {
    private var now = Instant.parse("2026-10-16T14:00:00Z")
    private val waits = mutableListOf<Duration>()
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
    }

    private fun limiter(credits: Int, window: Duration = Duration.ofMinutes(1)) =
        RateLimiter(credits, window, clock) { waits += it; now = now.plus(it) }

    @Test
    fun aBatchUsesOneCreditPerSymbol() = runBlocking {
        val limiter = limiter(8)
        limiter.acquire(8)
        limiter.acquire(8) // the second batch waits for the whole window
        limiter.acquire(4)
        assertEquals(listOf(Duration.ofMinutes(1), Duration.ofMinutes(1)), waits)
    }

    @Test
    fun creditsThatStillFitDoNotWait() = runBlocking {
        val limiter = limiter(8)
        limiter.acquire(3)
        limiter.acquire(5)
        assertEquals(emptyList<Duration>(), waits)
        limiter.acquire(1)
        assertEquals(listOf(Duration.ofMinutes(1)), waits)
    }

    @Test
    fun singleCallsKeepTheOldBehaviour() = runBlocking {
        val limiter = limiter(2)
        repeat(3) { limiter.acquire() }
        assertEquals(listOf(Duration.ofMinutes(1)), waits)
    }

    @Test
    fun aCallLargerThanTheQuotaIsRefused() {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { limiter(8).acquire(9) } }
    }

    @Test
    fun twelveDataFreeTierAllowsEightCreditsPerSlightlyLongerMinute() = runBlocking {
        val preset = RateLimiter.forTwelveDataFreeTier()
        preset.acquire(RateLimiter.TWELVE_DATA_FREE_CREDITS)
        assertEquals(8, RateLimiter.TWELVE_DATA_FREE_CREDITS)
    }
}
