package com.ibkrtax.mobile.state

import com.ibkrtax.mobile.AppContainer
import com.ibkrtax.mobile.keys.BackupKeyStatus
import com.ibkrtax.mobile.storage.ImportedReport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the import and settings parts of every skin show about stored data, keys and backups. */
data class DataView(
    val keyStatus: BackupKeyStatus? = null,
    /** Backup encryption and a backup folder are set up, so import is allowed. */
    val importReady: Boolean = false,
    /** Null until loaded or when storage is unavailable. */
    val history: List<ImportedReport>? = null,
)

/** Imports, keys and backup readiness shared by all skins (mobile-skins spec). */
class DataState(
    private val scope: CoroutineScope,
    private val keyStatus: suspend () -> BackupKeyStatus?,
    private val importReady: () -> Boolean,
    private val history: () -> List<ImportedReport>?,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _view = MutableStateFlow(DataView())
    val view: StateFlow<DataView> = _view.asStateFlow()

    /** Reloads everything; call after an import, a deletion or a change of keys or folder. */
    fun reload() {
        scope.launch {
            val status = keyStatus()
            val (ready, reports) = withContext(io) { importReady() to history() }
            _view.value = DataView(status, ready, reports)
        }
    }

    companion object {
        fun create(container: AppContainer, scope: CoroutineScope) = DataState(
            scope,
            keyStatus = { runCatching { container.backupKeys.status() }.getOrNull() },
            importReady = { runCatching { container.backup.isReady }.getOrDefault(false) },
            history = { runCatching { container.importHistory.list() }.getOrNull() },
        )
    }
}
