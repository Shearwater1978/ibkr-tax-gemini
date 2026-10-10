package com.ibkrtax.mobile.storage

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

/** The user-chosen backup folder (a Storage Access Framework tree URI). */
class BackupLocationStore(private val database: SupportSQLiteOpenHelper) {
    fun get(): String? =
        database.readableDatabase.query("SELECT value FROM app_settings WHERE key = ?", arrayOf(KEY)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    fun set(treeUri: String) {
        database.writableDatabase.insert(
            "app_settings",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("key", KEY)
                put("value", treeUri)
            },
        )
    }

    private companion object {
        const val KEY = "backup_folder_uri"
    }
}
