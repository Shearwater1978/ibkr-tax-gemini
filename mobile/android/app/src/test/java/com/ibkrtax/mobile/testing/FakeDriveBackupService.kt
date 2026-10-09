package com.ibkrtax.mobile.testing

import com.ibkrtax.mobile.backup.DriveBackupService
import com.ibkrtax.mobile.backup.DriveFailure
import com.ibkrtax.mobile.backup.DriveResult
import com.ibkrtax.mobile.backup.RemoteFile
import java.security.MessageDigest

/** In-memory Drive app data folder; no OAuth client or network involved. */
class FakeDriveBackupService : DriveBackupService {
    /** When set, every call fails with this reason (offline, auth, quota). */
    var failure: DriveFailure? = null

    /** Simulates a server-side corruption so verification must fail. */
    var corruptUploads: Boolean = false

    private val files = linkedMapOf<String, Pair<String, ByteArray>>()
    private var nextId = 1

    val storedFileCount: Int get() = files.size

    fun storedBytes(fileId: String): ByteArray? = files[fileId]?.second?.copyOf()

    override suspend fun upload(name: String, encrypted: ByteArray): DriveResult<RemoteFile> {
        failure?.let { return DriveResult.Failure(it) }
        val stored = if (corruptUploads) encrypted.copyOf(encrypted.size / 2) else encrypted.copyOf()
        val id = "fake-${nextId++}"
        files[id] = name to stored
        return DriveResult.Success(remoteFile(id, name, stored))
    }

    override suspend fun metadata(fileId: String): DriveResult<RemoteFile> {
        failure?.let { return DriveResult.Failure(it) }
        val (name, bytes) = files[fileId] ?: return DriveResult.Failure(DriveFailure.NOT_FOUND)
        return DriveResult.Success(remoteFile(fileId, name, bytes))
    }

    override suspend fun delete(fileId: String): DriveResult<Unit> {
        failure?.let { return DriveResult.Failure(it) }
        return if (files.remove(fileId) != null) {
            DriveResult.Success(Unit)
        } else {
            DriveResult.Failure(DriveFailure.NOT_FOUND)
        }
    }

    private fun remoteFile(id: String, name: String, bytes: ByteArray) =
        RemoteFile(id = id, name = name, sizeBytes = bytes.size.toLong(), sha256Hex = sha256Hex(bytes))

    companion object {
        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
