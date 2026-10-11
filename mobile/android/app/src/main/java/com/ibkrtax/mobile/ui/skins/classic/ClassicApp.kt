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
import com.ibkrtax.mobile.ui.skins.Feature
import com.ibkrtax.mobile.ui.skins.SkinId
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Today's UI: five tabs, the positions table and full detail (mobile-skins spec, Classic). */
object ClassicSkin : Skin {
    override val id = SkinId.CLASSIC
    override val nameRes = R.string.skin_classic_name
    override val descriptionRes = R.string.skin_classic_description
    override val features = mapOf(
        Feature.PORTFOLIO_VALUE_AND_TODAY to "Portfolio: summary cards",
        Feature.POSITION_DETAILS to "Portfolio: tap a row",
        Feature.SOLD_POSITIONS to "Portfolio: Show sold positions",
        Feature.OWN_CURRENCY_AND_USD to "Portfolio: Own currency / USD switch",
        Feature.TOP_MOVERS to "Portfolio: Today's top movers",
        Feature.DIVIDENDS_BY_YEAR_AND_MONTH to "Dividends tab",
        Feature.PAYER_HISTORY to "Dividends: tap a position; position details: Dividend history",
        Feature.ACTIVITY_SEARCH_AND_FILTERS to "Operations tab",
        Feature.REPORT_IMPORT to "Imports tab",
        Feature.BACKUP_KEYS_AND_LOCATION to "Settings tab",
        Feature.PRICE_KEY to "Settings tab",
        Feature.DELETE_IMPORTS to "Imports tab",
        Feature.APP_VERSION to "Settings tab",
        Feature.SKIN_SETTING to "Settings tab",
    )

    @Composable
    override fun App(states: AppStates) {
        MaterialTheme { ClassicApp(states) }
    }

    /** A grey table: cards on top, then rows of five columns. */
    @Composable
    override fun Preview(modifier: Modifier) {
        val line = MaterialTheme.colorScheme.outlineVariant
        Column(modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) { Box(Modifier.weight(1f).height(14.dp).background(line, RoundedCornerShape(3.dp))) }
            }
            repeat(9) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(5) { Box(Modifier.weight(1f).height(4.dp).background(line)) }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                repeat(5) { Box(Modifier.size(5.dp).background(line, RoundedCornerShape(3.dp))) }
            }
        }
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
                    onOpenDividends = { navController.navigateToTab(AppDestination.DIVIDENDS) },
                    income = states.income,
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
