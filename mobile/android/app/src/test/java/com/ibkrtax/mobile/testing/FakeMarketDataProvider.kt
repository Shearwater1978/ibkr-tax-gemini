package com.ibkrtax.mobile.testing

import com.ibkrtax.mobile.prices.MarketDataProvider
import com.ibkrtax.mobile.prices.PriceFailure
import com.ibkrtax.mobile.prices.PriceResult
import com.ibkrtax.mobile.prices.Quote

/** Programmable price source that records every request for data-minimization checks. */
class FakeMarketDataProvider(
    private val quotes: MutableMap<String, Quote> = mutableMapOf(),
) : MarketDataProvider {
    var failure: PriceFailure? = null

    val requests: MutableList<Set<String>> = mutableListOf()

    fun setQuote(quote: Quote) {
        quotes[quote.isin] = quote
    }

    override suspend fun latestQuotes(isins: Set<String>): PriceResult {
        requests += isins.toSet()
        failure?.let { return PriceResult.Failure(it) }
        return PriceResult.Success(quotes.filterKeys { it in isins })
    }
}
