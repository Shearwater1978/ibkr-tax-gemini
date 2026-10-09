package com.ibkrtax.mobile.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.appContainer
import com.ibkrtax.mobile.portfolio.CurrencySubtotal
import com.ibkrtax.mobile.portfolio.HoldingValue
import com.ibkrtax.mobile.portfolio.PortfolioSummary
import com.ibkrtax.mobile.prices.PriceFailure
import com.ibkrtax.mobile.prices.PriceFreshness
import com.ibkrtax.mobile.prices.RefreshOutcome
import com.ibkrtax.mobile.security.KeyUnwrapException
import com.ibkrtax.mobile.security.KeystoreUnavailableException
import com.ibkrtax.mobile.storage.HoldingsResult
import com.ibkrtax.mobile.ui.Formats
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface PortfolioUiState {
    data object Loading : PortfolioUiState

    data object NoReports : PortfolioUiState

    data object StorageUnavailable : PortfolioUiState

    data class Incomplete(val ticker: String, val date: String) : PortfolioUiState

    /** [prices] is null while a refresh is running. */
    data class Loaded(val accounts: List<String>, val subtotals: List<CurrencySubtotal>, val prices: RefreshOutcome?) : PortfolioUiState
}

/** Reads the encrypted database and cached prices; must run off the main thread. */
fun loadPortfolio(context: Context, prices: RefreshOutcome?): PortfolioUiState =
    try {
        val container = context.appContainer
        val reports = container.importHistory.list()
        if (reports.isEmpty()) {
            PortfolioUiState.NoReports
        } else {
            when (val result = container.portfolio.holdings()) {
                is HoldingsResult.Incomplete -> PortfolioUiState.Incomplete(result.ticker, result.date)
                is HoldingsResult.Success -> PortfolioUiState.Loaded(
                    accounts = reports.mapNotNull { it.accountMasked }.distinct(),
                    subtotals = PortfolioSummary.build(result.holdings, container.prices.prices(result.holdings, prices)),
                    prices = prices,
                )
            }
        }
    } catch (e: KeystoreUnavailableException) {
        PortfolioUiState.StorageUnavailable
    } catch (e: KeyUnwrapException) {
        PortfolioUiState.StorageUnavailable
    }

/** Requests fresh prices for in-scope holdings (network); call off the main thread. */
suspend fun refreshPrices(context: Context): RefreshOutcome {
    val holdings = (context.appContainer.portfolio.holdings() as? HoldingsResult.Success)?.holdings.orEmpty()
    return context.appContainer.prices.refresh(holdings)
}

@Composable
fun PortfolioScreen(onImportReport: () -> Unit) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    // Show cached prices at once, then refresh when the overview opens or the user asks.
    val state by produceState<PortfolioUiState>(PortfolioUiState.Loading, refreshTick) {
        val cached = withContext(Dispatchers.IO) { loadPortfolio(context, prices = null) }
        value = cached
        if (cached is PortfolioUiState.Loaded) {
            val outcome = withContext(Dispatchers.IO) { refreshPrices(context) }
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
        is PortfolioUiState.Loaded -> HoldingsList(current, onRefresh = { refreshTick++ })
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

@Composable
private fun HoldingsList(state: PortfolioUiState.Loaded, onRefresh: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.portfolio_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.informational_notice), style = MaterialTheme.typography.bodySmall)
            if (state.accounts.isNotEmpty()) {
                Text(stringResource(R.string.portfolio_accounts, state.accounts.joinToString()), style = MaterialTheme.typography.bodySmall)
            }
            PriceStatus(state.prices, onRefresh)
            Spacer(Modifier.height(8.dp))
            if (state.subtotals.isEmpty()) Text(stringResource(R.string.portfolio_no_holdings))
        }
        state.subtotals.forEach { subtotal ->
            item(key = "header-${subtotal.currency}") {
                Text(
                    subtotal.currency,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(subtotal.holdings, key = { "${it.holding.ticker}-${it.holding.isin}-${it.holding.currency}" }) { HoldingRow(it) }
            item(key = "subtotal-${subtotal.currency}") { SubtotalCard(subtotal) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun HoldingRow(value: HoldingValue) {
    val holding = value.holding
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(holding.ticker, fontWeight = FontWeight.Bold)
        if (holding.isin.isNotEmpty()) Text(holding.isin, style = MaterialTheme.typography.bodySmall)
        Text(
            stringResource(
                R.string.holding_position,
                Formats.quantity(holding.quantity),
                Formats.price(holding.averagePrice, holding.currency),
            ),
        )
        Text(stringResource(R.string.holding_cost, Formats.money(value.cost, holding.currency)))
        val price = value.price
        val marketValue = value.marketValue
        val gain = value.unrealizedGain
        when {
            !value.inPriceScope -> Text(stringResource(R.string.holding_price_out_of_scope), style = MaterialTheme.typography.bodySmall)
            price == null || marketValue == null || gain == null ->
                Text(stringResource(R.string.holding_price_unavailable), style = MaterialTheme.typography.bodySmall)
            else -> {
                val label = when (price.freshness) {
                    PriceFreshness.LIVE -> R.string.holding_price_live
                    PriceFreshness.LATEST_CLOSE -> R.string.holding_price_latest_close
                    PriceFreshness.STALE -> R.string.holding_price_stale
                }
                Text(
                    stringResource(label, Formats.price(price.quote.price, price.quote.currency), TIME.format(price.quote.retrievedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (price.freshness == PriceFreshness.STALE) FontWeight.Bold else null,
                )
                Text(stringResource(R.string.holding_value, Formats.money(marketValue, holding.currency), Formats.money(gain, holding.currency)))
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun SubtotalCard(subtotal: CurrencySubtotal) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.subtotal_title, subtotal.currency), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.subtotal_cost, Formats.money(subtotal.cost, subtotal.currency)))
            if (subtotal.isComplete) {
                Text(
                    stringResource(
                        R.string.subtotal_value,
                        Formats.money(subtotal.marketValue, subtotal.currency),
                        Formats.money(subtotal.unrealizedGain, subtotal.currency),
                    ),
                )
            } else {
                Text(
                    stringResource(R.string.subtotal_incomplete, subtotal.missingPrices, subtotal.holdings.size),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (subtotal.stalePrices > 0) {
                Text(stringResource(R.string.subtotal_stale, subtotal.stalePrices), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private val TIME: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

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
