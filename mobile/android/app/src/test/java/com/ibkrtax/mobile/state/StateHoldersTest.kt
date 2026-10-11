package com.ibkrtax.mobile.state

import com.ibkrtax.mobile.keys.BackupKeyStatus
import com.ibkrtax.mobile.prices.PriceSource
import com.ibkrtax.mobile.prices.RefreshOutcome
import com.ibkrtax.mobile.prices.RefreshProgress
import com.ibkrtax.mobile.storage.Operation
import com.ibkrtax.mobile.storage.OperationFilter
import com.ibkrtax.mobile.ui.skins.SkinCatalog
import com.ibkrtax.mobile.ui.skins.SkinId
import java.math.BigDecimal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StateHoldersTest {
    private fun op(id: Long) = Operation(id, "2024-01-01", "BUY", "T$id", BigDecimal.ONE, BigDecimal.TEN, "USD", BigDecimal.TEN, BigDecimal.ZERO)

    // --- skins ---

    @Test
    fun missingOrUnknownSkinNamesFallBackToClassic() {
        assertEquals(SkinId.CLASSIC, SkinCatalog.resolve(null).id)
        assertEquals(SkinId.CLASSIC, SkinCatalog.resolve("NEON").id)
        assertEquals(SkinId.CLASSIC, SkinCatalog.resolve("CLASSIC").id)
    }

    // --- portfolio ---

    @Test
    fun portfolioShowsCachedDataThenRefreshedDataWithProgress() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val loads = mutableListOf<RefreshOutcome?>()
        val market = CompletableDeferred<RefreshOutcome>()
        val progressSeen = mutableListOf<RefreshProgress?>()
        val state = PortfolioState(
            scope = TestScope(dispatcher),
            load = { prices -> loads += prices; loaded(prices) },
            refreshMarket = { onProgress -> onProgress(RefreshProgress(1, 2, PriceSource.YAHOO)); market.await() },
            io = dispatcher,
        )

        state.refresh()
        advanceUntilIdle()
        assertNull((state.ui.value as PortfolioUiState.Loaded).prices) // cached data first
        progressSeen += state.progress.value
        market.complete(RefreshOutcome.Updated)
        advanceUntilIdle()

        assertEquals(RefreshOutcome.Updated, (state.ui.value as PortfolioUiState.Loaded).prices)
        assertEquals(listOf(null, RefreshOutcome.Updated), loads)
        assertEquals(RefreshProgress(1, 2, PriceSource.YAHOO), progressSeen.single())
        assertNull(state.progress.value)
    }

    @Test
    fun portfolioWithoutReportsSkipsTheNetwork() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var marketCalls = 0
        val state = PortfolioState(TestScope(dispatcher), { PortfolioUiState.NoReports }, { marketCalls++; RefreshOutcome.Updated }, dispatcher)

        state.refresh()
        advanceUntilIdle()

        assertEquals(PortfolioUiState.NoReports, state.ui.value)
        assertEquals(0, marketCalls)
    }

    private fun loaded(prices: RefreshOutcome?) = PortfolioUiState.Loaded(
        rows = emptyList(),
        totals = com.ibkrtax.mobile.portfolio.MainPage.usdTotals(emptyList(), null),
        performance = emptyList(),
        summary = com.ibkrtax.mobile.portfolio.Performance.summary(emptyList(), emptyList(), null, java.time.LocalDate.of(2026, 1, 1)),
        rates = null,
        hasEuropean = false,
        prices = prices,
        priceSource = PriceSource.YAHOO,
    )

    // --- activity ---

    @Test
    fun activityPagesSearchesAndDropsStalePages() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val calls = mutableListOf<Triple<Set<OperationFilter>, String, Int>>()
        val state = ActivityState(
            scope = TestScope(dispatcher),
            page = { filters, query, limit, offset ->
                calls += Triple(filters, query, offset)
                if (offset >= 150) emptyList() else List(minOf(limit, 150 - offset)) { op((offset + it).toLong()) }
            },
            hasAny = { true },
            io = dispatcher,
        )

        state.reload()
        advanceUntilIdle()
        assertEquals(100, state.view.value.items.size)
        assertEquals(true, state.view.value.hasAny)
        assertFalse(state.view.value.endReached)

        state.loadMore()
        advanceUntilIdle()
        assertEquals(150, state.view.value.items.size)
        assertTrue(state.view.value.endReached)

        // Typing is debounced: only the last query is requested.
        state.setQuery("a")
        state.setQuery("aa")
        advanceTimeBy(100)
        state.setQuery("aap")
        advanceUntilIdle()
        assertEquals(listOf("aap"), calls.drop(2).map { it.second })

        state.toggle(OperationFilter.DIVIDENDS)
        advanceUntilIdle()
        assertEquals(setOf(OperationFilter.DIVIDENDS), calls.last().first)
        state.clear()
        advanceUntilIdle()
        assertEquals("" to emptySet<OperationFilter>(), state.view.value.query to state.view.value.filters)
    }

    @Test
    fun activityRechecksForDataOnReload() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var stored = true
        val state = ActivityState(TestScope(dispatcher), { _, _, _, _ -> if (stored) listOf(op(1)) else emptyList() }, { stored }, dispatcher)
        state.reload()
        advanceUntilIdle()
        assertEquals(true, state.view.value.hasAny)

        stored = false // everything deleted elsewhere
        state.reload()
        advanceUntilIdle()
        assertEquals(false, state.view.value.hasAny)
    }

    // --- income and data ---

    @Test
    fun incomeAndDataLoadOnReload() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val income = IncomeState(TestScope(dispatcher), { DividendData(emptyList(), null) }, dispatcher)
        val data = DataState(TestScope(dispatcher), { BackupKeyStatus.NotSetUp }, { true }, { emptyList() }, dispatcher)
        assertNull(income.data.value)

        income.reload()
        data.reload()
        advanceUntilIdle()

        assertEquals(DividendData(emptyList(), null), income.data.value)
        assertEquals(DataView(BackupKeyStatus.NotSetUp, importReady = true, history = emptyList()), data.view.value)
    }
}
