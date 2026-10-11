package com.ibkrtax.mobile.ui.skins.classic

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.state.ActivityState
import com.ibkrtax.mobile.storage.Operation
import com.ibkrtax.mobile.storage.OperationFilter
import com.ibkrtax.mobile.ui.Formats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Imported transactions, newest first, with ticker search and type filters (mobile-operations spec). */
@Composable
fun OperationsScreen(state: ActivityState, onImportReport: () -> Unit) {
    val view by state.view.collectAsState()
    val query = view.query
    val filters = view.filters
    val items = view.items
    val hasAny = view.hasAny
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { state.reload() }
    // New search or filters: the list starts again from the top.
    LaunchedEffect(query, filters) { listState.scrollToItem(0) }
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 20
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { state.loadMore() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.operations_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        when (hasAny) {
            null -> Text(stringResource(R.string.loading))
            false -> NoOperations(onImportReport)
            true -> {
                OutlinedTextField(
                    value = query,
                    onValueChange = state::setQuery,
                    label = { Text(stringResource(R.string.operations_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { state.setQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.operations_clear_search))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                ) {
                    OperationFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = filter in filters,
                            onClick = { state.toggle(filter) },
                            label = { Text(stringResource(filter.labelRes())) },
                        )
                    }
                }
                if (items.isEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.operations_no_match), textAlign = TextAlign.Center)
                        TextButton(onClick = state::clear) { Text(stringResource(R.string.operations_clear_all)) }
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(items, key = { it.id }) { OperationRow(it) }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoOperations(onImportReport: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.operations_empty), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onImportReport) { Text(stringResource(R.string.action_import_report)) }
    }
}

@Composable
private fun OperationRow(operation: Operation) {
    val muted = MaterialTheme.typography.bodySmall
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(operation.ticker, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
            Text(typeLabel(operation.eventType), style = muted, modifier = Modifier.weight(1f))
            Text(OperationDisplay.amount(operation), textAlign = TextAlign.End)
        }
        val details = listOfNotNull(
            displayDate(operation.date),
            OperationDisplay.details(operation),
            operation.fee.takeIf { it.signum() != 0 }?.let { stringResource(R.string.operations_commission, Formats.money(it.abs(), operation.currency)) },
        )
        Text(details.joinToString(" · "), style = muted)
    }
    HorizontalDivider()
}

/** Text for an operation's amount and details; pure so it can be unit-tested. */
object OperationDisplay {
    private val TRADES = setOf("BUY", "SELL", "TRANSFER")
    private val CASH = setOf("DIVIDEND", "TAX")

    /** Trades show the absolute value (the type says the direction); cash keeps its sign; corporate actions show shares. */
    fun amount(operation: Operation): String = when (operation.eventType) {
        in TRADES -> Formats.money(operation.amount.abs(), operation.currency)
        in CASH -> Formats.signedMoney(operation.amount, operation.currency)
        else -> signedQuantity(operation)
    }

    fun details(operation: Operation): String? = when (operation.eventType) {
        in TRADES -> "${Formats.quantity(operation.quantity.abs())} × ${Formats.number(operation.price, 2, 6)} ${operation.currency}"
        else -> null
    }

    private fun signedQuantity(operation: Operation): String =
        (if (operation.quantity.signum() > 0) "+" else "") + Formats.quantity(operation.quantity)
}

private fun displayDate(date: String): String =
    try {
        LocalDate.parse(date.take(10)).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    } catch (e: DateTimeParseException) {
        date
    }

@Composable
private fun typeLabel(eventType: String): String = when (eventType) {
    "BUY" -> stringResource(R.string.operation_buy)
    "SELL" -> stringResource(R.string.operation_sell)
    "DIVIDEND" -> stringResource(R.string.operation_dividend)
    "TAX" -> stringResource(R.string.operation_tax)
    "TRANSFER" -> stringResource(R.string.operation_transfer)
    "SPLIT" -> stringResource(R.string.operation_split)
    "STOCK_DIV" -> stringResource(R.string.operation_stock_dividend)
    "MERGER" -> stringResource(R.string.operation_merger)
    "SPINOFF" -> stringResource(R.string.operation_spinoff)
    else -> eventType
}

private fun OperationFilter.labelRes(): Int = when (this) {
    OperationFilter.BUYS -> R.string.operations_filter_buys
    OperationFilter.SELLS -> R.string.operations_filter_sells
    OperationFilter.DIVIDENDS -> R.string.operations_filter_dividends
    OperationFilter.TAXES -> R.string.operations_filter_taxes
    OperationFilter.OTHER -> R.string.operations_filter_other
}
