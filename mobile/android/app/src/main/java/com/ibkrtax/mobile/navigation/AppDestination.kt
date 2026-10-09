package com.ibkrtax.mobile.navigation

/**
 * Top-level destinations. Routes and order must match the iOS `AppDestination`
 * enum (see mobile/README.md) so both clients expose the same navigation.
 */
enum class AppDestination(val route: String) {
    PORTFOLIO("portfolio"),
    IMPORTS("imports"),
    SETTINGS("settings");

    companion object {
        val START = PORTFOLIO
    }
}
