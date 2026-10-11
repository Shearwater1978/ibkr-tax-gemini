package com.ibkrtax.mobile.backup

import com.ibkrtax.mobile.keys.Argon2idParams
import com.ibkrtax.mobile.keys.KeyManifest
import com.ibkrtax.mobile.keys.KeyVault
import com.ibkrtax.mobile.storage.DeletedImports
import com.ibkrtax.mobile.storage.ImportResult
import com.ibkrtax.mobile.testing.Fixtures
import java.security.SecureRandom
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreServiceTest {
    private class MemoryFolder : BackupFolder {
        val files = linkedMapOf<String, ByteArray>()
        var accessLost = false

        override fun write(name: String, bytes: ByteArray) {
            files[name] = bytes.copyOf()
        }

        override fun read(name: String): ByteArray? {
            if (accessLost) throw FolderAccessLostException()
            return files[name]?.copyOf()
        }

        override fun list() = files.keys.toList()

        override fun delete(name: String) {
            files.remove(name)
        }
    }

    /** The new phone's empty database. */
    private class MemoryReports : ReportStore {
        val imported = mutableListOf<ByteArray>()
        val status = linkedMapOf<String, String>()

        override fun import(bytes: ByteArray, backupFile: String): ImportResult {
            if (imported.any { it.contentEquals(bytes) }) return ImportResult.AlreadyImported
            imported += bytes
            status[backupFile] = "pending"
            return ImportResult.Imported(reportId = imported.size.toLong(), inserted = 5, skipped = 0)
        }

        override fun pendingBackups() = status.filterValues { it == "pending" }.keys.toList()

        override fun markBackedUp(backupFile: String) {
            status[backupFile] = "backed_up"
        }

        override fun deleteReport(reportId: Long): DeletedImports? = null

        override fun deleteAll() = DeletedImports(0, emptyList())
    }

    private val vault = KeyVault(Argon2idParams(memoryKiB = 1024, iterations = 1, parallelism = 1))
    private val passphrase = "correct horse battery".toCharArray()
    private val oldPhone = vault.setUp(passphrase)
    private val folder = MemoryFolder()
    private val reports = MemoryReports()
    private var saved: Pair<KeyManifest, ByteArray>? = null
    private val service = RestoreService(reports, vault)

    /** The backup folder as the old phone left it: the manifest and two encrypted reports. */
    private fun backedUp(extra: Map<String, ByteArray> = emptyMap()) {
        folder.write(BackupService.MANIFEST_FILE, oldPhone.manifest.toJson().toByteArray())
        folder.write("aa.bin", BackupFileFormat.encrypt(oldPhone.dataKey, Fixtures.flexQuery(Fixtures.VALID_BASIC)))
        folder.write("bb.bin", BackupFileFormat.encrypt(oldPhone.dataKey, Fixtures.flexQuery(Fixtures.VALID_FOLLOWUP)))
        extra.forEach(folder::write)
    }

    private suspend fun restore(secret: RestoreSecret?, cloudKey: ByteArray? = null) =
        service.restore(folder, secret, cloudKey) { manifest, key -> saved = manifest to key }

    @Test
    fun restoresWithThePassphraseAndKeepsTheFilesAsBackedUp() = runTest {
        backedUp()
        assertEquals(RestoreOutcome.Restored(reports = 2, inserted = 10, alreadyImported = 0, unreadable = 0), restore(RestoreSecret.Passphrase(passphrase)))
        assertEquals(mapOf("aa.bin" to "backed_up", "bb.bin" to "backed_up"), reports.status)
        assertArrayEquals(oldPhone.dataKey, saved!!.second)
        assertEquals(oldPhone.manifest, saved!!.first)
    }

    @Test
    fun restoresWithTheRecoveryCode() = runTest {
        backedUp()
        assertEquals(2, (restore(RestoreSecret.RecoveryCode(oldPhone.recoveryCode)) as RestoreOutcome.Restored).reports)
    }

    @Test
    fun aMatchingBlockStoreKeyNeedsNoSecret() = runTest {
        backedUp()
        assertEquals(2, (restore(secret = null, cloudKey = oldPhone.dataKey) as RestoreOutcome.Restored).reports)
    }

    @Test
    fun withoutAMatchingBlockStoreKeyTheSecretIsAskedFor() = runTest {
        backedUp()
        assertEquals(RestoreOutcome.NeedsSecret, restore(secret = null))
        assertEquals(RestoreOutcome.NeedsSecret, restore(secret = null, cloudKey = ByteArray(32).also(SecureRandom()::nextBytes)))
        assertTrue(reports.imported.isEmpty())
    }

    @Test
    fun aWrongPassphraseRestoresNothing() = runTest {
        backedUp()
        assertEquals(RestoreOutcome.WrongSecret, restore(RestoreSecret.Passphrase("wrong horse battery".toCharArray())))
        assertEquals(RestoreOutcome.WrongSecret, restore(RestoreSecret.RecoveryCode("not a code")))
        assertTrue(reports.imported.isEmpty())
        assertNull(saved)
    }

    @Test
    fun aDamagedFileIsCountedAndTheRestRestored() = runTest {
        backedUp(mapOf("cc.bin" to ByteArray(80), "notes.txt" to ByteArray(3)))
        assertEquals(RestoreOutcome.Restored(reports = 2, inserted = 10, alreadyImported = 0, unreadable = 1), restore(RestoreSecret.Passphrase(passphrase)))
    }

    @Test
    fun runningItAgainImportsNothingTwice() = runTest {
        backedUp()
        restore(RestoreSecret.Passphrase(passphrase))
        assertEquals(RestoreOutcome.Restored(reports = 0, inserted = 0, alreadyImported = 2, unreadable = 0), restore(RestoreSecret.Passphrase(passphrase)))
    }

    @Test
    fun aFolderWithoutBackupOrAccess() = runTest {
        assertEquals(RestoreOutcome.NoBackup, restore(RestoreSecret.Passphrase(passphrase)))
        backedUp()
        folder.accessLost = true
        assertEquals(RestoreOutcome.AccessLost, restore(RestoreSecret.Passphrase(passphrase)))
    }

    @Test
    fun aNewerManifestIsNotRead() = runTest {
        folder.write(BackupService.MANIFEST_FILE, """{"version":99}""".toByteArray())
        assertEquals(RestoreOutcome.NewerVersion, restore(RestoreSecret.Passphrase(passphrase)))
    }
}
