import Foundation

/**
 * Year choices on income screens (mobile-year-selection spec): the newest year with data and the
 * years with data up to two years before it are shown directly, newest first; all other years go
 * into a selector. Port of the Android `YearChoices`.
 */
struct YearChoices: Equatable {
    var direct: [Int]
    var older: [Int]

    static func of(_ years: [Int]) -> YearChoices {
        let newestFirst = Array(Set(years)).sorted(by: >)
        guard let newest = newestFirst.first else { return YearChoices(direct: [], older: []) }
        return YearChoices(direct: newestFirst.filter { $0 >= newest - 2 }, older: newestFirst.filter { $0 < newest - 2 })
    }
}

/// Text for an operation's amount and details (Android `OperationDisplay`); pure so it can be unit-tested.
enum OperationDisplay {
    private static let trades: Set = ["BUY", "SELL", "TRANSFER"]
    private static let cash: Set = ["DIVIDEND", "TAX"]

    /// Trades show the absolute value (the type says the direction); cash keeps its sign; corporate actions show shares.
    static func amount(_ operation: OperationRecord) -> String {
        if trades.contains(operation.eventType) { return Formats.money(operation.amount.magnitude, operation.currency) }
        if cash.contains(operation.eventType) { return Formats.signedMoney(operation.amount, operation.currency) }
        return (operation.quantity.signum > 0 ? "+" : "") + Formats.quantity(operation.quantity)
    }

    static func details(_ operation: OperationRecord) -> String? {
        guard trades.contains(operation.eventType) else { return nil }
        return "\(Formats.quantity(operation.quantity.magnitude)) × \(Formats.number(operation.price, 2, 6)) \(operation.currency)"
    }

    static func typeLabel(_ eventType: String) -> String {
        switch eventType {
        case "BUY": return Strings.operationBuy
        case "SELL": return Strings.operationSell
        case "DIVIDEND": return Strings.operationDividend
        case "TAX": return Strings.operationTax
        case "TRANSFER": return Strings.operationTransfer
        case "SPLIT": return Strings.operationSplit
        case "STOCK_DIV": return Strings.operationStockDividend
        case "MERGER": return Strings.operationMerger
        case "SPINOFF": return Strings.operationSpinoff
        default: return eventType
        }
    }

    static func filterLabel(_ filter: OperationFilter) -> String {
        switch filter {
        case .buys: return Strings.operationsFilterBuys
        case .sells: return Strings.operationsFilterSells
        case .dividends: return Strings.operationsFilterDividends
        case .taxes: return Strings.operationsFilterTaxes
        case .other: return Strings.operationsFilterOther
        }
    }
}
