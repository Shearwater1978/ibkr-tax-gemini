package com.ibkrtax.mobile.ui.skins.calm

import com.ibkrtax.mobile.state.IncomeState
import com.ibkrtax.mobile.portfolio.Dividends
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.ui.common.skinMoney
import com.ibkrtax.mobile.ui.common.skinPercent
import com.ibkrtax.mobile.ui.common.tickerColor
import com.ibkrtax.mobile.fx.UsdConverter
import com.ibkrtax.mobile.portfolio.MainPage
import com.ibkrtax.mobile.portfolio.PositionPerformance
import com.ibkrtax.mobile.portfolio.PositionRow
import com.ibkrtax.mobile.prices.PriceFailure
import com.ibkrtax.mobile.prices.PriceFreshness
import com.ibkrtax.mobile.prices.PriceSource
import com.ibkrtax.mobile.prices.RefreshOutcome
import com.ibkrtax.mobile.state.PortfolioState
import com.ibkrtax.mobile.state.PortfolioUiState
import com.ibkrtax.mobile.ui.Formats
import com.ibkrtax.mobile.ui.common.PriceSourcesLine
import com.ibkrtax.mobile.ui.common.QuoteSourceLabel
import com.ibkrtax.mobile.ui.common.priceStatusText
import com.ibkrtax.mobile.ui.common.twelveDataProgressText
import com.ibkrtax.mobile.ui.common.UiPreferences
import java.math.BigDecimal

private enum class CalmSort { VALUE, TODAY, NAME }

private sealed interface CalmSheet {
    data object View : CalmSheet
    data object Info : CalmSheet
    data object Return : CalmSheet
    data class Position(val key: String) : CalmSheet
    data class History(val ticker: String, val currency: String) : CalmSheet
}

internal fun signColor(value: BigDecimal?): Color = when (value?.signum()) {
    1 -> CalmColors.Gain
    -1 -> CalmColors.Loss
    else -> CalmColors.Muted
}

@Composable
internal fun TickerTile(ticker: String, size: Int = 40) {
    Box(Modifier.size(size.dp).background(tickerColor(ticker), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Text(ticker.take(4), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
internal fun Chip(text: String, background: Color, color: Color) {
    Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
fun CalmHome(state: PortfolioState, income: IncomeState, onImport: () -> Unit, onSettings: () -> Unit) {
    val ui by state.ui.collectAsState()
    val progress by state.progress.collectAsState()
    LaunchedEffect(Unit) { state.refresh() }
    when (val current = ui) {
        PortfolioUiState.Loading -> Centered(onImport, onSettings) { Text(stringResource(R.string.loading), color = CalmColors.Muted) }
        // Settings stays reachable in every state; it holds "Erase all data", the way out when storage cannot be opened.
        PortfolioUiState.StorageUnavailable -> Centered(onImport, onSettings) { Text(stringResource(R.string.storage_unavailable), textAlign = TextAlign.Center) }
        is PortfolioUiState.Incomplete -> Centered(onImport, onSettings) { Text(stringResource(R.string.portfolio_incomplete, current.ticker, current.date), textAlign = TextAlign.Center) }
        PortfolioUiState.NoReports -> Column(Modifier.fillMaxSize().padding(22.dp)) {
            TopRow(onImport, onSettings)
            Spacer(Modifier.height(40.dp))
            Text(stringResource(R.string.portfolio_empty_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.portfolio_empty_body), color = CalmColors.Muted, modifier = Modifier.padding(vertical = 10.dp))
            Button(onClick = onImport) { Text(stringResource(R.string.calm_add_reports)) }
        }
        is PortfolioUiState.Loaded -> Loaded(current, progress?.let { twelveDataProgressText(it) ?: "${it.done} / ${it.total}" }, income, state::refresh, onImport, onSettings)
    }
}

/** A centred message under the avatar and "+" buttons. */
@Composable
private fun Centered(onImport: () -> Unit, onSettings: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        TopRow(onImport, onSettings)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun TopRow(onImport: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onSettings, modifier = Modifier.size(38.dp).background(CalmColors.Card, CircleShape)) {
            Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.settings_title), tint = CalmColors.Ink)
        }
        IconButton(onClick = onImport, modifier = Modifier.size(38.dp).background(CalmColors.Accent, CircleShape)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.calm_add_reports), tint = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Loaded(data: PortfolioUiState.Loaded, progress: String?, income: IncomeState, onRefresh: () -> Unit, onImport: () -> Unit, onSettings: () -> Unit) {
    val dividends by income.data.collectAsState()
    LaunchedEffect(Unit) { income.reload() }
    val context = LocalContext.current
    var sort by rememberSaveable { mutableStateOf(CalmSort.VALUE) }
    var usd by remember { mutableStateOf(UiPreferences.usdView(context)) }
    var showSold by remember { mutableStateOf(UiPreferences.showSold(context)) }
    var sheet by remember { mutableStateOf<CalmSheet?>(null) }
    val totals = data.totals
    val rows = (if (usd) MainPage.inUsd(data.rows, data.rates) else data.rows).let { list ->
        when (sort) {
            CalmSort.VALUE -> list.sortedByDescending { row -> row.marketValue?.let { UsdConverter.toUsd(it, row.displayCurrency, data.rates) } ?: BigDecimal.ZERO }
            CalmSort.TODAY -> list.sortedWith(compareByDescending<PositionRow> { it.dailyChangePercent != null }.thenByDescending { it.dailyChangePercent })
            CalmSort.NAME -> list.sortedBy { it.ticker }
        }
    }
    val sold = data.performance.filter { it.isSold }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        item { TopRow(onImport, onSettings) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.calm_portfolio_value), color = CalmColors.Muted, fontSize = 14.sp)
                IconButton(onClick = { sheet = CalmSheet.Info }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.calm_about_value), tint = CalmColors.Muted, modifier = Modifier.size(16.dp))
                }
            }
            val whole = skinMoney(totals.marketValue, "USD")
            val dot = whole.lastIndexOf('.')
            Text(
                buildAnnotatedString {
                    append(whole.substring(0, dot))
                    withStyle(SpanStyle(fontSize = 24.sp, color = CalmColors.Muted)) { append(whole.substring(dot)) }
                },
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            val day = totals.dailyPnl
            Chip(
                "${if (day.signum() >= 0) "▲" else "▼"} ${skinMoney(day.abs(), "USD")} · ${Formats.number(totals.dailyPnlPercent?.abs() ?: BigDecimal.ZERO, 2, 2)}% ${stringResource(R.string.calm_today)}",
                if (day.signum() >= 0) CalmColors.GainSoft else CalmColors.LossSoft,
                if (day.signum() >= 0) CalmColors.Gain else CalmColors.Loss,
            )
            val notes = buildList {
                add(stringResource(R.string.header_approximate))
                totals.rateDate?.let { add(stringResource(R.string.header_rate_date, it)) }
                if (!totals.isComplete) add(stringResource(R.string.header_incomplete, totals.missingValues, data.rows.size))
            }
            Text(notes.joinToString(" · "), color = CalmColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            val profit = data.summary.totalProfit
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.calm_total_return) + " ")
                    withStyle(SpanStyle(color = signColor(profit.amount), fontWeight = FontWeight.Bold)) {
                        append(skinMoney(profit.amount, "USD", signed = true))
                        val percent = data.summary.totalProfitPercent
                        if (percent != null) append(" (" + skinPercent(percent) + ")")
                    }
                    append(" " + stringResource(R.string.calm_incl_sales_dividends) + " ›")
                },
                color = CalmColors.Muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 14.dp).clickable { sheet = CalmSheet.Return },
            )
            PriceLine(data, progress, onRefresh)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.calm_holdings), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { sheet = CalmSheet.View }) { Text(stringResource(R.string.calm_view) + " ▾") }
            }
        }
        if (rows.isEmpty()) item { Text(stringResource(R.string.portfolio_no_holdings), color = CalmColors.Muted, modifier = Modifier.padding(vertical = 16.dp)) }
        items(rows, key = { "${it.ticker}-${it.holding.isin}-${it.currency}" }) { row ->
            HoldingRow(row, onClick = { sheet = CalmSheet.Position("${row.ticker}|${row.currency}|${row.holding.isin}") })
        }
        if (showSold && sold.isNotEmpty()) {
            item { Text(stringResource(R.string.calm_sold), color = CalmColors.Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp)) }
            items(sold, key = { "sold-${it.ticker}-${it.currency}" }) { p -> SoldRow(p, onClick = { sheet = CalmSheet.Position("${p.ticker}|${p.currency}|sold") }) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    sheet?.let { current ->
        ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Color.White) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 32.dp)) {
                when (current) {
                    CalmSheet.View -> ViewSheet(
                        sort, { sort = it },
                        usd, { usd = it; UiPreferences.setUsdView(context, it) },
                        showSold, { showSold = it; UiPreferences.setShowSold(context, it) },
                        data.rates?.effectiveDate,
                    )
                    CalmSheet.Info -> InfoSheet(data)
                    CalmSheet.Return -> ReturnSheet(data)
                    is CalmSheet.History -> PayerHistorySheet(Dividends.history(dividends?.events.orEmpty(), current.ticker, current.currency), Modifier)
                    is CalmSheet.Position -> {
                        val (ticker, currency, isin) = current.key.split("|")
                        val position = if (isin == "sold") data.performance.firstOrNull { it.isSold && it.ticker == ticker && it.currency == currency }
                        else data.performance.firstOrNull { it.row?.ticker == ticker && it.row.currency == currency && it.row.holding.isin == isin }
                        position?.let { p -> PositionSheet(p, onHistory = { sheet = CalmSheet.History(p.ticker, p.currency) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceLine(data: PortfolioUiState.Loaded, progress: String?, onRefresh: () -> Unit) {
    val prices = data.prices
    val text = when (prices) {
        null -> stringResource(R.string.prices_refreshing) + (progress?.let { " $it" } ?: "")
        else -> priceStatusText(prices, data.priceSource) ?: stringResource(R.string.calm_prices_updated)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = CalmColors.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (prices != null) TextButton(onClick = onRefresh) { Text("↻ " + stringResource(R.string.prices_refresh), fontSize = 13.sp) }
    }
    // Each source of a shown price, with the Twelve Data attribution link (mobile-market-prices).
    PriceSourcesLine(data.rows, color = CalmColors.Muted)
}

@Composable
private fun freshnessLabel(freshness: PriceFreshness?): String? = when (freshness) {
    PriceFreshness.DELAYED -> stringResource(R.string.price_delayed_short)
    PriceFreshness.LATEST_CLOSE -> stringResource(R.string.price_close_short)
    PriceFreshness.STALE -> stringResource(R.string.price_stale_short)
    else -> null
}

@Composable
private fun HoldingRow(row: PositionRow, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        TickerTile(row.ticker)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(row.ticker, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            val parts = listOfNotNull(
                stringResource(R.string.calm_shares, Formats.quantity(row.holding.quantity)),
                row.listingExchange.ifEmpty { null },
                freshnessLabel(row.price?.freshness),
            )
            Text(parts.joinToString(" · "), color = CalmColors.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            val value = row.marketValue
            if (value != null) {
                Text(skinMoney(value, row.displayCurrency), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                row.dailyChangePercent?.let { Text(skinPercent(it), color = signColor(it), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            } else {
                Text("—", fontWeight = FontWeight.SemiBold)
                val reason = when {
                    !row.inPriceScope -> R.string.price_out_of_scope_short
                    row.rateMissing -> R.string.price_no_rate_short
                    else -> R.string.price_unavailable_short
                }
                Text(stringResource(reason), color = CalmColors.Muted, fontSize = 12.sp)
            }
        }
    }
    HorizontalDivider(color = CalmColors.Line)
}

@Composable
private fun SoldRow(position: PositionPerformance, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        TickerTile(position.ticker)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(position.ticker, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.sold_position, position.currency), color = CalmColors.Muted, fontSize = 13.sp)
        }
        Text(position.total?.let { skinMoney(it, position.currency, signed = true) } ?: "—", color = signColor(position.total), fontWeight = FontWeight.SemiBold)
    }
    HorizontalDivider(color = CalmColors.Line)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewSheet(
    sort: CalmSort, onSort: (CalmSort) -> Unit,
    usd: Boolean, onUsd: (Boolean) -> Unit,
    showSold: Boolean, onShowSold: (Boolean) -> Unit,
    rateDate: String?,
) {
    Text(stringResource(R.string.calm_view), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
    OptionRow(stringResource(R.string.calm_sort_by)) {
        SingleChoiceSegmentedButtonRow {
            val labels = listOf(R.string.calm_sort_value, R.string.calm_sort_today, R.string.calm_sort_name)
            CalmSort.entries.forEachIndexed { i, option ->
                SegmentedButton(selected = sort == option, onClick = { onSort(option) }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(stringResource(labels[i]), fontSize = 12.sp) }
            }
        }
    }
    OptionRow(stringResource(R.string.calm_amounts_in)) {
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(selected = !usd, onClick = { onUsd(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.view_own_currency), fontSize = 12.sp) }
            SegmentedButton(selected = usd, onClick = { onUsd(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.view_usd), fontSize = 12.sp) }
        }
    }
    OptionRow(stringResource(R.string.calm_show_sold)) { Switch(checked = showSold, onCheckedChange = onShowSold) }
    rateDate?.let { Text(stringResource(R.string.usd_view_note, it), color = CalmColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
}

@Composable
private fun OptionRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        control()
    }
    HorizontalDivider(color = CalmColors.Line)
}

@Composable
private fun InfoSheet(data: PortfolioUiState.Loaded) {
    Text(stringResource(R.string.calm_about_value), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text(
        stringResource(R.string.calm_about_value_body, data.rates?.effectiveDate ?: "—"),
        modifier = Modifier.padding(vertical = 10.dp),
    )
    Text(stringResource(R.string.calm_about_sources), color = CalmColors.Muted, fontSize = 13.sp)
}

@Composable
private fun ReturnSheet(data: PortfolioUiState.Loaded) {
    val rates = data.rates
    fun usd(value: BigDecimal?, currency: String) = value?.let { UsdConverter.toUsd(it, currency, rates) } ?: BigDecimal.ZERO
    val priceGain = data.performance.filter { !it.isSold }.fold(BigDecimal.ZERO) { s, p -> s.add(usd(p.priceGain, p.currency)) }
    val sales = data.performance.fold(BigDecimal.ZERO) { s, p -> s.add(usd(p.realizedGain, p.currency)) }
    val dividends = data.performance.fold(BigDecimal.ZERO) { s, p -> s.add(usd(p.dividends.net, p.currency)) }
    val summary = data.summary
    Text(stringResource(R.string.calm_total_return), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text(skinMoney(summary.totalProfit.amount, "USD", signed = true), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = signColor(summary.totalProfit.amount), modifier = Modifier.padding(vertical = 8.dp))
    Text(stringResource(R.string.card_invested, skinMoney(summary.invested.amount, "USD")), color = CalmColors.Muted)
    if (!summary.totalProfit.isComplete) Text(stringResource(R.string.card_incomplete), color = CalmColors.Loss, fontSize = 13.sp)
    Spacer(Modifier.height(12.dp))
    LegendLine(stringResource(R.string.calm_price_gain_held), skinMoney(priceGain, "USD", signed = true))
    LegendLine(stringResource(R.string.details_sales_gain), skinMoney(sales, "USD", signed = true))
    LegendLine(stringResource(R.string.calm_dividends_after_tax), skinMoney(dividends, "USD", signed = true))
    Text(stringResource(R.string.details_note), color = CalmColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun LegendLine(label: String, value: String, dot: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        dot?.let { Box(Modifier.size(10.dp).background(it, RoundedCornerShape(3.dp))); Spacer(Modifier.size(8.dp)) }
        Text(label, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PositionSheet(p: PositionPerformance, onHistory: () -> Unit) {
    val cur = p.currency
    Row(verticalAlignment = Alignment.CenterVertically) {
        TickerTile(p.ticker, 48)
        Column(Modifier.padding(start = 12.dp)) {
            Text(p.ticker, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(listOfNotNull(p.row?.listingExchange?.ifEmpty { null }, cur).joinToString(" · "), color = CalmColors.Muted, fontSize = 13.sp)
        }
    }
    val row = p.row
    if (row != null) {
        val quote = row.price?.quote
        Text(quote?.let { skinMoney(it.price, cur) } ?: "—", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        row.dailyChange?.let { change ->
            Chip(
                "${if (change.signum() >= 0) "▲" else "▼"} ${skinMoney(change.abs(), cur)} · ${row.dailyChangePercent?.let { Formats.number(it.abs(), 2, 2) } ?: "—"}% ${stringResource(R.string.calm_today)}",
                if (change.signum() >= 0) CalmColors.GainSoft else CalmColors.LossSoft,
                if (change.signum() >= 0) CalmColors.Gain else CalmColors.Loss,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(stringResource(R.string.details_quantity), Formats.quantity(row.holding.quantity), Modifier.weight(1f))
            Stat(stringResource(R.string.details_average), skinMoney(row.holding.averagePrice, cur), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(stringResource(R.string.details_invested), skinMoney(p.invested, cur), Modifier.weight(1f))
            Stat(stringResource(R.string.details_value), row.marketValue?.let { skinMoney(it, cur) } ?: stringResource(R.string.details_unavailable), Modifier.weight(1f))
        }
        QuoteSourceLabel(row.price?.quote?.source, color = CalmColors.Muted)
    } else {
        Text(stringResource(R.string.details_sold, cur), color = CalmColors.Muted, modifier = Modifier.padding(top = 12.dp))
    }
    Spacer(Modifier.height(16.dp))
    val total = p.total
    Row(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.calm_total_return), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(
            total?.let { skinMoney(it, cur, signed = true) + (p.totalPercent?.let { pct -> " · " + skinPercent(pct) } ?: "") } ?: stringResource(R.string.details_unavailable),
            color = signColor(total), fontWeight = FontWeight.Bold,
        )
    }
    val parts = listOf(
        Triple(p.priceGain, CalmColors.Accent, R.string.details_price_gain),
        Triple(p.realizedGain, Color(0xFF8FB0FF), R.string.details_sales_gain),
        Triple(p.dividends.net, Color(0xFFC8D8FF), R.string.details_dividends),
    )
    val positive = parts.mapNotNull { (v, c, _) -> v?.takeIf { it.signum() > 0 }?.let { it to c } }
    if (positive.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(10.dp).background(CalmColors.Card, RoundedCornerShape(6.dp))) {
            positive.forEach { (v, c) -> Box(Modifier.weight(v.toFloat().coerceAtLeast(0.01f)).fillMaxSize().background(c)) }
        }
    }
    parts.forEach { (v, c, label) -> LegendLine(stringResource(label), v?.let { skinMoney(it, cur, signed = true) } ?: stringResource(R.string.details_unavailable), c) }
    Text(
        stringResource(R.string.dividends_line, skinMoney(p.dividends.gross, cur), skinMoney(p.dividends.tax, cur)),
        color = CalmColors.Muted, fontSize = 12.sp,
    )
    if (p.dividends.gross.signum() != 0 || p.dividends.tax.signum() != 0) {
        Text(
            stringResource(R.string.payer_history_title) + " ›",
            color = CalmColors.Accent, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onHistory).padding(vertical = 8.dp),
        )
    }
    if (!p.realizedComplete) Text(stringResource(R.string.details_sales_incomplete), color = CalmColors.Muted, fontSize = 12.sp)
    Text(stringResource(R.string.details_note), color = CalmColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(CalmColors.Card, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, color = CalmColors.Muted, fontSize = 12.sp)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}
