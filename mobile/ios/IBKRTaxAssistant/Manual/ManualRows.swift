import Foundation

/// What the user can enter by hand (mobile-manual-operations); `fields` are the form fields each type needs. Android `ManualType`.
enum ManualType: String, CaseIterable {
    case buy = "BUY"
    case sell = "SELL"
    case transferIn = "TRANSFER_IN"
    case transferOut = "TRANSFER_OUT"
    case dividend = "DIVIDEND"
    case tax = "TAX"
    /// `heldBefore` is not typed: the app fills in the shares held before the split.
    case split = "SPLIT"
    case stockDividend = "STOCK_DIV"
    case merger = "MERGER"
    case spinoff = "SPINOFF"

    var fields: [String] {
        switch self {
        case .buy, .sell: return ["date", "ticker", "currency", "quantity", "price", "fee"]
        case .transferIn: return ["date", "ticker", "currency", "quantity", "price"]
        case .transferOut, .stockDividend: return ["date", "ticker", "currency", "quantity"]
        case .dividend: return ["date", "ticker", "currency", "amount", "tax"]
        case .tax: return ["date", "ticker", "currency", "amount"]
        case .split: return ["date", "ticker", "currency", "newShares", "oldShares", "heldBefore"]
        case .merger: return ["date", "ticker", "currency", "quantity", "newTicker", "newQuantity"]
        case .spinoff: return ["date", "ticker", "currency", "quantity", "fromTicker"]
        }
    }
}

/// Identity fields copied from imported operations of the same ticker and currency (Decision 3).
struct InstrumentInfo: Equatable {
    var isin = ""
    var conid = ""
    var instrumentDescription = ""
    var listingExchange = ""

    static let unknown = InstrumentInfo()
}

/**
 * Builds the `transactions` rows of a manual entry (Decision 2) with the importer's conventions, so FIFO, holdings
 * and dividends treat them like imported rows. `fields` must already pass `ManualValidation`. Port of the Android
 * `ManualRows`; reproduces `mobile/fixtures/manual/journal_v1.json`.
 */
enum ManualRows {
    static let description = "Manual"

    static func build(
        entryId: String,
        type: ManualType,
        fields: [String: String],
        instrument: (_ ticker: String, _ currency: String) -> InstrumentInfo = { _, _ in .unknown }
    ) -> [StoredTransaction] {
        let key = "manual:\(entryId)"
        let date = fields["date"] ?? ""
        let ticker = fields["ticker"] ?? ""
        let currency = fields["currency"] ?? ""
        func d(_ name: String) -> ExactDecimal { ExactDecimal(fields[name] ?? "") ?? .zero }
        func row(_ sourceKey: String, _ eventType: String, quantity: ExactDecimal = .zero, price: ExactDecimal = .zero, amount: ExactDecimal = .zero,
                 fee: ExactDecimal = .zero, description: String = Self.description, splitRatio: ExactDecimal? = nil, rowTicker: String? = nil) -> StoredTransaction {
            let symbol = rowTicker ?? ticker
            let info = instrument(symbol, currency)
            return StoredTransaction(
                sourceKey: sourceKey, date: date, eventType: eventType, ticker: symbol, quantity: quantity.description, price: price.description,
                currency: currency, amount: amount.description, fee: fee.description, description: description, splitRatio: splitRatio?.description,
                isin: info.isin, conid: info.conid, instrumentDescription: info.instrumentDescription, listingExchange: info.listingExchange
            )
        }

        switch type {
        case .buy, .sell:
            let quantity = type == .buy ? d("quantity") : d("quantity").negated
            return [row(key, type.rawValue, quantity: quantity, price: d("price"), amount: quantity.multiplied(by: d("price")), fee: d("fee").negated)]
        case .transferIn:
            return [row(key, "TRANSFER", quantity: d("quantity"), price: d("price"), amount: d("quantity").multiplied(by: d("price")))]
        case .transferOut:
            return [row(key, "TRANSFER", quantity: d("quantity").negated)]
        case .dividend:
            let tax = d("tax")
            return [row(key, "DIVIDEND", amount: d("amount"))] + (tax.signum != 0 ? [row("\(key):tax", "TAX", amount: tax.negated)] : [])
        case .tax:
            return [row(key, "TAX", amount: d("amount"))]
        case .split:
            let ratio = d("newShares").divided(by: d("oldShares")) ?? .one
            let held = d("heldBefore")
            // IBKR's positive split row: shares added (forward) or the new share count (reverse).
            let quantity = ratio.compare(.one) > 0 ? held.multiplied(by: ratio).subtracting(held) : held.multiplied(by: ratio)
            return [row(key, "SPLIT", quantity: quantity, splitRatio: ratio)]
        case .stockDividend:
            return [row(key, "STOCK_DIV", quantity: d("quantity"))]
        case .merger:
            // FIFO links both rows through the date and this description stem, and carries cost over.
            let stem = "Manual corporate action \(entryId)"
            let newTicker = fields["newTicker"] ?? ""
            return [
                row(key, "MERGER", quantity: d("quantity").negated, description: "\(stem) (\(ticker))"),
                row("\(key):new", "STOCK_DIV", quantity: d("newQuantity"), description: "\(stem) (\(newTicker))", rowTicker: newTicker),
            ]
        case .spinoff:
            return [row(key, "SPINOFF", quantity: d("quantity"), description: "Manual spin-off from \(fields["fromTicker"] ?? "")")]
        }
    }
}
