package com.ibkrtax.mobile.backup

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Automatic retries for pending backups with exponential backoff (encrypted-backup "Write
 * failure"): 30 s, then doubling up to 15 minutes, while the app runs. Stops when nothing is
 * pending or the folder access is lost (the user has to choose the folder again).
 */
class BackupRetry(
    private val scope: CoroutineScope,
    /** Backups waiting for upload; 0 when backups are not set up. Blocking. */
    private val pending: () -> Int,
    /** One retry round over all pending backups. Blocking. */
    private val retry: () -> RetryResult,
    /** Called after a round saved at least one backup or lost access, so screens can reload. */
    private val onChange: () -> Unit = {},
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val firstDelayMillis: Long = 30_000,
    private val maxDelayMillis: Long = 15 * 60_000,
) {
    private var job: Job? = null

    /** Starts retrying if backups are pending and no retry loop is running. */
    fun kick() {
        if (job?.isActive == true) return
        job = scope.launch {
            var wait = firstDelayMillis
            while (withContext(io) { pending() } > 0) {
                delay(wait)
                val result = withContext(io) { runCatching(retry).getOrNull() }
                if (result != null && (result.backedUp > 0 || result.accessLost)) onChange()
                if (result != null && (result.accessLost || result.stillPending == 0)) break
                wait = (wait * 2).coerceAtMost(maxDelayMillis)
            }
        }
    }
}
