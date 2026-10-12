import CryptoKit
import Foundation

/// One row of the `transactions` table, mirroring the Python `transactions` columns. Decimals stay exact text.
struct StoredTransaction: Equatable {
    var sourceKey: String
    var date: String
    var eventType: String
    var ticker: String
    var quantity: String
    var price: String
    var currency: String
    var amount: String
    var fee: String
    var description: String
    var splitRatio: String?
    var isin: String
    var conid: String
    var instrumentDescription: String
    var listingExchange = ""
}

enum InvalidRecordReason: Equatable {
    case missingDateTickerOrCurrency
    case unknownEventType
}

enum ImportPlanResult: Equatable {
    case ready(transactions: [StoredTransaction], duplicatesInReport: Int)
    case invalid(InvalidRecordReason)
}

/**
 * Port of the validation and de-duplication in Python `save_to_database` (through the Android
 * `ImportPlan`): any invalid record rejects the batch, and records with an already-seen source
 * key are skipped.
 */
enum ImportPlan {
    private static let separator = "\u{1f}"

    static func build(_ report: FlexReport) -> ImportPlanResult {
        let candidates = report.trades.map(fromTrade) + report.corporateActions.map(fromCorporateAction)
            + report.dividends.map { fromCash($0, type: "DIVIDEND", description: "Dividend") }
            + report.taxes.map { fromCash($0, type: "TAX", description: "Tax") }

        var seen = Set<String>()
        var unique: [StoredTransaction] = []
        for record in candidates {
            if record.date.isEmpty || record.ticker.isEmpty || record.currency.isEmpty { return .invalid(.missingDateTickerOrCurrency) }
            if record.eventType == "UNKNOWN" { return .invalid(.unknownEventType) }
            if seen.insert(record.sourceKey).inserted { unique.append(record) }
        }
        return .ready(transactions: unique, duplicatesInReport: candidates.count - unique.count)
    }

    private static func fromTrade(_ t: TradeRecord) -> StoredTransaction {
        StoredTransaction(
            sourceKey: sourceKey(t.date, t.type, t.identity.ticker, "\(t.quantity)", "\(t.price)", t.currency, "", "\(t.commission)", t.source, ""),
            date: t.date,
            eventType: t.type,
            ticker: t.identity.ticker,
            quantity: "\(t.quantity)",
            price: "\(t.price)",
            currency: t.currency,
            amount: "\(t.quantity * t.price)",
            fee: "\(t.commission)",
            description: t.source,
            splitRatio: nil,
            isin: t.identity.isin,
            conid: t.identity.conid,
            instrumentDescription: t.identity.instrumentDescription,
            listingExchange: t.identity.listingExchange
        )
    }

    private static func fromCorporateAction(_ c: CorporateActionRecord) -> StoredTransaction {
        let ratio = c.ratio.map { "\($0)" }
        return StoredTransaction(
            // Python keys the record's literal ratio, so a missing ratio is the string "None".
            sourceKey: sourceKey(c.date, c.type, c.identity.ticker, "\(c.quantity)", "0", c.currency, "", "0", c.source, ratio ?? "None"),
            date: c.date,
            eventType: c.type,
            ticker: c.identity.ticker,
            quantity: "\(c.quantity)",
            price: "0",
            currency: c.currency,
            amount: "0",
            fee: "0",
            description: c.source,
            splitRatio: ratio,
            isin: c.identity.isin,
            conid: c.identity.conid,
            instrumentDescription: c.identity.instrumentDescription,
            listingExchange: c.identity.listingExchange
        )
    }

    private static func fromCash(_ r: CashRecord, type: String, description: String) -> StoredTransaction {
        StoredTransaction(
            sourceKey: sourceKey(r.date, type, r.identity.ticker, "", "", r.currency, "\(r.amount)", "", "", ""),
            date: r.date,
            eventType: type,
            ticker: r.identity.ticker,
            quantity: "0",
            price: "0",
            currency: r.currency,
            amount: "\(r.amount)",
            fee: "0",
            description: description,
            splitRatio: nil,
            isin: r.identity.isin,
            conid: r.identity.conid,
            instrumentDescription: r.identity.instrumentDescription,
            listingExchange: r.identity.listingExchange
        )
    }

    /// Same field order and separator as Python `_source_key`.
    static func sourceKey(_ fields: String...) -> String {
        sha256Hex(Data(fields.joined(separator: separator).utf8))
    }

    static func sha256Hex(_ data: Data) -> String {
        SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
}
