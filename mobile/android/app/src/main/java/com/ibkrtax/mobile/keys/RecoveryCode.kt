package com.ibkrtax.mobile.keys

import java.security.SecureRandom

/**
 * 26 Crockford base32 characters (130 random bits), shown as six groups for writing
 * down. Input is forgiving: case, spaces and dashes are ignored, and the look-alikes
 * O, I and L are read as 0, 1 and 1.
 */
object RecoveryCode {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val LENGTH = 26
    private val GROUPS = listOf(4, 4, 4, 4, 5, 5)

    fun generate(random: SecureRandom = SecureRandom()): String =
        String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

    /** Groups for display, e.g. "K7QM-2HXP-…". */
    fun format(code: String): String {
        var start = 0
        return GROUPS.joinToString("-") { size -> code.substring(start, start + size).also { start += size } }
    }

    /** Canonical form of what the user typed, or null when it cannot be a recovery code. */
    fun normalize(input: String): String? {
        val cleaned = input.uppercase().filterNot { it == '-' || it.isWhitespace() }
            .map {
                when (it) {
                    'O' -> '0'
                    'I', 'L' -> '1'
                    else -> it
                }
            }
            .joinToString("")
        return cleaned.takeIf { it.length == LENGTH && it.all(ALPHABET::contains) }
    }
}
