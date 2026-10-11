package com.ibkrtax.mobile.ui.skins.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.portfolio.Dividends
import com.ibkrtax.mobile.state.IncomeState
import com.ibkrtax.mobile.ui.common.YearChoices
import com.ibkrtax.mobile.ui.common.skinMoney
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private const val ALL_YEARS = -1

/** Income (mobile-skin-focus): year pills with "All years", one sentence and a timeline of payments. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusIncome(state: IncomeState, onImport: () -> Unit) {
    val data by state.data.collectAsState()
    LaunchedEffect(Unit) { state.reload() }
    val current = data
    val years = current?.let { Dividends.years(it.events) }.orEmpty()
    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    var payer by remember { mutableStateOf<Pair<String, String>?>(null) }
    val locale = Locale.getDefault()
    val today = LocalDate.now()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        item { Text(stringResource(R.string.calm_tab_income), fontFamily = Serif, fontSize = 26.sp, modifier = Modifier.padding(top = 12.dp)) }
        if (current == null) {
            item { Text(stringResource(R.string.loading), color = FocusColors.Muted) }
            return@LazyColumn
        }
        if (years.isEmpty()) {
            item {
                Text(stringResource(R.string.dividends_empty), color = FocusColors.Muted, modifier = Modifier.padding(vertical = 10.dp))
                Button(onClick = onImport) { Text(stringResource(R.string.calm_add_reports)) }
            }
            return@LazyColumn
        }
        val selected = year?.takeIf { it == ALL_YEARS || it in years } ?: years.first()
        val chosen = selected.takeIf { it != ALL_YEARS }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val choices = YearChoices.of(years)
                choices.direct.reversed().forEach { y -> YearPill(y.toString(), y == selected) { year = y } }
                if (choices.older.isNotEmpty()) {
                    Box {
                        var open by remember { mutableStateOf(false) }
                        val olderOn = selected in choices.older
                        YearPill((if (olderOn) selected.toString() else stringResource(R.string.year_older)) + " ▾", olderOn) { open = true }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            choices.older.forEach { y -> DropdownMenuItem(text = { Text(y.toString()) }, onClick = { year = y; open = false }) }
                        }
                    }
                }
                YearPill(stringResource(R.string.calm_all_years), selected == ALL_YEARS, outlined = true) { year = ALL_YEARS }
            }
        }
        item {
            val period = Dividends.period(current.events, chosen, current.rates)
            val sentence = if (chosen == null) {
                Summaries.income(period, null, IncomePeriod.AllYears(years.last()))
            } else {
                Summaries.income(period, Dividends.changeFromYearBefore(current.events, chosen, current.rates, today), IncomePeriod.Year(chosen, chosen == today.year))
            }
            Text(sentence.annotated(), fontSize = 17.sp, lineHeight = 25.sp)
        }
        Dividends.payments(current.events, chosen).groupBy { it.month }.forEach { (month, payments) ->
            item(key = "m-$month") {
                val name = month.month.getDisplayName(TextStyle.FULL, locale) + if (chosen == null) " ${month.year}" else ""
                Text(name.uppercase(locale), color = FocusColors.Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.7.sp, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp))
            }
            items(payments, key = { "${it.date}-${it.ticker}-${it.currency}" }) { p ->
                Row(Modifier.fillMaxWidth().clickable { payer = p.ticker to p.currency }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(2.dp).height(40.dp).background(FocusColors.Line))
                    Box(Modifier.padding(horizontal = 12.dp).size(12.dp).background(FocusColors.Gain, CircleShape))
                    Column(Modifier.weight(1f)) {
                        Text(p.ticker, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        val day = month.month.getDisplayName(TextStyle.SHORT, locale) + " " + p.date.drop(8).trimStart('0')
                        val tax = p.amounts.tax.takeIf { it.signum() != 0 }?.let { stringResource(R.string.focus_tax_withheld, skinMoney(it.abs(), p.currency)) }
                        Text(listOfNotNull(day, tax).joinToString(" · "), color = FocusColors.Muted, fontSize = 13.sp)
                    }
                    Text(skinMoney(p.amounts.net, p.currency, signed = true), color = if (p.amounts.net.signum() >= 0) FocusColors.Gain else FocusColors.Loss, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    payer?.let { (ticker, currency) ->
        val events = current?.events.orEmpty()
        ModalBottomSheet(onDismissRequest = { payer = null }, containerColor = FocusColors.Page) {
            val history = Dividends.history(events, ticker, currency)
            Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 30.dp).verticalScroll(rememberScrollState())) {
                Text("$ticker · " + stringResource(R.string.payer_history_title), fontFamily = Serif, fontSize = 22.sp)
                Text(
                    stringResource(R.string.payer_history_subtitle, currency, pluralStringResource(R.plurals.payer_history_count, history.lines.size, history.lines.size)),
                    color = FocusColors.Muted, fontSize = 13.sp,
                )
                Text(stringResource(R.string.calm_net, skinMoney(history.total.net, currency)), fontFamily = Serif, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                Text(stringResource(R.string.dividends_line, skinMoney(history.total.gross, currency), skinMoney(history.total.tax, currency)), color = FocusColors.Muted, modifier = Modifier.padding(bottom = 8.dp))
                history.lines.forEach { line ->
                    val a = line.amounts
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(line.date, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (a.gross.signum() == 0) stringResource(R.string.payer_history_tax_only, skinMoney(a.tax, currency))
                                else stringResource(R.string.dividends_line, skinMoney(a.gross, currency), skinMoney(a.tax, currency)),
                                color = FocusColors.Muted, fontSize = 12.sp,
                            )
                        }
                        Text(skinMoney(a.net, currency), fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = FocusColors.Line)
                }
            }
        }
    }
}

@Composable
private fun YearPill(text: String, on: Boolean, outlined: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val base = Modifier.clip(shape).let { if (outlined && !on) it.border(1.dp, FocusColors.Line, shape) else it.background(if (on) FocusColors.Ink else FocusColors.Sand) }
    Text(
        text,
        color = if (on) Color.White else FocusColors.Muted,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = base.clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 7.dp),
    )
}
