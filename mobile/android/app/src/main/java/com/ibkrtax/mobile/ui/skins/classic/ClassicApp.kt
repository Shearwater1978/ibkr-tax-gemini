package com.ibkrtax.mobile.ui.skins.classic

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.ibkrtax.mobile.state.AppStates
import com.ibkrtax.mobile.ui.common.ImportsScreen
import com.ibkrtax.mobile.ui.common.SettingsScreen
import com.ibkrtax.mobile.ui.skins.Skin
import com.ibkrtax.mobile.ui.skins.SkinId

/** Today's UI: five tabs, the positions table and full detail (mobile-skins spec, Classic). */
object ClassicSkin : Skin {
    override val id = SkinId.CLASSIC
    override val nameRes = R.string.skin_classic_name
    override val descriptionRes = R.string.skin_classic_description

    @Composable
    override fun App(states: AppStates) {
        MaterialTheme { ClassicApp(states) }
    }
}

@Composable
fun ClassicApp(states: AppStates, navController: NavHostController = rememberNavController()) {
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
                    state = states.portfolio,
                    onImportReport = { navController.navigateToTab(AppDestination.IMPORTS) },
                    onOpenSettings = { navController.navigateToTab(AppDestination.SETTINGS) },
                )
            }
            composable(AppDestination.DIVIDENDS.route) {
                DividendsScreen(states.income, onImportReport = { navController.navigateToTab(AppDestination.IMPORTS) })
            }
            composable(AppDestination.OPERATIONS.route) {
                OperationsScreen(states.activity, onImportReport = { navController.navigateToTab(AppDestination.IMPORTS) })
            }
            composable(AppDestination.IMPORTS.route) { ImportsScreen(states.data) }
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
    AppDestination.DIVIDENDS -> R.string.tab_dividends
    AppDestination.OPERATIONS -> R.string.tab_operations
    AppDestination.IMPORTS -> R.string.tab_imports
    AppDestination.SETTINGS -> R.string.tab_settings
}

@Suppress("DEPRECATION")
private fun AppDestination.icon(): ImageVector = when (this) {
    AppDestination.PORTFOLIO -> Icons.Filled.Home
    AppDestination.DIVIDENDS -> Icons.Filled.Star
    AppDestination.OPERATIONS -> Icons.Filled.DateRange
    AppDestination.IMPORTS -> Icons.Filled.List
    AppDestination.SETTINGS -> Icons.Filled.Settings
}
