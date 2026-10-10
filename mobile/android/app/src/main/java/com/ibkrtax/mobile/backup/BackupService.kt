package com.ibkrtax.mobile.backup

import com.ibkrtax.mobile.importer.FlexParseResult
import com.ibkrtax.mobile.storage.DeletedImports
import com.ibkrtax.mobile.importer.FlexQueryParser
import com.ibkrtax.mobile.storage.ImportRejection
import com.ibkrtax.mobile.storage.ImportResult
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

/** Where imported reports and their backup state live (the encrypted database in the app). */
interface ReportStore {
    /** Imports atomically and records [backupFile] as pending in the same transaction. */
    fun import(bytes: ByteArray, backupFile: String): ImportResult

    fun pendingBackups(): List<String>

    fun markBackedUp(backupFile: String)

    /** Null when the import does not exist. */
    fun deleteReport(reportId: Long): DeletedImports?

    fun deleteAll(): DeletedImports
}

/** [backupsLeft]: backup files that could not be deleted because the folder was unreachable. */
data class DeletionOutcome(val removedRecords: Int, val backupsDeleted: Int, val backupsLeft: Int)

enum class BackupResult {
    BACKED_UP,

    /** Kept encrypted on the phone; retried later. */
    PENDING,

    /** The folder permission is gone; the user must choose the folder again. */
    ACCESS_LOST,
}

sealed interface ImportOutcome {
    /** Backup encryption or the backup folder is not set up, so import stays off (mobile-app-core task 1.2). */
    data object NotReady : ImportOutcome

    data class Imported(val inserted: Int, val skipped: Int, val backup: BackupResult) : ImportOutcome

    data object AlreadyImported : ImportOutcome

    data class Rejected(val reason: ImportRejection) : ImportOutcome
}

data class RetryResult(val backedUp: Int, val stillPending: Int, val accessLost: Boolean)

/**
 * Import with encrypted backup (encrypted-backup and mobile-report-upload specs): the report
 * is validated, encrypted with the data key and kept in app-private storage, imported
 * atomically, then written to the backup folder and verified by reading it back. A backup
 * that cannot be written stays pending; the local import is never lost.
 */
class BackupService(
    private val dataKey: () -> ByteArray?,
    private val manifestJson: () -> String?,
    private val folder: () -> BackupFolder?,
    private val reports: ReportStore,
    private val pendingDir: File,
    private val random: SecureRandom = SecureRandom(),
) {
    val isReady: Boolean get() = dataKey() != null && folder() != null

    fun importReport(bytes: ByteArray): ImportOutcome {
        val key = dataKey() ?: return ImportOutcome.NotReady
        val target = folder() ?: return ImportOutcome.NotReady
        // Unsupported or broken files get neither a backup nor records.
        val parsed = FlexQueryParser.parse(bytes, sourceFile = "")
        if (parsed is FlexParseResult.Failure) return ImportOutcome.Rejected(ImportRejection.Unparseable(parsed.error))

        val name = randomFileName()
        val encrypted = BackupFileFormat.encrypt(key, bytes, random)
        pendingDir.mkdirs()
        val pending = File(pendingDir, name).apply { writeBytes(encrypted) }

        return when (val result = reports.import(bytes, name)) {
            is ImportResult.Imported -> ImportOutcome.Imported(result.inserted, result.skipped, upload(target, name, encrypted))
            ImportResult.AlreadyImported -> ImportOutcome.AlreadyImported.also { pending.delete() }
            is ImportResult.Rejected -> ImportOutcome.Rejected(result.reason).also { pending.delete() }
        }
    }

    /** Uploads backups that could not be written earlier, and refreshes the manifest. */
    fun retryPending(): RetryResult {
        val target = folder() ?: return RetryResult(0, reports.pendingBackups().size, accessLost = false)
        var backedUp = 0
        var accessLost = false
        for (name in reports.pendingBackups()) {
            val encrypted = File(pendingDir, name).takeIf { it.exists() }?.readBytes() ?: continue
            when (upload(target, name, encrypted)) {
                BackupResult.BACKED_UP -> backedUp++
                BackupResult.ACCESS_LOST -> accessLost = true
                BackupResult.PENDING -> Unit
            }
            if (accessLost) break
        }
        if (!accessLost) syncManifest()
        return RetryResult(backedUp, reports.pendingBackups().size, accessLost)
    }

    /** Deletes one import, the records only it contains, and its backup file (mobile-report-upload "Delete imported reports"). */
    fun deleteImport(reportId: Long): DeletionOutcome? = reports.deleteReport(reportId)?.let(::deleteBackups)

    /** Deletes all imports and their backup files; the keys and manifest stay. */
    fun deleteAllImports(): DeletionOutcome = deleteBackups(reports.deleteAll())

    private fun deleteBackups(deleted: DeletedImports): DeletionOutcome {
        val target = folder()
        var removed = 0
        var left = 0
        for (name in deleted.backupFiles) {
            File(pendingDir, name).delete()
            try {
                if (target == null) left++ else target.delete(name).also { removed++ }
            } catch (e: IOException) {
                left++
            }
        }
        return DeletionOutcome(deleted.removedRecords, removed, left)
    }

    /** Writes the wrapped-key manifest; needed for restore and after any key change. */
    fun syncManifest(): BackupResult {
        val target = folder() ?: return BackupResult.PENDING
        val manifest = manifestJson()?.toByteArray(Charsets.UTF_8) ?: return BackupResult.PENDING
        return try {
            target.write(MANIFEST_FILE, manifest)
            BackupResult.BACKED_UP
        } catch (e: FolderAccessLostException) {
            BackupResult.ACCESS_LOST
        } catch (e: IOException) {
            BackupResult.PENDING
        }
    }

    private fun upload(target: BackupFolder, name: String, encrypted: ByteArray): BackupResult =
        try {
            target.write(name, encrypted)
            val readBack = target.read(name)
            if (readBack != null && readBack.size == encrypted.size && sha256(readBack).contentEquals(sha256(encrypted))) {
                reports.markBackedUp(name)
                File(pendingDir, name).delete()
                BackupResult.BACKED_UP
            } else {
                // Verification failed: remove the bad copy, keep the local one (encrypted-backup spec).
                runCatching { target.delete(name) }
                BackupResult.PENDING
            }
        } catch (e: FolderAccessLostException) {
            BackupResult.ACCESS_LOST
        } catch (e: IOException) {
            BackupResult.PENDING
        }

    private fun randomFileName(): String =
        ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) } + ".bin"

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    companion object {
        /** Fixed name so a restore can find it; it contains only wrapped keys and KDF parameters. */
        const val MANIFEST_FILE = "ibkr-tax-backup-manifest.json"
    }
}
