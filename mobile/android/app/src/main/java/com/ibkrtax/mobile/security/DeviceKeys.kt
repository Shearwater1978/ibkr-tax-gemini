package com.ibkrtax.mobile.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/** Raised when Android Keystore cannot provide a key; callers must never fall back to plaintext keys. */
class KeystoreUnavailableException(cause: Throwable) : Exception("Android Keystore unavailable", cause)

/**
 * Non-exportable device keys held in Android Keystore (key-management spec).
 * StrongBox is used when the device has it, otherwise the TEE/software Keystore.
 */
object DeviceKeys {
    private const val PROVIDER = "AndroidKeyStore"
    const val DATABASE_WRAP_ALIAS = "ibkrtax.device.dbkey-wrap.v1"
    const val PSEUDONYM_ALIAS = "ibkrtax.device.pseudonym.v1"

    fun aesWrappingKey(alias: String = DATABASE_WRAP_ALIAS): SecretKey =
        getOrCreate(alias, KeyProperties.KEY_ALGORITHM_AES) { strongBox ->
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setIsStrongBoxBacked(strongBox)
                .build()
        }

    fun hmacKey(alias: String = PSEUDONYM_ALIAS): SecretKey =
        getOrCreate(alias, KeyProperties.KEY_ALGORITHM_HMAC_SHA256) { strongBox ->
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setIsStrongBoxBacked(strongBox)
                .build()
        }

    /** True when the key lives in secure hardware (TEE or StrongBox), false for software Keystore. */
    fun isHardwareBacked(key: SecretKey): Boolean =
        try {
            val info = SecretKeyFactory.getInstance(key.algorithm, PROVIDER).getKeySpec(key, KeyInfo::class.java) as KeyInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                info.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                    info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else {
                @Suppress("DEPRECATION")
                info.isInsideSecureHardware
            }
        } catch (e: GeneralSecurityException) {
            false
        }

    /** Removes a device key; used by erasure. Data protected by it becomes unreadable. */
    fun delete(alias: String) {
        try {
            keyStore().deleteEntry(alias)
        } catch (e: GeneralSecurityException) {
            throw KeystoreUnavailableException(e)
        }
    }

    private fun getOrCreate(alias: String, algorithm: String, spec: (strongBox: Boolean) -> KeyGenParameterSpec): SecretKey =
        try {
            val keyStore = keyStore()
            (keyStore.getKey(alias, null) as SecretKey?) ?: generate(algorithm, spec)
        } catch (e: GeneralSecurityException) {
            throw KeystoreUnavailableException(e)
        } catch (e: ProviderException) {
            throw KeystoreUnavailableException(e)
        }

    private fun generate(algorithm: String, spec: (strongBox: Boolean) -> KeyGenParameterSpec): SecretKey {
        val generator = KeyGenerator.getInstance(algorithm, PROVIDER)
        return try {
            generator.init(spec(true))
            generator.generateKey()
        } catch (e: StrongBoxUnavailableException) {
            generator.init(spec(false))
            generator.generateKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
}
