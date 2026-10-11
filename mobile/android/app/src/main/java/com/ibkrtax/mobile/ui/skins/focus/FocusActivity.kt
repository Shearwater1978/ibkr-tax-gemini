package com.ibkrtax.mobile.ui.skins.focus

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.ui.common.skinMoney
import com.ibkrtax.mobile.state.ActivityState
import com.ibkrtax.mobile.storage.Operation
import com.ibkrtax.mobile.storage.OperationFilter
import com.ibkrtax.mobile.ui.common.OperationDisplay
import com.ibkrtax.mobile.ui.common.displayDate
import com.ibkrtax.mobile.ui.common.labelRes
import com.ibkrtax.mobile.ui.common.typeLabel
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Operations grouped by month, with search and type chips, opened from the "+" menu (mobile-skin-focus). */
@Composable
fun FocusActivity(state: ActivityState, onBack: () -> Unit, onImport: () -> Unit) {
    val view by state.view.collectAsState()
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { state.reload() }
    LaunchedEffect(view.query, view.filters) { list.scrollToItem(0) }
    val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 20 } }
    LaunchedEffect(list) { snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { state.loadMore() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Back("‹ " + stringResource(R.string.calm_back), onBack)
        Text(stringResource(R.string.calm_tab_activity), fontFamily = Serif, fontSize = 26.sp, modifier = Modifier.padding(bottom = 12.dp))
        if (view.hasAny == false) {
            Text(stringResource(R.string.operations_empty), color = FocusColors.Muted)
            Button(onClick = onImport, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.calm_add_reports)) }
            return@Column
        }
        TextField(
            value = view.query,
            onValueChange = state::setQuery,
            placeholder = { Text("⌕  " + stringResource(R.string.calm_search)) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = FocusColors.Card, unfocusedContainerColor = FocusColors.Card,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = FocusColors.Ink, selectedLabelColor = Color.White)
            FilterChip(selected = view.filters.isEmpty(), onClick = { if (view.filters.isNotEmpty()) state.clear() }, label = { Text(stringResource(R.string.calm_all)) }, colors = chipColors)
            OperationFilter.entries.forEach { f ->
                FilterChip(selected = f in view.filters, onClick = { state.toggle(f) }, label = { Text(stringResource(f.labelRes())) }, colors = chipColors)
            }
        }
        if (view.items.isEmpty() && view.hasAny == true) {
            Text(stringResource(R.string.operations_no_match), color = FocusColors.Muted, modifier = Modifier.padding(top = 20.dp))
            TextButton(onClick = state::clear) { Text(stringResource(R.string.operations_clear_all)) }
        }
        val grouped = view.items.groupBy { runCatching { YearMonth.parse(it.date.take(7)) }.getOrNull() }
        val monthFormat = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            grouped.forEach { (month, ops) ->
                item(key = "m-$month") {
                    Text(month?.format(monthFormat)?.replaceFirstChar { it.titlecase() } ?: "—", color = FocusColors.Muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
                }
                items(ops, key = { it.id }) { ActivityRow(it) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ActivityRow(op: Operation) {
    val (symbol, tint, background) = when (op.eventType) {
        "BUY" -> Triple("＋", FocusColors.Deep, FocusColors.Sand)
        "SELL" -> Triple("−", FocusColors.Loss, FocusColors.LossSoft)
        "DIVIDEND" -> Triple("↓", FocusColors.Gain, FocusColors.GainSoft)
        "TAX" -> Triple("%", FocusColors.Muted, FocusColors.Card)
        else -> Triple("⇄", FocusColors.Ink, FocusColors.Card)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(background, CircleShape), contentAlignment = Alignment.Center) { Text(symbol, color = tint, fontSize = 15.sp) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text("${typeLabel(op.eventType)} · ${op.ticker}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(listOfNotNull(displayDate(op.date), OperationDisplay.details(op)).joinToString(" · "), color = FocusColors.Muted, fontSize = 13.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(OperationDisplay.amount(op), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (op.eventType == "DIVIDEND") FocusColors.Gain else FocusColors.Ink)
            if (op.fee.signum() != 0) Text(stringResource(R.string.operations_commission, skinMoney(op.fee.abs(), op.currency)), color = FocusColors.Muted, fontSize = 12.sp)
        }
    }
    HorizontalDivider(color = FocusColors.Line)
}
