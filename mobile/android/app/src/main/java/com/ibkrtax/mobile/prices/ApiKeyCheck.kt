package com.ibkrtax.mobile.prices

sealed interface KeyCheckResult {
    data object Valid : KeyCheckResult

    /** The provider refused the key; it is not saved. */
    data object Rejected : KeyCheckResult

    /** The key could not be checked now (offline, rate limit, provider error); it is saved anyway. */
    data class Unverified(val reason: PriceFailure) : KeyCheckResult
}

/**
 * Checks a newly entered key with one request. The request carries only a well-known
 * test symbol and the key, never anything from the user's portfolio.
 */
object ApiKeyCheck {
    const val TEST_SYMBOL = "AAPL"

    suspend fun check(provider: MarketDataProvider): KeyCheckResult =
        when (val result = provider.latestQuotes(setOf(TEST_SYMBOL))) {
            // An accepted key may still lack a quote for the symbol; acceptance is what matters.
            is PriceResult.Success -> KeyCheckResult.Valid
            is PriceResult.Failure ->
                if (result.reason == PriceFailure.INVALID_KEY) KeyCheckResult.Rejected else KeyCheckResult.Unverified(result.reason)
        }
}
