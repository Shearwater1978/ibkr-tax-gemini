package com.ibkrtax.mobile.keys

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ibkrtax.mobile.security.AesGcmKeyWrapper
import com.ibkrtax.mobile.security.DeviceKeys
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Production Argon2id settings, the real Keystore, and Block Store on an emulator or device. */
@RunWith(AndroidJUnit4::class)
class BackupKeysInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alias = "test.backup-dk-wrap"
    private val dir = File(context.noBackupFilesDir, "test-backup-keys")

    @After
    fun cleanUp() {
        dir.deleteRecursively()
        DeviceKeys.delete(alias)
    }

    @Test
    fun productionArgon2idSettingsUnlockInReasonableTime() {
        val vault = KeyVault()
        val passphrase = "correct horse battery".toCharArray()
        val setup = vault.setUp(passphrase)

        val started = System.nanoTime()
        val dataKey = vault.unlockWithPassphrase(setup.manifest, passphrase)
        val millis = (System.nanoTime() - started) / 1_000_000

        Log.i("BackupKeysTest", "argon2id 64MiB/3/1 unlock took ${millis}ms")
        assertArrayEquals(setup.dataKey, dataKey)
        assertTrue("Unlock took ${millis}ms", millis < 15_000)
    }

    @Test
    fun dataKeyIsStoredWrappedByTheKeystore() {
        val files = LocalKeyFiles(dir, AesGcmKeyWrapper { DeviceKeys.aesWrappingKey(alias) })
        val setup = KeyVault(Argon2idParams(1024, 1, 1)).setUp("correct horse battery".toCharArray())

        files.save(setup.manifest, setup.dataKey)

        assertArrayEquals(setup.dataKey, files.dataKey())
        val raw = File(dir, "data-key.wrapped").readBytes()
        assertFalse("Data key stored in plaintext", raw.toList().windowed(32).any { it == setup.dataKey.toList() })
        files.delete()
        assertNull(files.manifest())
    }

    @Test
    fun blockStoreReportsItsEncryptionState(): Unit = runBlocking {
        // Emulators usually have no screen lock, so end-to-end encryption is normally unavailable here.
        val available = BlockStoreKeyCopy(context).isAvailable()
        Log.i("BackupKeysTest", "blockStoreEndToEnd=$available")
    }
}
