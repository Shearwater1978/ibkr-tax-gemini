package com.ibkrtax.mobile.keys

import com.ibkrtax.mobile.security.KeyWrapper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** On-device storage: DK wrapped by a device key, and the manifest (no plaintext secrets). */
class LocalKeyFiles(private val directory: File, private val wrapper: KeyWrapper) {
    private val wrappedKeyFile get() = File(directory, "data-key.wrapped")
    private val manifestFile get() = File(directory, "key-manifest.json")

    fun exists(): Boolean = manifestFile.exists() && wrappedKeyFile.exists()

    fun manifest(): KeyManifest? = manifestFile.takeIf { it.exists() }?.readText()?.let(KeyManifest::fromJson)

    fun dataKey(): ByteArray? = wrappedKeyFile.takeIf { it.exists() }?.readBytes()?.let(wrapper::unwrap)

    fun save(manifest: KeyManifest, dataKey: ByteArray) {
        directory.mkdirs()
        writeAtomically(wrappedKeyFile, wrapper.wrap(dataKey))
        writeAtomically(manifestFile, manifest.toJson().toByteArray(Charsets.UTF_8))
    }

    fun saveManifest(manifest: KeyManifest) = writeAtomically(manifestFile, manifest.toJson().toByteArray(Charsets.UTF_8))

    fun delete() {
        wrappedKeyFile.delete()
        manifestFile.delete()
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temp = File(target.path + ".tmp")
        temp.writeBytes(bytes)
        check(temp.renameTo(target) || (target.delete() && temp.renameTo(target))) { "Could not write ${target.name}" }
    }
}

sealed interface BackupKeyStatus {
    data object NotSetUp : BackupKeyStatus

    data class Ready(val cloudCopy: Boolean) : BackupKeyStatus
}

/**
 * Keys created but not stored yet: nothing is saved until the user confirms they wrote
 * down [recoveryCode] (key-management spec "Recovery code display"). The code is never stored.
 */
class PendingKeySetup internal constructor(internal val setup: KeySetup) {
    val recoveryCode: String get() = setup.recoveryCode
}

/** A replacement recovery code waiting for confirmation; nothing changes until it is confirmed. */
class PendingRecoveryCode internal constructor(internal val manifest: KeyManifest, val recoveryCode: String)

/**
 * Backup encryption keys on this device (mobile-encrypted-drive-backup tasks 2.2-2.6).
 * Argon2id is deliberately slow, so the work runs off the main thread.
 */
class BackupKeys(
    private val files: LocalKeyFiles,
    private val cloudCopy: CloudKeyCopy,
    private val vault: KeyVault = KeyVault(),
) {
    suspend fun status(): BackupKeyStatus = withContext(Dispatchers.IO) {
        if (!files.exists()) BackupKeyStatus.NotSetUp else BackupKeyStatus.Ready(cloudCopy.retrieve()?.let(::belongsHere) == true)
    }

    suspend fun prepare(passphrase: CharArray): PendingKeySetup =
        PendingKeySetup(withContext(Dispatchers.Default) { vault.setUp(passphrase) })

    /** Stores the keys after the user confirmed the recovery code; returns whether a Block Store copy was made. */
    suspend fun confirm(pending: PendingKeySetup): Boolean {
        withContext(Dispatchers.IO) { files.save(pending.setup.manifest, pending.setup.dataKey) }
        return storeCloudCopy(pending.setup.dataKey)
    }

    /** Whether confirming would also store a Block Store copy. */
    suspend fun cloudCopyAvailable(): Boolean = cloudCopy.isAvailable()

    /** Throws [WrongSecretException] when [current] is wrong. */
    suspend fun changePassphrase(current: CharArray, new: CharArray) {
        val manifest = withContext(Dispatchers.IO) { checkNotNull(files.manifest()) { "Backup encryption is not set up" } }
        val changed = withContext(Dispatchers.Default) {
            val dataKey = vault.unlockWithPassphrase(manifest, current)
            vault.changePassphrase(manifest, dataKey, new)
        }
        withContext(Dispatchers.IO) { files.saveManifest(changed) }
    }

    /** Throws [WrongSecretException] when [passphrase] is wrong. The old code keeps working until [confirmRecoveryCode]. */
    suspend fun prepareNewRecoveryCode(passphrase: CharArray): PendingRecoveryCode {
        val manifest = withContext(Dispatchers.IO) { checkNotNull(files.manifest()) { "Backup encryption is not set up" } }
        val (changed, code) = withContext(Dispatchers.Default) {
            vault.replaceRecoveryCode(manifest, vault.unlockWithPassphrase(manifest, passphrase))
        }
        return PendingRecoveryCode(changed, code)
    }

    suspend fun confirmRecoveryCode(pending: PendingRecoveryCode) {
        withContext(Dispatchers.IO) { files.saveManifest(pending.manifest) }
    }

    /** The data key for encrypting backups, or null when backup encryption is not set up. */
    fun dataKey(): ByteArray? = if (files.exists()) files.dataKey() else null

    /** The manifest (wrapped keys only) to store next to the backups. */
    fun manifestJson(): String? = files.manifest()?.toJson()

    /** Erasure: local key files and the Block Store copy. */
    suspend fun erase() {
        withContext(Dispatchers.IO) { files.delete() }
        cloudCopy.delete()
    }

    private suspend fun storeCloudCopy(dataKey: ByteArray): Boolean =
        cloudCopy.isAvailable() && runCatching { cloudCopy.store(dataKey) }.isSuccess

    private fun belongsHere(dataKey: ByteArray): Boolean = files.manifest()?.let { vault.matches(it, dataKey) } == true
}
