import Foundation

/// The user's provider API key; implementations must keep it in encrypted storage only.
protocol ApiKeyStore {
    func get() throws -> String?
    func set(_ key: String) throws
    func clear() throws
}

/// Last retrieved quote per symbol, with its timestamps.
protocol QuoteCache {
    func get(_ symbols: Set<String>) throws -> [String: Quote]
    func putAll(_ quotes: [Quote]) throws
}

/// A cached quote and how it may be presented right now.
struct PricedQuote: Equatable {
    var quote: Quote
    var freshness: PriceFreshness
}

enum RefreshOutcome: Equatable {
    /// No provider configured: nothing was requested.
    case noKey

    case updated

    /// Quotes retrieved before `startedAt` are stale; quotes received before the failure are not.
    case failed(PriceFailure, startedAt: Date)
}

/// Where prices come from right now.
enum PriceSource: Equatable {
    case finnhub
    case yahoo
    case none
}

/// How far a running refresh has got: `done` of `total` symbols requested from `source`.
struct RefreshProgress: Equatable {
    var done: Int
    var total: Int
    var source: PriceSource
}

/// (ticker, ISIN) of a holding, the key of the listing-exchange map.
struct InstrumentKey: Hashable {
    var ticker: String
    var isin: String
}

/**
 * Price scope (mobile-market-prices spec): holdings on a supported European exchange by their
 * Yahoo symbol (e.g. SAP.DE), any other USD holding as a US listing by its ticker.
 */
enum PriceScope {
    /// The symbol a holding's price is requested and cached under, or nil when it is out of scope.
    static func symbol(_ holding: Holding, exchange: String?) -> String? {
        if let market = Markets.european(exchange ?? "") { return Markets.europeanSymbol(holding.ticker, market: market) }
        return holding.currency == "USD" ? holding.ticker : nil
    }
}

/// Port of the Android `PriceService`.
struct PriceService {
    private static let minRequestInterval: TimeInterval = 60

    let keys: ApiKeyStore
    let cache: QuoteCache
    let providerFor: (String) -> MarketDataProvider
    var now: () -> Date = Date.init
    /// Used for US listings while no key is set (mobile-market-prices spec).
    var fallback: MarketDataProvider?
    /// Used for European listings, with or without a key (Finnhub's free plan has none).
    var europe: MarketDataProvider?

    func source() throws -> PriceSource {
        if try keys.get() != nil { return .finnhub }
        return fallback != nil ? .yahoo : .none
    }

    /**
     * `exchanges` maps (ticker, ISIN) to the IBKR listing exchange. US listings go to Finnhub (or
     * the Yahoo fallback), European ones to Yahoo; a failure in one part does not stop the other.
     */
    func refresh(_ holdings: [Holding], exchanges: [InstrumentKey: String] = [:], onProgress: @escaping (RefreshProgress) -> Void = { _ in }) async throws -> RefreshOutcome {
        let key = try keys.get()
        let usProvider = key.map(providerFor) ?? fallback
        if usProvider == nil && europe == nil { return .noKey }
        let usSource: PriceSource = key != nil ? .finnhub : .yahoo
        let startedAt = now()
        let inScope = symbolsInScope(holdings, exchanges)
        let cached = try cache.get(inScope)
        let needed = inScope.filter { needsRequest(cached[$0], startedAt) }
        let usRequest: Set<String> = usProvider != nil ? needed.filter { !Markets.isEuropean($0) } : []
        let europeRequest: Set<String> = europe != nil ? needed.filter(Markets.isEuropean) : []
        let total = usRequest.count + europeRequest.count
        if total == 0 { return .updated }

        var failure: PriceFailure?
        func keep(_ result: PriceResult) throws {
            switch result {
            case .success(let quotes):
                try cache.putAll(Array(quotes.values))
            case .failure(let reason, let partial):
                try cache.putAll(Array(partial.values))
                if failure == nil { failure = reason }
            }
        }
        onProgress(RefreshProgress(done: 0, total: total, source: usRequest.isEmpty ? .yahoo : usSource))
        if let usProvider, !usRequest.isEmpty {
            try keep(await usProvider.latestQuotes(usRequest) { onProgress(RefreshProgress(done: $0, total: total, source: usSource)) })
        }
        if let europe, !europeRequest.isEmpty {
            let offset = usRequest.count
            try keep(await europe.latestQuotes(europeRequest) { onProgress(RefreshProgress(done: offset + $0, total: total, source: .yahoo)) })
        }
        return failure.map { .failed($0, startedAt: startedAt) } ?? .updated
    }

    /// Cached prices keyed by request symbol; after a failed refresh, quotes it did not update are stale.
    func prices(_ holdings: [Holding], lastRefresh: RefreshOutcome?, exchanges: [InstrumentKey: String] = [:]) throws -> [String: PricedQuote] {
        let current = now()
        var failedSince: Date?
        if case .failed(_, let startedAt)? = lastRefresh { failedSince = startedAt }
        return try cache.get(symbolsInScope(holdings, exchanges)).mapValues { quote in
            let notUpdated = failedSince.map { quote.retrievedAt < $0 } ?? false
            return PricedQuote(quote: quote, freshness: PriceFreshnessRules.classify(
                quoteTime: quote.quoteTime, now: current, lastRefreshFailed: notUpdated, market: Markets.forSymbol(quote.symbol)))
        }
    }

    /// Saves the provider quota: skip just-fetched quotes and closing prices while the market is closed.
    private func needsRequest(_ cached: Quote?, _ now: Date) -> Bool {
        guard let cached else { return true }
        if now.timeIntervalSince(cached.retrievedAt) < Self.minRequestInterval { return false }
        let market = Markets.forSymbol(cached.symbol)
        let freshness = PriceFreshnessRules.classify(quoteTime: cached.quoteTime, now: now, lastRefreshFailed: false, market: market)
        return !(freshness == .latestClose && !market.isOpen(now))
    }

    private func symbolsInScope(_ holdings: [Holding], _ exchanges: [InstrumentKey: String]) -> Set<String> {
        Set(holdings.compactMap { PriceScope.symbol($0, exchange: exchanges[InstrumentKey(ticker: $0.ticker, isin: $0.isin)]) })
    }
}
