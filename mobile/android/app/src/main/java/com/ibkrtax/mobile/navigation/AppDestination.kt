package com.ibkrtax.mobile.navigation

/**
 * Top-level destinations, in tab order (see the navigation contract in mobile/README.md).
 * Must match the iOS `AppDestination` enum.
 */
enum class AppDestination(val route: String) {
    PORTFOLIO("portfolio"),
    DIVIDENDS("dividends"),
    OPERATIONS("operations"),
    IMPORTS("imports"),
    SETTINGS("settings");

    companion object {
        val START = PORTFOLIO
    }
}
