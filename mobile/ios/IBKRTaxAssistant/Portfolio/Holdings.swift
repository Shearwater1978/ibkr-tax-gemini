/**
 * One currency subposition of an instrument (mobile-portfolio-overview spec). Lots in
 * different currencies are never averaged together.
 */
struct Holding: Equatable {
    var ticker: String
    var isin: String
    var currency: String
    var quantity: ExactDecimal
    /// Quantity-weighted lot price, excluding commissions (like the desktop `cost_per_share`).
    var averagePrice: ExactDecimal
    var lots: [OpenLot]
}

/// Port of the Android `Holdings`.
enum Holdings {
    private static let epsilon = ExactDecimal("0.00000001")!

    private struct Key: Hashable {
        let ticker: String
        let isin: String
        let currency: String
    }

    static func fromLots(_ lots: [OpenLot]) -> [Holding] {
        var order: [Key] = []
        var groups: [Key: [OpenLot]] = [:]
        for lot in lots {
            let key = Key(ticker: lot.ticker, isin: lot.isin, currency: lot.currency)
            if groups[key] == nil { order.append(key) }
            groups[key, default: []].append(lot)
        }
        let holdings = order.compactMap { key -> Holding? in
            let group = groups[key]!
            let quantity = group.reduce(ExactDecimal.zero) { $0.adding($1.quantity) }
            // FIFO can leave dust below the matcher tolerance; that is not a holding.
            if quantity.compare(epsilon) <= 0 { return nil }
            let cost = group.reduce(ExactDecimal.zero) { $0.adding($1.quantity.multiplied(by: $1.price)) }
            return Holding(ticker: key.ticker, isin: key.isin, currency: key.currency, quantity: quantity, averagePrice: cost.divided(by: quantity)!, lots: group)
        }
        return FifoInventory.stableSorted(holdings) { ($0.ticker, $0.currency, $0.isin) < ($1.ticker, $1.currency, $1.isin) }
    }
}
