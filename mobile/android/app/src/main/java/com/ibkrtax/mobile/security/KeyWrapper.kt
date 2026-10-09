package com.ibkrtax.mobile.security

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts small secrets (such as the database key) under a device key. */
interface KeyWrapper {
    fun wrap(secret: ByteArray): ByteArray

    fun unwrap(wrapped: ByteArray): ByteArray
}

class KeyUnwrapException(cause: Throwable? = null) : Exception("Wrapped key cannot be decrypted", cause)

/**
 * AES-256-GCM with a Keystore key. Blob layout: version byte, IV length byte, IV,
 * ciphertext+tag. The version is authenticated as associated data.
 */
class AesGcmKeyWrapper(private val keyProvider: () -> SecretKey) : KeyWrapper {
    override fun wrap(secret: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        cipher.updateAAD(AAD)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(secret)
        return ByteBuffer.allocate(2 + iv.size + ciphertext.size)
            .put(VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        try {
            val buffer = ByteBuffer.wrap(wrapped)
            if (buffer.remaining() < 2 || buffer.get() != VERSION) throw KeyUnwrapException()
            val iv = ByteArray(buffer.get().toInt()).also(buffer::get)
            val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(AAD)
            return cipher.doFinal(ciphertext)
        } catch (e: GeneralSecurityException) {
            throw KeyUnwrapException(e)
        } catch (e: RuntimeException) {
            if (e is KeyUnwrapException) throw e
            throw KeyUnwrapException(e)
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val VERSION: Byte = 1
        val AAD = "ibkrtax.wrapped-key.v1".toByteArray()
    }
}
