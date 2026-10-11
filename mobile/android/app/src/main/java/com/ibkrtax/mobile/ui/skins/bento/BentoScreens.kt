package com.ibkrtax.mobile.ui.skins.bento

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import com.ibkrtax.mobile.ui.common.YearChoices
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.keys.BackupKeyStatus
import com.ibkrtax.mobile.portfolio.Dividends
import com.ibkrtax.mobile.prices.PriceSource
import com.ibkrtax.mobile.state.ActivityState
import com.ibkrtax.mobile.state.AppStates
import com.ibkrtax.mobile.state.IncomeState
import com.ibkrtax.mobile.state.PortfolioUiState
import com.ibkrtax.mobile.storage.Operation
import com.ibkrtax.mobile.storage.OperationFilter
import com.ibkrtax.mobile.ui.common.LocalBrokerChooser
import com.ibkrtax.mobile.ui.common.OperationDisplay
import com.ibkrtax.mobile.ui.common.displayDate
import com.ibkrtax.mobile.ui.common.labelRes
import com.ibkrtax.mobile.ui.common.skinMoney
import com.ibkrtax.mobile.ui.common.skinPercent
import com.ibkrtax.mobile.ui.common.typeLabel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private val YEAR_COLORS = listOf(BentoColors.Amber, BentoColors.Cyan, BentoColors.Violet)

@Composable
private fun Header(title: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Year pills, up to three years side by side per month, and the payers (mobile-skin-bento). */
@Composable
fun BentoIncome(state: IncomeState) {
    val data by state.data.collectAsState()
    LaunchedEffect(Unit) { state.reload() }
    val current = data
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        if (current == null) {
            Header(stringResource(R.string.calm_tab_income))
            Text(stringResource(R.string.loading), color = BentoColors.Muted)
            return@Column
        }
        val years = Dividends.years(current.events)
        if (years.isEmpty()) {
            Header(stringResource(R.string.calm_tab_income))
            Tile(Modifier.fillMaxWidth()) { Text(stringResource(R.string.dividends_empty), color = BentoColors.Muted) }
            return@Column
        }
        var year by rememberSaveable { mutableStateOf(years.first()) }
        if (year !in years) year = years.first()
        var month by rememberSaveable { mutableStateOf<Int?>(null) }
        val period = Dividends.year(current.events, year, current.rates)
        val shown = (year - 2..year).filter { it in years }
        val series = shown.map { y -> Dividends.year(current.events, y, current.rates).months.map { it.usdNet.amount } }
        val colors = YEAR_COLORS.takeLast(shown.size)
        val locale = Locale.getDefault()

        Header(stringResource(R.string.calm_tab_income))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val choices = YearChoices.of(years)
            choices.direct.reversed().forEach { y -> Pill(y.toString(), active = y == year) { year = y; month = null } }
            if (choices.older.isNotEmpty()) {
                Box {
                    var open by remember { mutableStateOf(false) }
                    val olderSelected = year in choices.older
                    Pill((if (olderSelected) year.toString() else stringResource(R.string.year_older)) + " ▾", active = olderSelected) { open = true }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        choices.older.forEach { y -> DropdownMenuItem(text = { Text(y.toString()) }, onClick = { year = y; month = null; open = false }) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Modifier.weight(1f)) {
                TileLabel(stringResource(R.string.bento_net_year, year))
                Text(skinMoney(period.usdNet.amount, "USD"), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                val change = Dividends.changeFromYearBefore(current.events, year, current.rates, LocalDate.now())
                Text(change?.let { stringResource(R.string.calm_vs_year, skinPercent(it), year - 1) } ?: stringResource(R.string.bento_first_year), color = bentoSign(change), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Tile(Modifier.weight(1f)) {
                TileLabel(stringResource(R.string.bento_tax_withheld))
                Text(skinMoney(period.usdTax.amount.abs(), "USD"), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                val gross = period.usdGross.amount
                val share = if (gross.signum() > 0) period.usdTax.amount.abs().multiply(BigDecimal(100)).divide(gross, java.math.MathContext.DECIMAL64).toInt() else 0
                Text(stringResource(R.string.bento_of_gross, share), color = BentoColors.Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Tile(Modifier.fillMaxWidth()) {
            TileLabel(stringResource(R.string.bento_net_per_month, shown.joinToString(" · ")))
            val max = series.flatten().maxOfOrNull { it.toDouble() }?.takeIf { it > 0 } ?: 1.0
            Row(Modifier.fillMaxWidth().height(130.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                (0 until 12).forEach { m ->
                    Row(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(if (month == m) Color.White.copy(alpha = 0.06f) else Color.Transparent)
                            .clickable { month = if (month == m) null else m }
                            .semantics { contentDescription = Month.of(m + 1).getDisplayName(TextStyle.SHORT, locale) },
                        horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.Bottom,
                    ) {
                        series.forEachIndexed { i, s ->
                            Box(Modifier.weight(1f).fillMaxHeight((s[m].toDouble() / max).toFloat().coerceIn(0.01f, 1f)).background(colors[i], RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..12).forEach { Text(Month.of(it).getDisplayName(TextStyle.NARROW, locale), color = BentoColors.Muted, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                shown.forEachIndexed { i, y ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(colors[i], CircleShape))
                        Text(" $y", color = BentoColors.Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        month?.let { m ->
            Spacer(Modifier.height(10.dp))
            Tile(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Month.of(m + 1).getDisplayName(TextStyle.FULL, locale), modifier = Modifier.weight(1f))
                    shown.forEachIndexed { i, y -> Text("$y: ${skinMoney(series[i][m], "USD")}", color = colors[i], fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp)) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(BentoColors.Tile).padding(horizontal = 14.dp, vertical = 4.dp)) {
            period.positions.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    LogoTile(p.ticker)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(p.ticker, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.dividends_line, skinMoney(p.amounts.gross, p.currency), skinMoney(p.amounts.tax, p.currency)), color = BentoColors.Muted, fontSize = 12.sp)
                    }
                    Text(skinMoney(p.amounts.net, p.currency), fontWeight = FontWeight.Bold)
                }
                if (i < period.positions.lastIndex) HorizontalDivider(color = BentoColors.Line)
            }
        }
        val missing = listOf(period.usdGross, period.usdTax, period.usdNet).flatMap { it.missingCurrencies }.toSortedSet()
        val note = buildList {
            add(stringResource(R.string.header_approximate))
            period.rateDate?.let { add(stringResource(R.string.header_rate_date, it)) }
            if (missing.isNotEmpty()) add(stringResource(R.string.dividends_incomplete, missing.joinToString()))
        }
        Text(note.joinToString(" · "), color = BentoColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        Spacer(Modifier.height(TAB_BAR_ROOM))
    }
}

@Composable
fun BentoActivity(state: ActivityState, onImport: () -> Unit) {
    val view by state.view.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { state.reload() }
    LaunchedEffect(view.query, view.filters) { list.scrollToItem(0) }
    val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 20 } }
    LaunchedEffect(list) { snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { state.loadMore() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header(stringResource(R.string.calm_tab_activity))
        if (view.hasAny == false) {
            Tile(Modifier.fillMaxWidth(), onClick = onImport) {
                Text(stringResource(R.string.operations_empty), color = BentoColors.Muted)
                Text(stringResource(R.string.calm_add_reports) + " ›", color = BentoColors.Violet, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
            return@Column
        }
        TextField(
            value = view.query,
            onValueChange = state::setQuery,
            placeholder = { Text(stringResource(R.string.calm_search)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = BentoColors.Tile, unfocusedContainerColor = BentoColors.Tile,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OperationFilter.entries.forEach { f -> Pill(stringResource(f.labelRes()), active = f in view.filters) { state.toggle(f) } }
        }
        if (view.items.isEmpty() && view.hasAny == true) {
            Tile(Modifier.fillMaxWidth(), onClick = state::clear) {
                Text(stringResource(R.string.operations_no_match), color = BentoColors.Muted)
                Text(stringResource(R.string.operations_clear_all), color = BentoColors.Violet, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            }
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))) {
            items(view.items, key = { it.id }) { ActivityItem(it) }
            item { Spacer(Modifier.height(TAB_BAR_ROOM)) }
        }
    }
}

@Composable
private fun ActivityItem(op: Operation) {
    Row(Modifier.fillMaxWidth().background(BentoColors.Tile).padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        LogoTile(op.ticker)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(typeLabel(op.eventType), fontWeight = FontWeight.Bold)
            Text(listOfNotNull(displayDate(op.date), OperationDisplay.details(op)).joinToString(" · "), color = BentoColors.Muted, fontSize = 12.sp)
        }
        Text(OperationDisplay.amount(op), fontWeight = FontWeight.Bold, color = if (op.eventType == "DIVIDEND") BentoColors.Gain else BentoColors.Ink)
    }
    HorizontalDivider(color = BentoColors.Line)
}

/** Import, status badges and the way into Settings (mobile-skin-bento "Data"). */
@Composable
fun BentoData(states: AppStates, onImport: () -> Unit, onSettings: () -> Unit) {
    val data by states.data.view.collectAsState()
    val portfolio by states.portfolio.ui.collectAsState()
    LaunchedEffect(Unit) { states.data.reload() }
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    val loaded = portfolio as? PortfolioUiState.Loaded

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Header(stringResource(R.string.bento_tab_data))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(BentoColors.Violet.copy(alpha = 0.06f))
                .border(BorderStroke(1.5.dp, Color(0xFF3A4050)), RoundedCornerShape(22.dp)).clickable(onClick = onImport).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(56.dp).background(BentoColors.Violet, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text("+", color = Color.White, fontSize = 30.sp) }
            Text(stringResource(R.string.bento_add_reports), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            val broker = LocalBrokerChooser.current?.current
            Text(
                broker?.let { stringResource(R.string.bento_add_reports_hint, stringResource(it.formatRes)) } ?: stringResource(R.string.bento_add_reports_choose),
                color = BentoColors.Muted, fontSize = 13.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(BentoColors.Tile).padding(horizontal = 14.dp, vertical = 4.dp)) {
            val keys = data.keyStatus
            Badge(stringResource(R.string.bento_backup), when {
                keys is BackupKeyStatus.Ready && data.importReady -> stringResource(R.string.bento_on) to true
                keys is BackupKeyStatus.Ready -> stringResource(R.string.bento_folder_missing) to false
                else -> stringResource(R.string.bento_off) to false
            })
            Badge(stringResource(R.string.bento_us_prices), when (loaded?.priceSource) {
                PriceSource.FINNHUB -> stringResource(R.string.bento_finnhub) to true
                PriceSource.YAHOO -> stringResource(R.string.bento_yahoo_no_key) to false
                else -> "—" to false
            })
            Badge(stringResource(R.string.bento_eu_prices), stringResource(R.string.bento_yahoo_delayed) to false)
            Badge(stringResource(R.string.bento_reports), (data.history?.size?.toString() ?: "—") to true)
            Badge(stringResource(R.string.bento_fx_rates), (loaded?.rates?.effectiveDate?.let { stringResource(R.string.bento_nbp, it) } ?: "—") to true, last = true)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(BentoColors.Tile).padding(horizontal = 14.dp, vertical = 4.dp)) {
            Entry(stringResource(R.string.bento_settings_entry), onSettings)
            HorizontalDivider(color = BentoColors.Line)
            Entry(stringResource(R.string.bento_delete_entry), onImport, color = BentoColors.Loss)
            HorizontalDivider(color = BentoColors.Line)
            Entry(stringResource(R.string.bento_version, version), onSettings)
        }
        Spacer(Modifier.height(TAB_BAR_ROOM))
    }
}

@Composable
private fun Badge(label: String, value: Pair<String, Boolean>, last: Boolean = false) {
    val (text, ok) = value
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Text(
            text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (ok) BentoColors.Gain else BentoColors.Amber,
            modifier = Modifier.background((if (ok) BentoColors.Gain else BentoColors.Amber).copy(alpha = 0.15f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
    if (!last) HorizontalDivider(color = BentoColors.Line)
}

@Composable
private fun Entry(label: String, onClick: () -> Unit, color: Color = BentoColors.Ink) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp)) {
        Text(label, color = color, modifier = Modifier.weight(1f))
        Text("›", color = BentoColors.Muted)
    }
}
