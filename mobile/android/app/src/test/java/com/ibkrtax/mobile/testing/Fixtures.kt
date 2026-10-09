package com.ibkrtax.mobile.testing

/** Loads shared synthetic fixtures from mobile/fixtures (added as test resources). */
object Fixtures {
    const val VALID_BASIC = "valid_basic.csv"
    const val VALID_FOLLOWUP = "valid_followup.csv"
    const val UNSUPPORTED_FORMAT = "unsupported_format.csv"
    const val MALFORMED_TRADES = "malformed_trades.csv"
    const val EMPTY = "empty.csv"

    val ALL = listOf(VALID_BASIC, VALID_FOLLOWUP, UNSUPPORTED_FORMAT, MALFORMED_TRADES, EMPTY)

    fun flexQuery(name: String): ByteArray = read("flex-query/$name")

    /** Records the Python reference parser produces for a valid fixture. */
    fun expectedJson(fixtureName: String): String =
        read("flex-query/expected/${fixtureName.removeSuffix(".csv")}.json").decodeToString()

    private fun read(path: String): ByteArray {
        val stream = Fixtures::class.java.classLoader?.getResourceAsStream(path)
            ?: error("Missing test fixture: $path")
        return stream.use { it.readBytes() }
    }
}
