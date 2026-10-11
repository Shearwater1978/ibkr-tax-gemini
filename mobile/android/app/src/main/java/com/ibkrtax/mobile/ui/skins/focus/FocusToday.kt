package com.ibkrtax.mobile.ui.skins.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.fx.UsdConverter
import com.ibkrtax.mobile.portfolio.Dividends
import com.ibkrtax.mobile.portfolio.MainPage
import com.ibkrtax.mobile.portfolio.PositionRow
import com.ibkrtax.mobile.prices.PriceFreshness
import com.ibkrtax.mobile.state.IncomeState
import com.ibkrtax.mobile.state.PortfolioState
import com.ibkrtax.mobile.state.PortfolioUiState
import com.ibkrtax.mobile.ui.Formats
import com.ibkrtax.mobile.ui.common.skinMoney
import com.ibkrtax.mobile.ui.common.skinPercent
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime

internal fun Sentence.annotated(): AnnotatedString = buildAnnotatedString {
    parts.forEach { part ->
        val style = when (part.tone) {
            Tone.PLAIN -> null
            Tone.STRONG -> SpanStyle(fontWeight = FontWeight.Bold)
            Tone.GAIN -> SpanStyle(fontWeight = FontWeight.Bold, color = FocusColors.Gain)
            Tone.LOSS -> SpanStyle(fontWeight = FontWeight.Bold, color = FocusColors.Loss)
        }
        if (style == null) append(part.text) else withStyle(style) { append(part.text) }
    }
}

internal fun rowKey(row: PositionRow) = "${row.ticker}|${row.currency}|${row.holding.isin}"

@Composable
internal fun Back(label: String, onBack: () -> Unit) {
    Text(label, color = FocusColors.Muted, fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(vertical = 10.dp))
}

@Composable
internal fun ChangePill(percent: BigDecimal?) {
    val (fg, bg) = when (percent?.signum()) {
        1 -> FocusColors.Gain to FocusColors.GainSoft
        -1 -> FocusColors.Loss to FocusColors.LossSoft
        else -> FocusColors.Muted to FocusColors.Sand
    }
    Text(percent?.let(::skinPercent) ?: "—", color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable
internal fun Mono(ticker: String) {
    Box(Modifier.size(40.dp).background(FocusColors.Sand, CircleShape), contentAlignment = Alignment.Center) {
        Text(ticker.take(2), fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/** Today (mobile-skin-focus): value, the summary sentence, insight cards and what you own. */
@Composable
fun FocusToday(
    state: PortfolioState,
    income: IncomeState,
    onHoldings: () -> Unit,
    onOpen: (String) -> Unit,
    onIncome: () -> Unit,
    onImport: () -> Unit,
    onSettings: () -> Unit,
) {
    val ui by state.ui.collectAsState()
    val progress by state.progress.collectAsState()
    val dividends by income.data.collectAsState()
    LaunchedEffect(Unit) {
        state.refresh()
        income.reload()
    }
    when (val data = ui) {
        PortfolioUiState.Loading -> Message(stringResource(R.string.loading))
        PortfolioUiState.StorageUnavailable -> Message(stringResource(R.string.storage_unavailable))
        is PortfolioUiState.Incomplete -> Message(stringResource(R.string.portfolio_incomplete, data.ticker, data.date))
        PortfolioUiState.NoReports -> Column(Modifier.fillMaxSize().padding(22.dp)) {
            TopLine("", onSettings)
            Text(stringResource(R.string.portfolio_empty_title), fontFamily = Serif, fontSize = 28.sp, modifier = Modifier.padding(top = 12.dp))
            Text(stringResource(R.string.portfolio_empty_body), color = FocusColors.Muted, modifier = Modifier.padding(vertical = 10.dp))
            Button(onClick = onImport) { Text(stringResource(R.string.calm_add_reports)) }
        }
        is PortfolioUiState.Loaded -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
            val totals = data.totals
            val freshness = data.rows.mapNotNull { it.price?.freshness }.let { all ->
                listOf(PriceFreshness.STALE, PriceFreshness.LATEST_CLOSE, PriceFreshness.DELAYED, PriceFreshness.LIVE).firstOrNull { it in all }
            }
            val freshWord = stringResource(
                when (freshness) {
                    PriceFreshness.STALE -> R.string.focus_prices_stale
                    PriceFreshness.LATEST_CLOSE -> R.string.focus_prices_close
                    PriceFreshness.DELAYED -> R.string.focus_prices_delayed
                    else -> R.string.focus_prices_fresh
                },
            )
            Column(Modifier.padding(horizontal = 22.dp)) {
                val hour = LocalTime.now().hour
                val greeting = stringResource(if (hour < 12) R.string.focus_morning else if (hour < 18) R.string.focus_afternoon else R.string.focus_evening)
                TopLine("$greeting · ${freshWord.lowercase()}", onSettings)
                Text(
                    "$" + Formats.number(totals.marketValue.setScale(0, RoundingMode.HALF_UP), 0, 0),
                    fontFamily = Serif, fontSize = 46.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                )
                Text(Summaries.today(data.rows, totals, data.summary, data.rates).annotated(), fontSize = 17.sp, lineHeight = 25.sp)
                Text(
                    listOfNotNull(stringResource(R.string.header_approximate), totals.rateDate?.let { stringResource(R.string.header_rate_date, it) }).joinToString(" · "),
                    color = FocusColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }

            val cards = rememberLazyListState()
            LazyRow(state = cards, contentPadding = PaddingValues(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 18.dp)) {
                item {
                    val today = LocalDate.now()
                    val events = dividends?.events.orEmpty().filter { it.date.startsWith(today.toString().take(7)) }
                    val month = Dividends.year(events, today.year, dividends?.rates).months[today.monthValue - 1].usdNet.amount
                    val companies = events.filter { it.eventType == Dividends.DIVIDEND }.map { it.ticker }.distinct().size
                    InsightCard(stringResource(R.string.focus_card_month), skinMoney(month, "USD"), pluralStringResource(R.plurals.focus_card_month_body, companies, companies), dark = true, onClick = onIncome)
                }
                data.rows.filter { it.dailyChangePercent != null }.maxByOrNull { it.dailyChangePercent!!.abs() }?.let { best ->
                    item {
                        InsightCard(
                            stringResource(R.string.focus_card_move),
                            "${best.ticker} ${skinPercent(best.dailyChangePercent!!)}",
                            stringResource(R.string.focus_card_move_body, best.dailyPnl?.let { skinMoney(it, best.currency, signed = true) } ?: "—", Formats.quantity(best.holding.quantity)),
                        ) { onOpen(rowKey(best)) }
                    }
                }
                item {
                    InsightCard(
                        stringResource(R.string.focus_card_prices),
                        progress?.let { stringResource(R.string.focus_prices_refreshing, "${it.done} / ${it.total}") } ?: freshWord,
                        stringResource(R.string.focus_prices_tap),
                        onClick = state::refresh,
                    )
                }
                item {
                    InsightCard(stringResource(R.string.focus_card_12m), skinMoney(data.summary.dividends12Months.amount, "USD"), stringResource(R.string.focus_card_12m_body), onClick = onIncome)
                }
            }
            PageDots(cards)

            Column(Modifier.padding(horizontal = 22.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.focus_own), fontFamily = Serif, fontSize = 21.sp, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.focus_see_all), color = FocusColors.Muted, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onHoldings).padding(4.dp))
                }
                Allocation(data, onHoldings)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The greeting line with the Settings button at its end, so Settings is one tap from Today. */
@Composable
private fun TopLine(text: String, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = FocusColors.Muted, fontSize = 15.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onSettings, modifier = Modifier.size(40.dp).background(FocusColors.Sand, CircleShape)) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title), tint = FocusColors.Ink)
        }
    }
}

/** One dot per card; the dot of the first fully visible card is dark, so the row reads as swipeable. */
@Composable
private fun PageDots(state: LazyListState) {
    val count by remember { derivedStateOf { state.layoutInfo.totalItemsCount } }
    val current by remember {
        derivedStateOf {
            if (!state.canScrollForward) state.layoutInfo.totalItemsCount - 1
            else state.layoutInfo.visibleItemsInfo.firstOrNull { it.offset >= 0 }?.index ?: state.firstVisibleItemIndex
        }
    }
    if (count < 2) return
    val label = stringResource(R.string.focus_card_position, current + 1, count)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp).semantics { contentDescription = label }, horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            val on = i == current
            Box(Modifier.padding(horizontal = 3.dp).size(width = if (on) 18.dp else 6.dp, height = 6.dp).background(if (on) FocusColors.Ink else FocusColors.Line, CircleShape))
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(text, textAlign = TextAlign.Center, color = FocusColors.Muted) }
}

@Composable
private fun InsightCard(label: String, value: String, body: String, dark: Boolean = false, onClick: () -> Unit) {
    val muted = if (dark) FocusColors.DeepMuted else FocusColors.Muted
    Column(
        Modifier.width(210.dp).clip(RoundedCornerShape(22.dp)).background(if (dark) FocusColors.Deep else FocusColors.Card).clickable(onClick = onClick).padding(16.dp),
    ) {
        Text(label, color = muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = if (dark) Color.White else FocusColors.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        Text(body, color = muted, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** The four largest holdings and the rest by USD weight, as a donut with a legend. */
@Composable
private fun Allocation(data: PortfolioUiState.Loaded, onClick: () -> Unit) {
    val total = data.totals.marketValue.takeIf { it.signum() > 0 } ?: return
    val weights = data.rows.mapNotNull { row -> row.marketValue?.let { UsdConverter.toUsd(it, row.currency, data.rates) }?.let { row.ticker to it } }
        .sortedByDescending { it.second }
        .map { (t, v) -> t to v.divide(total, MathContext.DECIMAL64).toFloat() }
    val others = stringResource(R.string.focus_others)
    val slices = weights.take(4) + listOfNotNull(weights.drop(4).takeIf { it.isNotEmpty() }?.let { rest -> others to rest.sumOf { it.second.toDouble() }.toFloat() })
    val description = slices.joinToString { "${it.first} ${Math.round(it.second * 100)}%" }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(FocusColors.Card).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(112.dp).semantics { contentDescription = description }) {
            val stroke = 21.dp.toPx()
            var start = -90f
            slices.forEachIndexed { i, (_, w) ->
                val sweep = w * 360f
                drawArc(FocusColors.Slices[i], start, sweep, useCenter = false, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                start += sweep
            }
        }
        Column(Modifier.weight(1f).padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEachIndexed { i, (name, w) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(FocusColors.Slices[i], CircleShape))
                    Text(name, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Text("${Math.round(w * 100)}%", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

/** All holdings grouped by today's direction, then sold positions. */
@Composable
fun FocusHoldings(state: PortfolioState, usd: Boolean, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val ui by state.ui.collectAsState()
    val data = ui as? PortfolioUiState.Loaded ?: return
    val rows = if (usd) MainPage.inUsd(data.rows, data.rates) else data.rows
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        Back(stringResource(R.string.focus_back_today), onBack)
        Text(stringResource(R.string.focus_holdings_title), fontFamily = Serif, fontSize = 26.sp)
        Text(stringResource(if (usd) R.string.focus_holdings_hint_usd else R.string.focus_holdings_hint_own), color = FocusColors.Muted, fontSize = 14.sp)
        val rising = rows.filter { (it.dailyChangePercent?.signum() ?: 0) > 0 }.sortedByDescending { it.dailyChangePercent }
        val falling = rows.filter { (it.dailyChangePercent?.signum() ?: 0) < 0 }.sortedBy { it.dailyChangePercent }
        val flat = rows - (rising + falling).toSet()
        listOf(R.string.focus_rising to rising, R.string.focus_falling to falling, R.string.focus_no_change to flat).forEach { (title, list) ->
            if (list.isNotEmpty()) {
                Group(stringResource(title))
                list.forEach { row ->
                    val detail = listOfNotNull(row.marketValue?.let { skinMoney(it, row.displayCurrency) }, stringResource(R.string.focus_shares, Formats.quantity(row.holding.quantity)))
                    HoldingRow(row.ticker, detail.joinToString(" · "), onClick = { onOpen(rowKey(row)) }) { ChangePill(row.dailyChangePercent) }
                }
            }
        }
        val sold = data.performance.filter { it.isSold }
        if (sold.isNotEmpty()) {
            Group(stringResource(R.string.focus_sold))
            sold.forEach { p ->
                HoldingRow(p.ticker, stringResource(R.string.details_sold, p.currency), onClick = { onOpen("${p.ticker}|${p.currency}|sold") }) {
                    p.total?.let { Text(skinMoney(it, p.currency, signed = true), color = if (it.signum() >= 0) FocusColors.Gain else FocusColors.Loss, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Group(title: String) {
    Text(title.uppercase(), color = FocusColors.Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.7.sp, modifier = Modifier.padding(top = 18.dp, bottom = 2.dp))
}

@Composable
private fun HoldingRow(ticker: String, detail: String, onClick: () -> Unit = {}, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Mono(ticker)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(ticker, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(detail, color = FocusColors.Muted, fontSize = 13.sp)
        }
        trailing()
    }
    HorizontalDivider(color = FocusColors.Line)
}

/** A position told in sentences (mobile-skin-focus "Position stories"). */
@Composable
fun FocusStory(state: PortfolioState, income: IncomeState, key: String, onBack: () -> Unit) {
    val ui by state.ui.collectAsState()
    val data = ui as? PortfolioUiState.Loaded ?: return
    val (ticker, currency, isin) = key.split("|").let { Triple(it[0], it.getOrElse(1) { "" }, it.getOrElse(2) { "" }) }
    val p = if (isin == "sold") data.performance.firstOrNull { it.isSold && it.ticker == ticker && it.currency == currency }
    else data.performance.firstOrNull { it.row?.ticker == ticker && it.row.currency == currency && it.row.holding.isin == isin }
    p ?: return
    val row = p.row
    val story = Summaries.story(p)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        Back("‹ " + stringResource(R.string.calm_back), onBack)
        Text(ticker, fontFamily = Serif, fontSize = 30.sp, modifier = Modifier.padding(top = 6.dp))
        if (row != null) {
            Text(row.marketValue?.let { skinMoney(it, currency) } ?: "—", fontFamily = Serif, fontSize = 38.sp, fontWeight = FontWeight.SemiBold)
            row.price?.quote?.let { q ->
                val day = row.dailyChange
                Text(
                    listOfNotNull(day?.let { skinMoney(row.dailyPnl ?: it, currency, signed = true) + " " + stringResource(R.string.calm_today) }, stringResource(R.string.focus_a_share, skinMoney(q.price, currency))).joinToString(" · "),
                    color = when (day?.signum()) { 1 -> FocusColors.Gain; -1 -> FocusColors.Loss; else -> FocusColors.Muted },
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp).background(when (day?.signum()) { 1 -> FocusColors.GainSoft; -1 -> FocusColors.LossSoft; else -> FocusColors.Sand }, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        } else {
            Text(stringResource(R.string.details_sold, currency), color = FocusColors.Muted)
        }
        StoryCard {
            Text(story[0].annotated(), fontSize = 16.sp, lineHeight = 24.sp)
            val value = row?.marketValue
            if (value != null && value.signum() > 0) {
                val fraction = p.invested.divide(value, MathContext.DECIMAL64).toFloat().coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(FocusColors.Sand)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(FocusColors.Gain, RoundedCornerShape(4.dp)))
                }
            }
        }
        var history by remember { mutableStateOf(false) }
        StoryCard {
            Text(story[1].annotated(), fontSize = 16.sp, lineHeight = 24.sp)
            if (p.dividends.gross.signum() != 0 || p.dividends.tax.signum() != 0) {
                Text(
                    stringResource(R.string.payer_history_title) + " ›",
                    color = FocusColors.Deep, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { history = true }.padding(vertical = 6.dp),
                )
            }
        }
        if (history) {
            val dividends by income.data.collectAsState()
            LaunchedEffect(Unit) { income.reload() }
            FocusPayerSheet(dividends?.events.orEmpty(), p.ticker, currency, onDismiss = { history = false })
        }
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(22.dp)).background(FocusColors.Deep).padding(18.dp)) {
            Text(stringResource(R.string.focus_story_total), color = FocusColors.DeepMuted, fontSize = 14.sp)
            val total = p.total
            Text(
                total?.let { skinMoney(it, currency, signed = true) + (p.totalPercent?.let { pct -> " · " + skinPercent(pct) } ?: "") } ?: story[2].text,
                color = Color.White, fontFamily = Serif, fontSize = if (total != null) 26.sp else 16.sp, fontWeight = FontWeight.Bold,
            )
        }
        Text(
            listOfNotNull(stringResource(R.string.details_note), row?.listingExchange?.ifEmpty { null }).joinToString(" · "),
            color = FocusColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun StoryCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(22.dp)).background(FocusColors.Card).padding(18.dp)) { content() }
}
