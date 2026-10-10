package com.ibkrtax.mobile.keys

import com.ibkrtax.mobile.security.AesGcmKeyWrapper
import java.nio.file.Files
import javax.crypto.KeyGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupKeysTest {
    private class FakeCloudCopy(var available: Boolean) : CloudKeyCopy {
        var stored: ByteArray? = null

        override suspend fun isAvailable() = available

        override suspend fun store(dataKey: ByteArray) {
            stored = dataKey.copyOf()
        }

        override suspend fun retrieve() = stored?.copyOf()

        override suspend fun delete() {
            stored = null
        }
    }

    private val vault = KeyVault(Argon2idParams(memoryKiB = 1024, iterations = 1, parallelism = 1))
    private val deviceKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val files = LocalKeyFiles(Files.createTempDirectory("keys").toFile(), AesGcmKeyWrapper { deviceKey })
    private val passphrase = "correct horse battery".toCharArray()

    @Test
    fun setupStoresWrappedKeyAndCopiesItOnlyWhenEndToEndEncrypted() = runBlocking {
        val cloud = FakeCloudCopy(available = true)
        val keys = BackupKeys(files, cloud, vault)
        assertEquals(BackupKeyStatus.NotSetUp, keys.status())

        val pending = keys.prepare(passphrase)
        assertEquals("nothing is stored before confirmation", BackupKeyStatus.NotSetUp, keys.status())
        val result = keys.confirm(pending)

        assertTrue(result)
        assertEquals(26, pending.recoveryCode.length)
        assertArrayEquals(files.dataKey(), cloud.stored)
        assertEquals(BackupKeyStatus.Ready(cloudCopy = true), keys.status())
        assertArrayEquals(files.dataKey(), vault.unlockWithRecoveryCode(files.manifest()!!, pending.recoveryCode))
    }

    @Test
    fun noBlockStoreCopyWithoutEndToEndEncryption() = runBlocking {
        val cloud = FakeCloudCopy(available = false)
        val keys = BackupKeys(files, cloud, vault)

        assertFalse(keys.confirm(keys.prepare(passphrase)))
        assertNull(cloud.stored)
        assertEquals(BackupKeyStatus.Ready(cloudCopy = false), keys.status())
    }

    @Test
    fun changingThePassphraseKeepsTheDataKey() = runBlocking {
        val keys = BackupKeys(files, FakeCloudCopy(available = false), vault)
        keys.confirm(keys.prepare(passphrase))
        val dataKey = files.dataKey()!!
        val newPassphrase = "a different passphrase".toCharArray()

        assertThrows(WrongSecretException::class.java) { runBlocking { keys.changePassphrase("wrong passphrase!".toCharArray(), newPassphrase) } }
        keys.changePassphrase(passphrase, newPassphrase)

        assertArrayEquals(dataKey, vault.unlockWithPassphrase(files.manifest()!!, newPassphrase))
        assertArrayEquals(dataKey, files.dataKey())
    }

    @Test
    fun newRecoveryCodeNeedsThePassphraseAndConfirmation() = runBlocking {
        val keys = BackupKeys(files, FakeCloudCopy(available = false), vault)
        val first = keys.prepare(passphrase)
        keys.confirm(first)

        assertThrows(WrongSecretException::class.java) { runBlocking { keys.prepareNewRecoveryCode("wrong passphrase!".toCharArray()) } }
        val pending = keys.prepareNewRecoveryCode(passphrase)
        // Not confirmed yet: the old code still works.
        assertArrayEquals(files.dataKey(), vault.unlockWithRecoveryCode(files.manifest()!!, first.recoveryCode))

        keys.confirmRecoveryCode(pending)

        assertArrayEquals(files.dataKey(), vault.unlockWithRecoveryCode(files.manifest()!!, pending.recoveryCode))
        assertThrows(WrongSecretException::class.java) { vault.unlockWithRecoveryCode(files.manifest()!!, first.recoveryCode) }
        assertArrayEquals(files.dataKey(), vault.unlockWithPassphrase(files.manifest()!!, passphrase))
    }

    @Test
    fun eraseRemovesLocalKeysAndTheBlockStoreCopy() = runBlocking {
        val cloud = FakeCloudCopy(available = true)
        val keys = BackupKeys(files, cloud, vault)
        keys.confirm(keys.prepare(passphrase))

        keys.erase()

        assertEquals(BackupKeyStatus.NotSetUp, keys.status())
        assertNull(cloud.stored)
    }
}
