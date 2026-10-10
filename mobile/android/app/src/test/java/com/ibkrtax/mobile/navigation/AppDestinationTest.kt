package com.ibkrtax.mobile.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTest {
    // iOS AppDestinationTests adopt this order when iOS work resumes (mobile/README.md).
    @Test
    fun destinationsMatchSharedNavigationContract() {
        assertEquals(listOf("portfolio", "operations", "imports", "settings"), AppDestination.entries.map { it.route })
    }

    @Test
    fun startDestinationIsPortfolio() {
        assertEquals(AppDestination.PORTFOLIO, AppDestination.START)
    }
}
