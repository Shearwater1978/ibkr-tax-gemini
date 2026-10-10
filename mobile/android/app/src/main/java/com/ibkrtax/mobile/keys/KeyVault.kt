package com.ibkrtax.mobile.keys

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.json.JSONObject

/** Argon2id settings; stored with each passphrase wrap so they can change later. */
data class Argon2idParams(val memoryKiB: Int, val iterations: Int, val parallelism: Int) {
    companion object {
        /** Design Decision 2: 64 MiB, 3 iterations, parallelism 1. */
        val DEFAULT = Argon2idParams(memoryKiB = 64 * 1024, iterations = 3, parallelism = 1)
    }
}

/**
 * The backup manifest's key section: the data key (DK) wrapped by the passphrase KEK and,
 * independently, by the recovery-code KEK, plus a key check value. It holds no secret in
 * plaintext (key-management spec "Manifest inspection").
 */
data class KeyManifest(
    val passphraseSalt: ByteArray,
    val argon2: Argon2idParams,
    val passphraseWrappedKey: ByteArray,
    val recoverySalt: ByteArray,
    val recoveryWrappedKey: ByteArray,
    val keyCheck: ByteArray,
) {
    fun toJson(): String = JSONObject()
        .put("version", VERSION)
        .put(
            "passphrase",
            JSONObject()
                .put("kdf", "argon2id")
                .put("memoryKiB", argon2.memoryKiB)
                .put("iterations", argon2.iterations)
                .put("parallelism", argon2.parallelism)
                .put("salt", b64(passphraseSalt))
                .put("wrappedKey", b64(passphraseWrappedKey)),
        )
        .put("recovery", JSONObject().put("kdf", "hkdf-sha256").put("salt", b64(recoverySalt)).put("wrappedKey", b64(recoveryWrappedKey)))
        .put("keyCheck", b64(keyCheck))
        .toString()

    override fun equals(other: Any?): Boolean = other is KeyManifest && toJson() == other.toJson()

    override fun hashCode(): Int = toJson().hashCode()

    companion object {
        const val VERSION = 1

        /** Throws [UnsupportedManifestException] for a newer format (encrypted-backup spec "Unknown format version"). */
        fun fromJson(json: String): KeyManifest {
            val root = JSONObject(json)
            if (root.getInt("version") != VERSION) throw UnsupportedManifestException(root.getInt("version"))
            val passphrase = root.getJSONObject("passphrase")
            val recovery = root.getJSONObject("recovery")
            return KeyManifest(
                passphraseSalt = unb64(passphrase.getString("salt")),
                argon2 = Argon2idParams(passphrase.getInt("memoryKiB"), passphrase.getInt("iterations"), passphrase.getInt("parallelism")),
                passphraseWrappedKey = unb64(passphrase.getString("wrappedKey")),
                recoverySalt = unb64(recovery.getString("salt")),
                recoveryWrappedKey = unb64(recovery.getString("wrappedKey")),
                keyCheck = unb64(root.getString("keyCheck")),
            )
        }

        private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

        private fun unb64(text: String) = Base64.getDecoder().decode(text)
    }
}

class UnsupportedManifestException(val version: Int) : Exception("Backup manifest version $version needs a newer app")

/** The secret did not unwrap the data key: wrong passphrase or recovery code, or a damaged manifest. */
class WrongSecretException : Exception("Wrong passphrase or recovery code")

/** Result of first-time setup: show [recoveryCode] once, keep [dataKey] on the device, store [manifest] with backups. */
class KeySetup(val manifest: KeyManifest, val recoveryCode: String, val dataKey: ByteArray)

/**
 * Data key management (mobile-encrypted-drive-backup Decision 2): a random 256-bit DK,
 * wrapped with AES-256-GCM under a passphrase KEK (Argon2id) and a recovery-code KEK
 * (HKDF-SHA256). Changing the passphrase re-wraps DK only.
 */
class KeyVault(
    private val argon2: Argon2idParams = Argon2idParams.DEFAULT,
    private val random: SecureRandom = SecureRandom(),
) {
    fun setUp(passphrase: CharArray): KeySetup {
        require(PassphrasePolicy.check(passphrase) == null) { "Passphrase does not meet the policy" }
        val dataKey = randomBytes(KEY_BYTES)
        val recoveryCode = RecoveryCode.generate(random)
        val recoverySalt = randomBytes(SALT_BYTES)
        val manifest = passphraseWrap(dataKey, passphrase).let { (salt, wrapped) ->
            KeyManifest(
                passphraseSalt = salt,
                argon2 = argon2,
                passphraseWrappedKey = wrapped,
                recoverySalt = recoverySalt,
                recoveryWrappedKey = wrap(recoveryKek(recoveryCode, recoverySalt), dataKey, PURPOSE_RECOVERY),
                keyCheck = keyCheck(dataKey),
            )
        }
        return KeySetup(manifest, recoveryCode, dataKey)
    }

    fun unlockWithPassphrase(manifest: KeyManifest, passphrase: CharArray): ByteArray =
        unwrap(argon2Kek(passphrase, manifest.passphraseSalt, manifest.argon2), manifest.passphraseWrappedKey, PURPOSE_PASSPHRASE)
            .also { verify(manifest, it) }

    /** Throws [WrongSecretException] for a malformed or wrong code. */
    fun unlockWithRecoveryCode(manifest: KeyManifest, code: String): ByteArray {
        val normalized = RecoveryCode.normalize(code) ?: throw WrongSecretException()
        return unwrap(recoveryKek(normalized, manifest.recoverySalt), manifest.recoveryWrappedKey, PURPOSE_RECOVERY)
            .also { verify(manifest, it) }
    }

    /** New salt and wrap for the passphrase; the recovery wrap and DK stay the same. */
    fun changePassphrase(manifest: KeyManifest, dataKey: ByteArray, newPassphrase: CharArray): KeyManifest {
        require(PassphrasePolicy.check(newPassphrase) == null) { "Passphrase does not meet the policy" }
        verify(manifest, dataKey)
        val (salt, wrapped) = passphraseWrap(dataKey, newPassphrase)
        return manifest.copy(passphraseSalt = salt, argon2 = argon2, passphraseWrappedKey = wrapped)
    }

    /** True when [dataKey] belongs to [manifest], e.g. a key retrieved from Block Store. */
    fun matches(manifest: KeyManifest, dataKey: ByteArray): Boolean =
        MessageDigest.isEqual(keyCheck(dataKey), manifest.keyCheck)

    private fun verify(manifest: KeyManifest, dataKey: ByteArray) {
        if (!matches(manifest, dataKey)) throw WrongSecretException()
    }

    private fun passphraseWrap(dataKey: ByteArray, passphrase: CharArray): Pair<ByteArray, ByteArray> {
        val salt = randomBytes(SALT_BYTES)
        return salt to wrap(argon2Kek(passphrase, salt, argon2), dataKey, PURPOSE_PASSPHRASE)
    }

    private fun argon2Kek(passphrase: CharArray, salt: ByteArray, params: Argon2idParams): ByteArray {
        val generator = Argon2BytesGenerator()
        generator.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(params.memoryKiB)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .withSalt(salt)
                .build(),
        )
        return ByteArray(KEY_BYTES).also { generator.generateBytes(passphrase, it) }
    }

    private fun recoveryKek(code: String, salt: ByteArray): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(code.toByteArray(Charsets.US_ASCII), salt, RECOVERY_INFO))
        return ByteArray(KEY_BYTES).also { hkdf.generateBytes(it, 0, it.size) }
    }

    private fun wrap(kek: ByteArray, dataKey: ByteArray, purpose: String): ByteArray {
        val nonce = randomBytes(NONCE_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad(purpose))
        val sealed = cipher.doFinal(dataKey)
        return ByteBuffer.allocate(nonce.size + sealed.size).put(nonce).put(sealed).array()
    }

    private fun unwrap(kek: ByteArray, wrapped: ByteArray, purpose: String): ByteArray =
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_BITS, wrapped, 0, NONCE_BYTES))
            cipher.updateAAD(aad(purpose))
            cipher.doFinal(wrapped, NONCE_BYTES, wrapped.size - NONCE_BYTES)
        } catch (e: GeneralSecurityException) {
            throw WrongSecretException()
        } catch (e: IllegalArgumentException) {
            throw WrongSecretException()
        }

    private fun keyCheck(dataKey: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(dataKey, "HmacSHA256"))
        return mac.doFinal(KEY_CHECK_LABEL).copyOf(KEY_CHECK_BYTES)
    }

    private fun aad(purpose: String) = "ibkrtax.dk.v${KeyManifest.VERSION}:$purpose".toByteArray(Charsets.US_ASCII)

    private fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)

    private companion object {
        const val KEY_BYTES = 32
        const val SALT_BYTES = 16
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_CHECK_BYTES = 16
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PURPOSE_PASSPHRASE = "passphrase"
        const val PURPOSE_RECOVERY = "recovery"
        val RECOVERY_INFO = "ibkrtax recovery kek v1".toByteArray(Charsets.US_ASCII)
        val KEY_CHECK_LABEL = "ibkrtax key check v1".toByteArray(Charsets.US_ASCII)
    }
}
