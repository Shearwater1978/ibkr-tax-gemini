package com.ibkrtax.mobile.security

import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests with software keys; Keystore-backed behavior is covered by instrumented tests. */
class SecurityPrimitivesTest {
    private fun aesKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun hmacKey(): SecretKey = KeyGenerator.getInstance("HmacSHA256").generateKey()

    private fun tempFile(): File = Files.createTempDirectory("dbkey").toFile().resolve("db.key")

    @Test
    fun wrapperRoundTripsAndRejectsTampering() {
        val key = aesKey()
        val wrapper = AesGcmKeyWrapper { key }
        val secret = ByteArray(32) { it.toByte() }

        val wrapped = wrapper.wrap(secret)
        assertArrayEquals(secret, wrapper.unwrap(wrapped))

        wrapped[wrapped.size - 1] = (wrapped.last() + 1).toByte()
        assertThrows(KeyUnwrapException::class.java) { wrapper.unwrap(wrapped) }
        assertThrows(KeyUnwrapException::class.java) { AesGcmKeyWrapper { aesKey() }.unwrap(wrapper.wrap(secret)) }
        assertThrows(KeyUnwrapException::class.java) { wrapper.unwrap(byteArrayOf()) }
    }

    @Test
    fun databaseKeyIsCreatedOnceAndStoredOnlyWrapped() {
        val key = aesKey()
        val file = tempFile()
        val provider = DatabaseKeyProvider(file, AesGcmKeyWrapper { key })

        val first = provider.getOrCreate()
        assertEquals(32, first.size)
        assertArrayEquals(first, DatabaseKeyProvider(file, AesGcmKeyWrapper { key }).getOrCreate())

        val stored = file.readBytes()
        assertFalse(stored.toList().windowed(first.size).any { it == first.toList() })
    }

    @Test
    fun unreadableWrappedKeyIsNeverReplaced() {
        val file = tempFile()
        DatabaseKeyProvider(file, AesGcmKeyWrapper { aesKey() }).getOrCreate()
        val before = file.readBytes()

        assertThrows(KeyUnwrapException::class.java) {
            DatabaseKeyProvider(file, AesGcmKeyWrapper { aesKey() }).getOrCreate()
        }
        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun pseudonymsAreStableKeyedAndDomainSeparated() {
        val key = hmacKey()
        val pseudonymizer = Pseudonymizer { key }
        val pseudonym = pseudonymizer.pseudonym(IdentifierKind.ACCOUNT, "U00000001")

        assertEquals(64, pseudonym.length)
        assertFalse(pseudonym.contains("00000001"))
        assertEquals(pseudonym, pseudonymizer.pseudonym(IdentifierKind.ACCOUNT, " U00000001 "))
        assertNotEquals(pseudonym, pseudonymizer.pseudonym(IdentifierKind.TAX_ID, "U00000001"))
        assertNotEquals(pseudonym, Pseudonymizer { hmacKey() }.pseudonym(IdentifierKind.ACCOUNT, "U00000001"))
    }

    @Test
    fun maskingShowsOnlyLastFourCharacters() {
        assertEquals("•••••0001", Masking.lastFour("U00000001"))
        assertEquals("••••", Masking.lastFour("1234"))
        assertEquals("••••", Masking.lastFour(""))
        assertTrue(Masking.lastFour("Synthetic Test User").endsWith("User"))
    }
}
