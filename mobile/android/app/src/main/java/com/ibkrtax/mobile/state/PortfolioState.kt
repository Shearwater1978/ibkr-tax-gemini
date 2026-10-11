package com.ibkrtax.mobile.state

import com.ibkrtax.mobile.AppContainer
import com.ibkrtax.mobile.fx.FxRates
import com.ibkrtax.mobile.portfolio.MainPage
import com.ibkrtax.mobile.portfolio.Performance
import com.ibkrtax.mobile.portfolio.PortfolioSummary
import com.ibkrtax.mobile.portfolio.PositionPerformance
import com.ibkrtax.mobile.portfolio.PositionRow
import com.ibkrtax.mobile.portfolio.UsdTotals
import com.ibkrtax.mobile.prices.Markets
import com.ibkrtax.mobile.prices.PriceScope
import com.ibkrtax.mobile.prices.PriceSource
import com.ibkrtax.mobile.prices.RefreshOutcome
import com.ibkrtax.mobile.prices.RefreshProgress
import com.ibkrtax.mobile.security.KeyUnwrapException
import com.ibkrtax.mobile.security.KeystoreUnavailableException
import com.ibkrtax.mobile.storage.HoldingsResult
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PortfolioUiState {
    data object Loading : PortfolioUiState

    data object NoReports : PortfolioUiState

    data object StorageUnavailable : PortfolioUiState

    data class Incomplete(val ticker: String, val date: String) : PortfolioUiState

    /** [prices] is null while a refresh is running. */
    data class Loaded(
        val rows: List<PositionRow>,
        val totals: UsdTotals,
        /** Held positions (same order as [rows]) followed by fully sold ones. */
        val performance: List<PositionPerformance>,
        val summary: PortfolioSummary,
        val rates: FxRates?,
        /** Some held positions are European listings, which are always priced from Yahoo Finance. */
        val hasEuropean: Boolean,
        val prices: RefreshOutcome?,
        val priceSource: PriceSource,
    ) : PortfolioUiState
}

/**
 * The portfolio for every skin (mobile-skins spec): shows cached data at once, then refreshes
 * prices and NBP rates. One instance per app session, so all skins show the same numbers.
 */
class PortfolioState(
    private val scope: CoroutineScope,
    /** Reads the database, cached prices and rates; blocking. */
    private val load: (RefreshOutcome?) -> PortfolioUiState,
    /** Fetches prices and rates over the network. */
    private val refreshMarket: suspend ((RefreshProgress) -> Unit) -> RefreshOutcome,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _ui = MutableStateFlow<PortfolioUiState>(PortfolioUiState.Loading)
    val ui: StateFlow<PortfolioUiState> = _ui.asStateFlow()

    private val _progress = MutableStateFlow<RefreshProgress?>(null)

    /** What a running price refresh is doing; null when none runs. */
    val progress: StateFlow<RefreshProgress?> = _progress.asStateFlow()

    private var job: Job? = null

    /** Loads cached data, then refreshes market data; a new call replaces a running one. */
    fun refresh() {
        job?.cancel()
        job = scope.launch {
            val cached = withContext(io) { load(null) }
            _ui.value = cached
            if (cached is PortfolioUiState.Loaded) {
                _progress.value = null
                try {
                    val outcome = withContext(io) { refreshMarket { _progress.value = it } }
                    _ui.value = withContext(io) { load(outcome) }
                } finally {
                    _progress.value = null
                }
            }
        }
    }

    /** Re-reads stored data without network, e.g. after a currency or import change. */
    fun reload() {
        job?.cancel()
        job = scope.launch { _ui.value = withContext(io) { load((_ui.value as? PortfolioUiState.Loaded)?.prices) } }
    }

    companion object {
        fun create(container: AppContainer, scope: CoroutineScope) =
            PortfolioState(scope, { prices -> loadPortfolio(container, prices) }, { onProgress -> refreshMarketData(container, onProgress) })

        /** Reads the encrypted database, cached prices and cached NBP rates; must run off the main thread. */
        fun loadPortfolio(container: AppContainer, prices: RefreshOutcome?): PortfolioUiState =
            try {
                val reports = container.importHistory.list()
                if (reports.isEmpty()) {
                    PortfolioUiState.NoReports
                } else {
                    when (val result = container.portfolio.holdings()) {
                        is HoldingsResult.Incomplete -> PortfolioUiState.Incomplete(result.ticker, result.date)
                        is HoldingsResult.Success -> {
                            val exchanges = container.portfolio.listingExchanges()
                            val rows = MainPage.rows(result.holdings, container.prices.prices(result.holdings, prices, exchanges), exchanges)
                            val rates = container.fx.rates()
                            val dividends = container.dividendEvents.all()
                            val performance = Performance.positions(rows, result.realized, dividends)
                            PortfolioUiState.Loaded(
                                rows = rows,
                                totals = MainPage.usdTotals(rows, rates),
                                performance = performance,
                                summary = Performance.summary(performance, dividends, rates, LocalDate.now()),
                                rates = rates,
                                hasEuropean = rows.any { row -> PriceScope.symbol(row.holding, row.listingExchange)?.let(Markets::isEuropean) == true },
                                prices = prices,
                                priceSource = container.prices.source(),
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
        suspend fun refreshMarketData(container: AppContainer, onProgress: (RefreshProgress) -> Unit = {}): RefreshOutcome = coroutineScope {
            val rates = async { container.fx.refresh() }
            val holdings = (container.portfolio.holdings() as? HoldingsResult.Success)?.holdings.orEmpty()
            val prices = container.prices.refresh(holdings, container.portfolio.listingExchanges(), onProgress)
            rates.await()
            prices
        }
    }
}
