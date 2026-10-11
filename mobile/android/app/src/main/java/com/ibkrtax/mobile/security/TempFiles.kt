package com.ibkrtax.mobile.security

import java.io.File

/**
 * No plaintext caches (local-storage spec, task 5.4). The app itself writes nothing to its cache
 * folder; whatever a library leaves there is removed when the app starts and when it goes to the
 * background. Encrypted data (database, wrapped keys, pending backups) lives elsewhere and stays.
 */
object TempFiles {
    /** Deletes everything inside [cacheDir], keeping the folder itself. */
    fun clear(cacheDir: File) {
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }
}
