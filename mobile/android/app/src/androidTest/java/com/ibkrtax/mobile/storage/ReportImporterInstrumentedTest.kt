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
