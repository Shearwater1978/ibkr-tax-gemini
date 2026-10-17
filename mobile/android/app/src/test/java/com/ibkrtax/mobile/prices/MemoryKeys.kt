package com.ibkrtax.mobile.prices

/** In-memory [ApiKeyStore] for tests; [key] is the Finnhub key, as before the provider choice. */
internal class MemoryKeys(key: String? = null, var choice: PriceProviderId? = null) : ApiKeyStore {
    val keys = mutableMapOf<PriceProviderId, String>().apply { key?.let { put(PriceProviderId.FINNHUB, it) } }

    var key: String?
        get() = keys[PriceProviderId.FINNHUB]
        set(value) {
            if (value == null) keys.remove(PriceProviderId.FINNHUB) else keys[PriceProviderId.FINNHUB] = value
        }

    override fun get(provider: PriceProviderId) = keys[provider]

    override fun set(provider: PriceProviderId, key: String) {
        require(provider != PriceProviderId.NONE)
        keys[provider] = key
    }

    override fun clear(provider: PriceProviderId) {
        keys.remove(provider)
    }

    override fun storedChoice() = choice

    override fun choose(provider: PriceProviderId) {
        choice = provider
    }
}
