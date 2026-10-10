package com.ibkrtax.mobile.prices

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * An exchange's regular session on weekdays (holidays are not modelled). [delayed] feeds get a
 * "delayed" label instead of the live rule (mobile-market-prices spec).
 */
data class Market(val yahooSuffix: String?, val zone: ZoneId, val open: LocalTime, val close: LocalTime, val delayed: Boolean) {
    fun isOpen(now: Instant): Boolean {
        val local = now.atZone(zone)
        return local.dayOfWeek.isWeekday() && !local.toLocalTime().isBefore(open) && local.toLocalTime().isBefore(close)
    }

    /** Close of the most recent session that has ended at [now]. */
    fun lastSessionClose(now: Instant): Instant {
        var day = now.atZone(zone).toLocalDate()
        while (true) {
            val close = day.atTime(close).atZone(zone)
            if (close.dayOfWeek.isWeekday() && !close.toInstant().isAfter(now)) return close.toInstant()
            day = day.minusDays(1)
        }
    }

    private fun DayOfWeek.isWeekday() = this != DayOfWeek.SATURDAY && this != DayOfWeek.SUNDAY
}

object Markets {
    /** NYSE/Nasdaq regular session; Finnhub and Yahoo's US quotes are treated as live. */
    val US = Market(null, ZoneId.of("America/New_York"), LocalTime.of(9, 30), LocalTime.of(16, 0), delayed = false)

    private fun europe(suffix: String, zone: String, open: String, close: String) =
        Market(suffix, ZoneId.of(zone), LocalTime.parse(open), LocalTime.parse(close), delayed = true)

    private val XETRA = europe(".DE", "Europe/Berlin", "09:00", "17:30")
    private val FRANKFURT = europe(".F", "Europe/Berlin", "08:00", "22:00")
    private val LONDON = europe(".L", "Europe/London", "08:00", "16:30")

    /** IBKR listing exchange code -> market; the table in the mobile-market-prices spec. */
    private val EUROPE: Map<String, Market> = mapOf(
        "IBIS" to XETRA,
        "IBIS2" to XETRA,
        "FWB" to FRANKFURT,
        "FWB2" to FRANKFURT,
        "SWB" to europe(".SG", "Europe/Berlin", "08:00", "22:00"),
        "LSE" to LONDON,
        "LSEETF" to LONDON,
        "AEB" to europe(".AS", "Europe/Amsterdam", "09:00", "17:30"),
        "SBF" to europe(".PA", "Europe/Paris", "09:00", "17:30"),
        "ENEXT.BE" to europe(".BR", "Europe/Brussels", "09:00", "17:30"),
        "BVL" to europe(".LS", "Europe/Lisbon", "08:00", "16:30"),
        "EBS" to europe(".SW", "Europe/Zurich", "09:00", "17:30"),
        "BVME" to europe(".MI", "Europe/Rome", "09:00", "17:30"),
        "BM" to europe(".MC", "Europe/Madrid", "09:00", "17:30"),
        "VSE" to europe(".VI", "Europe/Vienna", "09:00", "17:30"),
        "SFB" to europe(".ST", "Europe/Stockholm", "09:00", "17:30"),
        "CPH" to europe(".CO", "Europe/Copenhagen", "09:00", "17:00"),
        "OSE" to europe(".OL", "Europe/Oslo", "09:00", "16:20"),
        "HEX" to europe(".HE", "Europe/Helsinki", "10:00", "18:30"),
        "WSE" to europe(".WA", "Europe/Warsaw", "09:00", "17:00"),
    )
    private val BY_SUFFIX: Map<String, Market> = EUROPE.values.associateBy { it.yahooSuffix!! }

    /** The European market for an IBKR listing exchange code, or null when it is not supported. */
    fun european(exchange: String): Market? = EUROPE[exchange.trim().uppercase()]

    /** The market a requested symbol belongs to: European by its Yahoo suffix, otherwise US. */
    fun forSymbol(symbol: String): Market =
        symbol.lastIndexOf('.').takeIf { it > 0 }?.let { BY_SUFFIX[symbol.substring(it)] } ?: US

    fun isEuropean(symbol: String): Boolean = forSymbol(symbol) !== US

    /** IBKR ticker to Yahoo symbol: "BP." -> "BP.L", "NOVO B" -> "NOVO-B.CO". */
    fun europeanSymbol(ticker: String, market: Market): String =
        ticker.trim().trimEnd('.').replace(Regex("[ .]+"), "-") + market.yahooSuffix
}
