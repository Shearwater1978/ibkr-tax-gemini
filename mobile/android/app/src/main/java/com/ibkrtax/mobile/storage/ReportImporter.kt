package com.ibkrtax.mobile.storage

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.ibkrtax.mobile.importer.FlexImportError
import com.ibkrtax.mobile.importer.FlexParseResult
import com.ibkrtax.mobile.importer.FlexQueryParser
import com.ibkrtax.mobile.importer.ImportPlan
import com.ibkrtax.mobile.importer.ImportPlanResult
import com.ibkrtax.mobile.importer.InvalidRecordReason
import com.ibkrtax.mobile.importer.StoredTransaction
import com.ibkrtax.mobile.security.IdentifierKind
import com.ibkrtax.mobile.security.Masking
import com.ibkrtax.mobile.security.Pseudonymizer
import java.time.Clock

sealed interface ImportResult {
    data class Imported(val reportId: Long, val inserted: Int, val skipped: Int) : ImportResult

    /** The same file was imported before; nothing changed. */
    data object AlreadyImported : ImportResult

    data class Rejected(val reason: ImportRejection) : ImportResult
}

sealed interface ImportRejection {
    data class Unparseable(val error: FlexImportError) : ImportRejection

    data class InvalidRecord(val reason: InvalidRecordReason) : ImportRejection
}

/**
 * Imports one report atomically (mobile-report-upload spec): every derived record and
 * the report entry commit together, or nothing does. Re-importing the same bytes is
 * detected by a keyed fingerprint; overlapping reports skip records whose source key
 * already exists, like the desktop `save_to_database`.
 */
class ReportImporter(
    private val database: SupportSQLiteOpenHelper,
    private val pseudonymizer: Pseudonymizer,
    private val clock: Clock = Clock.systemUTC(),
    /** Test hook that runs just before commit; throwing here must roll everything back. */
    private val beforeCommit: () -> Unit = {},
) {
    fun import(bytes: ByteArray): ImportResult {
        // The file name may contain the account number, so it is neither parsed into records nor stored.
        val report = when (val parsed = FlexQueryParser.parse(bytes, sourceFile = "")) {
            is FlexParseResult.Failure -> return ImportResult.Rejected(ImportRejection.Unparseable(parsed.error))
            is FlexParseResult.Success -> parsed.report
        }
        val plan = when (val built = ImportPlan.build(report)) {
            is ImportPlanResult.Invalid -> return ImportResult.Rejected(ImportRejection.InvalidRecord(built.reason))
            is ImportPlanResult.Ready -> built
        }
        val fingerprint = pseudonymizer.pseudonym(IdentifierKind.REPORT_CONTENT, ImportPlan.sha256Hex(bytes))
        val accountPseudonym = report.accountId?.let { pseudonymizer.pseudonym(IdentifierKind.ACCOUNT, it) }

        val db = database.writableDatabase
        db.beginTransaction()
        try {
            if (reportExists(db, fingerprint)) return ImportResult.AlreadyImported

            val reportId = db.insert(
                "reports",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("fingerprint", fingerprint)
                    put("account_pseudonym", accountPseudonym)
                    put("account_masked", report.accountId?.let(Masking::lastFour))
                    put("imported_at", clock.instant().toString())
                    put("inserted_count", 0)
                    put("skipped_count", 0)
                },
            )

            var inserted = 0
            for (transaction in plan.transactions) {
                val rowId = db.insert("transactions", SQLiteDatabase.CONFLICT_IGNORE, transaction.toValues(reportId))
                if (rowId == -1L) fillMissingIdentity(db, transaction) else inserted++
            }
            val skipped = plan.duplicatesInReport + plan.transactions.size - inserted

            db.update(
                "reports",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("inserted_count", inserted)
                    put("skipped_count", skipped)
                },
                "id = ?",
                arrayOf(reportId),
            )
            beforeCommit()
            db.setTransactionSuccessful()
            return ImportResult.Imported(reportId, inserted, skipped)
        } finally {
            db.endTransaction()
        }
    }

    private fun reportExists(db: SupportSQLiteDatabase, fingerprint: String): Boolean =
        db.query("SELECT 1 FROM reports WHERE fingerprint = ?", arrayOf(fingerprint)).use { it.moveToFirst() }

    /** Same as the desktop import: an existing record gains identity fields it lacked. */
    private fun fillMissingIdentity(db: SupportSQLiteDatabase, t: StoredTransaction) {
        db.execSQL(
            "UPDATE transactions SET " +
                "isin = CASE WHEN ? <> '' THEN ? ELSE isin END, " +
                "conid = CASE WHEN ? <> '' THEN ? ELSE conid END, " +
                "instrument_description = CASE WHEN ? <> '' THEN ? ELSE instrument_description END " +
                "WHERE source_key = ?",
            arrayOf(t.isin, t.isin, t.conid, t.conid, t.instrumentDescription, t.instrumentDescription, t.sourceKey),
        )
    }

    private fun StoredTransaction.toValues(reportId: Long) = ContentValues().apply {
        put("report_id", reportId)
        put("source_key", sourceKey)
        put("date", date)
        put("event_type", eventType)
        put("ticker", ticker)
        put("quantity", quantity)
        put("price", price)
        put("currency", currency)
        put("amount", amount)
        put("fee", fee)
        put("description", description)
        put("split_ratio", splitRatio)
        put("isin", isin)
        put("conid", conid)
        put("instrument_description", instrumentDescription)
    }
}
