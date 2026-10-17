package com.ibkrtax.mobile.prices

/**
 * The market-data provider the user chose (mobile-market-prices "Choose a price provider").
 * Stored by name; never rename an entry. [keySetting] names the encrypted settings row of its key.
 */
enum class PriceProviderId(val keySetting: String?) {
    /** No price requests to any provider, Yahoo Finance included. */
    NONE(null),
    FINNHUB("finnhub_api_key"),
    TWELVE_DATA("twelvedata_api_key"),
    ;

    companion object {
        /** The provider for a stored name; null when nothing or an unknown name is stored. */
        fun resolve(stored: String?): PriceProviderId? = entries.firstOrNull { it.name == stored }

        /** Without a stored choice, users from before the choice keep Finnhub if they have its key. */
        fun default(hasFinnhubKey: Boolean): PriceProviderId = if (hasFinnhubKey) FINNHUB else NONE
    }
}
