/// Type filters on the Operations tab (mobile-operations spec); `other` is every remaining event type.
enum OperationFilter: CaseIterable {
    case buys
    case sells
    case dividends
    case taxes
    case other

    var eventType: String? {
        switch self {
        case .buys: return "BUY"
        case .sells: return "SELL"
        case .dividends: return "DIVIDEND"
        case .taxes: return "TAX"
        case .other: return nil
        }
    }

    /// Event types that have their own filter; corporate actions and transfers fall under `other`.
    static let named = allCases.compactMap(\.eventType)

    static func of(_ eventType: String) -> OperationFilter { allCases.first { $0.eventType == eventType } ?? .other }
}

/// One imported transaction as listed to the user: no account, report or file reference.
struct Operation: Equatable {
    var id: Int64
    var date: String
    var eventType: String
    var ticker: String
    var quantity: ExactDecimal
    var price: ExactDecimal
    var currency: String
    var amount: ExactDecimal
    var fee: ExactDecimal
}

/// Read-only, paged access to imported transactions, newest first. Port of the Android `OperationsRepository`.
struct OperationsRepository {
    let database: EncryptedDatabase

    func hasAny() throws -> Bool {
        try database.query("SELECT EXISTS (SELECT 1 FROM transactions)").first?.first == .integer(1)
    }

    /// No `filters` means all types; `tickerQuery` matches anywhere in the ticker, ignoring case.
    func page(filters: Set<OperationFilter>, tickerQuery: String, limit: Int, offset: Int) throws -> [Operation] {
        var conditions: [String] = []
        var arguments: [SQLValue?] = []
        if !filters.isEmpty {
            var alternatives: [String] = []
            let named = OperationFilter.allCases.filter(filters.contains).compactMap(\.eventType)
            if !named.isEmpty {
                alternatives.append("event_type IN (\(placeholders(named.count)))")
                arguments += named.map { .text($0) }
            }
            if filters.contains(.other) {
                alternatives.append("event_type NOT IN (\(placeholders(OperationFilter.named.count)))")
                arguments += OperationFilter.named.map { .text($0) }
            }
            conditions.append("(" + alternatives.joined(separator: " OR ") + ")")
        }
        let query = tickerQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        if !query.isEmpty {
            // SQLite LIKE ignores case for ASCII; escape the wildcards the user may type.
            conditions.append("ticker LIKE ? ESCAPE '\\'")
            let escaped = query.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "%", with: "\\%").replacingOccurrences(of: "_", with: "\\_")
            arguments.append(.text("%" + escaped + "%"))
        }
        let whereClause = conditions.isEmpty ? "" : "WHERE " + conditions.joined(separator: " AND ")
        arguments += [.integer(Int64(limit)), .integer(Int64(offset))]
        return try database.query(
            "SELECT id, date, event_type, ticker, quantity, price, currency, amount, fee FROM transactions \(whereClause) ORDER BY date DESC, id DESC LIMIT ? OFFSET ?",
            arguments
        ).compactMap { row in
            guard case .integer(let id)? = row[0], case .text(let date)? = row[1], case .text(let type)? = row[2],
                  case .text(let ticker)? = row[3], case .text(let currency)? = row[6] else { return nil }
            return Operation(id: id, date: date, eventType: type, ticker: ticker, quantity: decimal(row[4]), price: decimal(row[5]),
                             currency: currency, amount: decimal(row[7]), fee: decimal(row[8]))
        }
    }

    private func placeholders(_ count: Int) -> String { Array(repeating: "?", count: count).joined(separator: ",") }

    private func decimal(_ value: SQLValue?) -> ExactDecimal {
        if case .text(let text)? = value, let decimal = ExactDecimal(text) { return decimal }
        return .zero
    }
}

enum HoldingsResult: Equatable {
    /// `realized` lists every matched sell, including sells of positions no longer held.
    case success(holdings: [Holding], realized: [RealizedSale])

    /// Imported data sells more than it buys for `ticker`; holdings would be wrong, so none are shown.
    case incomplete(ticker: String, date: String)
}

/// Reads imported transactions from the encrypted database and derives current holdings. Port of the Android `PortfolioRepository`.
struct PortfolioRepository {
    let database: EncryptedDatabase

    func holdings() throws -> HoldingsResult {
        switch FifoInventory.openLots(try events()) {
        case .success(let lots, let realized): return .success(holdings: Holdings.fromLots(lots), realized: realized)
        case .unmatchedSell(let ticker, let date): return .incomplete(ticker: ticker, date: date)
        }
    }

    /// Listing exchange per (ticker, ISIN), from the most recent record that has one.
    func listingExchanges() throws -> [InstrumentKey: String] {
        var exchanges: [InstrumentKey: String] = [:]
        for row in try database.query("SELECT ticker, isin, listing_exchange FROM transactions WHERE listing_exchange <> '' ORDER BY date, id") {
            if case .text(let ticker)? = row[0], case .text(let isin)? = row[1], case .text(let exchange)? = row[2] {
                exchanges[InstrumentKey(ticker: ticker, isin: isin)] = exchange
            }
        }
        return exchanges
    }

    private func events() throws -> [PortfolioEvent] {
        try database.query("SELECT id, date, event_type, ticker, quantity, price, currency, isin, description, split_ratio FROM transactions ORDER BY date, id")
            .compactMap { row in
                guard case .integer(let id)? = row[0], case .text(let date)? = row[1], case .text(let type)? = row[2], case .text(let ticker)? = row[3],
                      case .text(let quantity)? = row[4], case .text(let price)? = row[5], case .text(let currency)? = row[6],
                      case .text(let isin)? = row[7], case .text(let description)? = row[8],
                      let quantityValue = ExactDecimal(quantity), let priceValue = ExactDecimal(price) else { return nil }
                var ratio: ExactDecimal?
                if case .text(let text)? = row[9] { ratio = ExactDecimal(text) }
                return PortfolioEvent(order: id, date: date, eventType: type, ticker: ticker, quantity: quantityValue, price: priceValue,
                                      currency: currency, isin: isin, description: description, splitRatio: ratio)
            }
    }
}
