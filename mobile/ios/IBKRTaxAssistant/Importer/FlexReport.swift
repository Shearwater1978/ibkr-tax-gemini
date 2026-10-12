/// Instrument identity fields shared by every parsed record (see src/instrument_identity.py).
struct InstrumentIdentity: Equatable {
    var ticker: String
    var isin: String
    var conid = ""
    var instrumentDescription = ""
    /// Listing exchange from Financial Instrument Information (display only; not part of the identity).
    var listingExchange = ""
}

struct TradeRecord: Equatable {
    var identity: InstrumentIdentity
    var currency: String
    var date: String
    var quantity: ExactDecimal
    var price: ExactDecimal
    var commission: ExactDecimal
    /// BUY, SELL, TRANSFER or UNKNOWN.
    var type: String
    var source: String
    var sourceFile: String
}

struct CorporateActionRecord: Equatable {
    var identity: InstrumentIdentity
    var currency: String
    var date: String
    var quantity: ExactDecimal
    /// SPLIT, STOCK_DIV, MERGER or SPINOFF.
    var type: String
    var ratio: ExactDecimal?
    var source: String
    var sourceFile: String
}

/// A dividend or a withholding-tax entry.
struct CashRecord: Equatable {
    var identity: InstrumentIdentity
    var currency: String
    var date: String
    var amount: ExactDecimal
    var sourceFile: String
}

struct FlexReport: Equatable {
    var trades: [TradeRecord]
    var dividends: [CashRecord]
    var taxes: [CashRecord]
    var corporateActions: [CorporateActionRecord]
    /// Raw account number: a direct identifier, to be pseudonymized before storage or display.
    var accountId: String?
}

/// Import errors carry only codes, section names and line numbers, never report contents.
enum FlexImportError: Error, Equatable {
    case emptyFile
    case unsupportedFormat
    case malformed(MalformedReason, line: Int, section: String? = nil)
}

enum MalformedReason: Equatable {
    case notUtf8
    case unterminatedQuote
    case missingColumns
    case shortRow
    case invalidDate
    case invalidNumber
}
