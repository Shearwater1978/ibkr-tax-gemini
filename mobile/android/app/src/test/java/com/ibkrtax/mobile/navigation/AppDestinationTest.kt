package com.ibkrtax.mobile.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTest {
    // Must stay identical to the iOS AppDestinationTests expectations (mobile/README.md).
    @Test
    fun destinationsMatchSharedNavigationContract() {
        assertEquals(listOf("portfolio", "dividends", "operations", "imports", "settings"), AppDestination.entries.map { it.route })
    }

    @Test
    fun startDestinationIsPortfolio() {
        assertEquals(AppDestination.PORTFOLIO, AppDestination.START)
    }
}
