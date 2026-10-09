package com.ibkrtax.mobile.storage

import androidx.sqlite.db.SupportSQLiteOpenHelper

/** An imported report as shown to the user: no file name, only a masked account. */
data class ImportedReport(
    val id: Long,
    val importedAt: String,
    val accountMasked: String?,
    val inserted: Int,
    val skipped: Int,
)

class ImportHistory(private val database: SupportSQLiteOpenHelper) {
    fun list(): List<ImportedReport> =
        database.readableDatabase.query(
            "SELECT id, imported_at, account_masked, inserted_count, skipped_count FROM reports ORDER BY id DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ImportedReport(
                            id = cursor.getLong(0),
                            importedAt = cursor.getString(1),
                            accountMasked = if (cursor.isNull(2)) null else cursor.getString(2),
                            inserted = cursor.getInt(3),
                            skipped = cursor.getInt(4),
                        ),
                    )
                }
            }
        }
}
