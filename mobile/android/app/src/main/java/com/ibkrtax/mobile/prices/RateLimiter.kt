package com.ibkrtax.mobile.prices

import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Something that makes a caller wait until [acquire]'s credits fit the provider's quota. */
fun interface CreditLimiter {
    suspend fun acquire(credits: Int)
}

/**
 * Sliding-window limit on provider credits, shared across refreshes so repeated refreshes
 * cannot exceed the provider's per-minute quota. A plain call costs one credit; a Twelve Data
 * batch costs one credit per symbol.
 */
class RateLimiter(
    private val maxCredits: Int,
    private val window: Duration,
    private val clock: Clock = Clock.systemUTC(),
    private val sleep: suspend (Duration) -> Unit = { delay(it.toMillis()) },
) : CreditLimiter {
    private val spent = ArrayDeque<Pair<Instant, Int>>()
    private val mutex = Mutex()

    /** Suspends until one more call fits in the window, then records it. */
    suspend fun acquire() = acquire(1)

    /** Suspends until [credits] more fit in the window, then records them. */
    override suspend fun acquire(credits: Int) = mutex.withLock {
        require(credits in 1..maxCredits) { "A call cannot cost $credits of $maxCredits credits" }
        while (true) {
            val now = clock.instant()
            val windowStart = now.minus(window)
            while (spent.isNotEmpty() && !spent.first().first.isAfter(windowStart)) spent.removeFirst()
            if (spent.sumOf { it.second } + credits <= maxCredits) {
                spent.addLast(now to credits)
                return@withLock
            }
            sleep(Duration.between(now, spent.first().first.plus(window)))
        }
    }

    companion object {
        /** Finnhub's free tier allows 60 calls per minute; keep some headroom. */
        fun forFinnhubFreeTier() = RateLimiter(maxCredits = 55, window = Duration.ofMinutes(1))

        /**
         * Twelve Data's free plan: 8 credits per minute (one per symbol). The quota refills at the
         * start of each minute on their clock, so the window is a second longer to absorb clock drift.
         */
        const val TWELVE_DATA_FREE_CREDITS = 8

        fun forTwelveDataFreeTier() = RateLimiter(maxCredits = TWELVE_DATA_FREE_CREDITS, window = Duration.ofSeconds(61))
    }
}
