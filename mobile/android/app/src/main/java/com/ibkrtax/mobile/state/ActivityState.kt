package com.ibkrtax.mobile.state

import com.ibkrtax.mobile.AppContainer
import com.ibkrtax.mobile.storage.Operation
import com.ibkrtax.mobile.storage.OperationFilter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The activity list as every skin shows it: newest first, searched and filtered (mobile-operations spec). */
data class ActivityView(
    val query: String = "",
    val filters: Set<OperationFilter> = emptySet(),
    val items: List<Operation> = emptyList(),
    val endReached: Boolean = false,
    /** Null until known; false shows the empty state. */
    val hasAny: Boolean? = null,
)

/** Paged operations shared by all skins; search typing is debounced and stale pages are dropped. */
class ActivityState(
    private val scope: CoroutineScope,
    private val page: (Set<OperationFilter>, String, Int, Int) -> List<Operation>,
    private val hasAny: () -> Boolean,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val debounceMillis: Long = 250,
) {
    private val _view = MutableStateFlow(ActivityView())
    val view: StateFlow<ActivityView> = _view.asStateFlow()
    private var first: Job? = null
    private var more: Job? = null

    /** Reloads the first page, e.g. when the activity screen opens or data changed. */
    fun reload() = restart(debounce = false, recheck = true)

    fun setQuery(query: String) {
        _view.update { it.copy(query = query) }
        restart(debounce = true)
    }

    fun toggle(filter: OperationFilter) {
        _view.update { it.copy(filters = if (filter in it.filters) it.filters - filter else it.filters + filter) }
        restart(debounce = false)
    }

    fun clear() {
        _view.update { it.copy(query = "", filters = emptySet()) }
        restart(debounce = false)
    }

    /** Appends the next page; ignored while one loads, at the end, or before the first page. */
    fun loadMore() {
        val current = _view.value
        if (more?.isActive == true || current.endReached || current.items.isEmpty()) return
        more = scope.launch {
            val next = withContext(io) { page(current.filters, current.query, PAGE_SIZE, current.items.size) }
            val now = _view.value
            // Drop the page if the search changed while it loaded.
            if (now.query != current.query || now.filters != current.filters || now.items.size != current.items.size) return@launch
            _view.value = now.copy(items = now.items + next, endReached = next.size < PAGE_SIZE)
        }
    }

    /** [recheck] asks again whether any operation exists (data may have been imported or deleted). */
    private fun restart(debounce: Boolean, recheck: Boolean = false) {
        first?.cancel()
        more?.cancel()
        first = scope.launch {
            if (debounce) delay(debounceMillis)
            val wanted = _view.value
            val known = wanted.hasAny.takeUnless { recheck }
            val (any, items) = withContext(io) { (known ?: hasAny()) to page(wanted.filters, wanted.query, PAGE_SIZE, 0) }
            _view.update { it.copy(items = items, endReached = items.size < PAGE_SIZE, hasAny = any) }
        }
    }

    companion object {
        const val PAGE_SIZE = 100

        fun create(container: AppContainer, scope: CoroutineScope) =
            ActivityState(scope, container.operations::page, container.operations::hasAny)
    }
}
