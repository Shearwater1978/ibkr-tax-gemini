import Foundation

/// A stored transaction as the portfolio engine sees it; `order` is the database id.
struct PortfolioEvent: Equatable {
    var order: Int64
    var date: String
    var eventType: String
    var ticker: String
    var quantity: ExactDecimal
    var price: ExactDecimal
    var currency: String
    var isin: String
    var description = ""
    var splitRatio: ExactDecimal?
}

/// A remaining FIFO lot, in the currency it was bought in.
struct OpenLot: Equatable {
    var ticker: String
    var isin: String
    var date: String
    var quantity: ExactDecimal
    var price: ExactDecimal
    var currency: String
}

/**
 * One sell matched against FIFO lots, in the sale's currency and excluding commissions.
 * `cost` is nil when a closed lot was bought in another currency: the gain is then unknown
 * rather than mixed across currencies.
 */
struct RealizedSale: Equatable {
    var ticker: String
    var isin: String
    var currency: String
    var date: String
    var quantity: ExactDecimal
    var proceeds: ExactDecimal
    var cost: ExactDecimal?

    var gain: ExactDecimal? { cost.map { proceeds.subtracting($0) } }
}

enum FifoResult: Equatable {
    /// `realized` holds one entry per sell, in matching order; open lots are unaffected by it.
    case success(lots: [OpenLot], realized: [RealizedSale])

    /// A removal exceeded the held quantity (desktop `UNMATCHED_SELL`): the data is incomplete.
    case unmatchedSell(ticker: String, date: String)
}

/**
 * Open-lot part of the desktop pipeline: `resolve_instrument_identities`, ticker aliases, and
 * `TradeMatcher` from src/fifo.py, without PLN conversion. Port of the Android `FifoInventory`;
 * shared FIFO scenarios (mobile/fixtures/fifo) pin the parity. All arithmetic uses Python's
 * default Decimal context (28 digits, half-even), so lots round identically.
 */
enum FifoInventory {
    private static let epsilon = ExactDecimal("0.00000001")!
    private static let typePriority = ["SPLIT": 0, "STOCK_DIV": 1, "MERGER": 1, "SPLIT_ADD": 1, "BUY": 2, "TRANSFER": 2, "SELL": 3]
    private static let nonFifo: Set = ["DIVIDEND", "TAX"]
    private static let trailingParentheses = try! NSRegularExpression(pattern: #"\s*\([^()]*\)\s*$"#)

    static func openLots(_ events: [PortfolioEvent]) -> FifoResult {
        let ordered = stableSorted(events) { ($0.date, $0.order) < ($1.date, $1.order) }
        let trades = InstrumentIdentities.resolve(ordered).filter { !nonFifo.contains($0.eventType) }
        let matcher = Matcher(trades)
        do {
            return .success(lots: try matcher.run(), realized: matcher.realized)
        } catch let error as UnmatchedSellError {
            return .unmatchedSell(ticker: error.ticker, date: error.date)
        } catch {
            preconditionFailure("unexpected FIFO error")
        }
    }

    /// Kotlin `sortedWith` is stable; Swift does not promise it, so ties keep input order explicitly.
    static func stableSorted<T>(_ items: [T], by less: (T, T) -> Bool) -> [T] {
        items.enumerated().sorted { a, b in
            less(a.element, b.element) || (!less(b.element, a.element) && a.offset < b.offset)
        }.map(\.element)
    }

    private struct UnmatchedSellError: Error {
        let ticker: String
        let date: String
    }

    private final class Lot {
        var quantity: ExactDecimal
        var price: ExactDecimal
        let ticker: String
        let isin: String
        let date: String
        let currency: String

        init(quantity: ExactDecimal, price: ExactDecimal, ticker: String, isin: String, date: String, currency: String) {
            self.quantity = quantity
            self.price = price
            self.ticker = ticker
            self.isin = isin
            self.date = date
            self.currency = currency
        }

        func copy() -> Lot { Lot(quantity: quantity, price: price, ticker: ticker, isin: isin, date: date, currency: currency) }
    }

    /// An inventory key: a ticker, or a (ticker, ISIN) pair for tickers seen with several ISINs.
    private struct Key: Hashable {
        let ticker: String
        let isin: String?
    }

    /// The removal and addition rows of one merger or stock dividend.
    private struct Group: Hashable {
        let date: String
        let stem: String
    }

    /// Insertion-ordered dictionary, like Kotlin's `LinkedHashMap`.
    private struct OrderedMap<K: Hashable, V> {
        private(set) var keys: [K] = []
        private var values: [K: V] = [:]

        subscript(key: K) -> V? {
            get { values[key] }
            set {
                if newValue != nil && values[key] == nil { keys.append(key) }
                if newValue == nil { keys.removeAll { $0 == key } }
                values[key] = newValue
            }
        }

        var orderedValues: [V] { keys.map { values[$0]! } }
    }

    /// Port of TradeMatcher.process_trades.
    private final class Matcher {
        private let trades: [PortfolioEvent]
        private let sensitive: Set<String>
        private var inventory = OrderedMap<Key, [Lot]>()
        private var carry: [Group: [Lot]] = [:]
        private(set) var realized: [RealizedSale] = []

        init(_ trades: [PortfolioEvent]) {
            self.trades = trades
            var observed: [String: Set<String>] = [:]
            for trade in trades where !trade.isin.isEmpty { observed[trade.ticker, default: []].insert(trade.isin) }
            sensitive = Set(observed.filter { $0.value.count > 1 }.keys)
        }

        func run() throws -> [OpenLot] {
            let sorted = stableSorted(trades) { a, b in
                (a.date, typePriority[a.eventType] ?? 99) < (b.date, typePriority[b.eventType] ?? 99)
            }

            var addedQty: [Group: ExactDecimal] = [:]
            var removals: [Group: Int] = [:]
            for trade in sorted {
                guard let group = actionGroup(trade) else { continue }
                if trade.quantity.signum > 0 {
                    addedQty[group] = (addedQty[group] ?? .zero).adding(trade.quantity)
                } else if trade.quantity.signum < 0 {
                    removals[group, default: 0] += 1
                }
            }
            var deferred = OrderedMap<Group, [PortfolioEvent]>()

            for trade in sorted {
                if inventory[key(trade)] == nil { inventory[key(trade)] = [] }
                if trade.eventType == "SPLIT" {
                    split(trade)
                    continue
                }
                if trade.quantity.signum > 0 {
                    if trade.eventType == "BUY" || trade.eventType == "TRANSFER" {
                        buy(trade, price: trade.price)
                    } else {
                        // Corporate-action additions arrive at zero cost unless cost carries over.
                        let group = actionGroup(trade)
                        if let group, carry[group] != nil, removals[group] == 0 {
                            carriedAdd(trade, group: group, groupAddedQty: addedQty[group]!)
                        } else if let group, (removals[group] ?? 0) > 0 {
                            deferred[group] = (deferred[group] ?? []) + [trade]
                        } else {
                            buy(trade, price: .zero)
                        }
                    }
                } else if trade.quantity.signum < 0 {
                    let consumed = try consume(trade)
                    if trade.eventType == "SELL" {
                        realized.append(realizedSale(trade, consumed))
                        continue
                    }
                    guard let group = actionGroup(trade) else { continue }
                    removals[group]! -= 1
                    if !consumed.isEmpty { carry[group, default: []] += consumed }
                    if removals[group] == 0, let pending = deferred[group] {
                        deferred[group] = nil
                        for event in pending {
                            if carry[group] != nil {
                                carriedAdd(event, group: group, groupAddedQty: addedQty[group]!)
                            } else {
                                buy(event, price: .zero)
                            }
                        }
                    }
                }
            }
            for event in deferred.orderedValues.joined() { buy(event, price: .zero) }

            return inventory.orderedValues.joined().map {
                OpenLot(ticker: $0.ticker, isin: $0.isin, date: $0.date, quantity: $0.quantity, price: $0.price, currency: $0.currency)
            }
        }

        private func realizedSale(_ trade: PortfolioEvent, _ consumed: [Lot]) -> RealizedSale {
            let quantity = trade.quantity.magnitude
            let sameCurrency = consumed.allSatisfy { $0.currency == trade.currency }
            return RealizedSale(
                ticker: trade.ticker,
                isin: trade.isin,
                currency: trade.currency,
                date: trade.date,
                quantity: quantity,
                proceeds: quantity.multiplied(by: trade.price),
                cost: sameCurrency ? consumed.reduce(.zero) { $0.adding($1.quantity.multiplied(by: $1.price)) } : nil
            )
        }

        private func key(_ trade: PortfolioEvent) -> Key {
            Key(ticker: trade.ticker, isin: sensitive.contains(trade.ticker) ? trade.isin : nil)
        }

        private func actionGroup(_ trade: PortfolioEvent) -> Group? {
            guard trade.eventType == "MERGER" || trade.eventType == "STOCK_DIV", !trade.description.isEmpty else { return nil }
            let range = NSRange(trade.description.startIndex..., in: trade.description)
            let stem = trailingParentheses.stringByReplacingMatches(in: trade.description, range: range, withTemplate: "")
            return stem != trade.description ? Group(date: trade.date, stem: stem) : nil
        }

        private func buy(_ trade: PortfolioEvent, price: ExactDecimal) {
            inventory[key(trade)]!.append(Lot(quantity: trade.quantity, price: price, ticker: trade.ticker, isin: trade.isin, date: trade.date, currency: trade.currency))
        }

        /// The old shares' purchase dates and cost move to the new shares.
        private func carriedAdd(_ trade: PortfolioEvent, group: Group, groupAddedQty: ExactDecimal) {
            let lots = carry[group]!
            let removedQty = lots.reduce(ExactDecimal.zero) { $0.adding($1.quantity) }
            let share = trade.quantity.divided(by: groupAddedQty)!
            let scale = groupAddedQty.divided(by: removedQty)!
            for lot in lots {
                inventory[key(trade)]!.append(Lot(
                    quantity: lot.quantity.multiplied(by: scale).multiplied(by: share),
                    price: lot.price.divided(by: scale)!,
                    ticker: trade.ticker,
                    isin: trade.isin,
                    date: lot.date,
                    currency: lot.currency
                ))
            }
        }

        private func split(_ trade: PortfolioEvent) {
            // IBKR books a reverse split as two rows; the negative one is ignored.
            if trade.quantity.signum < 0 { return }
            let ratio = trade.splitRatio ?? .one
            let own = key(trade)
            let keys: [Key]
            if !(inventory[own] ?? []).isEmpty {
                keys = [own]
            } else if own.isin != nil {
                // A split that changes the ISIN is booked under the new ISIN while shares sit under the old one.
                keys = inventory.keys.filter { $0.isin != nil && $0.ticker == trade.ticker && !(inventory[$0] ?? []).isEmpty }
            } else {
                keys = []
            }
            for k in keys {
                for lot in inventory[k]! {
                    lot.quantity = lot.quantity.multiplied(by: ratio)
                    if ratio.signum != 0 { lot.price = lot.price.divided(by: ratio)! }
                }
            }
        }

        /// Port of _consume_inventory; returns the consumed (possibly partial) lots.
        private func consume(_ trade: PortfolioEvent) throws -> [Lot] {
            let k = key(trade)
            var remaining = trade.quantity.magnitude
            var consumed: [Lot] = []
            while remaining.signum > 0, let lot = inventory[k]!.first {
                if lot.quantity.compare(remaining.adding(epsilon)) <= 0 {
                    consumed.append(lot.copy())
                    inventory[k]!.removeFirst()
                    remaining = remaining.subtracting(lot.quantity)
                } else {
                    let part = lot.copy()
                    part.quantity = remaining
                    consumed.append(part)
                    lot.quantity = lot.quantity.subtracting(remaining)
                    remaining = .zero
                }
            }
            if remaining.compare(epsilon) > 0 { throw UnmatchedSellError(ticker: trade.ticker, date: trade.date) }
            return consumed
        }
    }
}

/// Port of src/instrument_identity.py plus the alias step in process_yearly_data.
enum InstrumentIdentities {
    private static let tickerAliases = ["TOT": "TTE", "FB": "META"]
    private static let placeholderTicker = try! NSRegularExpression(pattern: #"^\d+[A-Z]?$"#)
    private static let actionSymbol = try! NSRegularExpression(pattern: #"^([A-Z][A-Z0-9.]*)\("#)

    static func resolve(_ events: [PortfolioEvent]) -> [PortfolioEvent] {
        let withTickers = events.map { event -> PortfolioEvent in
            let description = event.description
            let symbol = actionSymbol.firstMatch(in: description, range: NSRange(description.startIndex..., in: description))
                .flatMap { Range($0.range(at: 1), in: description) }.map { String(description[$0]) }
            let ticker = event.ticker.trimmingCharacters(in: .whitespacesAndNewlines)
            let placeholder = placeholderTicker.firstMatch(in: ticker, range: NSRange(ticker.startIndex..., in: ticker)) != nil
            var resolved = event
            if placeholder, let symbol { resolved.ticker = symbol }
            return resolved
        }

        var firstSeen: [String: [String: String]] = [:]
        for event in withTickers where !event.ticker.isEmpty && !event.isin.isEmpty {
            let ticker = normalized(event.ticker)
            if let date = firstSeen[ticker]?[event.isin], date <= event.date { continue }
            firstSeen[ticker, default: [:]][event.isin] = event.date
        }
        let earliestIsin = firstSeen.mapValues { isins in
            isins.min { ($0.value, $0.key) < ($1.value, $1.key) }!.key
        }

        return withTickers.map { event in
            var resolved = event
            if resolved.isin.isEmpty { resolved.isin = earliestIsin[normalized(event.ticker)] ?? "" }
            resolved.ticker = tickerAliases[event.ticker] ?? event.ticker
            return resolved
        }
    }

    private static func normalized(_ ticker: String) -> String {
        let upper = ticker.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        return tickerAliases[upper] ?? upper
    }
}
