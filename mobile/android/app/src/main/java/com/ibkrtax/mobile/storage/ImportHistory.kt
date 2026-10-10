package com.ibkrtax.mobile.storage

import androidx.sqlite.db.SupportSQLiteOpenHelper

/** An imported report as shown to the user: no file name, only a masked account. */
data class ImportedReport(
    val id: Long,
    val importedAt: String,
    val accountMasked: String?,
    val inserted: Int,
    val skipped: Int,
    /** "pending", "backed_up", or null for reports imported without backup (debug samples). */
    val backupStatus: String? = null,
)

class ImportHistory(private val database: SupportSQLiteOpenHelper) {
    fun list(): List<ImportedReport> =
        database.readableDatabase.query(
            "SELECT id, imported_at, account_masked, inserted_count, skipped_count, backup_status FROM reports ORDER BY id DESC",
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
                            backupStatus = if (cursor.isNull(5)) null else cursor.getString(5),
                        ),
                    )
                }
            }
        }

    companion object {
        const val PENDING = ReportImporter.BACKUP_PENDING
        const val BACKED_UP = ReportImporter.BACKUP_DONE
    }
}
