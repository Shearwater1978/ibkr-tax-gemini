package com.ibkrtax.mobile.backup

/**
 * Google Drive app data folder access. Callers pass only client-side encrypted
 * bytes (mobile-encrypted-drive-backup); this layer never sees plaintext reports.
 */
interface DriveBackupService {
    suspend fun upload(name: String, encrypted: ByteArray): DriveResult<RemoteFile>

    suspend fun metadata(fileId: String): DriveResult<RemoteFile>

    suspend fun delete(fileId: String): DriveResult<Unit>
}

/** Size and checksum let the caller verify an upload before removing the local copy. */
data class RemoteFile(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val sha256Hex: String,
)

sealed interface DriveResult<out T> {
    data class Success<T>(val value: T) : DriveResult<T>

    data class Failure(val reason: DriveFailure) : DriveResult<Nothing>
}

enum class DriveFailure {
    OFFLINE,
    UNAUTHORIZED,
    QUOTA_EXCEEDED,
    NOT_FOUND,
}
