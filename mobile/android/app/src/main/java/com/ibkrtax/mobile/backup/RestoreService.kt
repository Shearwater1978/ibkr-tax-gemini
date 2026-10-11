package com.ibkrtax.mobile.backup

import com.ibkrtax.mobile.keys.KeyManifest
import com.ibkrtax.mobile.keys.KeyVault
import com.ibkrtax.mobile.keys.UnsupportedManifestException
import com.ibkrtax.mobile.keys.WrongSecretException
import com.ibkrtax.mobile.storage.ImportResult
import java.io.IOException
import org.json.JSONException

/** What the user typed to unlock the backup's data key. */
sealed interface RestoreSecret {
    class Passphrase(val value: CharArray) : RestoreSecret

    class RecoveryCode(val value: String) : RestoreSecret
}

sealed interface RestoreOutcome {
    /** The folder holds no backup manifest. */
    data object NoBackup : RestoreOutcome

    /** No matching Block Store key: ask for the passphrase or recovery code. */
    data object NeedsSecret : RestoreOutcome

    /** The passphrase or recovery code does not unlock the data key; nothing was restored. */
    data object WrongSecret : RestoreOutcome

    /** The manifest was written by a newer app version. */
    data object NewerVersion : RestoreOutcome

    data object AccessLost : RestoreOutcome

    /** [unreadable]: backup files that could not be decrypted or imported; the others were restored. */
    data class Restored(val reports: Int, val inserted: Int, val alreadyImported: Int, val unreadable: Int) : RestoreOutcome
}

/**
 * Restore on a new device (encrypted-backup "Restore on a new device", key-management
 * "Automatic key restore with Android Block Store"). The data key comes from Block Store when
 * that copy matches the backup manifest, otherwise from the passphrase or recovery code. Every
 * backup file is decrypted first, so a wrong key restores nothing; the reports are then imported
 * like new ones (fresh device-bound pseudonyms) and kept as backed up, since their files exist.
 */
class RestoreService(private val reports: ReportStore, private val vault: KeyVault = KeyVault()) {
    /** [saveKeys] stores the unlocked key on this device before the reports are imported. */
    suspend fun restore(
        folder: BackupFolder,
        secret: RestoreSecret?,
        cloudKey: ByteArray?,
        saveKeys: suspend (KeyManifest, ByteArray) -> Unit,
    ): RestoreOutcome {
        try {
            val manifest = try {
                folder.read(BackupService.MANIFEST_FILE)?.let { KeyManifest.fromJson(String(it, Charsets.UTF_8)) } ?: return RestoreOutcome.NoBackup
            } catch (e: UnsupportedManifestException) {
                return RestoreOutcome.NewerVersion
            } catch (e: JSONException) {
                return RestoreOutcome.NoBackup
            } catch (e: IllegalArgumentException) {
                return RestoreOutcome.NoBackup
            }
            val dataKey = when (secret) {
                null -> cloudKey?.takeIf { vault.matches(manifest, it) } ?: return RestoreOutcome.NeedsSecret
                else -> try {
                    when (secret) {
                        is RestoreSecret.Passphrase -> vault.unlockWithPassphrase(manifest, secret.value)
                        is RestoreSecret.RecoveryCode -> vault.unlockWithRecoveryCode(manifest, secret.value)
                    }
                } catch (e: WrongSecretException) {
                    return RestoreOutcome.WrongSecret
                }
            }

            var unreadable = 0
            val decrypted = folder.list().filter { it.endsWith(BACKUP_SUFFIX) }.sorted().mapNotNull { name ->
                val plain = folder.read(name)?.let { runCatching { BackupFileFormat.decrypt(dataKey, it) }.getOrNull() }
                if (plain == null) unreadable++
                plain?.let { name to it }
            }
            saveKeys(manifest, dataKey)

            var restored = 0
            var inserted = 0
            var already = 0
            for ((name, bytes) in decrypted) {
                when (val result = reports.import(bytes, name)) {
                    is ImportResult.Imported -> {
                        reports.markBackedUp(name)
                        restored++
                        inserted += result.inserted
                    }
                    ImportResult.AlreadyImported -> already++
                    is ImportResult.Rejected -> unreadable++
                }
            }
            return RestoreOutcome.Restored(restored, inserted, already, unreadable)
        } catch (e: IOException) {
            // Includes FolderAccessLostException: the folder could not be read.
            return RestoreOutcome.AccessLost
        }
    }

    private companion object {
        const val BACKUP_SUFFIX = ".bin"
    }
}
