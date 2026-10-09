package com.ibkrtax.mobile.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Display formatting only; values are always shown in their own currency. */
object Formats {
    fun money(value: BigDecimal, currency: String, locale: Locale = Locale.getDefault()): String =
        "${number(value, 2, 2, locale)} $currency"

    fun price(value: BigDecimal, currency: String, locale: Locale = Locale.getDefault()): String =
        "${number(value, 2, 4, locale)} $currency"

    fun quantity(value: BigDecimal, locale: Locale = Locale.getDefault()): String = number(value, 0, 8, locale)

    private fun number(value: BigDecimal, minFraction: Int, maxFraction: Int, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = minFraction
            maximumFractionDigits = maxFraction
            roundingMode = RoundingMode.HALF_EVEN
        }.format(value)
}
