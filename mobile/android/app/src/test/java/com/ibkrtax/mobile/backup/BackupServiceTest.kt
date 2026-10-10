package com.ibkrtax.mobile.backup

import com.ibkrtax.mobile.importer.FlexImportError
import com.ibkrtax.mobile.storage.ImportRejection
import com.ibkrtax.mobile.storage.ImportResult
import com.ibkrtax.mobile.testing.Fixtures
import java.io.IOException
import java.nio.file.Files
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupServiceTest {
    /** In-memory folder that can fail, lose access, or corrupt what it stores. */
    private class MemoryFolder : BackupFolder {
        val files = linkedMapOf<String, ByteArray>()
        var failWrites = false
        var accessLost = false
        var corrupt = false

        override fun write(name: String, bytes: ByteArray) {
            if (accessLost) throw FolderAccessLostException()
            if (failWrites) throw IOException("offline")
            files[name] = if (corrupt) bytes.copyOf(bytes.size - 1) else bytes.copyOf()
        }

        override fun read(name: String) = files[name]?.copyOf()

        override fun list() = files.keys.toList()

        override fun delete(name: String) {
            files.remove(name)
        }
    }

    private class MemoryReports : ReportStore {
        val imported = mutableListOf<ByteArray>()
        val status = linkedMapOf<String, String>()

        override fun import(bytes: ByteArray, backupFile: String): ImportResult {
            if (imported.any { it.contentEquals(bytes) }) return ImportResult.AlreadyImported
            imported += bytes
            status[backupFile] = "pending"
            return ImportResult.Imported(reportId = imported.size.toLong(), inserted = 9, skipped = 0)
        }

        override fun pendingBackups() = status.filterValues { it == "pending" }.keys.toList()

        override fun markBackedUp(backupFile: String) {
            status[backupFile] = "backed_up"
        }
    }

    private val dataKey = ByteArray(32).also(SecureRandom()::nextBytes)
    private val folder = MemoryFolder()
    private val reports = MemoryReports()
    private val pendingDir = Files.createTempDirectory("pending").toFile()
    private val report = Fixtures.flexQuery(Fixtures.VALID_BASIC)

    private fun service(key: ByteArray? = dataKey, target: BackupFolder? = folder) =
        BackupService({ key }, { """{"version":1}""" }, { target }, reports, pendingDir)

    @Test
    fun fileFormatRoundTripsAndRejectsTamperingAndNewerVersions() {
        val encrypted = BackupFileFormat.encrypt(dataKey, report)
        assertArrayEquals(report, BackupFileFormat.decrypt(dataKey, encrypted))
        assertFalse("ciphertext must not contain the report", String(encrypted, Charsets.ISO_8859_1).contains("U00000001"))

        val other = BackupFileFormat.encrypt(dataKey, report)
        assertFalse("each file gets its own id and nonce", encrypted.contentEquals(other))

        assertThrows(CorruptBackupException::class.java) { BackupFileFormat.decrypt(ByteArray(32), encrypted) }
        assertThrows(CorruptBackupException::class.java) { BackupFileFormat.decrypt(dataKey, encrypted.copyOf().also { it[30] = (it[30] + 1).toByte() }) }
        assertThrows(UnsupportedBackupVersionException::class.java) { BackupFileFormat.decrypt(dataKey, encrypted.copyOf().also { it[4] = 2 }) }
        assertThrows(CorruptBackupException::class.java) { BackupFileFormat.decrypt(dataKey, "not a backup".toByteArray()) }
    }

    @Test
    fun importIsOffUntilKeysAndFolderExist() {
        assertEquals(ImportOutcome.NotReady, service(key = null).importReport(report))
        assertEquals(ImportOutcome.NotReady, service(target = null).importReport(report))
        assertTrue(reports.imported.isEmpty())
    }

    @Test
    fun importWritesAVerifiedEncryptedBackupWithANeutralName() {
        val outcome = service().importReport(report)

        assertEquals(ImportOutcome.Imported(9, 0, BackupResult.BACKED_UP), outcome)
        val (name, stored) = folder.files.entries.single()
        assertTrue(name.matches(Regex("[0-9a-f]{32}\\.bin")))
        assertArrayEquals(report, BackupFileFormat.decrypt(dataKey, stored))
        assertEquals(emptyList<String>(), reports.pendingBackups())
        assertEquals("local copy removed after verification", 0, pendingDir.listFiles()!!.size)
    }

    @Test
    fun unsupportedFilesGetNeitherBackupNorRecords() {
        val outcome = service().importReport(Fixtures.flexQuery(Fixtures.UNSUPPORTED_FORMAT))

        assertEquals(ImportOutcome.Rejected(ImportRejection.Unparseable(FlexImportError.UnsupportedFormat)), outcome)
        assertTrue(folder.files.isEmpty())
        assertTrue(reports.imported.isEmpty())
        assertEquals(0, pendingDir.listFiles()?.size ?: 0)
    }

    @Test
    fun duplicateImportLeavesNoBackup() {
        service().importReport(report)
        assertEquals(ImportOutcome.AlreadyImported, service().importReport(report))
        assertEquals(1, folder.files.size)
        assertEquals(0, pendingDir.listFiles()!!.size)
    }

    @Test
    fun offlineFolderKeepsTheImportAndAPendingEncryptedCopy() {
        folder.failWrites = true
        val outcome = service().importReport(report)

        assertEquals(ImportOutcome.Imported(9, 0, BackupResult.PENDING), outcome)
        assertEquals(1, reports.imported.size)
        val name = reports.pendingBackups().single()
        assertArrayEquals(report, BackupFileFormat.decrypt(dataKey, java.io.File(pendingDir, name).readBytes()))

        folder.failWrites = false
        assertEquals(RetryResult(backedUp = 1, stillPending = 0, accessLost = false), service().retryPending())
        assertNotNull(folder.files[name])
        assertNotNull("manifest written on retry", folder.files[BackupService.MANIFEST_FILE])
    }

    @Test
    fun failedVerificationRemovesTheBadCopyAndStaysPending() {
        folder.corrupt = true
        val outcome = service().importReport(report)

        assertEquals(ImportOutcome.Imported(9, 0, BackupResult.PENDING), outcome)
        assertTrue("bad copy deleted from the folder", folder.files.isEmpty())
        assertEquals(1, reports.pendingBackups().size)
    }

    @Test
    fun lostFolderAccessIsReported() {
        folder.accessLost = true
        assertEquals(ImportOutcome.Imported(9, 0, BackupResult.ACCESS_LOST), service().importReport(report))
        assertEquals(RetryResult(backedUp = 0, stillPending = 1, accessLost = true), service().retryPending())
        assertEquals(BackupResult.ACCESS_LOST, service().syncManifest())
    }
}
