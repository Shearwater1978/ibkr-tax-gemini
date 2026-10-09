package com.ibkrtax.mobile.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ibkrtax.mobile.security.DeviceKeys
import com.ibkrtax.mobile.security.IdentifierKind
import com.ibkrtax.mobile.security.Pseudonymizer
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on an emulator or device. Emulator Keystore may be software-backed, so
 * hardware backing is recorded, not asserted; physical-device checks are separate.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedStorageInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "instrumented-test.db"
    private val testAliases = listOf("test.wrap", "test.hmac")

    @Before
    @After
    fun cleanUp() {
        EncryptedDatabase.delete(context, dbName)
        testAliases.forEach(DeviceKeys::delete)
    }

    @Test
    fun deviceKeysAreNonExportableAndReused() {
        val wrap = DeviceKeys.aesWrappingKey("test.wrap")
        val hmac = DeviceKeys.hmacKey("test.hmac")

        assertNull("Keystore keys must not expose key material", wrap.encoded)
        assertNull(hmac.encoded)
        assertEquals(wrap, DeviceKeys.aesWrappingKey("test.wrap"))
        android.util.Log.i("EncryptedStorageTest", "hardwareBacked=${DeviceKeys.isHardwareBacked(wrap)}")
    }

    @Test
    fun pseudonymsUseTheKeystoreHmacKey() {
        val pseudonymizer = Pseudonymizer { DeviceKeys.hmacKey("test.hmac") }
        val pseudonym = pseudonymizer.pseudonym(IdentifierKind.ACCOUNT, "U00000001")

        assertEquals(64, pseudonym.length)
        assertEquals(pseudonym, pseudonymizer.pseudonym(IdentifierKind.ACCOUNT, "U00000001"))
    }

    @Test
    fun databaseFileIsEncryptedAtRest() {
        EncryptedDatabase.open(context, dbName).use { helper ->
            helper.writableDatabase.execSQL(
                "INSERT INTO schema_info (key, value) VALUES ('marker', 'SYNTHETIC_PLAINTEXT_MARKER')",
            )
        }

        val bytes = context.getDatabasePath(dbName).readBytes()
        val text = String(bytes, Charsets.ISO_8859_1)
        assertFalse("SQLite plaintext header found", text.startsWith("SQLite format 3"))
        assertFalse("Plaintext value found in file", text.contains("SYNTHETIC_PLAINTEXT_MARKER"))
        assertTrue(File(context.noBackupFilesDir, "$dbName.key").exists())
    }

    @Test
    fun reopeningWithTheDeviceKeyReadsData() {
        EncryptedDatabase.open(context, dbName).use { helper ->
            helper.writableDatabase.execSQL("INSERT INTO schema_info (key, value) VALUES ('marker', 'kept')")
        }
        EncryptedDatabase.open(context, dbName).use { helper ->
            helper.readableDatabase.query("SELECT value FROM schema_info WHERE key = 'marker'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("kept", cursor.getString(0))
            }
        }
    }

    @Test
    fun providerKeyAndQuotesAreStoredOnlyInsideTheEncryptedDatabase() {
        val apiKey = "SYNTHETIC_TEST_API_KEY_1234"
        EncryptedDatabase.open(context, dbName).use { helper ->
            val store = PriceStore(helper)
            assertNull("A new install must not contain a provider key", store.get())

            store.set("  $apiKey  ")
            val time = java.time.Instant.parse("2024-01-03T16:00:00Z")
            store.putAll(listOf(com.ibkrtax.mobile.prices.Quote("AAPL", java.math.BigDecimal("190.10"), "USD", time, time, java.math.BigDecimal("189.50"))))

            assertEquals(apiKey, store.get())
            assertEquals(0, java.math.BigDecimal("190.10").compareTo(store.get(setOf("AAPL", "MSFT")).getValue("AAPL").price))
            assertEquals(0, java.math.BigDecimal("0.60").compareTo(store.get(setOf("AAPL")).getValue("AAPL").dailyChange))

            val fx = FxStore(helper)
            assertNull(fx.get())
            fx.put(com.ibkrtax.mobile.fx.FxRates("2026-10-08", mapOf("USD" to java.math.BigDecimal("4.0"), "EUR" to java.math.BigDecimal("4.4"))))
            fx.put(com.ibkrtax.mobile.fx.FxRates("2026-10-09", mapOf("USD" to java.math.BigDecimal("4.1"))))
            assertEquals(com.ibkrtax.mobile.fx.FxRates("2026-10-09", mapOf("USD" to java.math.BigDecimal("4.1"))), fx.get())
        }

        val text = String(context.getDatabasePath(dbName).readBytes(), Charsets.ISO_8859_1)
        assertFalse("API key found in plaintext", text.contains(apiKey))

        EncryptedDatabase.open(context, dbName).use { helper ->
            PriceStore(helper).clear()
            assertNull(PriceStore(helper).get())
        }
    }

    @Test
    fun databaseCannotBeReadWithoutTheKey() {
        EncryptedDatabase.open(context, dbName).use { it.writableDatabase }

        try {
            EncryptedDatabase.openWithKey(context, dbName, ByteArray(32)).use { helper ->
                helper.readableDatabase.query("SELECT count(*) FROM schema_info").use { it.moveToFirst() }
            }
            fail("Database opened with the wrong key")
        } catch (expected: Exception) {
            // SQLCipher reports "file is not a database".
        }
    }
}
