package com.ibkrtax.mobile.security

import javax.crypto.Mac
import javax.crypto.SecretKey

/** Kinds of direct identifiers; the kind is mixed into the HMAC input for domain separation. */
enum class IdentifierKind {
    ACCOUNT,
    NAME,
    TAX_ID,
    SOURCE_FILE,
}

/**
 * HMAC-SHA256 pseudonyms keyed by a non-exportable device key (data-anonymization
 * spec). Without that key a pseudonym cannot be linked back to its identifier.
 */
class Pseudonymizer(private val keyProvider: () -> SecretKey) {
    fun pseudonym(kind: IdentifierKind, identifier: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(keyProvider())
        val digest = mac.doFinal("${kind.name}:${identifier.trim()}".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val ALGORITHM = "HmacSHA256"
    }
}

/** UI masking: only the last four characters stay visible. */
object Masking {
    private const val VISIBLE = 4
    private const val MASK = '•'

    fun lastFour(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length <= VISIBLE) return MASK.toString().repeat(VISIBLE)
        return MASK.toString().repeat(trimmed.length - VISIBLE) + trimmed.takeLast(VISIBLE)
    }
}
