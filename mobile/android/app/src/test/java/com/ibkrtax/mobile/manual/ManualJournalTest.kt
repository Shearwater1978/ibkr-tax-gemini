package com.ibkrtax.mobile.manual

import com.ibkrtax.mobile.backup.BackupFileFormat
import com.ibkrtax.mobile.backup.BackupFolder
import com.ibkrtax.mobile.backup.BackupResult
import com.ibkrtax.mobile.backup.BackupService
import com.ibkrtax.mobile.backup.FolderAccessLostException
import com.ibkrtax.mobile.backup.ReportStore
import com.ibkrtax.mobile.backup.RestoreOutcome
import com.ibkrtax.mobile.backup.RestoreSecret
import com.ibkrtax.mobile.backup.RestoreService
import com.ibkrtax.mobile.keys.Argon2idParams
import com.ibkrtax.mobile.keys.KeyVault
import com.ibkrtax.mobile.storage.DeletedImports
import com.ibkrtax.mobile.storage.ImportResult
import com.ibkrtax.mobile.storage.ManualEntry
import com.ibkrtax.mobile.testing.Fixtures
import java.io.IOException
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The encrypted manual journal: format, write-verify-replace, retry and restore (mobile-manual-operations Decision 6). */
class ManualJournalTest {
    private class MemoryFolder : BackupFolder {
        val files = linkedMapOf<String, ByteArray>()
        var offline = false

        override fun write(name: String, bytes: ByteArray) {
            if (offline) throw IOException("offline")
            files[name] = bytes.copyOf()
        }

        override fun read(name: String): ByteArray? {
            if (offline) throw IOException("offline")
            return files[name]?.copyOf()
        }

        override fun list() = files.keys.toList()

        override fun delete(name: String) {
            if (offline) throw FolderAccessLostException()
            files.remove(name)
        }
    }

    private class MemoryJournalStore : JournalStore {
        var stored = listOf<ManualEntry>()
        var state = JournalState()

        override fun entries() = stored

        override fun journalState() = state

        override fun setJournalState(state: JournalState) {
            this.state = state
        }

        override fun restoreEntries(entries: List<ManualEntry>) {
            stored = entries
        }
    }

    private val fixture = Fixtures.manual("journal_v1.json")
    private val dataKey = ByteArray(32) { it.toByte() }
    private val clock = Clock.fixed(Instant.parse("2026-10-13T08:00:00Z"), ZoneOffset.UTC)
    private val folder = MemoryFolder()
    private val store = MemoryJournalStore()
    private var key: ByteArray? = dataKey
    private var target: BackupFolder? = folder
    private val backup = ManualJournalBackup({ key }, { target }, store, Files.createTempDirectory("journal").toFile(), clock)

    private fun decryptedJournal(name: String) = ManualJournal.decode(BackupFileFormat.decrypt(dataKey, folder.files.getValue(name)))!!

    @Test
    fun theFixtureDecodesAndEncodesToTheSameJson() {
        val decoded = ManualJournal.decode(fixture)!!
        assertEquals(12, decoded.sequence)
        assertEquals(14, decoded.entries.size)
        assertEquals("00000000-0000-4000-8000-000000000001", decoded.entries.first().entryId)
        assertEquals("150.00", JSONObject(decoded.entries.first().fields).getString("price"))

        val encoded = ManualJournal.encode(decoded.entries, decoded.sequence, "2026-10-13T08:00:00Z")
        assertTrue(JSONObject(encoded).similar(JSONObject(fixture.decodeToString())))
    }

    @Test
    fun reportsAndNewerJournalsAreNotJournals() {
        assertNull(ManualJournal.decode(Fixtures.flexQuery(Fixtures.VALID_BASIC)))
        val newer = JSONObject(fixture.decodeToString()).put("version", 2).toString().toByteArray()
        assertNull(ManualJournal.decode(newer))
    }

    @Test
    fun eachChangeWritesAVerifiedJournalAndThenRemovesThePreviousOne() {
        store.stored = ManualJournal.decode(fixture)!!.entries.take(2)
        assertEquals(BackupResult.BACKED_UP, backup.changed())
        val first = store.state.file!!
        assertEquals(1, decryptedJournal(first).sequence)

        store.stored = store.stored.take(1)
        assertEquals(BackupResult.BACKED_UP, backup.changed())
        val second = store.state.file!!
        assertEquals(listOf(second), folder.files.keys.toList())
        assertEquals(2, decryptedJournal(second).sequence)
        assertEquals(1, decryptedJournal(second).entries.size)
        assertFalse(backup.isPending)
    }

    @Test
    fun anUnreachableFolderKeepsTheJournalPendingUntilARetry() {
        store.stored = ManualJournal.decode(fixture)!!.entries.take(1)
        folder.offline = true
        assertEquals(BackupResult.PENDING, backup.changed())
        assertTrue(backup.isPending)
        assertTrue(folder.files.isEmpty())

        folder.offline = false
        assertEquals(BackupResult.BACKED_UP, backup.sync())
        assertFalse(backup.isPending)
        assertEquals(1, folder.files.size)
    }

    @Test
    fun withoutBackupKeysTheChangeIsRememberedAndWrittenOnceKeysExist() {
        key = null
        store.stored = ManualJournal.decode(fixture)!!.entries.take(1)
        assertEquals(BackupResult.PENDING, backup.changed())
        assertFalse(backup.isPending)
        assertTrue(store.state.stale)

        key = dataKey
        assertTrue(backup.isPending)
        assertEquals(BackupResult.BACKED_UP, backup.sync())
        assertEquals(1, decryptedJournal(store.state.file!!).entries.size)
    }

    @Test
    fun theBackupServiceRetriesThePendingJournal() {
        val reports = object : ReportStore {
            override fun import(bytes: ByteArray, backupFile: String) = ImportResult.AlreadyImported

            override fun pendingBackups() = emptyList<String>()

            override fun markBackedUp(backupFile: String) = Unit

            override fun deleteReport(reportId: Long): DeletedImports? = null

            override fun deleteAll() = DeletedImports(0, emptyList())
        }
        val service = BackupService({ key }, { "{}" }, { target }, reports, Files.createTempDirectory("pending").toFile(), journal = backup)
        store.stored = ManualJournal.decode(fixture)!!.entries.take(1)
        target = null
        backup.changed()
        assertEquals(1, service.pendingCount())

        target = folder
        val result = service.retryPending()
        assertEquals(1, result.backedUp)
        assertEquals(0, result.stillPending)
    }

    @Test
    fun restoreBringsBackTheNewestJournalAfterTheReports() = runTest {
        val vault = KeyVault(Argon2idParams(memoryKiB = 1024, iterations = 1, parallelism = 1))
        val oldPhone = vault.setUp("correct horse battery".toCharArray())
        val old = ManualJournal.decode(fixture)!!
        folder.write(BackupService.MANIFEST_FILE, oldPhone.manifest.toJson().toByteArray())
        folder.write("aa.bin", BackupFileFormat.encrypt(oldPhone.dataKey, Fixtures.flexQuery(Fixtures.VALID_BASIC)))
        folder.write("bb.bin", BackupFileFormat.encrypt(oldPhone.dataKey, ManualJournal.encode(old.entries.take(3), 4, "x").toByteArray()))
        folder.write("cc.bin", BackupFileFormat.encrypt(oldPhone.dataKey, fixture))

        val imported = mutableListOf<String>()
        val reports = object : ReportStore {
            override fun import(bytes: ByteArray, backupFile: String): ImportResult {
                imported += backupFile
                return ImportResult.Imported(1, 9, 0)
            }

            override fun pendingBackups() = emptyList<String>()

            override fun markBackedUp(backupFile: String) = Unit

            override fun deleteReport(reportId: Long): DeletedImports? = null

            override fun deleteAll() = DeletedImports(0, emptyList())
        }
        val restore = RestoreService(reports, vault, journal = backup)

        val outcome = restore.restore(folder, RestoreSecret.Passphrase("correct horse battery".toCharArray()), null) { _, _ -> }

        assertEquals(RestoreOutcome.Restored(reports = 1, inserted = 9, alreadyImported = 0, unreadable = 0, manualEntries = 14), outcome)
        assertEquals(listOf("aa.bin"), imported)
        assertEquals(14, store.stored.size)
        assertEquals(JournalState(sequence = 12, file = "cc.bin"), store.state)
    }
}
