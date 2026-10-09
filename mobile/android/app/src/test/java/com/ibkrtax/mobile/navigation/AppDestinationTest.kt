package com.ibkrtax.mobile.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTest {
    // Must stay identical to the iOS AppDestinationTests expectations.
    @Test
    fun destinationsMatchSharedNavigationContract() {
        assertEquals(listOf("portfolio", "imports", "settings"), AppDestination.entries.map { it.route })
    }

    @Test
    fun startDestinationIsPortfolio() {
        assertEquals(AppDestination.PORTFOLIO, AppDestination.START)
    }
}
