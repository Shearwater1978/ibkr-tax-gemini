package com.ibkrtax.mobile.prices

import java.time.Duration
import java.time.Instant

enum class PriceFreshness {
    /** Market open and the quote is at most 15 minutes old. */
    LIVE,

    /** Delayed feed (Yahoo's European quotes): market open and the quote is at most 30 minutes old. */
    DELAYED,

    /** Market closed and the quote is from the most recent session: shown as the latest close. */
    LATEST_CLOSE,

    /** Too old, or the last refresh failed: never presented as current. */
    STALE,
}

/** US regular session (NYSE/Nasdaq), 09:30-16:00 New York time on weekdays. Exchange holidays are not modelled. */
object UsMarketHours {
    fun isOpen(now: Instant): Boolean = Markets.US.isOpen(now)

    fun lastSessionClose(now: Instant): Instant = Markets.US.lastSessionClose(now)
}

object PriceFreshnessRules {
    val MAX_LIVE_AGE: Duration = Duration.ofMinutes(15)
    val MAX_DELAYED_AGE: Duration = Duration.ofMinutes(30)

    /** A quote this close to the session end is treated as the closing price. */
    private val CLOSE_TOLERANCE: Duration = Duration.ofMinutes(5)

    /** [market] is the quote's exchange (see [Markets.forSymbol]); its session decides live, delayed or close. */
    fun classify(quoteTime: Instant, now: Instant, lastRefreshFailed: Boolean, market: Market = Markets.US): PriceFreshness = when {
        lastRefreshFailed -> PriceFreshness.STALE
        market.isOpen(now) -> {
            val age = Duration.between(quoteTime, now)
            when {
                market.delayed && age <= MAX_DELAYED_AGE -> PriceFreshness.DELAYED
                !market.delayed && age <= MAX_LIVE_AGE -> PriceFreshness.LIVE
                else -> PriceFreshness.STALE
            }
        }
        !quoteTime.isBefore(market.lastSessionClose(now).minus(CLOSE_TOLERANCE)) -> PriceFreshness.LATEST_CLOSE
        else -> PriceFreshness.STALE
    }
}
