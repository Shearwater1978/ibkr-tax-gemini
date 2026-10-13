package com.ibkrtax.mobile.ui

import com.ibkrtax.mobile.ui.common.ManualCurrencies
import com.ibkrtax.mobile.ui.common.plainDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the manual-entry form accepts as a number before validation (mobile-manual-operations Decision 4). */
class ManualEntryTextTest {
    @Test
    fun aCommaWorksAsTheDecimalSeparator() {
        assertEquals("150.25", plainDecimal(" 150,25 "))
        assertEquals("150.25", plainDecimal("150.25"))
        assertEquals("1500", plainDecimal("1 500"))
        // Ambiguous text stays as typed, so validation rejects it instead of guessing.
        assertEquals("1,500.25", plainDecimal("1,500.25"))
        assertEquals("1,2,3", plainDecimal("1,2,3"))
    }

    @Test
    fun commonCurrenciesAndOneOutsideTheList() {
        assertEquals(ManualCurrencies.COMMON, ManualCurrencies.choices("USD"))
        assertEquals("USD", ManualCurrencies.choices(null).first())
        assertEquals(listOf("ZAR") + ManualCurrencies.COMMON, ManualCurrencies.choices("ZAR"))
        assertEquals(ManualCurrencies.COMMON.size, ManualCurrencies.COMMON.toSet().size)
    }
}
