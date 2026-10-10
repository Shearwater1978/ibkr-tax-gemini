package com.ibkrtax.mobile.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ibkrtax.mobile.importer.FlexImportError
import com.ibkrtax.mobile.importer.MalformedReason
import com.ibkrtax.mobile.security.DeviceKeys
import com.ibkrtax.mobile.security.Pseudonymizer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Atomic import and duplicate detection against the real SQLCipher database. */
@RunWith(AndroidJUnit4::class)
class ReportImporterInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "importer-test.db"
    private val hmacAlias = "test.importer.hmac"
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        EncryptedDatabase.delete(context, dbName)
        DeviceKeys.delete(hmacAlias)
        helper = EncryptedDatabase.open(context, dbName)
    }

    @After
    fun tearDown() {
        helper.close()
        EncryptedDatabase.delete(context, dbName)
        DeviceKeys.delete(hmacAlias)
    }

    private fun importer(beforeCommit: () -> Unit = {}) =
        ReportImporter(helper, Pseudonymizer { DeviceKeys.hmacKey(hmacAlias) }, beforeCommit = beforeCommit)

    private fun fixture(name: String): ByteArray =
        InstrumentationRegistry.getInstrumentation().context.assets.open("flex-query/$name").use { it.readBytes() }

    private fun count(table: String): Int =
        helper.readableDatabase.query("SELECT count(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun validReportImportsAllRecords() {
        val result = importer().import(fixture("valid_basic.csv")) as ImportResult.Imported

        assertEquals(9, result.inserted)
        assertEquals(0, result.skipped)
        assertEquals(9, count("transactions"))
        assertEquals(1, count("reports"))
    }

    @Test
    fun sameReportAgainIsAlreadyImportedAndChangesNothing() {
        importer().import(fixture("valid_basic.csv"))

        assertEquals(ImportResult.AlreadyImported, importer().import(fixture("valid_basic.csv")))
        assertEquals(9, count("transactions"))
        assertEquals(1, count("reports"))
    }

    @Test
    fun followUpReportAddsOnlyNewRecords() {
        importer().import(fixture("valid_basic.csv"))
        val result = importer().import(fixture("valid_followup.csv")) as ImportResult.Imported

        assertEquals(2, result.inserted)
        assertEquals(11, count("transactions"))
        assertEquals(2, count("reports"))
    }

    @Test
    fun overlappingReportSkipsExistingRecords() {
        importer().import(fixture("valid_basic.csv"))
        // Different bytes (extra blank line) but the same records: a new report with nothing new.
        val overlapping = fixture("valid_basic.csv") + "\n".toByteArray()
        val result = importer().import(overlapping) as ImportResult.Imported

        assertEquals(0, result.inserted)
        assertEquals(9, result.skipped)
        assertEquals(9, count("transactions"))
    }

    @Test
    fun rejectedReportsStoreNothing() {
        importer().import(fixture("valid_basic.csv"))

        assertEquals(
            ImportResult.Rejected(ImportRejection.Unparseable(FlexImportError.UnsupportedFormat)),
            importer().import(fixture("unsupported_format.csv")),
        )
        assertEquals(
            ImportResult.Rejected(ImportRejection.Unparseable(FlexImportError.EmptyFile)),
            importer().import(fixture("empty.csv")),
        )
        assertEquals(
            ImportResult.Rejected(
                ImportRejection.Unparseable(FlexImportError.Malformed(MalformedReason.INVALID_DATE, 5, "Trades")),
            ),
            importer().import(fixture("malformed_trades.csv")),
        )
        assertEquals(9, count("transactions"))
        assertEquals(1, count("reports"))
    }

    @Test
    fun failureBeforeCommitRollsBackEverything() {
        try {
            importer(beforeCommit = { throw IllegalStateException("simulated crash") }).import(fixture("valid_basic.csv"))
            fail("Expected the simulated failure")
        } catch (expected: IllegalStateException) {
            // The transaction must be rolled back.
        }
        assertEquals(0, count("transactions"))
        assertEquals(0, count("reports"))

        // A retry after the failure imports normally.
        assertTrue(importer().import(fixture("valid_basic.csv")) is ImportResult.Imported)
    }

    @Test
    fun holdingsAggregateAcrossImportedReports() {
        importer().import(fixture("valid_basic.csv"))
        importer().import(fixture("valid_followup.csv"))

        val holdings = (PortfolioRepository(helper).holdings() as HoldingsResult.Success).holdings
        // MSFT was fully sold; SAP split 2-for-1 (4 -> 8) then 3 were sold.
        assertEquals(listOf("AAPL" to "USD", "SAP" to "EUR"), holdings.map { it.ticker to it.currency })

        val aapl = holdings.first { it.ticker == "AAPL" }
        assertEquals(0, java.math.BigDecimal("9").compareTo(aapl.quantity))
        // Lots left: 2 @ 150, 5 @ 160, 2 @ 185 -> 1470 / 9
        assertEquals(0, java.math.BigDecimal("163.3333333333333333333333333").compareTo(aapl.averagePrice))

        assertEquals(
            mapOf(("AAPL" to "US0378331005") to "NASDAQ", ("SAP" to "DE0007164600") to "IBIS", ("MSFT" to "US5949181045") to "NASDAQ"),
            PortfolioRepository(helper).listingExchanges(),
        )

        val sap = holdings.first { it.ticker == "SAP" }
        assertEquals(0, java.math.BigDecimal("5").compareTo(sap.quantity))
        assertEquals(0, java.math.BigDecimal("60").compareTo(sap.averagePrice))
    }

    @Test
    fun backupStateIsRecordedWithTheImport() {
        importer().import(fixture("valid_basic.csv"), backupFile = "0123456789abcdef0123456789abcdef.bin")
        importer().import(fixture("valid_followup.csv"))

        assertEquals(listOf("0123456789abcdef0123456789abcdef.bin"), importer().pendingBackups())
        val statuses = ImportHistory(helper).list().map { it.backupStatus }
        assertEquals(listOf(null, ImportHistory.PENDING), statuses)

        importer().markBackedUp("0123456789abcdef0123456789abcdef.bin")
        assertEquals(emptyList<String>(), importer().pendingBackups())
        assertEquals(ImportHistory.BACKED_UP, ImportHistory(helper).list().last().backupStatus)
    }

    @Test
    fun deletingAnImportKeepsRecordsThatAnOverlappingImportContains() {
        val first = importer().import(fixture("valid_basic.csv"), backupFile = "a.bin") as ImportResult.Imported
        // Same records, different bytes: a second, fully overlapping report.
        val overlap = importer().import(fixture("valid_basic.csv") + byteArrayOf(10), backupFile = "b.bin") as ImportResult.Imported
        assertEquals(0, overlap.inserted)

        val deleted = importer().deleteReport(first.reportId)!!
        assertEquals(0, deleted.removedRecords)
        assertEquals(listOf("a.bin"), deleted.backupFiles)
        assertEquals("the overlapping report still contains all 9 records", 9, count("transactions"))
        assertEquals(1, count("reports"))

        assertEquals(9, importer().deleteReport(overlap.reportId)!!.removedRecords)
        assertEquals(0, count("transactions"))
        assertEquals(0, count("reports"))
        assertEquals(null, importer().deleteReport(overlap.reportId))
    }

    @Test
    fun deletingOneImportRemovesOnlyItsOwnRecords() {
        importer().import(fixture("valid_basic.csv"))
        val followup = importer().import(fixture("valid_followup.csv")) as ImportResult.Imported

        assertEquals(2, importer().deleteReport(followup.reportId)!!.removedRecords)
        assertEquals(9, count("transactions"))
    }

    @Test
    fun deleteAllRemovesEverythingAndListsBackupFiles() {
        importer().import(fixture("valid_basic.csv"), backupFile = "a.bin")
        importer().import(fixture("valid_followup.csv"), backupFile = "b.bin")

        val deleted = importer().deleteAll()

        assertEquals(11, deleted.removedRecords)
        assertEquals(setOf("a.bin", "b.bin"), deleted.backupFiles.toSet())
        assertEquals(0, count("transactions"))
        assertEquals(0, count("reports"))
        assertEquals(0, count("report_records"))
    }

    @Test
    fun reportStoresOnlyPseudonymousAccountData() {
        importer().import(fixture("valid_basic.csv"))

        helper.readableDatabase.query("SELECT account_pseudonym, account_masked, fingerprint FROM reports").use {
            assertTrue(it.moveToFirst())
            assertEquals(64, it.getString(0).length)
            assertFalse(it.getString(0).contains("00000001"))
            assertEquals("•••••0001", it.getString(1))
            assertEquals(64, it.getString(2).length)
        }
    }
}
