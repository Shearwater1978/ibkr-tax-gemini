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
    companion object {
        fun create(container: AppContainer): AppStates {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            return AppStates(
                portfolio = PortfolioState.create(container, scope),
                income = IncomeState.create(container, scope),
                activity = ActivityState.create(container, scope),
                data = DataState.create(container, scope),
            )
        }
    }
}
