package com.ibkrtax.mobile.security

import java.io.File
import java.security.SecureRandom

/**
 * Supplies the SQLCipher key. A random 256-bit key is generated once and stored
 * only in wrapped form; the plaintext key exists in memory only.
 */
class DatabaseKeyProvider(
    private val wrappedKeyFile: File,
    private val wrapper: KeyWrapper,
    private val random: SecureRandom = SecureRandom(),
) {
    /**
     * Returns the database key, creating it on first use. Throws [KeyUnwrapException]
     * rather than replacing an unreadable key, so existing data is never silently orphaned.
     */
    @Synchronized
    fun getOrCreate(): ByteArray {
        if (wrappedKeyFile.exists()) return wrapper.unwrap(wrappedKeyFile.readBytes())

        val key = ByteArray(KEY_BYTES).also(random::nextBytes)
        val wrapped = wrapper.wrap(key)
        wrappedKeyFile.parentFile?.mkdirs()
        val temp = File(wrappedKeyFile.path + ".tmp")
        temp.writeBytes(wrapped)
        check(temp.renameTo(wrappedKeyFile)) { "Could not store wrapped database key" }
        return key
    }

    private companion object {
        const val KEY_BYTES = 32
    }
}
