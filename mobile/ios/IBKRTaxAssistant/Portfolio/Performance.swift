/**
 * What one position has earned, in its own currency (mobile-position-performance spec).
 * A fully sold position has `row` nil, quantity zero and no price gain.
 */
struct PositionPerformance: Equatable {
    var ticker: String
    var currency: String
    var row: PositionRow?
    /// FIFO cost of the open lots, excluding commissions.
    var invested: ExactDecimal
    /// Market value minus invested; nil without a price, zero when fully sold.
    var priceGain: ExactDecimal?
    var realizedGain: ExactDecimal
    /// False when a sale closed lots bought in another currency; those gains are left out.
    var realizedComplete: Bool
    var dividends: DividendAmounts

    var isSold: Bool { row == nil }

    /// Price gain + gain from sales + net dividends; nil when the price gain is unknown.
    var total: ExactDecimal? { priceGain.map { $0.adding(realizedGain).adding(dividends.net) } }

    /// `total` as a percentage of `invested`; nil for sold positions and unknown totals.
    var totalPercent: ExactDecimal? {
        guard let total, invested.signum > 0 else { return nil }
        return total.divided(by: invested)?.multiplied(by: ExactDecimal("100")!)
    }
}

/// An approximate USD amount; `isComplete` is false when a price, rate or gain had to be left out.
struct UsdAmount: Equatable {
    var amount: ExactDecimal
    var isComplete: Bool
}

/// The main-page summary cards, all in approximate USD.
struct PortfolioSummary: Equatable {
    var value: UsdAmount
    var invested: UsdAmount
    var totalProfit: UsdAmount
    var totalProfitPercent: ExactDecimal?
    var dividends12Months: UsdAmount
}

/// Port of the Android `Performance`.
enum Performance {
    private struct Key: Hashable, Comparable {
        let ticker: String
        let currency: String

        static func < (a: Key, b: Key) -> Bool { (a.ticker, a.currency) < (b.ticker, b.currency) }
    }

    /**
     * Held positions in `rows` order, then fully sold ones (by ticker). Gains from sales and
     * dividends are matched by ticker and currency; when one ticker has several held rows in a
     * currency (an ISIN change), they go to the first of them only, so nothing counts twice.
     */
    static func positions(_ rows: [PositionRow], realized: [RealizedSale], dividends: [CashEvent]) -> [PositionPerformance] {
        let sales = Dictionary(grouping: realized) { Key(ticker: $0.ticker, currency: $0.currency) }
        let paid = Dictionary(grouping: dividends) { Key(ticker: $0.ticker, currency: $0.currency) }
        var claimed = Set<Key>()

        let held = rows.map { row -> PositionPerformance in
            let key = Key(ticker: row.ticker, currency: row.currency)
            let first = claimed.insert(key).inserted
            return performance(key, row: row, invested: row.cost, priceGain: row.unrealizedPnl, sales: first ? sales[key] : nil, dividends: first ? paid[key] : nil)
        }
        let sold = Set(sales.keys).union(paid.keys).subtracting(claimed).sorted().map { key in
            performance(key, row: nil, invested: .zero, priceGain: .zero, sales: sales[key], dividends: paid[key])
        }
        return held + sold
    }

    /**
     * `positions` must come from `positions` with every row, so gains and dividends of sold
     * positions are included whether or not the user shows them.
     */
    static func summary(_ positions: [PositionPerformance], dividends: [CashEvent], rates: FxRates?, today: CalendarDay) -> PortfolioSummary {
        var value = UsdAccumulator(rates: rates)
        var invested = UsdAccumulator(rates: rates)
        var profit = UsdAccumulator(rates: rates)
        for position in positions {
            if let row = position.row {
                value.add(row.marketValue, row.currency)
                invested.add(position.invested, row.currency)
            }
            // Added part by part, so a missing price leaves out only the price gain.
            profit.add(position.priceGain, position.currency)
            profit.add(position.realizedGain, position.currency)
            profit.add(position.dividends.net, position.currency)
            if !position.realizedComplete { profit.complete = false }
        }
        let since = today.minusMonths(12)
        var recent = UsdAccumulator(rates: rates)
        for event in dividends {
            if let day = CalendarDay(iso: event.date), day > since, day <= today { recent.add(event.amount, event.currency) }
        }
        let investedUsd = invested.result
        let profitUsd = profit.result
        return PortfolioSummary(
            value: value.result,
            invested: investedUsd,
            totalProfit: profitUsd,
            totalProfitPercent: investedUsd.amount.signum > 0 ? profitUsd.amount.divided(by: investedUsd.amount)?.multiplied(by: ExactDecimal("100")!) : nil,
            dividends12Months: recent.result
        )
    }

    private static func performance(_ key: Key, row: PositionRow?, invested: ExactDecimal, priceGain: ExactDecimal?,
                                    sales: [RealizedSale]?, dividends: [CashEvent]?) -> PositionPerformance {
        let sales = sales ?? []
        return PositionPerformance(
            ticker: key.ticker,
            currency: key.currency,
            row: row,
            invested: invested,
            priceGain: priceGain,
            realizedGain: sales.compactMap(\.gain).reduce(ExactDecimal.zero) { $0.adding($1) },
            realizedComplete: sales.allSatisfy { $0.gain != nil },
            dividends: DividendAmounts.sum(dividends ?? [])
        )
    }

    /// Adds amounts in USD; a missing amount or rate marks the result incomplete instead of guessing.
    private struct UsdAccumulator {
        let rates: FxRates?
        var sum = ExactDecimal.zero
        var complete = true

        mutating func add(_ amount: ExactDecimal?, _ currency: String) {
            if let usd = amount.flatMap({ UsdConverter.toUsd($0, currency: currency, rates: rates) }) { sum = sum.adding(usd) } else { complete = false }
        }

        var result: UsdAmount { UsdAmount(amount: sum, isComplete: complete) }
    }
}

/**
 * Today's largest moves among held positions (mobile-top-movers spec), in each position's own
 * currency. A position with no change is in neither list, so the lists never overlap.
 */
struct TopMovers: Equatable {
    /// Largest rise first.
    var gainers: [PositionRow]
    /// Largest fall first.
    var losers: [PositionRow]
    /// The worst freshness among the ranked prices: stale, then latest close, then delayed, then live.
    var freshness: PriceFreshness
}

enum TopMoversRanking {
    static let maxPerList = 5

    /// Nil when no position has a price with a previous close, so the section is hidden.
    static func rank(_ rows: [PositionRow]) -> TopMovers? {
        let ranked = rows.filter { $0.dailyChangePercent != nil && $0.price != nil }
        if ranked.isEmpty { return nil }
        func order(_ descending: Bool) -> (PositionRow, PositionRow) -> Bool {
            { a, b in
                let compared = a.dailyChangePercent!.compare(b.dailyChangePercent!)
                return compared != 0 ? (descending ? compared > 0 : compared < 0) : a.ticker < b.ticker
            }
        }
        let gainers = FifoInventory.stableSorted(ranked.filter { $0.dailyChangePercent!.signum > 0 }, by: order(true)).prefix(maxPerList)
        let losers = FifoInventory.stableSorted(ranked.filter { $0.dailyChangePercent!.signum < 0 }, by: order(false)).prefix(maxPerList)
        let all = ranked.map { $0.price!.freshness }
        let freshness: PriceFreshness = all.contains(.stale) ? .stale : all.contains(.latestClose) ? .latestClose : all.contains(.delayed) ? .delayed : .live
        return TopMovers(gainers: Array(gainers), losers: Array(losers), freshness: freshness)
    }
}
