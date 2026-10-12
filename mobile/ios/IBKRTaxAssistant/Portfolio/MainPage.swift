/// One row of the positions table. Every amount is in the row's own `currency`.
struct PositionRow: Equatable {
    var holding: Holding
    var listingExchange: String
    /// False when the MVP price scope does not cover this holding (no request is made).
    var inPriceScope: Bool
    var price: PricedQuote?
    var cost: ExactDecimal
    var marketValue: ExactDecimal?
    var dailyChange: ExactDecimal?
    var dailyChangePercent: ExactDecimal?
    /// Quantity × change since the previous close.
    var dailyPnl: ExactDecimal?
    /// Market value minus FIFO cost.
    var unrealizedPnl: ExactDecimal?
    /// Currency the amounts above are in: the holding's own, or USD in the USD view.
    var displayCurrency: String
    /// USD view only: no NBP rate for the holding's currency, so the amounts are unavailable.
    var rateMissing = false

    var ticker: String { holding.ticker }
    var currency: String { holding.currency }
    var isStale: Bool { price?.freshness == .stale }
}

/**
 * Approximate USD header (mobile-portfolio-overview). Positions whose price or NBP rate is
 * missing are left out of the sums and counted, so the UI can mark the total incomplete.
 */
struct UsdTotals: Equatable {
    var marketValue: ExactDecimal
    var dailyPnl: ExactDecimal
    var dailyPnlPercent: ExactDecimal?
    var missingValues: Int
    var missingDailyPnl: Int
    /// NBP table date, when any non-USD amount was converted.
    var rateDate: String?

    var isComplete: Bool { missingValues == 0 }
}

enum PnlMode: Equatable {
    case daily
    case unrealized
}

enum SortColumn: Equatable {
    case symbol
    case lastPrice
    case change
    case position
    case pnl
}

struct SortOrder: Equatable {
    var column: SortColumn = .symbol
    var descending = false

    /// Selecting the active column reverses it; another column starts ascending.
    func select(_ column: SortColumn) -> SortOrder {
        column == self.column ? SortOrder(column: column, descending: !descending) : SortOrder(column: column)
    }
}

/// Port of the Android `MainPage`.
enum MainPage {
    private static let hundred = ExactDecimal("100")!

    /// `prices` are keyed by request symbol (see PriceScope); `exchanges` by (ticker, ISIN).
    static func rows(_ holdings: [Holding], prices: [String: PricedQuote], exchanges: [InstrumentKey: String] = [:]) -> [PositionRow] {
        holdings.map { holding in
            let exchange = exchanges[InstrumentKey(ticker: holding.ticker, isin: holding.isin)]
            let symbol = PriceScope.symbol(holding, exchange: exchange)
            let price = symbol.flatMap { prices[$0] }.flatMap { $0.quote.currency == holding.currency ? $0 : nil }
            let cost = holding.lots.reduce(ExactDecimal.zero) { $0.adding($1.quantity.multiplied(by: $1.price)) }
            let marketValue = price.map { $0.quote.price.multiplied(by: holding.quantity) }
            let change = price?.quote.dailyChange
            var percent: ExactDecimal?
            if let change, let previousClose = price?.quote.previousClose { percent = change.divided(by: previousClose)?.multiplied(by: hundred) }
            return PositionRow(
                holding: holding,
                listingExchange: exchange ?? "",
                inPriceScope: symbol != nil,
                price: price,
                cost: cost,
                marketValue: marketValue,
                dailyChange: change,
                dailyChangePercent: percent,
                dailyPnl: change.map { $0.multiplied(by: holding.quantity) },
                unrealizedPnl: marketValue.map { $0.subtracting(cost) },
                displayCurrency: holding.currency
            )
        }
    }

    /**
     * The same rows with every amount in USD at NBP cross rates (mobile-usd-view spec). USD rows are
     * returned as they are; rows whose currency has no rate keep only quantity and percentages.
     * Sorting the result sorts by the USD values.
     */
    static func inUsd(_ rows: [PositionRow], rates: FxRates?) -> [PositionRow] {
        rows.map { row in
            if row.currency == UsdConverter.usd { return row }
            func usd(_ value: ExactDecimal?) -> ExactDecimal? { value.flatMap { UsdConverter.toUsd($0, currency: row.currency, rates: rates) } }
            var converted = row
            converted.price = row.price.flatMap { priced in
                usd(priced.quote.price).map { price in
                    var quote = priced.quote
                    quote.price = price
                    quote.currency = UsdConverter.usd
                    quote.previousClose = usd(priced.quote.previousClose)
                    return PricedQuote(quote: quote, freshness: priced.freshness)
                }
            }
            converted.cost = usd(row.cost) ?? .zero
            converted.marketValue = usd(row.marketValue)
            converted.dailyChange = usd(row.dailyChange)
            converted.dailyPnl = usd(row.dailyPnl)
            converted.unrealizedPnl = usd(row.unrealizedPnl)
            converted.displayCurrency = UsdConverter.usd
            converted.rateMissing = usd(.one) == nil
            return converted
        }
    }

    static func usdTotals(_ rows: [PositionRow], rates: FxRates?) -> UsdTotals {
        var value = ExactDecimal.zero
        var daily = ExactDecimal.zero
        var previousValue = ExactDecimal.zero
        var missingValues = 0
        var missingDaily = 0
        var converted = false
        for row in rows {
            let usdValue = row.marketValue.flatMap { UsdConverter.toUsd($0, currency: row.currency, rates: rates) }
            if let usdValue { value = value.adding(usdValue) } else { missingValues += 1 }

            let usdDaily = row.dailyPnl.flatMap { UsdConverter.toUsd($0, currency: row.currency, rates: rates) }
            if let usdDaily, let usdValue {
                daily = daily.adding(usdDaily)
                previousValue = previousValue.adding(usdValue.subtracting(usdDaily))
            } else {
                missingDaily += 1
            }
            if row.currency != UsdConverter.usd && usdValue != nil { converted = true }
        }
        return UsdTotals(
            marketValue: value,
            dailyPnl: daily,
            dailyPnlPercent: previousValue.signum > 0 ? daily.divided(by: previousValue)?.multiplied(by: hundred) : nil,
            missingValues: missingValues,
            missingDailyPnl: missingDaily,
            rateDate: converted ? rates?.effectiveDate : nil
        )
    }

    /// Rows without a value for the sort column always go last, whatever the direction.
    static func sorted(_ rows: [PositionRow], order: SortOrder, mode: PnlMode) -> [PositionRow] {
        func bySymbol(_ a: PositionRow, _ b: PositionRow) -> Int {
            let left = (a.ticker, a.currency, a.holding.isin), right = (b.ticker, b.currency, b.holding.isin)
            return left < right ? -1 : (left == right ? 0 : 1)
        }
        // Kotlin's `reversed()` comparator flips the symbol tie-break as well.
        func sort(_ rows: [PositionRow], _ compare: (PositionRow, PositionRow) -> Int) -> [PositionRow] {
            FifoInventory.stableSorted(rows) { (order.descending ? -1 : 1) * compare($0, $1) < 0 }
        }
        let key: (PositionRow) -> ExactDecimal?
        switch order.column {
        case .symbol: return sort(rows, bySymbol)
        case .lastPrice: key = { $0.price?.quote.price }
        case .change: key = { $0.dailyChangePercent }
        case .position: key = { $0.holding.quantity }
        case .pnl: key = { mode == .daily ? $0.dailyPnl : $0.unrealizedPnl }
        }
        let present = rows.filter { key($0) != nil }
        let missing = rows.filter { key($0) == nil }
        let byKey = sort(present) { a, b in
            let compared = key(a)!.compare(key(b)!)
            return compared != 0 ? compared : bySymbol(a, b)
        }
        return byKey + FifoInventory.stableSorted(missing) { bySymbol($0, $1) < 0 }
    }
}
