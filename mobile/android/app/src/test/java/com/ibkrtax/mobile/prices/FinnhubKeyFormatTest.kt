package com.ibkrtax.mobile.prices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinnhubKeyFormatTest {
    @Test
    fun acceptsASingleAlphanumericToken() {
        assertEquals("abcdefghij0123456789", FinnhubKeyFormat.candidate("abcdefghij0123456789"))
        assertEquals("abcdefghij0123456789", FinnhubKeyFormat.candidate("  abcdefghij0123456789\n"))
    }

    @Test
    fun ignoresOtherClipboardContent() {
        assertNull(FinnhubKeyFormat.candidate(null))
        assertNull(FinnhubKeyFormat.candidate(""))
        assertNull(FinnhubKeyFormat.candidate("short123"))
        assertNull(FinnhubKeyFormat.candidate("two words abcdefghij0123456789"))
        assertNull(FinnhubKeyFormat.candidate("https://finnhub.io/dashboard"))
        assertNull(FinnhubKeyFormat.candidate("someone@example.com"))
    }
}
