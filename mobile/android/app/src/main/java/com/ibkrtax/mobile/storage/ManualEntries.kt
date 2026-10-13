package com.ibkrtax.mobile.storage

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.ibkrtax.mobile.importer.StoredTransaction
import com.ibkrtax.mobile.manual.JournalState
import com.ibkrtax.mobile.manual.JournalStore
import com.ibkrtax.mobile.manual.ManualStore
import com.ibkrtax.mobile.manual.StoredRow

/**
 * One operation entered by hand (mobile-manual-operations): the form as entered and the `transactions`
 * rows built from it. [fields] is the form as a JSON object of decimal strings, like the journal's `fields`.
 */
data class ManualEntry(
    val entryId: String,
    val type: String,
    val fields: String,
    val enteredAt: String,
    val rows: List<StoredTransaction>,
)

/** Stores manual entries next to imported operations; imported rows are never changed here. */
class ManualEntries(private val database: SupportSQLiteOpenHelper) : ManualStore, JournalStore {
    /** Adds the entry, or replaces it and all its rows when [ManualEntry.entryId] exists. */
    override fun save(entry: ManualEntry) {
        require(entry.rows.isNotEmpty()) { "A manual entry needs at least one row" }
        require(entry.rows.all { it.sourceKey.startsWith(SOURCE_KEY_PREFIX + entry.entryId) }) { "Manual source keys must be manual:<entryId>…" }
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            // Deleting the entry removes its old rows through ON DELETE CASCADE.
            db.execSQL("DELETE FROM manual_entries WHERE entry_id = ?", arrayOf(entry.entryId))
            db.insert(
                "manual_entries",
                SQLiteDatabase.CONFLICT_ABORT,
                ContentValues().apply {
                    put("entry_id", entry.entryId)
                    put("type", entry.type)
                    put("fields", entry.fields)
                    put("entered_at", entry.enteredAt)
                },
            )
            entry.rows.forEach { db.insert("transactions", SQLiteDatabase.CONFLICT_ABORT, it.toManualValues(entry.entryId)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** False when no such entry exists. */
    override fun delete(entryId: String): Boolean {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            val exists = db.query("SELECT 1 FROM manual_entries WHERE entry_id = ?", arrayOf(entryId)).use { it.moveToFirst() }
            db.execSQL("DELETE FROM manual_entries WHERE entry_id = ?", arrayOf(entryId))
            db.setTransactionSuccessful()
            return exists
        } finally {
            db.endTransaction()
        }
    }

    override fun get(entryId: String): ManualEntry? = load("WHERE entry_id = ?", arrayOf<Any?>(entryId)).firstOrNull()

    override fun allRows(): List<StoredRow> =
        database.readableDatabase.query("SELECT $ROW_COLUMNS FROM transactions ORDER BY date, id").use { cursor ->
            buildList { while (cursor.moveToNext()) add(StoredRow(if (cursor.isNull(0)) null else cursor.getString(0), cursor.toTransaction())) }
        }

    /** Every manual entry, oldest entered first. */
    fun all(): List<ManualEntry> = load("", emptyArray())

    override fun entries(): List<ManualEntry> = all()

    override fun journalState(): JournalState {
        val values = database.readableDatabase.query("SELECT key, value FROM app_settings WHERE key LIKE 'manual_journal_%'").use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
        }
        return JournalState(
            sequence = values[JOURNAL_SEQUENCE]?.toLongOrNull() ?: 0,
            file = values[JOURNAL_FILE],
            pending = values[JOURNAL_PENDING],
            stale = values[JOURNAL_STALE] == "true",
        )
    }

    override fun setJournalState(state: JournalState) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM app_settings WHERE key LIKE 'manual_journal_%'")
            mapOf(JOURNAL_SEQUENCE to state.sequence.toString(), JOURNAL_FILE to state.file, JOURNAL_PENDING to state.pending, JOURNAL_STALE to state.stale.toString())
                .forEach { (key, value) ->
                    if (value != null) {
                        db.insert(
                            "app_settings",
                            SQLiteDatabase.CONFLICT_REPLACE,
                            ContentValues().apply {
                                put("key", key)
                                put("value", value)
                            },
                        )
                    }
                }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun restoreEntries(entries: List<ManualEntry>) = entries.forEach(::save)

    private fun load(where: String, args: Array<Any?>): List<ManualEntry> {
        val db = database.readableDatabase
        val rows = db.query("SELECT $ROW_COLUMNS FROM transactions WHERE origin = 'manual' ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.toTransaction()) }
        }.groupBy({ it.first }, { it.second })
        return db.query("SELECT entry_id, type, fields, entered_at FROM manual_entries $where ORDER BY entered_at, entry_id", args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    add(ManualEntry(id, cursor.getString(1), cursor.getString(2), cursor.getString(3), rows[id].orEmpty()))
                }
            }
        }
    }

    private fun StoredTransaction.toManualValues(entryId: String) = ContentValues().apply {
        put("origin", ORIGIN_MANUAL)
        putNull("report_id")
        put("entry_id", entryId)
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
        put("listing_exchange", listingExchange)
    }

    private fun Cursor.toTransaction() = StoredTransaction(
        sourceKey = getString(1),
        date = getString(2),
        eventType = getString(3),
        ticker = getString(4),
        quantity = getString(5),
        price = getString(6),
        currency = getString(7),
        amount = getString(8),
        fee = getString(9),
        description = getString(10),
        splitRatio = if (isNull(11)) null else getString(11),
        isin = getString(12),
        conid = getString(13),
        instrumentDescription = getString(14),
        listingExchange = getString(15),
    )

    companion object {
        const val ORIGIN_MANUAL = "manual"
        const val SOURCE_KEY_PREFIX = "manual:"
        private const val JOURNAL_SEQUENCE = "manual_journal_sequence"
        private const val JOURNAL_FILE = "manual_journal_file"
        private const val JOURNAL_PENDING = "manual_journal_pending"
        private const val JOURNAL_STALE = "manual_journal_stale"
        private const val ROW_COLUMNS = "entry_id, source_key, date, event_type, ticker, quantity, price, currency, amount, fee, " +
            "description, split_ratio, isin, conid, instrument_description, listing_exchange"
    }
}
