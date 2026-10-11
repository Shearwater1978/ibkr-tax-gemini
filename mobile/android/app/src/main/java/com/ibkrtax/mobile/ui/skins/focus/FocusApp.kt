package com.ibkrtax.mobile.ui.skins.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.keys.BackupKeyStatus
import com.ibkrtax.mobile.prices.PriceSource
import com.ibkrtax.mobile.state.AppStates
import com.ibkrtax.mobile.state.PortfolioUiState
import com.ibkrtax.mobile.ui.common.ImportsScreen
import com.ibkrtax.mobile.ui.common.SettingsScreen
import com.ibkrtax.mobile.ui.common.UiPreferences
import com.ibkrtax.mobile.ui.skins.Feature
import com.ibkrtax.mobile.ui.skins.Skin
import com.ibkrtax.mobile.ui.skins.SkinId

/** Focus's warm palette (mobile-skin-focus); light only, serif headlines. */
object FocusColors {
    val Page = Color(0xFFFBF8F3)
    val Card = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1D1B16)
    val Muted = Color(0xFF6B655A)
    val Line = Color(0xFFEBE5DA)
    val Sand = Color(0xFFEFE7D9)
    val Deep = Color(0xFF24304A)
    val DeepMuted = Color(0xFFB9C2D6)
    val Gain = Color(0xFF2E7D4F)
    val GainSoft = Color(0xFFE3F1E8)
    val Loss = Color(0xFFB93C0A)
    val LossSoft = Color(0xFFFBE9DF)
    val Slices = listOf(Deep, Color(0xFFD97706), Gain, Color(0xFF9A6FB0), Color(0xFFC9BFAE))
}

internal val Serif = FontFamily.Serif

@Composable
private fun FocusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = FocusColors.Ink,
            onPrimary = Color.White,
            background = FocusColors.Page,
            surface = FocusColors.Page,
            surfaceContainerLow = FocusColors.Page,
            onBackground = FocusColors.Ink,
            onSurface = FocusColors.Ink,
            surfaceVariant = FocusColors.Sand,
            onSurfaceVariant = FocusColors.Muted,
            outlineVariant = FocusColors.Line,
            error = FocusColors.Loss,
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(26.dp)),
        content = content,
    )
}

/** Words first: Today and Income, with everything about data behind a central "+". */
object FocusSkin : Skin {
    override val id = SkinId.FOCUS
    override val nameRes = R.string.skin_focus_name
    override val descriptionRes = R.string.skin_focus_description
    override val features = mapOf(
        Feature.PORTFOLIO_VALUE_AND_TODAY to "Today: value and summary sentence",
        Feature.POSITION_DETAILS to "Today: What you own > a holding (story)",
        Feature.SOLD_POSITIONS to "Today: What you own > Sold",
        Feature.OWN_CURRENCY_AND_USD to "+ menu: Show amounts in",
        Feature.TOP_MOVERS to "Today: Biggest move card; holdings Rising / Falling today",
        Feature.DIVIDENDS_BY_YEAR_AND_MONTH to "Income: year pills and monthly timeline with tax",
        Feature.PAYER_HISTORY to "Income: tap a payment; story: Dividend history (sheet)",
        Feature.ACTIVITY_SEARCH_AND_FILTERS to "+ menu: Activity and operations",
        Feature.REPORT_IMPORT to "+ menu: Add reports",
        Feature.BACKUP_KEYS_AND_LOCATION to "+ menu: Security and recovery code",
        Feature.PRICE_KEY to "Today: Settings button, or + menu: Settings and skins",
        Feature.DELETE_IMPORTS to "+ menu: Delete imported data",
        Feature.APP_VERSION to "Today: Settings button, or + menu: Settings and skins",
        Feature.SKIN_SETTING to "Today: Settings button, or + menu: Settings and skins",
    )

    @Composable
    override fun App(states: AppStates) {
        FocusTheme { FocusNavigation(states) }
    }

    /** A serif number, a sentence, a donut and the "+" bar on warm paper. */
    @Composable
    override fun Preview(modifier: Modifier) {
        Column(modifier.clip(RoundedCornerShape(10.dp)).background(FocusColors.Page).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.width(38.dp).height(10.dp).background(FocusColors.Ink, RoundedCornerShape(3.dp)))
            repeat(2) { Box(Modifier.fillMaxWidth().height(4.dp).background(FocusColors.Muted.copy(alpha = 0.5f), RoundedCornerShape(2.dp))) }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.width(26.dp).height(16.dp).background(FocusColors.Deep, RoundedCornerShape(4.dp)))
                Box(Modifier.width(20.dp).height(16.dp).background(FocusColors.Card, RoundedCornerShape(4.dp)))
            }
            Box(Modifier.size(24.dp).background(FocusColors.Slices[1], CircleShape).padding(6.dp).background(FocusColors.Page, CircleShape))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(6.dp).background(FocusColors.Muted, CircleShape))
                Box(Modifier.size(11.dp).background(FocusColors.Ink, RoundedCornerShape(3.dp)))
                Box(Modifier.size(6.dp).background(FocusColors.Muted, CircleShape))
            }
        }
    }
}

internal object FocusRoute {
    const val TODAY = "today"
    const val INCOME = "income"
    const val HOLDINGS = "holdings"
    const val STORY = "story/{key}"
    const val ACTIVITY = "activity"
    const val IMPORT = "import"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    fun story(key: String) = "story/" + android.net.Uri.encode(key)
}

private val TABS = setOf(FocusRoute.TODAY, FocusRoute.INCOME)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusNavigation(states: AppStates, nav: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val stack by nav.currentBackStack.collectAsState()
    var usd by remember { mutableStateOf(UiPreferences.usdView(context)) }
    var menu by remember { mutableStateOf(false) }
    val titled = mapOf(FocusRoute.IMPORT to R.string.calm_add_reports, FocusRoute.REPORTS to R.string.imports_history_title, FocusRoute.SETTINGS to R.string.settings_title)
    Scaffold(
        containerColor = FocusColors.Page,
        topBar = {
            titled[route]?.let { title ->
                TopAppBar(
                    title = { Text(stringResource(title), fontFamily = Serif) },
                    navigationIcon = {
                        @Suppress("DEPRECATION")
                        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.calm_back)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = FocusColors.Page),
                )
            }
        },
        bottomBar = { BottomBar(current = stack.tabRoute(), menuOpen = menu, onTab = { menu = false; nav.tab(it) }, onMenu = { menu = !menu }) },
    ) { padding ->
        NavHost(nav, startDestination = FocusRoute.TODAY, modifier = Modifier.padding(padding).fillMaxSize()) {
            composable(FocusRoute.TODAY) {
                FocusToday(
                    states.portfolio, states.income,
                    onHoldings = { nav.navigate(FocusRoute.HOLDINGS) },
                    onOpen = { nav.navigate(FocusRoute.story(it)) },
                    onIncome = { nav.tab(FocusRoute.INCOME) },
                    onImport = { nav.navigate(FocusRoute.IMPORT) },
                    onSettings = { nav.navigate(FocusRoute.SETTINGS) },
                )
            }
            composable(FocusRoute.HOLDINGS) { FocusHoldings(states.portfolio, usd, onOpen = { nav.navigate(FocusRoute.story(it)) }, onBack = { nav.popBackStack() }) }
            composable(FocusRoute.STORY) { e -> FocusStory(states.portfolio, states.income, e.arguments?.getString("key").orEmpty(), onBack = { nav.popBackStack() }) }
            composable(FocusRoute.INCOME) { FocusIncome(states.income, onImport = { nav.navigate(FocusRoute.IMPORT) }) }
            composable(FocusRoute.ACTIVITY) { FocusActivity(states.activity, onBack = { nav.popBackStack() }, onImport = { nav.navigate(FocusRoute.IMPORT) }) }
            composable(FocusRoute.IMPORT) { ImportsScreen(states.data) }
            composable(FocusRoute.REPORTS) { ImportsScreen(states.data, reportsOnly = true) }
            composable(FocusRoute.SETTINGS) { SettingsScreen() }
        }
    }
    if (menu) {
        PlusMenu(
            states = states,
            usd = usd,
            onDismiss = { menu = false },
            onToggleUsd = {
                usd = !usd
                UiPreferences.setUsdView(context, usd)
            },
            onGo = { target -> menu = false; nav.navigate(target) },
        )
    }
}

private fun NavHostController.tab(route: String) {
    val stack = currentBackStack.value
    val current = stack.lastOrNull()?.destination?.route
    if (current == route) return
    // Leave screens opened from the current tab first, so a tab always opens on its first screen.
    val owner = stack.tabRoute()
    if (owner != null && owner != current) popBackStack(owner, inclusive = false)
    if (owner == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** The tab the top screen was opened from: the last tab screen on the back stack. */
private fun List<NavBackStackEntry>.tabRoute(): String? = lastOrNull { it.destination.route in TABS }?.destination?.route

@Composable
private fun BottomBar(current: String?, menuOpen: Boolean, onTab: (String) -> Unit, onMenu: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(FocusColors.Page).navigationBarsPadding()) {
        HorizontalDivider(color = FocusColors.Line)
        Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            TabLabel(stringResource(R.string.focus_tab_today), current == FocusRoute.TODAY) { onTab(FocusRoute.TODAY) }
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(20.dp)).background(FocusColors.Ink).clickable(role = Role.Button, onClick = onMenu),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (menuOpen) "×" else "+", color = Color.White, fontSize = 30.sp)
            }
            TabLabel(stringResource(R.string.calm_tab_income), current == FocusRoute.INCOME) { onTab(FocusRoute.INCOME) }
        }
    }
}

@Composable
private fun TabLabel(text: String, on: Boolean, onClick: () -> Unit) {
    Text(
        (if (on) "● " else "") + text,
        color = if (on) FocusColors.Ink else FocusColors.Muted,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        modifier = Modifier.width(100.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Tab, onClick = onClick).padding(vertical = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** Everything about data in one place (mobile-skin-focus "Focus navigation"). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlusMenu(states: AppStates, usd: Boolean, onDismiss: () -> Unit, onToggleUsd: () -> Unit, onGo: (String) -> Unit) {
    val data by states.data.view.collectAsState()
    val portfolio by states.portfolio.ui.collectAsState()
    LaunchedEffect(Unit) { states.data.reload() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = FocusColors.Page) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 24.dp)) {
            MenuRow("＋ " + stringResource(R.string.calm_add_reports), "›", bold = true) { onGo(FocusRoute.IMPORT) }
            MenuRow(stringResource(R.string.focus_menu_activity), "›") { onGo(FocusRoute.ACTIVITY) }
            MenuRow(stringResource(R.string.focus_menu_amounts), stringResource(if (usd) R.string.focus_amounts_usd else R.string.focus_amounts_own) + " ⇄", onClick = onToggleUsd)
            val backup = when {
                data.keyStatus is BackupKeyStatus.Ready && data.importReady -> "✓ " + stringResource(R.string.bento_on)
                data.keyStatus is BackupKeyStatus.Ready -> stringResource(R.string.bento_folder_missing)
                else -> stringResource(R.string.bento_off)
            }
            MenuRow(stringResource(R.string.focus_menu_backup), backup) { onGo(FocusRoute.SETTINGS) }
            val us = when ((portfolio as? PortfolioUiState.Loaded)?.priceSource) {
                PriceSource.FINNHUB -> stringResource(R.string.bento_finnhub)
                PriceSource.YAHOO -> stringResource(R.string.bento_yahoo_no_key)
                else -> "—"
            }
            MenuRow(stringResource(R.string.focus_menu_prices), stringResource(R.string.focus_prices_status, us)) { onGo(FocusRoute.SETTINGS) }
            MenuRow(stringResource(R.string.focus_menu_security), "›") { onGo(FocusRoute.SETTINGS) }
            MenuRow(stringResource(R.string.focus_menu_settings), "›") { onGo(FocusRoute.SETTINGS) }
            MenuRow(stringResource(R.string.focus_menu_delete), "›", color = FocusColors.Loss, last = true) { onGo(FocusRoute.REPORTS) }
        }
    }
}

@Composable
private fun MenuRow(label: String, value: String, bold: Boolean = false, color: Color = FocusColors.Ink, last: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = FocusColors.Muted, fontSize = 13.sp)
    }
    if (!last) HorizontalDivider(color = FocusColors.Line)
}
