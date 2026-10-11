package com.ibkrtax.mobile.ui.skins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinCatalogTest {
    @Test
    fun everySkinSaysWhereEveryFeatureLives() {
        // mobile-skins "Keep feature parity": a skin may hide features behind taps, never drop them.
        for (skin in SkinCatalog.skins) {
            val missing = Feature.entries.filter { skin.features[it].isNullOrBlank() }
            assertTrue("${skin.id} misses $missing", missing.isEmpty())
        }
    }

    @Test
    fun catalogHasUniqueIdsAndTheAgreedDefaults() {
        assertEquals(SkinCatalog.skins.size, SkinCatalog.skins.map { it.id }.toSet().size)
        assertEquals(SkinId.CLASSIC, SkinCatalog.default.id)
        assertEquals(SkinId.CALM, SkinCatalog.firstLaunch.id)
    }

    @Test
    fun storedNamesResolveAndUnknownOnesFallBackToClassic() {
        assertEquals(SkinId.CALM, SkinCatalog.resolve("CALM").id)
        assertEquals(SkinId.CLASSIC, SkinCatalog.resolve("calm").id) // names are stored exactly
        assertEquals(SkinId.CLASSIC, SkinCatalog.resolve(null).id)
    }
}
