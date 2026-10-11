package com.ibkrtax.mobile.ui.common

import androidx.compose.ui.graphics.Color
import com.ibkrtax.mobile.ui.Formats
import java.math.BigDecimal

/** Own-currency amounts with a symbol where one is common ("$1,234.50", "€12.00"), else the code. */
fun skinMoney(value: BigDecimal, currency: String, signed: Boolean = false): String {
    val symbol = mapOf("USD" to "$", "EUR" to "€", "GBP" to "£")[currency]
    val sign = when {
        signed && value.signum() > 0 -> "+"
        value.signum() < 0 -> "−"
        else -> ""
    }
    val number = Formats.number(value.abs(), 2, 2)
    return if (symbol != null) "$sign$symbol$number" else "$sign$number $currency"
}

fun skinPercent(value: BigDecimal): String = Formats.signedPercent(value).replace("-", "−")

/** A stable colour per ticker for the letter tiles. */
fun tickerColor(ticker: String): Color {
    val palette = listOf(0xFF111111, 0xFF2B8A3E, 0xFF0A66C2, 0xFFE11D48, 0xFF1D4ED8, 0xFF0B3D91, 0xFFB45309, 0xFF7C3AED, 0xFF0E7490)
    return Color(palette[Math.floorMod(ticker.hashCode(), palette.size)])
}
