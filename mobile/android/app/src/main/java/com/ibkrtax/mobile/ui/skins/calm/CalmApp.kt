package com.ibkrtax.mobile.ui.skins.calm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.state.AppStates
import com.ibkrtax.mobile.ui.common.ImportsScreen
import com.ibkrtax.mobile.ui.common.SettingsScreen
import com.ibkrtax.mobile.ui.skins.Feature
import com.ibkrtax.mobile.ui.skins.Skin
import com.ibkrtax.mobile.ui.skins.SkinId

/** Calm's palette (mobile-skin-calm): white, one blue accent, soft green/red chips. */
object CalmColors {
    val Accent = Color(0xFF2F6BFF)
    val AccentSoft = Color(0xFFE8EFFF)
    val Ink = Color(0xFF0F1419)
    val Muted = Color(0xFF6B7280)
    val Line = Color(0xFFECEEF1)
    val Card = Color(0xFFF6F7F9)
    val Gain = Color(0xFF0A8F5A)
    val GainSoft = Color(0xFFE5F6EE)
    val Loss = Color(0xFFD6453D)
    val LossSoft = Color(0xFFFDECEA)
}

@Composable
private fun CalmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = CalmColors.Accent,
            onPrimary = Color.White,
            primaryContainer = CalmColors.AccentSoft,
            background = Color.White,
            surface = Color.White,
            surfaceContainerLow = Color.White,
            onBackground = CalmColors.Ink,
            onSurface = CalmColors.Ink,
            surfaceVariant = CalmColors.Card,
            onSurfaceVariant = CalmColors.Muted,
            outlineVariant = CalmColors.Line,
            error = CalmColors.Loss,
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content,
    )
}

/** Light and minimal: Home, Income and Activity; import behind "+", settings behind the avatar. */
object CalmSkin : Skin {
    override val id = SkinId.CALM
    override val nameRes = R.string.skin_calm_name
    override val descriptionRes = R.string.skin_calm_description
    override val features = mapOf(
        Feature.PORTFOLIO_VALUE_AND_TODAY to "Home: hero value and today chip",
        Feature.POSITION_DETAILS to "Home: tap a holding (sheet)",
        Feature.SOLD_POSITIONS to "Home: View > Show sold positions",
        Feature.OWN_CURRENCY_AND_USD to "Home: View > Amounts in",
        Feature.TOP_MOVERS to "Home: View > Sort by today",
        Feature.DIVIDENDS_BY_YEAR_AND_MONTH to "Income tab",
        Feature.PAYER_HISTORY to "Income: tap a payer (sheet)",
        Feature.ACTIVITY_SEARCH_AND_FILTERS to "Activity tab",
        Feature.REPORT_IMPORT to "Home: + button",
        Feature.BACKUP_KEYS_AND_LOCATION to "Home: avatar > Settings",
        Feature.PRICE_KEY to "Home: avatar > Settings",
        Feature.DELETE_IMPORTS to "Home: + button (import screen)",
        Feature.APP_VERSION to "Home: avatar > Settings",
        Feature.SKIN_SETTING to "Home: avatar > Settings",
    )

    @Composable
    override fun App(states: AppStates) {
        CalmTheme { CalmNavigation(states) }
    }

    /** A big number, a green chip and a few rows on white. */
    @Composable
    override fun Preview(modifier: Modifier) {
        Column(modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.width(40.dp).height(10.dp).background(CalmColors.Ink, RoundedCornerShape(3.dp)))
            Box(Modifier.width(26.dp).height(6.dp).background(CalmColors.GainSoft, RoundedCornerShape(3.dp)))
            Spacer(Modifier.height(2.dp))
            repeat(5) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(7.dp).background(CalmColors.Accent, RoundedCornerShape(2.dp)))
                    Box(Modifier.weight(1f).height(4.dp).background(CalmColors.Line))
                    Box(Modifier.width(10.dp).height(4.dp).background(CalmColors.Gain))
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                repeat(3) { Box(Modifier.size(6.dp).background(CalmColors.Muted, CircleShape)) }
            }
        }
    }
}

private enum class CalmRoute(val route: String) { HOME("home"), INCOME("income"), ACTIVITY("activity"), IMPORT("import"), SETTINGS("settings") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalmNavigation(states: AppStates, nav: NavHostController = rememberNavController()) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = listOf(CalmRoute.HOME, CalmRoute.INCOME, CalmRoute.ACTIVITY)
    val onTab = route in tabs.map { it.route }
    Scaffold(
        containerColor = Color.White,
        topBar = {
            if (!onTab && route != null) {
                TopAppBar(
                    title = { Text(stringResource(if (route == CalmRoute.IMPORT.route) R.string.calm_add_reports else R.string.settings_title)) },
                    navigationIcon = {
                        @Suppress("DEPRECATION")
                        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.calm_back)) }
                    },
                )
            }
        },
        bottomBar = {
            if (onTab) {
                NavigationBar(containerColor = Color.White) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon(), contentDescription = null) },
                            label = { Text(stringResource(tab.label())) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = CalmColors.AccentSoft),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = CalmRoute.HOME.route, modifier = Modifier.padding(padding).fillMaxSize()) {
            composable(CalmRoute.HOME.route) {
                CalmHome(states.portfolio, onImport = { nav.navigate(CalmRoute.IMPORT.route) }, onSettings = { nav.navigate(CalmRoute.SETTINGS.route) })
            }
            composable(CalmRoute.INCOME.route) { CalmIncome(states.income, onImport = { nav.navigate(CalmRoute.IMPORT.route) }) }
            composable(CalmRoute.ACTIVITY.route) { CalmActivity(states.activity, onImport = { nav.navigate(CalmRoute.IMPORT.route) }) }
            composable(CalmRoute.IMPORT.route) { ImportsScreen(states.data) }
            composable(CalmRoute.SETTINGS.route) { SettingsScreen() }
        }
    }
}

private fun CalmRoute.label(): Int = when (this) {
    CalmRoute.HOME -> R.string.calm_tab_home
    CalmRoute.INCOME -> R.string.calm_tab_income
    else -> R.string.calm_tab_activity
}

@Suppress("DEPRECATION")
private fun CalmRoute.icon(): ImageVector = when (this) {
    CalmRoute.HOME -> Icons.Filled.Home
    CalmRoute.INCOME -> Icons.Filled.DateRange
    else -> Icons.Filled.List
}
