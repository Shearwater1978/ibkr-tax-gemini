package com.ibkrtax.mobile.importer

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import java.util.Locale

/**
 * Field helpers ported from `src/parser.py`. Keep them behaviorally identical to
 * the Python reference; shared fixtures and IbkrTextTest pin the parity.
 */
internal object IbkrText {
    /** Python's default Decimal context: 28 significant digits, half-even. */
    private val PYTHON_DECIMAL = MathContext(28, RoundingMode.HALF_EVEN)

    private val DATE_FORMATS: List<DateTimeFormatter> = listOf(
        strict("uuuu-M-d"),
        strict("uuuuMMdd"),
        strict("M/d/uuuu"),
        strict("d/M/uuuu"),
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d-MMM-")
            // Python %y: 69-99 -> 19xx, 00-68 -> 20xx.
            .appendValueReduced(ChronoField.YEAR, 2, 2, 1969)
            .toFormatter(Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.STRICT),
    )

    private val LEADING_SYMBOL = Regex("""^([A-Za-z0-9.]+)\s*\(""")
    private val EMBEDDED_TICKER = Regex("""\(([A-Za-z0-9.]+),\s+[^,]+,\s+[A-Za-z0-9]{9,}\)""")
    private val ANY_ISIN = Regex("""\b[A-Z0-9.]+\s*\(([A-Z]{2}[A-Z0-9]{10})\)""", RegexOption.IGNORE_CASE)
    private val SPLIT_RATIO = Regex("""\bSPLIT\s+(\d+(?:\.\d+)?)\s+FOR\s+(\d+(?:\.\d+)?)\b""", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("""\s+""")

    private val TRANSFER_KEYWORDS = listOf(
        "ACATS",
        "TRANSFER",
        "INTERNAL",
        "POSITION MOVEM",
        "RECEIVE DELIVER",
        "CASH IN LIEU",
    )

    private fun strict(pattern: String) =
        DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)

    /** Python `parse_decimal`, but returns null instead of 0 for unparseable input. */
    fun parseDecimalOrNull(value: String): BigDecimal? {
        val clean = value.replace(",", "").replace("\"", "").trim()
        if (clean.isEmpty()) return BigDecimal.ZERO
        return try {
            BigDecimal(clean)
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Converts IBKR dates (optionally with ", time") to ISO `YYYY-MM-DD`, or null. */
    fun normalizeDate(value: String): String? {
        if (value.isEmpty()) return null
        val clean = value.split(",")[0].trim().split(" ")[0]
        for (format in DATE_FORMATS) {
            try {
                return LocalDate.parse(clean, format).toString()
            } catch (e: DateTimeParseException) {
                continue
            }
        }
        return null
    }

    fun extractTicker(description: String, symbolColumn: String, quantity: BigDecimal): String {
        val sign = quantity.signum()
        if (sign < 0) {
            firstSymbol(symbolColumn)?.let { return it }
            LEADING_SYMBOL.find(description)?.let { return it.groupValues[1].trim() }
        }
        if (sign > 0) {
            EMBEDDED_TICKER.find(description)?.let { return it.groupValues[1].trim() }
        }
        firstSymbol(symbolColumn)?.let { return it }
        LEADING_SYMBOL.find(description)?.let { return it.groupValues[1].trim() }

        val candidate = description.trim().split(WHITESPACE).firstOrNull().orEmpty()
        if (candidate.isNotEmpty() && isPythonUpper(candidate) && candidate.length < 12) return candidate
        return "UNKNOWN"
    }

    fun extractIsin(description: String, ticker: String = ""): String {
        if (ticker.isNotEmpty()) {
            val escaped = Regex.escape(ticker.trim())
            Regex("""\(\s*$escaped\s*,\s*[^,]+,\s*([A-Z]{2}[A-Z0-9]{10})\s*\)""", RegexOption.IGNORE_CASE)
                .find(description)?.let { return it.groupValues[1].uppercase() }
            Regex("""\b$escaped\s*\(([A-Z]{2}[A-Z0-9]{10})\)""", RegexOption.IGNORE_CASE)
                .find(description)?.let { return it.groupValues[1].uppercase() }
        }
        return ANY_ISIN.find(description)?.groupValues?.get(1)?.uppercase().orEmpty()
    }

    fun classifyTradeType(description: String, quantity: BigDecimal): String {
        val upper = description.uppercase()
        return when {
            TRANSFER_KEYWORDS.any { it in upper } -> "TRANSFER"
            quantity.signum() > 0 -> "BUY"
            quantity.signum() < 0 -> "SELL"
            else -> "UNKNOWN"
        }
    }

    fun classifyCorporateAction(description: String, quantity: BigDecimal): String = when {
        "SPINOFF" in description.uppercase() -> "SPINOFF"
        quantity.signum() > 0 -> "STOCK_DIV"
        quantity.signum() < 0 -> "MERGER"
        else -> "CORP_ACTION_INFO"
    }

    fun extractSplitRatio(description: String): BigDecimal? {
        val match = SPLIT_RATIO.find(description) ?: return null
        val denominator = BigDecimal(match.groupValues[2])
        if (denominator.signum() == 0) return null
        return BigDecimal(match.groupValues[1]).divide(denominator, PYTHON_DECIMAL)
    }

    /** Python `str(Decimal)` uses the same to-scientific-string rules as `BigDecimal.toString`. */
    fun format(value: BigDecimal): String = value.toString()

    private fun firstSymbol(symbolColumn: String): String? {
        if (symbolColumn.isBlank()) return null
        return symbolColumn.trim().split(",")[0].trim().split(WHITESPACE).firstOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun isPythonUpper(value: String): Boolean =
        value.any { it.isUpperCase() } && value.none { it.isLowerCase() }
}
