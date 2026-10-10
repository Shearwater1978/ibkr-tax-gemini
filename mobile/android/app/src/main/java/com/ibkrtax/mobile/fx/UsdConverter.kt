package com.ibkrtax.mobile.fx

import java.math.BigDecimal

/**
 * The one place where amounts become approximate USD: header totals, summary cards, dividends
 * and the USD view all use it, so they cannot disagree. Null when a needed NBP rate is missing.
 */
object UsdConverter {
    const val USD = "USD"

    fun toUsd(amount: BigDecimal, currency: String, rates: FxRates?): BigDecimal? =
        if (currency == USD) amount else rates?.toUsd(amount, currency)
}
