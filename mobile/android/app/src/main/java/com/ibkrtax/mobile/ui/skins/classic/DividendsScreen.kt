package com.ibkrtax.mobile.ui.skins.classic

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import com.ibkrtax.mobile.ui.common.YearChoices
import androidx.compose.runtime.remember
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.portfolio.DividendAmounts
import com.ibkrtax.mobile.portfolio.CashEvent
import com.ibkrtax.mobile.portfolio.Dividends
import com.ibkrtax.mobile.state.DividendData
import com.ibkrtax.mobile.state.IncomeState
import com.ibkrtax.mobile.portfolio.UsdSum
import com.ibkrtax.mobile.ui.BarChart
import com.ibkrtax.mobile.ui.BarGroup
import com.ibkrtax.mobile.ui.ChartBar
import com.ibkrtax.mobile.ui.Formats
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// One colour per compared year, oldest first (amber, blue, purple), readable in light and dark themes.
private val YEAR_COLORS = listOf(Color(0xFFF9A825), Color(0xFF29B6F6), Color(0xFF9575CD))

/** Received dividends per month, year and position (mobile-dividends spec). */
@Composable
fun DividendsScreen(state: IncomeState, onImportReport: () -> Unit) {
    val data by state.data.collectAsState()
    LaunchedEffect(Unit) { state.reload() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Text(
            stringResource(R.string.dividends_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        val current = data
        when {
            current == null -> Text(stringResource(R.string.loading))
            Dividends.years(current.events).isEmpty() -> NoDividends(onImportReport)
            else -> DividendContent(current)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NoDividends(onImportReport: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.dividends_empty), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onImportReport) { Text(stringResource(R.string.action_import_report)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DividendContent(data: DividendData) {
    val years = Dividends.years(data.events)
    var selected by rememberSaveable { mutableStateOf(years.first()) }
    if (selected !in years) selected = years.first()
    var payer by remember { mutableStateOf<Pair<String, String>?>(null) }
    val year = Dividends.year(data.events, selected, data.rates)
    val muted = MaterialTheme.typography.bodySmall
    val locale = Locale.getDefault()

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        val choices = YearChoices.of(years)
        choices.direct.forEach { y -> FilterChip(selected = y == selected, onClick = { selected = y }, label = { Text(y.toString()) }) }
        if (choices.older.isNotEmpty()) {
            Box {
                var open by remember { mutableStateOf(false) }
                val olderSelected = selected in choices.older
                FilterChip(
                    selected = olderSelected,
                    onClick = { open = true },
                    label = { Text((if (olderSelected) selected.toString() else stringResource(R.string.year_older)) + " ▾") },
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    choices.older.forEach { y -> DropdownMenuItem(text = { Text(y.toString()) }, onClick = { selected = y; open = false }) }
                }
            }
        }
    }

    // Year totals in USD.
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Total(stringResource(R.string.dividends_gross), year.usdGross, Modifier.weight(1f))
        Total(stringResource(R.string.dividends_tax), year.usdTax, Modifier.weight(1f))
        Total(stringResource(R.string.dividends_net), year.usdNet, Modifier.weight(1f), bold = true)
    }
    Text(usdNote(year.rateDate, listOf(year.usdGross, year.usdTax, year.usdNet)), style = muted, modifier = Modifier.padding(top = 4.dp))

    // Net per month for the selected year.
    SectionTitle(stringResource(R.string.dividends_by_month, selected))
    val barColor = MaterialTheme.colorScheme.primary
    BarChart(
        groups = year.months.map { month ->
            val label = monthLabel(month.month, locale)
            val net = month.usdNet.amount
            BarGroup(
                label = label,
                bars = listOf(ChartBar(net.toDouble(), barColor, "$label $selected: ${Formats.money(net, "USD")}")),
                valueLabel = if (net.signum() != 0) Formats.compact(net) else null,
            )
        },
    )

    // Totals per currency, in their own currency.
    if (year.byCurrency.size > 1 || year.byCurrency.keys.singleOrNull() != "USD") {
        SectionTitle(stringResource(R.string.dividends_by_currency))
        year.byCurrency.forEach { (currency, amounts) -> AmountsLine(currency, amounts, currency) }
    }

    // Year-on-year comparison.
    val recent = Dividends.netByMonthForRecentYears(data.events, data.rates)
    if (recent.size > 1) {
        SectionTitle(stringResource(R.string.dividends_compare))
        val colors = recent.keys.zip(YEAR_COLORS).toMap()
        BarChart(
            groups = (1..12).map { month ->
                val label = monthLabel(month, locale)
                BarGroup(
                    label = label,
                    bars = recent.map { (y, months) ->
                        val net = months[month - 1].amount
                        ChartBar(net.toDouble(), colors.getValue(y), "$label $y: ${Formats.money(net, "USD")}")
                    },
                )
            },
            height = 110.dp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp)) {
            colors.forEach { (y, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(color))
                    Text(" $y", style = muted)
                }
            }
        }
    }

    // Per position.
    SectionTitle(stringResource(R.string.dividends_by_position, selected))
    year.positions.forEach { position -> AmountsLine(position.ticker, position.amounts, position.currency, onClick = { payer = position.ticker to position.currency }) }

    Dividends.coverage(data.events)?.let { (first, last) ->
        val format = DateTimeFormatter.ofPattern("MMM yyyy", locale)
        Text(
            stringResource(R.string.dividends_coverage, first.format(format), last.format(format)),
            style = muted,
            modifier = Modifier.padding(top = 12.dp),
        )
    }

    payer?.let { (ticker, currency) -> PayerHistorySheet(data.events, ticker, currency, onDismiss = { payer = null }) }
}

/** One company's dividend history over all years (mobile-payer-history). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PayerHistorySheet(events: List<CashEvent>, ticker: String, currency: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val history = Dividends.history(events, ticker, currency)
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("$ticker · " + stringResource(R.string.payer_history_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.payer_history_subtitle, currency, pluralStringResource(R.plurals.payer_history_count, history.lines.size, history.lines.size)),
                style = MaterialTheme.typography.bodySmall,
            )
            SectionTitle(stringResource(R.string.payer_history_totals))
            AmountsLine(currency, history.total, currency)
            history.lines.forEach { line -> AmountsLine(line.date, line.amounts, currency) }
        }
    }
}

@Composable
private fun Total(label: String, sum: UsdSum, modifier: Modifier, bold: Boolean = false) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            Formats.money(sum.amount, "USD"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@Composable
private fun AmountsLine(title: String, amounts: DividendAmounts, currency: String, onClick: (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth().then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier).padding(vertical = 6.dp)) {
        Row {
            Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(Formats.money(amounts.net, currency))
        }
        Text(
            stringResource(R.string.dividends_line, Formats.money(amounts.gross, currency), Formats.money(amounts.tax, currency)),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    HorizontalDivider()
}

@Composable
private fun usdNote(rateDate: String?, sums: List<UsdSum>): String {
    val missing = sums.flatMap { it.missingCurrencies }.toSortedSet()
    return buildList {
        add(stringResource(R.string.header_approximate))
        rateDate?.let { add(stringResource(R.string.header_rate_date, it)) }
        if (missing.isNotEmpty()) add(stringResource(R.string.dividends_incomplete, missing.joinToString()))
    }.joinToString(" · ")
}

private fun monthLabel(month: Int, locale: Locale): String = Month.of(month).getDisplayName(TextStyle.SHORT, locale)
