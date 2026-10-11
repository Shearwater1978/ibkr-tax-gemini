package com.ibkrtax.mobile.ui.skins.bento

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
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

/** Bento's dark palette (mobile-skin-bento). */
object BentoColors {
    val Page = Color(0xFF0E1015)
    val Tile = Color(0xFF171A21)
    val Tile2 = Color(0xFF1F232C)
    val Ink = Color(0xFFF3F5F8)
    val Muted = Color(0xFF8B93A1)
    val Line = Color(0xFF262A33)
    val Gain = Color(0xFF3DDC97)
    val Loss = Color(0xFFFF6B6B)
    val Violet = Color(0xFF8B7BFF)
    val Cyan = Color(0xFF3CC8F0)
    val Amber = Color(0xFFF6B84B)
    val HeroGradient = Brush.linearGradient(listOf(Color(0xFF2A2160), Tile))
}

/** Height of the floating tab bar plus its margins; every screen keeps this much room at the end. */
internal val TAB_BAR_ROOM = 104.dp

@Composable
private fun BentoTheme(content: @Composable () -> Unit) {
    // Light status-bar icons on the dark page while Bento is shown; restored when another skin takes over.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (wasLight != null) {
                controller.isAppearanceLightStatusBars = wasLight
                controller.isAppearanceLightNavigationBars = wasLight
            }
        }
    }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = BentoColors.Violet,
            onPrimary = Color.White,
            background = BentoColors.Page,
            surface = BentoColors.Page,
            surfaceContainer = BentoColors.Tile,
            surfaceContainerHigh = BentoColors.Tile2,
            surfaceVariant = BentoColors.Tile,
            onBackground = BentoColors.Ink,
            onSurface = BentoColors.Ink,
            onSurfaceVariant = BentoColors.Muted,
            outlineVariant = BentoColors.Line,
            error = BentoColors.Loss,
        ),
        content = content,
    )
}

/** Dark tile dashboard with a floating tab bar: Home, Income, Activity and Data. */
object BentoSkin : Skin {
    override val id = SkinId.BENTO
    override val nameRes = R.string.skin_bento_name
    override val descriptionRes = R.string.skin_bento_description
    override val features = mapOf(
        Feature.PORTFOLIO_VALUE_AND_TODAY to "Home: value tile",
        Feature.POSITION_DETAILS to "Home: tap a holding or the best/worst tile",
        Feature.SOLD_POSITIONS to "Home: Sold positions list under the holdings",
        Feature.OWN_CURRENCY_AND_USD to "Home: USD / Own currency pill",
        Feature.TOP_MOVERS to "Home: Best and Worst today tiles; sort by today",
        Feature.DIVIDENDS_BY_YEAR_AND_MONTH to "Income tab",
        Feature.PAYER_HISTORY to "Income: tap a payer",
        Feature.ACTIVITY_SEARCH_AND_FILTERS to "Activity tab",
        Feature.REPORT_IMPORT to "Data tab: Add reports",
        Feature.BACKUP_KEYS_AND_LOCATION to "Data tab: Settings",
        Feature.PRICE_KEY to "Data tab: Settings",
        Feature.DELETE_IMPORTS to "Data tab: Reports imported or Delete imported data",
        Feature.APP_VERSION to "Data tab: Settings",
        Feature.SKIN_SETTING to "Data tab: Settings",
    )

    @Composable
    override fun App(states: AppStates) {
        BentoTheme { BentoNavigation(states) }
    }

    /** Dark tiles in a grid and a pill at the bottom. */
    @Composable
    override fun Preview(modifier: Modifier) {
        Column(modifier.clip(RoundedCornerShape(10.dp)).background(BentoColors.Page).padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.fillMaxWidth().height(18.dp).background(BentoColors.HeroGradient, RoundedCornerShape(4.dp)))
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.weight(1f).height(16.dp).background(BentoColors.Tile, RoundedCornerShape(4.dp)))
                    Box(Modifier.weight(1f).height(16.dp).background(BentoColors.Tile, RoundedCornerShape(4.dp)))
                }
            }
            Row(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))) {
                Box(Modifier.weight(6f).fillMaxSize().background(BentoColors.Violet))
                Box(Modifier.weight(3f).fillMaxSize().background(BentoColors.Cyan))
                Box(Modifier.weight(1f).fillMaxSize().background(BentoColors.Amber))
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(8.dp).background(BentoColors.Tile2, RoundedCornerShape(4.dp)))
        }
    }
}

internal object BentoRoute {
    const val HOME = "home"
    const val INCOME = "income"
    const val ACTIVITY = "activity"
    const val DATA = "data"
    const val IMPORT = "import"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
    const val POSITION = "position/{key}"
    const val PAYER = "payer/{ticker}/{currency}"

    fun position(key: String) = "position/" + android.net.Uri.encode(key)
    fun payer(ticker: String, currency: String) = "payer/" + android.net.Uri.encode(ticker) + "/" + android.net.Uri.encode(currency)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BentoNavigation(states: AppStates, nav: NavHostController = rememberNavController()) {
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val withTopBar = route == BentoRoute.IMPORT || route == BentoRoute.REPORTS || route == BentoRoute.SETTINGS
    Scaffold(
        containerColor = BentoColors.Page,
        topBar = {
            if (withTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(
                                when (route) {
                                    BentoRoute.IMPORT -> R.string.calm_add_reports
                                    BentoRoute.REPORTS -> R.string.imports_history_title
                                    else -> R.string.settings_title
                                },
                            ),
                        )
                    },
                    navigationIcon = {
                        @Suppress("DEPRECATION")
                        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.calm_back)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BentoColors.Page),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(nav, startDestination = BentoRoute.HOME, modifier = Modifier.fillMaxSize()) {
                composable(BentoRoute.HOME) { BentoHome(states.portfolio, states.income, onOpen = { nav.navigate(BentoRoute.position(it)) }, onIncome = { nav.tab(BentoRoute.INCOME) }) }
                composable(BentoRoute.POSITION) { e -> BentoPosition(states.portfolio, e.arguments?.getString("key").orEmpty(), onBack = { nav.popBackStack() }) }
                composable(BentoRoute.INCOME) { BentoIncome(states.income, onPayer = { t, c -> nav.navigate(BentoRoute.payer(t, c)) }) }
                composable(BentoRoute.PAYER) { e ->
                    BentoPayer(states.income, e.arguments?.getString("ticker").orEmpty(), e.arguments?.getString("currency").orEmpty(), onBack = { nav.popBackStack() })
                }
                composable(BentoRoute.ACTIVITY) { BentoActivity(states.activity, onImport = { nav.navigate(BentoRoute.IMPORT) }) }
                composable(BentoRoute.DATA) { BentoData(
                        states,
                        onImport = { nav.navigate(BentoRoute.IMPORT) },
                        onReports = { nav.navigate(BentoRoute.REPORTS) },
                        onSettings = { nav.navigate(BentoRoute.SETTINGS) },
                    ) }
                composable(BentoRoute.IMPORT) { ImportsScreen(states.data) }
                composable(BentoRoute.REPORTS) { ImportsScreen(states.data, reportsOnly = true) }
                composable(BentoRoute.SETTINGS) { SettingsScreen() }
            }
            if (!withTopBar) {
                TabBar(
                    current = route,
                    onTab = { nav.tab(it) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                )
            }
        }
    }
}

private fun NavHostController.tab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun TabBar(current: String?, onTab: (String) -> Unit, modifier: Modifier) {
    val tabs = listOf(
        BentoRoute.HOME to stringResource(R.string.calm_tab_home),
        BentoRoute.INCOME to stringResource(R.string.calm_tab_income),
        BentoRoute.ACTIVITY to stringResource(R.string.calm_tab_activity),
        BentoRoute.DATA to stringResource(R.string.bento_tab_data),
    )
    Row(
        modifier.background(BentoColors.Tile2.copy(alpha = 0.95f), RoundedCornerShape(50)).border(1.dp, Color(0xFF2C313C), RoundedCornerShape(50)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { (route, label) ->
            val on = current == route || (route == BentoRoute.HOME && current == BentoRoute.POSITION) || (route == BentoRoute.INCOME && current == BentoRoute.PAYER)
            Text(
                label,
                color = if (on) BentoColors.Page else BentoColors.Muted,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) BentoColors.Ink else Color.Transparent).clickable { onTab(route) }.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** A dark rounded tile; [onClick] makes the whole tile a button. */
@Composable
internal fun Tile(modifier: Modifier = Modifier, brush: Brush? = null, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val base = modifier.clip(RoundedCornerShape(22.dp))
    val background = if (brush != null) base.background(brush) else base.background(BentoColors.Tile)
    Box(background.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Column { content() }
    }
}

@Composable
internal fun TileLabel(text: String) {
    Text(text.uppercase(), color = BentoColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
}

internal fun bentoSign(value: java.math.BigDecimal?): Color = when (value?.signum()) {
    1 -> BentoColors.Gain
    -1 -> BentoColors.Loss
    else -> BentoColors.Muted
}

@Composable
internal fun LogoTile(ticker: String, size: Int = 38) {
    Box(Modifier.size(size.dp).background(com.ibkrtax.mobile.ui.common.tickerColor(ticker), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Text(ticker.take(4), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}
