package com.ibkrtax.mobile.state

import com.ibkrtax.mobile.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Everything a skin reads and acts on (mobile-skins spec). Created once per app session, so a
 * skin switch keeps the loaded data and every skin shows identical numbers.
 */
class AppStates(
    val portfolio: PortfolioState,
    val income: IncomeState,
    val activity: ActivityState,
    val data: DataState,
) {
    /** A manual operation was added, edited or deleted: every view re-reads stored data, without new prices. */
    fun manualChanged() {
        portfolio.reload()
        income.reload()
        activity.reload()
        data.reload()
    }

    companion object {
        fun create(container: AppContainer): AppStates {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            return AppStates(
                portfolio = PortfolioState.create(container, scope),
                income = IncomeState.create(container, scope),
                activity = ActivityState.create(container, scope),
                // Loading data once at start also starts backup retries if any are pending.
                data = DataState.create(container, scope).also { it.reload() },
            )
        }
    }
}
