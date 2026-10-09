package com.ibkrtax.mobile.fx

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * NBP table A mid rates (PLN per one unit of each currency) and their table date.
 * Used only for the informational USD total, never for tax results (which use the
 * desktop T-1 rule).
 */
data class FxRates(val effectiveDate: String, val plnPerUnit: Map<String, BigDecimal>) {
    /** Converts through PLN cross rates; null when a needed rate is missing. */
    fun toUsd(amount: BigDecimal, currency: String): BigDecimal? {
        if (currency == USD) return amount
        val usd = rate(USD) ?: return null
        val from = rate(currency) ?: return null
        return amount.multiply(from, PY).divide(usd, PY)
    }

    private fun rate(currency: String): BigDecimal? = if (currency == PLN) BigDecimal.ONE else plnPerUnit[currency]

    private companion object {
        const val USD = "USD"
        const val PLN = "PLN"
        val PY = MathContext(28, RoundingMode.HALF_EVEN)
    }
}

/** Cached NBP rates; implementations keep them in the encrypted database. */
interface FxRateCache {
    fun get(): FxRates?

    fun put(rates: FxRates)
}

sealed interface FxRefreshOutcome {
    data object Updated : FxRefreshOutcome

    /** NBP could not be reached or answered unexpectedly; cached rates (if any) stay in use. */
    data object Failed : FxRefreshOutcome
}

class FxService(
    private val client: NbpClient,
    private val cache: FxRateCache,
) {
    suspend fun refresh(): FxRefreshOutcome {
        val rates = client.latestTableA() ?: return FxRefreshOutcome.Failed
        cache.put(rates)
        return FxRefreshOutcome.Updated
    }

    fun rates(): FxRates? = cache.get()
}
