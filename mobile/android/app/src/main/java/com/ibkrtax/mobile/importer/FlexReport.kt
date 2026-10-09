package com.ibkrtax.mobile.importer

import java.math.BigDecimal

/** Instrument identity fields shared by every parsed record (see src/instrument_identity.py). */
data class InstrumentIdentity(
    val ticker: String,
    val isin: String,
    val conid: String = "",
    val instrumentDescription: String = "",
)

data class TradeRecord(
    val identity: InstrumentIdentity,
    val currency: String,
    val date: String,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val commission: BigDecimal,
    /** BUY, SELL, TRANSFER or UNKNOWN. */
    val type: String,
    val source: String,
    val sourceFile: String,
)

data class CorporateActionRecord(
    val identity: InstrumentIdentity,
    val currency: String,
    val date: String,
    val quantity: BigDecimal,
    /** SPLIT, STOCK_DIV, MERGER or SPINOFF. */
    val type: String,
    val ratio: BigDecimal?,
    val source: String,
    val sourceFile: String,
)

/** A dividend or a withholding-tax entry. */
data class CashRecord(
    val identity: InstrumentIdentity,
    val currency: String,
    val date: String,
    val amount: BigDecimal,
    val sourceFile: String,
)

data class FlexReport(
    val trades: List<TradeRecord>,
    val dividends: List<CashRecord>,
    val taxes: List<CashRecord>,
    val corporateActions: List<CorporateActionRecord>,
)

sealed interface FlexParseResult {
    data class Success(val report: FlexReport) : FlexParseResult

    data class Failure(val error: FlexImportError) : FlexParseResult
}

/** Import errors carry only codes, section names and line numbers, never report contents. */
sealed interface FlexImportError {
    data object EmptyFile : FlexImportError

    data object UnsupportedFormat : FlexImportError

    data class Malformed(val reason: MalformedReason, val line: Int, val section: String? = null) : FlexImportError
}

enum class MalformedReason {
    NOT_UTF8,
    UNTERMINATED_QUOTE,
    MISSING_COLUMNS,
    SHORT_ROW,
    INVALID_DATE,
    INVALID_NUMBER,
}
