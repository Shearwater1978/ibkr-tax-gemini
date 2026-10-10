package com.ibkrtax.mobile.security

/**
 * IBKR names exports after the account (e.g. "U1234567_2023.csv"); file names shown on
 * screen keep only the last four digits of account-like tokens (data-anonymization spec).
 */
object FileNameMasking {
    // One or two capital letters followed by 6+ digits, not inside a longer word or number.
    private val ACCOUNT = Regex("""(?<![A-Za-z0-9])([A-Z]{1,2})(\d{2,})(\d{4})(?!\d)""")

    fun mask(fileName: String): String = ACCOUNT.replace(fileName) { match ->
        val (prefix, hidden, last) = match.destructured
        prefix + "•".repeat(hidden.length) + last
    }
}
