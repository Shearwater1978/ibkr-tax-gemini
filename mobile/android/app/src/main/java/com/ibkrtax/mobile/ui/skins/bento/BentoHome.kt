package com.ibkrtax.mobile.ui.skins.bento

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.fx.UsdConverter
import com.ibkrtax.mobile.portfolio.Dividends
import com.ibkrtax.mobile.portfolio.PositionPerformance
import com.ibkrtax.mobile.portfolio.PositionRow
import com.ibkrtax.mobile.prices.PriceFreshness
import com.ibkrtax.mobile.state.IncomeState
import com.ibkrtax.mobile.state.PortfolioState
import com.ibkrtax.mobile.state.PortfolioUiState
import com.ibkrtax.mobile.ui.Formats
import com.ibkrtax.mobile.ui.common.UiPreferences
import com.ibkrtax.mobile.ui.common.skinMoney
import com.ibkrtax.mobile.ui.common.skinPercent
import java.math.BigDecimal
import java.math.MathContext
import java.time.YearMonth

private val CURRENCY_COLORS = listOf(BentoColors.Violet, BentoColors.Cyan, BentoColors.Amber, BentoColors.Gain, BentoColors.Loss)

internal fun positionKey(row: PositionRow) = "${row.ticker}|${row.currency}|${row.holding.isin}"

internal fun soldKey(p: PositionPerformance) = "${p.ticker}|${p.currency}|sold"

@Composable
fun BentoHome(state: PortfolioState, income: IncomeState, onOpen: (String) -> Unit, onIncome: () -> Unit) {
    val ui by state.ui.collectAsState()
    val dividends by income.data.collectAsState()
    LaunchedEffect(Unit) {
        state.refresh()
        income.reload()
    }
    val context = LocalContext.current
    var usd by remember { mutableStateOf(UiPreferences.usdView(context)) }
    var byToday by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.bento_overview), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Pill(if (usd) stringResource(R.string.view_usd) else stringResource(R.string.view_own_currency), active = usd) {
                usd = !usd
                UiPreferences.setUsdView(context, usd)
            }
        }
        when (val current = ui) {
            PortfolioUiState.Loading -> Text(stringResource(R.string.loading), color = BentoColors.Muted)
            PortfolioUiState.StorageUnavailable -> Text(stringResource(R.string.storage_unavailable))
            is PortfolioUiState.Incomplete -> Text(stringResource(R.string.portfolio_incomplete, current.ticker, current.date))
            PortfolioUiState.NoReports -> Tile(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.portfolio_empty_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(stringResource(R.string.bento_empty_body), color = BentoColors.Muted, modifier = Modifier.padding(top = 6.dp))
            }
            is PortfolioUiState.Loaded -> Dashboard(current, dividends?.let { lastTwelveMonths(it.events, it.rates) }, usd, byToday, { byToday = it }, onOpen, onIncome, state::refresh)
        }
    }
}

/** Net USD dividends of the last twelve months, oldest first, for the small chart. */
private fun lastTwelveMonths(events: List<com.ibkrtax.mobile.portfolio.CashEvent>, rates: com.ibkrtax.mobile.fx.FxRates?): List<BigDecimal> {
    val now = YearMonth.now()
    return (11 downTo 0).map { back ->
        val month = now.minusMonths(back.toLong())
        Dividends.period(events.filter { it.month == month }, null, rates).usdNet.amount
    }
}

@Composable
private fun Dashboard(
    data: PortfolioUiState.Loaded,
    lastYear: List<BigDecimal>?,
    usd: Boolean,
    byToday: Boolean,
    onSort: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onIncome: () -> Unit,
    onRefresh: () -> Unit,
) {
    val totals = data.totals
    val summary = data.summary
    val valueUsd = data.rows.associateWith { row -> row.marketValue?.let { UsdConverter.toUsd(it, row.currency, data.rates) } }
    val total = valueUsd.values.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add)
    val ranked = data.rows.filter { it.dailyChangePercent != null }
    val best = ranked.maxByOrNull { it.dailyChangePercent!! }
    val worst = ranked.minByOrNull { it.dailyChangePercent!! }
    val rows = if (byToday) data.rows.sortedWith(compareByDescending<PositionRow> { it.dailyChangePercent != null }.thenByDescending { it.dailyChangePercent })
    else data.rows.sortedByDescending { valueUsd[it] ?: BigDecimal.ZERO }
    val sold = data.performance.filter { it.isSold }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Tile(Modifier.fillMaxWidth(), brush = BentoColors.HeroGradient, onClick = onRefresh) {
                TileLabel(stringResource(R.string.bento_value_label))
                Text(skinMoney(totals.marketValue, "USD"), fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "${skinMoney(totals.dailyPnl, "USD", signed = true)} · ${totals.dailyPnlPercent?.let(::skinPercent) ?: "—"} ${stringResource(R.string.calm_today)}",
                    color = bentoSign(totals.dailyPnl), fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                )
                val notes = buildList {
                    add(stringResource(R.string.header_approximate))
                    totals.rateDate?.let { add(stringResource(R.string.header_rate_date, it)) }
                    if (!totals.isComplete) add(stringResource(R.string.header_incomplete, totals.missingValues, data.rows.size))
                    if (data.prices == null) add(stringResource(R.string.prices_refreshing))
                }
                Text(notes.joinToString(" · "), color = BentoColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile(Modifier.weight(1f).height(124.dp)) {
                    TileLabel(stringResource(R.string.bento_total_return))
                    Text(skinMoney(summary.totalProfit.amount, "USD", signed = true), color = bentoSign(summary.totalProfit.amount), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    summary.totalProfitPercent?.let { Text(skinPercent(it), color = bentoSign(it), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    if (!summary.totalProfit.isComplete) Text(stringResource(R.string.card_incomplete), color = BentoColors.Amber, fontSize = 11.sp)
                }
                Tile(Modifier.weight(1f).height(124.dp), onClick = onIncome) {
                    TileLabel(stringResource(R.string.bento_dividends_12m))
                    Text(skinMoney(summary.dividends12Months.amount, "USD"), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    MiniBars(lastYear.orEmpty())
                }
            }
        }
        if (best != null && worst != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MoverTile(stringResource(R.string.bento_best_today), best, Modifier.weight(1f)) { onOpen(positionKey(best)) }
                    MoverTile(stringResource(R.string.bento_worst_today), worst, Modifier.weight(1f)) { onOpen(positionKey(worst)) }
                }
            }
        }
        item {
            val byCurrency = data.rows.groupBy { it.currency }.mapValues { (_, list) -> list.mapNotNull { valueUsd[it] }.fold(BigDecimal.ZERO, BigDecimal::add) }
                .filterValues { it.signum() > 0 }.toList().sortedByDescending { it.second }
            if (byCurrency.isNotEmpty() && total.signum() > 0) {
                Tile(Modifier.fillMaxWidth()) {
                    TileLabel(stringResource(R.string.bento_by_currency))
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(12.dp).clip(RoundedCornerShape(6.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        byCurrency.forEachIndexed { i, (_, v) -> Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(CURRENCY_COLORS[i % CURRENCY_COLORS.size])) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        byCurrency.forEachIndexed { i, (cur, v) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(CURRENCY_COLORS[i % CURRENCY_COLORS.size], CircleShape))
                                Text(" $cur ${v.multiply(BigDecimal(100)).divide(total, MathContext.DECIMAL64).toInt()}%", color = BentoColors.Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.calm_holdings), fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Pill(stringResource(if (byToday) R.string.bento_sort_today else R.string.bento_sort_value), active = false) { onSort(!byToday) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(BentoColors.Tile).padding(horizontal = 14.dp, vertical = 4.dp)) {
                rows.forEachIndexed { i, row ->
                    HoldingItem(row, usd, valueUsd[row], total, data.rates) { onOpen(positionKey(row)) }
                    if (i < rows.lastIndex) HorizontalDivider(color = BentoColors.Line)
                }
                if (rows.isEmpty()) Text(stringResource(R.string.portfolio_no_holdings), color = BentoColors.Muted, modifier = Modifier.padding(vertical = 14.dp))
            }
        }
        if (sold.isNotEmpty()) {
            item { Text(stringResource(R.string.calm_sold), fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(BentoColors.Tile).padding(horizontal = 14.dp, vertical = 4.dp)) {
                    sold.forEachIndexed { i, p ->
                        Row(Modifier.fillMaxWidth().clickable { onOpen(soldKey(p)) }.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            LogoTile(p.ticker)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(p.ticker, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.sold_position, p.currency), color = BentoColors.Muted, fontSize = 12.sp)
                            }
                            Text(p.total?.let { skinMoney(it, p.currency, signed = true) } ?: "—", color = bentoSign(p.total), fontWeight = FontWeight.Bold)
                        }
                        if (i < sold.lastIndex) HorizontalDivider(color = BentoColors.Line)
                    }
                }
            }
        }
        // Room for the floating tab bar, so the last row scrolls fully above it (mobile-skin-bento).
        item { Spacer(Modifier.height(TAB_BAR_ROOM)) }
    }
}

@Composable
internal fun Pill(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = if (active) Color.White else BentoColors.Ink,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (active) BentoColors.Violet else BentoColors.Tile2).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun MiniBars(values: List<BigDecimal>) {
    val max = values.maxOfOrNull { it.toDouble() }?.takeIf { it > 0 } ?: 1.0
    Row(Modifier.fillMaxWidth().height(34.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { v ->
            Box(Modifier.weight(1f).fillMaxHeight(((v.toDouble() / max).toFloat()).coerceIn(0.06f, 1f)).background(BentoColors.Violet, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun MoverTile(label: String, row: PositionRow, modifier: Modifier, onClick: () -> Unit) {
    Tile(modifier, onClick = onClick) {
        TileLabel(label)
        Text(row.ticker, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Text(row.dailyChangePercent?.let(::skinPercent) ?: "—", color = bentoSign(row.dailyChangePercent), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        freshness(row.price?.freshness)?.let { Text(it, color = BentoColors.Muted, fontSize = 11.sp) }
    }
}

@Composable
internal fun freshness(f: PriceFreshness?): String? = when (f) {
    PriceFreshness.DELAYED -> stringResource(R.string.price_delayed_short)
    PriceFreshness.LATEST_CLOSE -> stringResource(R.string.price_close_short)
    PriceFreshness.STALE -> stringResource(R.string.price_stale_short)
    else -> null
}

@Composable
private fun HoldingItem(row: PositionRow, usd: Boolean, valueUsd: BigDecimal?, total: BigDecimal, rates: com.ibkrtax.mobile.fx.FxRates?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        LogoTile(row.ticker)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(row.ticker, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            val weight = if (valueUsd != null && total.signum() > 0) valueUsd.divide(total, MathContext.DECIMAL64).toFloat() else 0f
            Box(Modifier.padding(top = 6.dp).width(110.dp).height(4.dp).background(BentoColors.Tile2, RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxWidth(weight.coerceIn(0f, 1f)).fillMaxHeight().background(BentoColors.Cyan, RoundedCornerShape(2.dp)))
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            val shown = if (usd) valueUsd?.let { skinMoney(it, "USD") } else row.marketValue?.let { skinMoney(it, row.currency) }
            Text(shown ?: "—", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            val change = row.dailyChangePercent
            Text(
                change?.let(::skinPercent) ?: stringResource(if (!row.inPriceScope) R.string.price_out_of_scope_short else if (usd && rates == null) R.string.price_no_rate_short else R.string.price_unavailable_short),
                color = bentoSign(change), fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Full-screen position detail with the return as steps (mobile-skin-bento). */
@Composable
fun BentoPosition(state: PortfolioState, key: String, onBack: () -> Unit) {
    val ui by state.ui.collectAsState()
    val data = ui as? PortfolioUiState.Loaded ?: return
    val (ticker, currency, isin) = key.split("|").let { Triple(it[0], it.getOrElse(1) { "" }, it.getOrElse(2) { "" }) }
    val p = if (isin == "sold") data.performance.firstOrNull { it.isSold && it.ticker == ticker && it.currency == currency }
    else data.performance.firstOrNull { it.row?.ticker == ticker && it.row.currency == currency && it.row.holding.isin == isin }
    p ?: return
    val cur = p.currency
    val row = p.row
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF13306B), BentoColors.Page))).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("‹ " + stringResource(R.string.calm_back), color = BentoColors.Muted, modifier = Modifier.clickable(onClick = onBack).padding(vertical = 6.dp).weight(1f))
                listOfNotNull(row?.listingExchange?.ifEmpty { null }, freshness(row?.price?.freshness)).takeIf { it.isNotEmpty() }?.let { Pill(it.joinToString(" · "), active = false) {} }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                LogoTile(ticker, 48)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(ticker, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(if (row == null) stringResource(R.string.details_sold, cur) else cur, color = BentoColors.Muted, fontSize = 13.sp)
                }
            }
            row?.price?.quote?.let { q ->
                Text(skinMoney(q.price, cur), fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 12.dp))
                row.dailyChange?.let { Text("${skinMoney(it, cur, signed = true)} · ${row.dailyChangePercent?.let(::skinPercent) ?: ""} ${stringResource(R.string.calm_today)}", color = bentoSign(it), fontWeight = FontWeight.Bold) }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (row != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Small(stringResource(R.string.details_quantity), Formats.quantity(row.holding.quantity), Modifier.weight(1f))
                    Small(stringResource(R.string.details_average), skinMoney(row.holding.averagePrice, cur), Modifier.weight(1f))
                    Small(stringResource(R.string.details_value), row.marketValue?.let { skinMoney(it, cur) } ?: "—", Modifier.weight(1f))
                }
            }
            Tile(Modifier.fillMaxWidth()) {
                TileLabel(stringResource(R.string.bento_return_steps))
                val steps = listOf(
                    Triple(stringResource(R.string.bento_invested_short), p.invested, BentoColors.Tile2),
                    Triple(stringResource(R.string.bento_price_short), p.priceGain, BentoColors.Violet),
                    Triple(stringResource(R.string.bento_sales_short), p.realizedGain, BentoColors.Cyan),
                    Triple(stringResource(R.string.bento_dividends_short), p.dividends.net, BentoColors.Amber),
                    Triple(stringResource(R.string.bento_total_short), p.total, BentoColors.Gain),
                )
                val max = steps.mapNotNull { it.second?.abs()?.toDouble() }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
                Row(Modifier.fillMaxWidth().height(120.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    steps.forEach { (_, v, c) ->
                        val h = ((v?.abs()?.toDouble() ?: 0.0) / max).toFloat().coerceIn(0.03f, 1f)
                        Box(Modifier.weight(1f).fillMaxHeight(h).background(c, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp)))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    steps.forEachIndexed { i, (label, v, _) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, color = BentoColors.Muted, fontSize = 11.sp)
                            Text(
                                v?.let { skinMoney(it, cur, signed = i > 0) } ?: "—",
                                color = if (i == steps.lastIndex) bentoSign(v) else BentoColors.Muted,
                                fontSize = 11.sp, fontWeight = if (i == steps.lastIndex) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Small(stringResource(R.string.bento_dividends_gross), skinMoney(p.dividends.gross, cur), Modifier.weight(1f))
                Small(stringResource(R.string.bento_tax_withheld), skinMoney(p.dividends.tax.abs(), cur), Modifier.weight(1f))
            }
            if (!p.realizedComplete) Text(stringResource(R.string.details_sales_incomplete), color = BentoColors.Muted, fontSize = 12.sp)
            Text(stringResource(R.string.details_note), color = BentoColors.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(TAB_BAR_ROOM))
        }
    }
}

@Composable
private fun Small(label: String, value: String, modifier: Modifier) {
    Tile(modifier) {
        TileLabel(label)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
