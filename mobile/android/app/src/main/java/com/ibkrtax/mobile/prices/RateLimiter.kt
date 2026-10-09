package com.ibkrtax.mobile.prices

import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sliding-window limit on provider calls, shared across refreshes so repeated
 * refreshes cannot exceed the provider's per-minute quota.
 */
class RateLimiter(
    private val maxCalls: Int,
    private val window: Duration,
    private val clock: Clock = Clock.systemUTC(),
    private val sleep: suspend (Duration) -> Unit = { delay(it.toMillis()) },
) {
    private val calls = ArrayDeque<Instant>()
    private val mutex = Mutex()

    /** Suspends until another call fits in the window, then records it. */
    suspend fun acquire() = mutex.withLock {
        while (true) {
            val now = clock.instant()
            val windowStart = now.minus(window)
            while (calls.isNotEmpty() && !calls.first().isAfter(windowStart)) calls.removeFirst()
            if (calls.size < maxCalls) {
                calls.addLast(now)
                return@withLock
            }
            sleep(Duration.between(now, calls.first().plus(window)))
        }
    }

    companion object {
        /** Finnhub's free tier allows 60 calls per minute; keep some headroom. */
        fun forFinnhubFreeTier() = RateLimiter(maxCalls = 55, window = Duration.ofMinutes(1))
    }
}
