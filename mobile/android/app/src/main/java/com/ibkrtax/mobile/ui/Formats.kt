package com.ibkrtax.mobile.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Display formatting only; amounts are shown in the currency they are given in. */
object Formats {
    fun money(value: BigDecimal, currency: String, locale: Locale = Locale.getDefault()): String =
        "${number(value, 2, 2, locale)} $currency"

    fun signedMoney(value: BigDecimal, currency: String, locale: Locale = Locale.getDefault()): String =
        "${signedNumber(value, locale)} $currency"

    fun quantity(value: BigDecimal, locale: Locale = Locale.getDefault()): String = number(value, 0, 8, locale)

    fun signedNumber(value: BigDecimal): String = signedNumber(value, Locale.getDefault())

    fun signedNumber(value: BigDecimal, locale: Locale): String =
        (if (value.signum() > 0) "+" else "") + number(value, 2, 2, locale)

    fun signedPercent(value: BigDecimal, locale: Locale = Locale.getDefault()): String =
        (if (value.signum() > 0) "+" else "") + number(value, 2, 2, locale) + "%"

    /** Short chart labels: "0.5", "87", "1.2k", "15k". */
    fun compact(value: BigDecimal, locale: Locale = Locale.getDefault()): String {
        val abs = value.abs()
        return when {
            abs < BigDecimal.TEN -> number(value, 0, 1, locale)
            abs < BigDecimal(1_000) -> number(value, 0, 0, locale)
            abs < BigDecimal(10_000) -> number(value.movePointLeft(3), 0, 1, locale) + "k"
            else -> number(value.movePointLeft(3), 0, 0, locale) + "k"
        }
    }

    fun number(value: BigDecimal, minFraction: Int, maxFraction: Int, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = minFraction
            maximumFractionDigits = maxFraction
            roundingMode = RoundingMode.HALF_EVEN
        }.format(value)
}
