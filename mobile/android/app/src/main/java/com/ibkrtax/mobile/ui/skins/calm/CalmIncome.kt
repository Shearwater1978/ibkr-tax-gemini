package com.ibkrtax.mobile.ui.skins.calm

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.ui.common.skinMoney
import com.ibkrtax.mobile.ui.common.skinPercent
import com.ibkrtax.mobile.portfolio.Dividends
import com.ibkrtax.mobile.portfolio.PayerHistory
import com.ibkrtax.mobile.state.DividendData
import com.ibkrtax.mobile.state.IncomeState
import com.ibkrtax.mobile.ui.BarChart
import com.ibkrtax.mobile.ui.BarGroup
import com.ibkrtax.mobile.ui.ChartBar
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** Stands for "all years" in the year selection. */
private const val ALL_YEARS = 0

@Composable
fun CalmIncome(state: IncomeState, onImport: () -> Unit) {
    val data by state.data.collectAsState()
    LaunchedEffect(Unit) { state.reload() }
    val current = data
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp).verticalScroll(rememberScrollState())) {
        when {
            current == null -> Text(stringResource(R.string.loading), color = CalmColors.Muted, modifier = Modifier.padding(top = 24.dp))
            Dividends.years(current.events).isEmpty() -> {
                Title(null, onYears = {})
                Text(stringResource(R.string.dividends_empty), color = CalmColors.Muted, modifier = Modifier.padding(vertical = 12.dp))
                Button(onClick = onImport) { Text(stringResource(R.string.calm_add_reports)) }
            }
            else -> IncomeContent(current)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Title(year: Int?, onYears: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.calm_tab_income), fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (year != null) {
            Text(
                (if (year == ALL_YEARS) stringResource(R.string.calm_all_years) else year.toString()) + " ▾",
                fontWeight = FontWeight.SemiBold,
                color = CalmColors.Muted,
                modifier = Modifier.background(CalmColors.Card, RoundedCornerShape(50)).clickable(onClick = onYears).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncomeContent(data: DividendData) {
    val years = Dividends.years(data.events)
    var year by rememberSaveable { mutableStateOf(years.first()) }
    if (year != ALL_YEARS && year !in years) year = years.first()
    var compare by rememberSaveable { mutableStateOf(false) }
    var month by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickYear by rememberSaveable { mutableStateOf(false) }
    var payer by remember { mutableStateOf<Pair<String, String>?>(null) }
    val period = Dividends.period(data.events, year.takeIf { it != ALL_YEARS }, data.rates)
    val before = if (year != ALL_YEARS && compare) Dividends.year(data.events, year - 1, data.rates) else null
    val locale = Locale.getDefault()
    val today = LocalDate.now()

    Title(year) { pickYear = true }
    Text(
        when (year) {
            ALL_YEARS -> stringResource(R.string.calm_dividends_all)
            today.year -> stringResource(R.string.calm_dividends_this_year)
            else -> stringResource(R.string.calm_dividends_in, year)
        },
        color = CalmColors.Muted, fontSize = 14.sp,
    )
    Text(skinMoney(period.usdNet.amount, "USD"), fontSize = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.calm_after_tax, skinMoney(period.usdTax.amount.abs(), "USD")), color = CalmColors.Muted, fontSize = 13.sp)
        if (year != ALL_YEARS) {
            Dividends.changeFromYearBefore(data.events, year, data.rates, today)?.let { change ->
                Chip(
                    stringResource(R.string.calm_vs_year, skinPercent(change), year - 1),
                    if (change.signum() >= 0) CalmColors.GainSoft else CalmColors.LossSoft,
                    if (change.signum() >= 0) CalmColors.Gain else CalmColors.Loss,
                )
            }
        }
    }
    val missing = listOf(period.usdGross, period.usdTax, period.usdNet).flatMap { it.missingCurrencies }.toSortedSet()
    val note = buildList {
        add(stringResource(R.string.header_approximate))
        period.rateDate?.let { add(stringResource(R.string.header_rate_date, it)) }
        if (missing.isNotEmpty()) add(stringResource(R.string.dividends_incomplete, missing.joinToString()))
    }
    Text(note.joinToString(" · "), color = CalmColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

    BarChart(
        groups = period.months.mapIndexed { i, m ->
            val label = Month.of(m.month).getDisplayName(TextStyle.NARROW, locale)
            val bars = buildList {
                before?.let { add(ChartBar(it.months[i].usdNet.amount.toDouble(), Color(0xFFC8D8FF), "$label ${year - 1}")) }
                add(ChartBar(m.usdNet.amount.toDouble(), if (month == i || before != null) CalmColors.Accent else CalmColors.AccentSoft, "$label: ${skinMoney(m.usdNet.amount, "USD")}"))
            }
            BarGroup(label, bars)
        },
        height = 150.dp,
        modifier = Modifier.padding(top = 18.dp),
        onGroupClick = { month = if (month == it) null else it },
    )
    month?.let { i ->
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).background(CalmColors.Card, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(Month.of(i + 1).getDisplayName(TextStyle.FULL, locale) + if (year == ALL_YEARS) "" else " $year", modifier = Modifier.weight(1f))
            Text(stringResource(R.string.calm_net, skinMoney(period.months[i].usdNet.amount, "USD")), fontWeight = FontWeight.Bold)
        }
    }
    if (year != ALL_YEARS) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            SegmentedButton(selected = !compare, onClick = { compare = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.calm_top_payers)) }
            SegmentedButton(selected = compare, onClick = { compare = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.calm_vs_last_year)) }
        }
    }
    Spacer(Modifier.height(8.dp))
    period.positions.forEach { p ->
        Row(Modifier.fillMaxWidth().clickable { payer = p.ticker to p.currency }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TickerTile(p.ticker)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(p.ticker, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.dividends_line, skinMoney(p.amounts.gross, p.currency), skinMoney(p.amounts.tax, p.currency)), color = CalmColors.Muted, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(skinMoney(p.amounts.net, p.currency), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.calm_net_short), color = CalmColors.Muted, fontSize = 12.sp)
            }
        }
        HorizontalDivider(color = CalmColors.Line)
    }

    if (pickYear) {
        ModalBottomSheet(onDismissRequest = { pickYear = false }, containerColor = Color.White) {
            Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp)) {
                Text(stringResource(R.string.calm_show_year), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                (years + ALL_YEARS).forEach { y ->
                    Row(
                        Modifier.fillMaxWidth().clickable { year = y; month = null; if (y == ALL_YEARS) compare = false; pickYear = false }.padding(vertical = 14.dp),
                    ) {
                        Text(if (y == ALL_YEARS) stringResource(R.string.calm_all_years) else y.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (y == year) Text("✓", color = CalmColors.Accent, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = CalmColors.Line)
                }
            }
        }
    }

    payer?.let { (ticker, currency) ->
        ModalBottomSheet(onDismissRequest = { payer = null }, containerColor = Color.White) {
            PayerHistorySheet(Dividends.history(data.events, ticker, currency))
        }
    }
}

/** A payer's bookings over all years, newest first, with totals (mobile-payer-history). */
@Composable
private fun PayerHistorySheet(history: PayerHistory) {
    val cur = history.currency
    Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TickerTile(history.ticker)
            Column(Modifier.padding(start = 12.dp)) {
                Text(history.ticker + " · " + stringResource(R.string.payer_history_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.payer_history_subtitle, cur, pluralStringResource(R.plurals.payer_history_count, history.lines.size, history.lines.size)), color = CalmColors.Muted, fontSize = 13.sp)
            }
        }
        Text(stringResource(R.string.calm_net, skinMoney(history.total.net, cur)), fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
        Text(stringResource(R.string.dividends_line, skinMoney(history.total.gross, cur), skinMoney(history.total.tax, cur)), color = CalmColors.Muted, modifier = Modifier.padding(bottom = 8.dp))
        history.lines.forEach { line ->
            val a = line.amounts
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(line.date, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (a.gross.signum() == 0) stringResource(R.string.payer_history_tax_only, skinMoney(a.tax, cur))
                        else stringResource(R.string.dividends_line, skinMoney(a.gross, cur), skinMoney(a.tax, cur)),
                        color = CalmColors.Muted, fontSize = 12.sp,
                    )
                }
                Text(skinMoney(a.net, cur), fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = CalmColors.Line)
        }
    }
}
