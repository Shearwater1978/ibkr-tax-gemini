package com.ibkrtax.mobile.storage

import android.content.Context
import android.database.SQLException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ibkrtax.mobile.importer.StoredTransaction
import com.ibkrtax.mobile.security.DeviceKeys
import com.ibkrtax.mobile.security.Pseudonymizer
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Schema version 7 and manual entries (mobile-manual-operations tasks 2.1–2.4) against the real SQLCipher database. */
@RunWith(AndroidJUnit4::class)
class ManualStorageInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "manual-test.db"
    private val hmacAlias = "test.manual.hmac"
    private val key = ByteArray(32) { 7 }
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        EncryptedDatabase.delete(context, dbName)
        DeviceKeys.delete(hmacAlias)
        helper = EncryptedDatabase.openWithKey(context, dbName, key)
    }

    @After
    fun tearDown() {
        helper.close()
        EncryptedDatabase.delete(context, dbName)
        DeviceKeys.delete(hmacAlias)
    }

    private val importer get() = ReportImporter(helper, Pseudonymizer { DeviceKeys.hmacKey(hmacAlias) })
    private val manual get() = ManualEntries(helper)

    private fun fixture(name: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open("flex-query/$name").use { it.readBytes() }

    private fun count(where: String = ""): Int =
        helper.readableDatabase.query("SELECT count(*) FROM transactions $where").use { it.moveToFirst(); it.getInt(0) }

    private fun buy(entryId: String, quantity: String = "3", suffix: String = "") = StoredTransaction(
        sourceKey = "manual:$entryId$suffix", date = "2024-02-01", eventType = "BUY", ticker = "AAPL", quantity = quantity,
        price = "180.00", currency = "USD", amount = "540.00", fee = "-1.00", description = "Manual", splitRatio = null,
        isin = "", conid = "", instrumentDescription = "",
    )

    private fun entry(entryId: String, vararg rows: StoredTransaction) =
        ManualEntry(entryId, "BUY", """{"quantity":"3"}""", "2026-10-13T08:00:00Z", rows.toList())

    @Test
    fun versionSixDatabaseMigratesWithEveryRowKept() {
        helper.close()
        EncryptedDatabase.delete(context, dbName)
        val v6 = SupportOpenHelperFactory(key).create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(dbName).callback(VersionSix).build(),
        )
        v6.writableDatabase.apply {
            execSQL("INSERT INTO reports (id, fingerprint, imported_at, inserted_count, skipped_count) VALUES (1, 'fp', '2026-01-01T00:00:00Z', 2, 0)")
            execSQL(
                "INSERT INTO transactions (id, report_id, source_key, date, event_type, ticker, quantity, price, currency, amount, fee, " +
                    "description, split_ratio, isin, conid, instrument_description, listing_exchange) VALUES " +
                    "(5, 1, 'k1', '2023-01-10', 'BUY', 'AAPL', '10', '150.00', 'USD', '1500.00', '-1.00', 'IBKR Trade', NULL, 'US0378331005', '900001', 'APPLE INC', 'NASDAQ'), " +
                    "(9, 1, 'k2', '2023-07-03', 'SPLIT', 'SAP', '4', '0', 'EUR', '0', '0', 'SAP split', '2', 'DE0007164600', '', '', 'IBIS')",
            )
            execSQL("INSERT INTO report_records (report_id, source_key) VALUES (1, 'k1'), (1, 'k2')")
        }
        v6.close()

        helper = EncryptedDatabase.openWithKey(context, dbName, key)
        val rows = helper.readableDatabase.query(
            "SELECT id, origin, report_id, entry_id, source_key, split_ratio, listing_exchange FROM transactions ORDER BY id",
        ).use { c -> buildList { while (c.moveToNext()) add(listOf(c.getLong(0), c.getString(1), c.getLong(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6))) } }
        assertEquals(
            listOf(
                listOf(5L, "report", 1L, null, "k1", null, "NASDAQ"),
                listOf(9L, "report", 1L, null, "k2", "2", "IBIS"),
            ),
            rows,
        )
        // New rows continue after the copied ids.
        manual.save(entry("e1", buy("e1")))
        val id = helper.readableDatabase.query("SELECT id FROM transactions WHERE entry_id = 'e1'").use { it.moveToFirst(); it.getLong(0) }
        assertTrue(id > 9)
    }

    @Test
    fun manualEntriesAreSavedReplacedAndDeletedWithTheirRows() {
        manual.save(entry("e1", buy("e1")))
        assertEquals(listOf(buy("e1")), manual.get("e1")!!.rows)
        assertEquals(1, count("WHERE origin = 'manual' AND report_id IS NULL"))

        manual.save(entry("e1", buy("e1", quantity = "5")))
        assertEquals(listOf(buy("e1", quantity = "5")), manual.get("e1")!!.rows)
        assertEquals(1, count())

        assertTrue(manual.delete("e1"))
        assertFalse(manual.delete("e1"))
        assertNull(manual.get("e1"))
        assertEquals(0, count())
    }

    @Test
    fun rowsMustBelongToTheirEntryAndBeConsistent() {
        try {
            manual.save(entry("e1", buy("other")))
            fail("rows of another entry were accepted")
        } catch (expected: IllegalArgumentException) {
        }
        try {
            helper.writableDatabase.execSQL(
                "INSERT INTO transactions (origin, source_key, date, event_type, ticker, quantity, price, currency, amount, fee, description) " +
                    "VALUES ('report', 'x', '2024-01-01', 'BUY', 'AAPL', '1', '1', 'USD', '1', '0', '')",
            )
            fail("a report row without a report was accepted")
        } catch (expected: SQLException) {
        }
        assertEquals(0, count())
    }

    @Test
    fun deletingImportsKeepsManualOperations() {
        val imported = importer.import(fixture("valid_basic.csv")) as ImportResult.Imported
        manual.save(entry("e1", buy("e1")))
        val reportRows = count("WHERE origin = 'report'")

        assertEquals(reportRows, importer.deleteReport(imported.reportId)!!.removedRecords)
        assertEquals(1, count())

        importer.import(fixture("valid_basic.csv"))
        assertEquals(reportRows, importer.deleteAll().removedRecords)
        assertEquals(listOf("manual:e1"), manual.all().flatMap { e -> e.rows.map { it.sourceKey } })
    }

    @Test
    fun allRowsMarkManualRowsWithTheirEntry() {
        importer.import(fixture("valid_basic.csv"))
        manual.save(entry("e1", buy("e1")))

        val rows = manual.allRows()
        assertEquals(count(), rows.size)
        assertEquals(listOf("manual:e1"), rows.filter { it.entryId == "e1" }.map { it.row.sourceKey })
        assertEquals(count("WHERE origin = 'report'"), rows.count { it.entryId == null })
        assertEquals(rows.map { it.row.date }.sorted(), rows.map { it.row.date })
    }

    @Test
    fun aDividendAndItsTaxAreOneEntry() {
        val dividend = buy("d1").copy(eventType = "DIVIDEND", quantity = "0", price = "0", amount = "2.40", fee = "0")
        val tax = buy("d1", suffix = ":tax").copy(eventType = "TAX", quantity = "0", price = "0", amount = "-0.36", fee = "0")
        manual.save(entry("d1", dividend, tax))

        assertEquals(listOf(dividend, tax), manual.get("d1")!!.rows)
        manual.delete("d1")
        assertEquals(0, count())
    }

    /** The version 6 tables this migration touches, exactly as version 6 created them. */
    private object VersionSix : SupportSQLiteOpenHelper.Callback(6) {
        override fun onConfigure(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys = ON")
        }

        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE reports (id INTEGER PRIMARY KEY AUTOINCREMENT, fingerprint TEXT NOT NULL UNIQUE, account_pseudonym TEXT, " +
                    "account_masked TEXT, imported_at TEXT NOT NULL, inserted_count INTEGER NOT NULL, skipped_count INTEGER NOT NULL, " +
                    "backup_file TEXT, backup_status TEXT)",
            )
            db.execSQL(
                "CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, report_id INTEGER NOT NULL REFERENCES reports(id), " +
                    "source_key TEXT NOT NULL UNIQUE, date TEXT NOT NULL, event_type TEXT NOT NULL, ticker TEXT NOT NULL, quantity TEXT NOT NULL, " +
                    "price TEXT NOT NULL, currency TEXT NOT NULL, amount TEXT NOT NULL, fee TEXT NOT NULL, description TEXT NOT NULL, " +
                    "split_ratio TEXT, isin TEXT NOT NULL DEFAULT '', conid TEXT NOT NULL DEFAULT '', " +
                    "instrument_description TEXT NOT NULL DEFAULT '', listing_exchange TEXT NOT NULL DEFAULT '')",
            )
            db.execSQL("CREATE INDEX transactions_ticker_date ON transactions (ticker, date)")
            db.execSQL(
                "CREATE TABLE report_records (report_id INTEGER NOT NULL REFERENCES reports(id), source_key TEXT NOT NULL, " +
                    "PRIMARY KEY (report_id, source_key))",
            )
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
