package com.ibkrtax.mobile.storage

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

/** The confirmed folder (a Storage Access Framework tree URI) and, if any, the app's subfolder inside it. */
data class BackupLocation(val treeUri: String, val subfolder: String?)

/** The user-chosen backup location, kept in the encrypted database. */
class BackupLocationStore(private val database: SupportSQLiteOpenHelper) {
    fun get(): BackupLocation? = read(URI_KEY)?.let { BackupLocation(it, read(SUBFOLDER_KEY)?.ifEmpty { null }) }

    fun set(location: BackupLocation) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            write(URI_KEY, location.treeUri)
            // Empty means "use the confirmed folder itself" (it already held a manifest).
            write(SUBFOLDER_KEY, location.subfolder.orEmpty())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun read(key: String): String? =
        database.readableDatabase.query("SELECT value FROM app_settings WHERE key = ?", arrayOf(key)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun write(key: String, value: String) {
        database.writableDatabase.insert(
            "app_settings",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("key", key)
                put("value", value)
            },
        )
    }

    private companion object {
        const val URI_KEY = "backup_folder_uri"
        const val SUBFOLDER_KEY = "backup_subfolder"
    }
}
