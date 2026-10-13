import Foundation

/// Why a manual entry cannot be saved as entered; `field` names the form field at fault. Android `ManualProblem`.
enum ManualProblem: Equatable {
    case missing(String)
    /// Not a plain decimal, an ISO date, a ticker or a currency code.
    case invalid(String)
    /// Must be greater than zero (quantities, prices, share counts) or, for fees and tax, not negative.
    case outOfRange(String)
    case futureDate

    var field: String {
        switch self {
        case .missing(let field), .invalid(let field), .outOfRange(let field): return field
        case .futureDate: return "date"
        }
    }
}

/// Field checks of Decision 4. Numbers must be plain decimal strings; the form converts what the user typed.
enum ManualValidation {
    private enum Rule { case positive, notNegative, nonZero, wholePositive }

    private static func rules(_ type: ManualType) -> [String: Rule] {
        switch type {
        case .buy, .sell: return ["quantity": .positive, "price": .positive, "fee": .notNegative]
        case .transferIn: return ["quantity": .positive, "price": .positive]
        case .transferOut, .stockDividend, .spinoff: return ["quantity": .positive]
        case .dividend: return ["amount": .positive, "tax": .notNegative]
        case .tax: return ["amount": .nonZero]
        case .split: return ["newShares": .wholePositive, "oldShares": .wholePositive, "heldBefore": .notNegative]
        case .merger: return ["quantity": .positive, "newQuantity": .positive]
        }
    }

    static func check(_ type: ManualType, _ fields: [String: String], today: CalendarDay) -> [ManualProblem] {
        var problems: [ManualProblem] = []
        for name in type.fields {
            let value = (fields[name] ?? "").trimmingCharacters(in: .whitespaces)
            if value.isEmpty {
                problems.append(.missing(name))
                continue
            }
            switch name {
            case "date":
                if let day = CalendarDay(iso: value), value.count == 10 {
                    if day > today { problems.append(.futureDate) }
                } else {
                    problems.append(.invalid(name))
                }
            case "ticker", "newTicker", "fromTicker":
                if !matches(value, #"[A-Z0-9][A-Z0-9.]{0,11}"#) { problems.append(.invalid(name)) }
            case "currency":
                if !matches(value, "[A-Z]{3}") { problems.append(.invalid(name)) }
            default:
                if let problem = number(name, value, rules(type)[name]) { problems.append(problem) }
            }
        }
        if type == .dividend, problems.isEmpty, let tax = ExactDecimal(fields["tax"] ?? ""), let amount = ExactDecimal(fields["amount"] ?? ""), tax.compare(amount) > 0 {
            problems.append(.outOfRange("tax"))
        }
        if type == .merger, !problems.contains(where: { $0.field == "newTicker" }), fields["newTicker"]?.trimmingCharacters(in: .whitespaces) == fields["ticker"]?.trimmingCharacters(in: .whitespaces) {
            problems.append(.invalid("newTicker"))
        }
        return problems
    }

    private static func number(_ name: String, _ value: String, _ rule: Rule?) -> ManualProblem? {
        guard matches(value, #"-?\d+(\.\d+)?"#), let number = ExactDecimal(value) else { return .invalid(name) }
        let ok: Bool
        switch rule {
        case .positive?: ok = number.signum > 0
        case .notNegative?: ok = number.signum >= 0
        case .nonZero?: ok = number.signum != 0
        case .wholePositive?: ok = matches(value, #"\d+"#) && number.signum > 0
        case nil: ok = true
        }
        return ok ? nil : .outOfRange(name)
    }

    private static func matches(_ value: String, _ pattern: String) -> Bool {
        value.range(of: "^\(pattern)$", options: .regularExpression) != nil
    }
}

/// A change would sell or move out more `ticker` shares on `date` than the `held` ones (Decision 4).
struct Oversell: Equatable {
    var ticker: String
    var date: String
    var held: ExactDecimal
}

/// FIFO-based checks for manual entries; rows become FIFO events the way the portfolio reads them.
enum ManualFifo {
    static func events(_ rows: [StoredTransaction]) -> [PortfolioEvent] {
        rows.enumerated().map { index, row in
            PortfolioEvent(order: Int64(index), date: row.date, eventType: row.eventType, ticker: row.ticker, quantity: ExactDecimal(row.quantity) ?? .zero,
                           price: ExactDecimal(row.price) ?? .zero, currency: row.currency, isin: row.isin, description: row.description,
                           splitRatio: row.splitRatio.flatMap { ExactDecimal($0) })
        }
    }

    /// Nil when `after` matches every removal, or fails exactly as `before` already did, so incomplete data does not block unrelated entries.
    static func oversell(before: [PortfolioEvent], after: [PortfolioEvent]) -> Oversell? {
        let result = FifoInventory.openLots(after)
        guard case .unmatchedSell(let ticker, let date) = result, FifoInventory.openLots(before) != result else { return nil }
        return Oversell(ticker: ticker, date: date, held: heldBefore(after, ticker: ticker, date: date))
    }

    /// Shares of `ticker` held on `date` before that day's removals: earlier events plus that day's additions.
    static func heldBefore(_ events: [PortfolioEvent], ticker: String, date: String) -> ExactDecimal {
        let earlier = events.filter { $0.date < date || ($0.date == date && $0.quantity.signum > 0) }
        guard case .success(let lots, _) = FifoInventory.openLots(earlier) else { return .zero }
        return lots.filter { $0.ticker == ticker }.reduce(ExactDecimal.zero) { $0.adding($1.quantity) }
    }
}

/// Possible duplicates between manual and other operations (Decision 5); rows are never merged.
enum ManualDuplicates {
    static func matches(_ a: StoredTransaction, _ b: StoredTransaction) -> Bool {
        guard a.date == b.date, a.eventType == b.eventType, a.ticker == b.ticker, a.currency == b.currency else { return false }
        let cash = a.eventType == "DIVIDEND" || a.eventType == "TAX"
        let x = ExactDecimal(cash ? a.amount : a.quantity) ?? .zero
        let y = ExactDecimal(cash ? b.amount : b.quantity) ?? .zero
        return x.magnitude.compare(y.magnitude) == 0
    }

    /// Each row of `candidates` paired with the first row of `others` it matches.
    static func find(_ candidates: [StoredTransaction], in others: [StoredTransaction]) -> [(StoredTransaction, StoredTransaction)] {
        candidates.compactMap { row in others.first { matches(row, $0) }.map { (row, $0) } }
    }
}

/// Instrument identity for manual entries from the imported rows of the same ticker and currency (Decision 3).
enum ManualInstruments {
    static func lookup(_ rows: [StoredTransaction]) -> (String, String) -> InstrumentInfo {
        struct Key: Hashable { let ticker: String; let currency: String }
        let known = Dictionary(grouping: rows) { Key(ticker: $0.ticker, currency: $0.currency) }.mapValues { group in
            InstrumentInfo(
                isin: group.last { !$0.isin.isEmpty }?.isin ?? "",
                conid: group.last { !$0.conid.isEmpty }?.conid ?? "",
                instrumentDescription: group.last { !$0.instrumentDescription.isEmpty }?.instrumentDescription ?? "",
                listingExchange: group.last { !$0.listingExchange.isEmpty }?.listingExchange ?? ""
            )
        }
        return { ticker, currency in known[Key(ticker: ticker, currency: currency)] ?? .unknown }
    }
}
