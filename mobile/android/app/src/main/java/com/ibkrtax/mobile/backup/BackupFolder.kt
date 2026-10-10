package com.ibkrtax.mobile.backup

import java.io.IOException

/** The folder's permission was revoked or the folder is gone (backup-location spec "Lost folder access"). */
class FolderAccessLostException(cause: Throwable? = null) : IOException("Backup folder is no longer accessible", cause)

/**
 * The user-chosen backup folder. The app only writes ciphertext and the wrapped-key
 * manifest here; the storage app (Google Drive etc.) does any syncing.
 */
interface BackupFolder {
    /** Creates or replaces [name]. Throws [FolderAccessLostException] or [IOException]. */
    fun write(name: String, bytes: ByteArray)

    /** Null when the file does not exist. */
    fun read(name: String): ByteArray?

    fun list(): List<String>

    fun delete(name: String)
}
