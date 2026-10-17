package com.ibkrtax.mobile.prices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Provider choice and keys (mobile-market-prices "Choose a price provider and use the user's own key"). */
class PriceProviderSelectionTest {
    @Test
    fun withoutAStoredChoiceAFinnhubKeyKeepsFinnhub() {
        assertEquals(PriceProviderId.FINNHUB, MemoryKeys(key = "finnhub-key").selected())
    }

    @Test
    fun withoutAStoredChoiceOrKeyNothingIsChosen() {
        assertEquals(PriceProviderId.NONE, MemoryKeys().selected())
        // A Twelve Data key alone does not choose Twelve Data.
        assertEquals(PriceProviderId.NONE, MemoryKeys().apply { set(PriceProviderId.TWELVE_DATA, "td") }.selected())
    }

    @Test
    fun aStoredChoiceWins() {
        assertEquals(PriceProviderId.NONE, MemoryKeys(key = "finnhub-key", choice = PriceProviderId.NONE).selected())
        assertEquals(PriceProviderId.TWELVE_DATA, MemoryKeys(choice = PriceProviderId.TWELVE_DATA).selected())
    }

    @Test
    fun keysAreKeptPerProvider() {
        val keys = MemoryKeys(key = "finnhub-key")
        keys.set(PriceProviderId.TWELVE_DATA, "twelve-key")
        assertEquals("finnhub-key", keys.get(PriceProviderId.FINNHUB))
        assertEquals("twelve-key", keys.get(PriceProviderId.TWELVE_DATA))
        keys.clear(PriceProviderId.FINNHUB)
        assertNull(keys.get(PriceProviderId.FINNHUB))
        assertEquals("twelve-key", keys.get(PriceProviderId.TWELVE_DATA))
        assertNull(keys.get(PriceProviderId.NONE))
    }

    @Test
    fun storedNamesResolveExactlyAndKeySettingsStayStable() {
        assertEquals(PriceProviderId.TWELVE_DATA, PriceProviderId.resolve("TWELVE_DATA"))
        assertNull(PriceProviderId.resolve("YAHOO"))
        assertNull(PriceProviderId.resolve(null))
        // The Finnhub row name predates the choice; changing it would lose existing keys.
        assertEquals("finnhub_api_key", PriceProviderId.FINNHUB.keySetting)
        assertEquals("twelvedata_api_key", PriceProviderId.TWELVE_DATA.keySetting)
    }
}
