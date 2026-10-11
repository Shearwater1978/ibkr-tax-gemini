package com.ibkrtax.mobile.state

import com.ibkrtax.mobile.AppContainer
import com.ibkrtax.mobile.fx.FxRates
import com.ibkrtax.mobile.portfolio.CashEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Imported dividend and withholding-tax rows plus the NBP rates used for USD totals. */
data class DividendData(val events: List<CashEvent>, val rates: FxRates?)

/** Dividends for every skin (mobile-skins spec); null until the first load finished. */
class IncomeState(
    private val scope: CoroutineScope,
    private val load: suspend () -> DividendData,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _data = MutableStateFlow<DividendData?>(null)
    val data: StateFlow<DividendData?> = _data.asStateFlow()

    fun reload() {
        scope.launch { _data.value = withContext(io) { load() } }
    }

    companion object {
        fun create(container: AppContainer, scope: CoroutineScope) = IncomeState(scope, {
            // Rates come from the main page's NBP refresh; fetch once only if nothing is cached yet.
            val rates = container.fx.rates() ?: run {
                container.fx.refresh()
                container.fx.rates()
            }
            DividendData(container.dividendEvents.all(), rates)
        })
    }
}
