package com.ibkrtax.mobile.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.appContainer
import com.ibkrtax.mobile.portfolio.MainPage
import com.ibkrtax.mobile.portfolio.PnlMode
import com.ibkrtax.mobile.portfolio.PositionRow
import com.ibkrtax.mobile.portfolio.SortColumn
import com.ibkrtax.mobile.portfolio.SortOrder
import com.ibkrtax.mobile.portfolio.UsdTotals
import com.ibkrtax.mobile.prices.PriceFailure
import com.ibkrtax.mobile.prices.PriceFreshness
import com.ibkrtax.mobile.prices.RefreshOutcome
import com.ibkrtax.mobile.security.KeyUnwrapException
import com.ibkrtax.mobile.security.KeystoreUnavailableException
import com.ibkrtax.mobile.storage.HoldingsResult
import com.ibkrtax.mobile.ui.Formats
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

sealed interface PortfolioUiState {
    data object Loading : PortfolioUiState

    data object NoReports : PortfolioUiState

    data object StorageUnavailable : PortfolioUiState

    data class Incomplete(val ticker: String, val date: String) : PortfolioUiState

    /** [prices] is null while a refresh is running. */
    data class Loaded(
        val accounts: List<String>,
        val rows: List<PositionRow>,
        val totals: UsdTotals,
        val prices: RefreshOutcome?,
    ) : PortfolioUiState
}

/** Reads the encrypted database, cached prices and cached NBP rates; must run off the main thread. */
fun loadPortfolio(context: Context, prices: RefreshOutcome?): PortfolioUiState =
    try {
        val container = context.appContainer
        val reports = container.importHistory.list()
        if (reports.isEmpty()) {
            PortfolioUiState.NoReports
        } else {
            when (val result = container.portfolio.holdings()) {
                is HoldingsResult.Incomplete -> PortfolioUiState.Incomplete(result.ticker, result.date)
                is HoldingsResult.Success -> {
                    val rows = MainPage.rows(
                        result.holdings,
                        container.prices.prices(result.holdings, prices),
                        container.portfolio.listingExchanges(),
                    )
                    PortfolioUiState.Loaded(
                        accounts = reports.mapNotNull { it.accountMasked }.distinct(),
                        rows = rows,
                        totals = MainPage.usdTotals(rows, container.fx.rates()),
                        prices = prices,
                    )
                }
            }
        }
    } catch (e: KeystoreUnavailableException) {
        PortfolioUiState.StorageUnavailable
    } catch (e: KeyUnwrapException) {
        PortfolioUiState.StorageUnavailable
    }

/** Refreshes prices for in-scope holdings and the NBP table in parallel (network); call off the main thread. */
suspend fun refreshMarketData(context: Context): RefreshOutcome = coroutineScope {
    val container = context.appContainer
    val rates = async { container.fx.refresh() }
    val holdings = (container.portfolio.holdings() as? HoldingsResult.Success)?.holdings.orEmpty()
    val prices = container.prices.refresh(holdings)
    rates.await()
    prices
}

@Composable
fun PortfolioScreen(onImportReport: () -> Unit) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    // Show cached data at once, then refresh when the page opens or the user asks.
    val state by produceState<PortfolioUiState>(PortfolioUiState.Loading, refreshTick) {
        val cached = withContext(Dispatchers.IO) { loadPortfolio(context, prices = null) }
        value = cached
        if (cached is PortfolioUiState.Loaded) {
            val outcome = withContext(Dispatchers.IO) { refreshMarketData(context) }
            value = withContext(Dispatchers.IO) { loadPortfolio(context, outcome) }
        }
    }

    when (val current = state) {
        PortfolioUiState.Loading -> Centered { Text(stringResource(R.string.loading)) }
        PortfolioUiState.NoReports -> EmptyPortfolio(onImportReport)
        PortfolioUiState.StorageUnavailable -> Centered { Text(stringResource(R.string.storage_unavailable), textAlign = TextAlign.Center) }
        is PortfolioUiState.Incomplete -> Centered {
            Text(stringResource(R.string.portfolio_incomplete, current.ticker, current.date), textAlign = TextAlign.Center)
        }
        is PortfolioUiState.Loaded -> Positions(current, onRefresh = { refreshTick++ })
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun EmptyPortfolio(onImportReport: () -> Unit) {
    Centered {
        Text(stringResource(R.string.portfolio_empty_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.portfolio_empty_body), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onImportReport) { Text(stringResource(R.string.action_import_report)) }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.informational_notice), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

private val GAIN = Color(0xFF2E7D32)
private val LOSS = Color(0xFFC62828)

private fun signColor(value: BigDecimal?): Color =
    when (value?.signum()) {
        1 -> GAIN
        -1 -> LOSS
        else -> Color.Unspecified
    }

@Composable
private fun Positions(state: PortfolioUiState.Loaded, onRefresh: () -> Unit) {
    var sort by remember { mutableStateOf(SortOrder()) }
    var mode by rememberSaveable { mutableStateOf(PnlMode.DAILY) }
    val rows = MainPage.sorted(state.rows, sort, mode)

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item { Header(state, onRefresh) }
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                PnlMode.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = mode == option,
                        onClick = { mode = option },
                        shape = SegmentedButtonDefaults.itemShape(index, PnlMode.entries.size),
                    ) {
                        Text(stringResource(if (option == PnlMode.DAILY) R.string.pnl_daily else R.string.pnl_unrealized))
                    }
                }
            }
        }
        item { ColumnHeaders(sort, onSort = { sort = sort.select(it) }) }
        if (rows.isEmpty()) item { Text(stringResource(R.string.portfolio_no_holdings), modifier = Modifier.padding(vertical = 16.dp)) }
        items(rows, key = { "${it.ticker}-${it.holding.isin}-${it.currency}" }) { PositionLine(it, mode) }
        item {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.informational_notice), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(state: PortfolioUiState.Loaded, onRefresh: () -> Unit) {
    val totals = state.totals
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(stringResource(R.string.portfolio_title), style = MaterialTheme.typography.titleMedium)
        if (state.accounts.isNotEmpty()) {
            Text(stringResource(R.string.portfolio_accounts, state.accounts.joinToString()), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.header_total_value), style = MaterialTheme.typography.bodySmall)
                Text(Formats.money(totals.marketValue, "USD"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.header_daily_pnl), style = MaterialTheme.typography.bodySmall)
                Text(
                    Formats.signedMoney(totals.dailyPnl, "USD"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = signColor(totals.dailyPnl),
                )
                totals.dailyPnlPercent?.let {
                    Text(Formats.signedPercent(it), color = signColor(it), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        val notes = buildList {
            add(stringResource(R.string.header_approximate))
            totals.rateDate?.let { add(stringResource(R.string.header_rate_date, it)) }
            if (!totals.isComplete) add(stringResource(R.string.header_incomplete, totals.missingValues, state.rows.size))
        }
        Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        PriceStatus(state.prices, onRefresh)
    }
}

@Composable
private fun ColumnHeaders(sort: SortOrder, onSort: (SortColumn) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        HeaderCell(R.string.column_instrument, SortColumn.SYMBOL, sort, onSort, weight = 1.5f, alignEnd = false)
        HeaderCell(R.string.column_last, SortColumn.LAST_PRICE, sort, onSort, weight = 1.1f)
        HeaderCell(R.string.column_change, SortColumn.CHANGE, sort, onSort, weight = 1.1f)
        HeaderCell(R.string.column_position, SortColumn.POSITION, sort, onSort, weight = 0.8f)
        HeaderCell(R.string.column_pnl, SortColumn.PNL, sort, onSort, weight = 1.1f)
    }
    HorizontalDivider()
}

@Composable
private fun RowScope.HeaderCell(
    label: Int,
    column: SortColumn,
    sort: SortOrder,
    onSort: (SortColumn) -> Unit,
    weight: Float,
    alignEnd: Boolean = true,
) {
    val arrow = when {
        sort.column != column -> ""
        sort.descending -> " ▼"
        else -> " ▲"
    }
    Text(
        stringResource(label) + arrow,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (sort.column == column) FontWeight.Bold else null,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        modifier = Modifier.weight(weight).clickable { onSort(column) },
    )
}

@Composable
private fun PositionLine(row: PositionRow, mode: PnlMode) {
    val pnl = if (mode == PnlMode.DAILY) row.dailyPnl else row.unrealizedPnl
    val muted = MaterialTheme.typography.bodySmall
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1.5f)) {
            Text(row.ticker, fontWeight = FontWeight.Bold)
            Text(listOf(row.listingExchange, row.currency).filter { it.isNotEmpty() }.joinToString(" · "), style = muted)
        }
        Column(modifier = Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
            val price = row.price
            if (price == null) {
                Text("—")
                Text(stringResource(if (row.inPriceScope) R.string.price_unavailable_short else R.string.price_out_of_scope_short), style = muted)
            } else {
                Text(Formats.number(price.quote.price, 2, 4))
                when (price.freshness) {
                    PriceFreshness.LIVE -> Unit
                    PriceFreshness.LATEST_CLOSE -> Text(stringResource(R.string.price_close_short), style = muted)
                    PriceFreshness.STALE -> Text(stringResource(R.string.price_stale_short), style = muted, color = LOSS)
                }
            }
        }
        Column(modifier = Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
            val change = row.dailyChange
            Text(change?.let(Formats::signedNumber) ?: "—", color = signColor(change))
            row.dailyChangePercent?.let { Text(Formats.signedPercent(it), style = muted, color = signColor(it)) }
        }
        Text(Formats.quantity(row.holding.quantity), textAlign = TextAlign.End, modifier = Modifier.weight(0.8f))
        Text(pnl?.let(Formats::signedNumber) ?: "—", color = signColor(pnl), textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
    }
    HorizontalDivider()
}

@Composable
private fun PriceStatus(prices: RefreshOutcome?, onRefresh: () -> Unit) {
    val message = when (prices) {
        null -> stringResource(R.string.prices_refreshing)
        RefreshOutcome.NoKey -> stringResource(R.string.prices_no_key)
        RefreshOutcome.Updated -> null
        is RefreshOutcome.Failed -> stringResource(
            R.string.prices_failed,
            stringResource(
                when (prices.reason) {
                    PriceFailure.OFFLINE -> R.string.prices_failure_offline
                    PriceFailure.INVALID_KEY -> R.string.prices_failure_invalid_key
                    PriceFailure.RATE_LIMITED -> R.string.prices_failure_rate_limited
                    PriceFailure.PROVIDER_ERROR -> R.string.prices_failure_provider
                },
            ),
        )
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (prices != null && prices != RefreshOutcome.NoKey) {
        TextButton(onClick = onRefresh) { Text(stringResource(R.string.prices_refresh)) }
    }
}
