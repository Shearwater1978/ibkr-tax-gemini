package com.ibkrtax.mobile.prices

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class PriceFreshness {
    /** Market open and the quote is at most 15 minutes old. */
    LIVE,

    /** Market closed and the quote is from the most recent session: shown as the latest close. */
    LATEST_CLOSE,

    /** Too old, or the last refresh failed: never presented as current. */
    STALE,
}

/** US regular session (NYSE/Nasdaq), 09:30-16:00 New York time on weekdays. Exchange holidays are not modelled. */
object UsMarketHours {
    private val ZONE: ZoneId = ZoneId.of("America/New_York")
    private val OPEN: LocalTime = LocalTime.of(9, 30)
    private val CLOSE: LocalTime = LocalTime.of(16, 0)

    fun isOpen(now: Instant): Boolean {
        val local = now.atZone(ZONE)
        return local.isWeekday() && !local.toLocalTime().isBefore(OPEN) && local.toLocalTime().isBefore(CLOSE)
    }

    /** Close of the most recent session that has ended at [now]. */
    fun lastSessionClose(now: Instant): Instant {
        var day = now.atZone(ZONE).toLocalDate()
        while (true) {
            val close = day.atTime(CLOSE).atZone(ZONE)
            if (close.isWeekday() && !close.toInstant().isAfter(now)) return close.toInstant()
            day = day.minusDays(1)
        }
    }

    private fun ZonedDateTime.isWeekday() = dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY
}

object PriceFreshnessRules {
    val MAX_LIVE_AGE: Duration = Duration.ofMinutes(15)

    /** A quote this close to the session end is treated as the closing price. */
    private val CLOSE_TOLERANCE: Duration = Duration.ofMinutes(5)

    fun classify(quoteTime: Instant, now: Instant, lastRefreshFailed: Boolean): PriceFreshness = when {
        lastRefreshFailed -> PriceFreshness.STALE
        UsMarketHours.isOpen(now) ->
            if (Duration.between(quoteTime, now) <= MAX_LIVE_AGE) PriceFreshness.LIVE else PriceFreshness.STALE
        !quoteTime.isBefore(UsMarketHours.lastSessionClose(now).minus(CLOSE_TOLERANCE)) -> PriceFreshness.LATEST_CLOSE
        else -> PriceFreshness.STALE
    }
}
