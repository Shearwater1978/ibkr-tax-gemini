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

    /** Start of the most recent session that has begun at [now]. */
    fun lastSessionStart(now: Instant): Instant {
        var day = now.atZone(ZONE).toLocalDate()
        while (true) {
            val start = day.atTime(OPEN).atZone(ZONE)
            if (start.isWeekday() && !start.toInstant().isAfter(now)) return start.toInstant()
            day = day.minusDays(1)
        }
    }

    private fun ZonedDateTime.isWeekday() = dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY
}

object PriceFreshnessRules {
    val MAX_LIVE_AGE: Duration = Duration.ofMinutes(15)

    fun classify(quoteTime: Instant, now: Instant, lastRefreshFailed: Boolean): PriceFreshness = when {
        lastRefreshFailed -> PriceFreshness.STALE
        UsMarketHours.isOpen(now) ->
            if (Duration.between(quoteTime, now) <= MAX_LIVE_AGE) PriceFreshness.LIVE else PriceFreshness.STALE
        !quoteTime.isBefore(UsMarketHours.lastSessionStart(now)) -> PriceFreshness.LATEST_CLOSE
        else -> PriceFreshness.STALE
    }
}
