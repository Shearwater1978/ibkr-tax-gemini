package com.ibkrtax.mobile.security

import com.ibkrtax.mobile.backup.FolderErasure

/** What erasure could do; [failedSteps] lists steps that threw (their data may remain). */
data class ErasureResult(val folder: FolderErasure?, val failedSteps: List<String>) {
    val complete: Boolean get() = failedSteps.isEmpty() && folder?.folderReachable != false && folder?.left == 0
}

/**
 * Erase all data (data-anonymization "Erasure and minimization", key-management "Erasure", task
 * 7.6). The order matters: backup files first, while the folder is known; then the backup keys
 * (local and Block Store), which makes any copy left in the provider's trash unreadable; then the
 * database, settings and files on the device; device keys last. Every step runs even if an
 * earlier one fails.
 */
class DataEraser(
    private val eraseFolder: () -> FolderErasure,
    private val eraseBackupKeys: suspend () -> Unit,
    private val deleteLocalData: () -> Unit,
    private val deleteDeviceKeys: () -> Unit,
) {
    suspend fun eraseAll(): ErasureResult {
        val failed = mutableListOf<String>()
        val folder = runCatching(eraseFolder).onFailure { failed += "backup folder" }.getOrNull()
        runCatching { eraseBackupKeys() }.onFailure { failed += "backup keys" }
        runCatching(deleteLocalData).onFailure { failed += "local data" }
        runCatching(deleteDeviceKeys).onFailure { failed += "device keys" }
        return ErasureResult(folder, failed)
    }
}
