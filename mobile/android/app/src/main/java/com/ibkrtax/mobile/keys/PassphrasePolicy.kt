package com.ibkrtax.mobile.keys

enum class PassphraseProblem {
    TOO_SHORT,

    /** One repeated character or a plain ascending/descending sequence. */
    TOO_SIMPLE,
}

/** Backup passphrase rules (key-management spec "Weak passphrase"). */
object PassphrasePolicy {
    const val MIN_LENGTH = 12

    fun check(passphrase: CharArray): PassphraseProblem? {
        if (passphrase.size < MIN_LENGTH) return PassphraseProblem.TOO_SHORT
        if (passphrase.all { it == passphrase[0] }) return PassphraseProblem.TOO_SIMPLE
        val steps = (1 until passphrase.size).map { step(passphrase[it - 1], passphrase[it]) }.toSet()
        if (steps == setOf(1) || steps == setOf(-1)) return PassphraseProblem.TOO_SIMPLE
        return null
    }

    /** Digits wrap around, so "1234567890" and "...8901" count as one sequence. */
    private fun step(from: Char, to: Char): Int = when {
        from == '9' && to == '0' -> 1
        from == '0' && to == '9' -> -1
        else -> to.code - from.code
    }
}
