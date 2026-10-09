package com.ibkrtax.mobile.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.navigation.AppDestination
import com.ibkrtax.mobile.ui.screens.ImportsScreen
import com.ibkrtax.mobile.ui.screens.PortfolioScreen
import com.ibkrtax.mobile.ui.screens.SettingsScreen

@Composable
fun AppScaffold(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = { navController.navigateToTab(destination) },
                        icon = { Icon(destination.icon(), contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes())) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.START.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestination.PORTFOLIO.route) {
                PortfolioScreen(
                    onImportReport = { navController.navigateToTab(AppDestination.IMPORTS) },
                    onOpenSettings = { navController.navigateToTab(AppDestination.SETTINGS) },
                )
            }
            composable(AppDestination.IMPORTS.route) { ImportsScreen() }
            composable(AppDestination.SETTINGS.route) { SettingsScreen() }
        }
    }
}

private fun NavHostController.navigateToTab(destination: AppDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun AppDestination.labelRes(): Int = when (this) {
    AppDestination.PORTFOLIO -> R.string.tab_portfolio
    AppDestination.IMPORTS -> R.string.tab_imports
    AppDestination.SETTINGS -> R.string.tab_settings
}

@Suppress("DEPRECATION")
private fun AppDestination.icon(): ImageVector = when (this) {
    AppDestination.PORTFOLIO -> Icons.Filled.Home
    AppDestination.IMPORTS -> Icons.Filled.List
    AppDestination.SETTINGS -> Icons.Filled.Settings
}
