package com.ibkrtax.mobile.backup

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class UnsupportedBackupVersionException(val version: Int) : Exception("Backup file version $version needs a newer app")

/** The file is not a backup, was damaged, or was encrypted with another data key. */
class CorruptBackupException : Exception("Backup file cannot be decrypted")

/**
 * Encrypted backup file (encrypted-backup spec "Versioned encrypted file format"):
 * magic "IBKB", format version, random 16-byte file id, 12-byte nonce, then AES-256-GCM
 * ciphertext. The whole header is authenticated, so a changed version or id fails to decrypt.
 */
object BackupFileFormat {
    const val VERSION = 1
    private val MAGIC = byteArrayOf('I'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), 'B'.code.toByte())
    private const val FILE_ID_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val HEADER_BYTES = 4 + 1 + FILE_ID_BYTES + NONCE_BYTES

    fun encrypt(dataKey: ByteArray, plaintext: ByteArray, random: SecureRandom = SecureRandom()): ByteArray {
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC)
            .put(VERSION.toByte())
            .put(ByteArray(FILE_ID_BYTES).also(random::nextBytes))
            .put(ByteArray(NONCE_BYTES).also(random::nextBytes))
            .array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dataKey, "AES"), GCMParameterSpec(TAG_BITS, header, HEADER_BYTES - NONCE_BYTES, NONCE_BYTES))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plaintext)
    }

    fun decrypt(dataKey: ByteArray, file: ByteArray): ByteArray {
        if (file.size < HEADER_BYTES || !file.copyOfRange(0, 4).contentEquals(MAGIC)) throw CorruptBackupException()
        val version = file[4].toInt()
        if (version != VERSION) throw UnsupportedBackupVersionException(version)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dataKey, "AES"), GCMParameterSpec(TAG_BITS, file, HEADER_BYTES - NONCE_BYTES, NONCE_BYTES))
            cipher.updateAAD(file, 0, HEADER_BYTES)
            cipher.doFinal(file, HEADER_BYTES, file.size - HEADER_BYTES)
        } catch (e: GeneralSecurityException) {
            throw CorruptBackupException()
        }
    }
}
