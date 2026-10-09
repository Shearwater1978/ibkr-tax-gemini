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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.ibkrtax.mobile.security.KeyUnwrapException
import com.ibkrtax.mobile.security.KeystoreUnavailableException
import com.ibkrtax.mobile.storage.HoldingsResult
import com.ibkrtax.mobile.ui.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface PortfolioUiState {
    data object Loading : PortfolioUiState

    data object NoReports : PortfolioUiState

    data object StorageUnavailable : PortfolioUiState

    data class Incomplete(val ticker: String, val date: String) : PortfolioUiState

    data class Loaded(val accounts: List<String>, val subtotals: List<CurrencySubtotal>) : PortfolioUiState
}

/** Reads the encrypted database; must run off the main thread. */
fun loadPortfolio(context: Context): PortfolioUiState =
    try {
        val container = context.appContainer
        val reports = container.importHistory.list()
        if (reports.isEmpty()) {
            PortfolioUiState.NoReports
        } else {
            when (val result = container.portfolio.holdings()) {
                is HoldingsResult.Incomplete -> PortfolioUiState.Incomplete(result.ticker, result.date)
                // Prices arrive with mobile-market-prices; until then every value is unavailable.
                is HoldingsResult.Success -> PortfolioUiState.Loaded(
                    accounts = reports.mapNotNull { it.accountMasked }.distinct(),
                    subtotals = PortfolioSummary.build(result.holdings, quotes = emptyMap()),
                )
            }
        }
    } catch (e: KeystoreUnavailableException) {
        PortfolioUiState.StorageUnavailable
    } catch (e: KeyUnwrapException) {
        PortfolioUiState.StorageUnavailable
    }

@Composable
fun PortfolioScreen(onImportReport: () -> Unit) {
    val context = LocalContext.current
    val state by produceState<PortfolioUiState>(PortfolioUiState.Loading) {
        value = withContext(Dispatchers.IO) { loadPortfolio(context) }
    }

    when (val current = state) {
        PortfolioUiState.Loading -> Centered { Text(stringResource(R.string.loading)) }
        PortfolioUiState.NoReports -> EmptyPortfolio(onImportReport)
        PortfolioUiState.StorageUnavailable -> Centered { Text(stringResource(R.string.storage_unavailable), textAlign = TextAlign.Center) }
        is PortfolioUiState.Incomplete -> Centered {
            Text(stringResource(R.string.portfolio_incomplete, current.ticker, current.date), textAlign = TextAlign.Center)
        }
        is PortfolioUiState.Loaded -> HoldingsList(current)
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
private fun HoldingsList(state: PortfolioUiState.Loaded) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.portfolio_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.informational_notice), style = MaterialTheme.typography.bodySmall)
            if (state.accounts.isNotEmpty()) {
                Text(stringResource(R.string.portfolio_accounts, state.accounts.joinToString()), style = MaterialTheme.typography.bodySmall)
            }
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
        val marketValue = value.marketValue
        val gain = value.unrealizedGain
        if (marketValue != null && gain != null) {
            Text(stringResource(R.string.holding_value, Formats.money(marketValue, holding.currency), Formats.money(gain, holding.currency)))
        } else {
            Text(stringResource(R.string.holding_price_unavailable), style = MaterialTheme.typography.bodySmall)
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
        }
    }
}
