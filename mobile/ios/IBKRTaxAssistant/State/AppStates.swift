import Foundation

/// Imports shared by every screen (Android `DataState`): the history list and what changes it.
@MainActor
final class DataState: ObservableObject {
    /// Nil until loaded.
    @Published private(set) var history: [ImportedReport]?
    @Published private(set) var storageUnavailable = false

    private let container: AppContainer
    /// Called after any change, so the portfolio reloads too.
    var onChange: () -> Void = {}

    init(container: AppContainer) {
        self.container = container
    }

    func reload() {
        Task {
            let result = await Task.detached { [container] in Result { try container.importHistory().list() } }.value
            switch result {
            case .success(let reports):
                history = reports
                storageUnavailable = false
            case .failure:
                history = nil
                storageUnavailable = true
            }
        }
    }

    /// Returns the number of records removed, or nil when the import no longer exists or storage failed.
    func deleteReport(_ id: Int64) async -> Int? {
        let removed = await Task.detached { [container] in try? container.importer().deleteReport(id)?.removedRecords }.value
        changed()
        return removed
    }

    func deleteAll() async -> Int? {
        let removed = await Task.detached { [container] in try? container.importer().deleteAll().removedRecords }.value
        changed()
        return removed
    }

    /// Debug builds: imports the synthetic reports without a backup, plus made-up past prices and rates.
    func loadSamples() async -> [(name: String, result: ImportResult?)] {
        let results = await Task.detached { [container] () -> [(name: String, result: ImportResult?)] in
            try? container.priceStore().putAll(SampleReports.samplePrices())
            try? container.fxStore().put(SampleReports.sampleRates())
            return SampleReports.names.map { name in
                (name, SampleReports.read(name).flatMap { try? container.importer().importReport($0) })
            }
        }.value
        changed()
        return results
    }

    private func changed() {
        reload()
        onChange()
    }
}

enum PortfolioUiState: Equatable {
    case loading
    case noReports
    case storageUnavailable
    case incomplete(ticker: String, date: String)

    case loaded(LoadedPortfolio)
}

struct LoadedPortfolio: Equatable {
    var rows: [PositionRow]
    var totals: UsdTotals
    /// Held positions (same order as `rows`) followed by fully sold ones.
    var performance: [PositionPerformance]
    var summary: PortfolioSummary
    var rates: FxRates?
    /// Some held positions are European listings, which are always priced from Yahoo Finance.
    var hasEuropean: Bool
    /// Nil while a refresh is running.
    var prices: RefreshOutcome?
    var priceSource: PriceSource
    /// Dividend and tax rows, for the payer history.
    var dividends: [CashEvent]
}

/**
 * The portfolio for every screen (Android `PortfolioState`): shows cached data at once, then
 * refreshes prices and NBP rates. Position performance and summary cards follow with their port.
 */
@MainActor
final class PortfolioState: ObservableObject {
    @Published private(set) var ui: PortfolioUiState = .loading
    /// What a running price refresh is doing; nil when none runs.
    @Published private(set) var progress: RefreshProgress?

    private let container: AppContainer
    private var task: Task<Void, Never>?

    init(container: AppContainer) {
        self.container = container
    }

    /// Loads cached data, then refreshes market data; a new call replaces a running one.
    func refresh() {
        task?.cancel()
        task = Task {
            let cached = await load(nil)
            if Task.isCancelled { return }
            ui = cached
            guard case .loaded = cached else { return }
            let outcome = await Self.refreshMarketData(container) { progress in
                Task { @MainActor in self.progress = progress }
            }
            progress = nil
            if Task.isCancelled { return }
            ui = await load(outcome)
        }
    }

    /// Re-reads stored data without network, e.g. after an import change.
    func reload() {
        task?.cancel()
        var prices: RefreshOutcome?
        if case .loaded(let current) = ui { prices = current.prices }
        task = Task {
            let state = await load(prices)
            if !Task.isCancelled { ui = state }
        }
    }

    private func load(_ prices: RefreshOutcome?) async -> PortfolioUiState {
        await Task.detached { [container] in Self.loadPortfolio(container, prices: prices) }.value
    }

    /// Reads the encrypted database, cached prices and cached NBP rates; runs off the main thread.
    nonisolated static func loadPortfolio(_ container: AppContainer, prices: RefreshOutcome?) -> PortfolioUiState {
        do {
            if try container.importHistory().list().isEmpty { return .noReports }
            switch try container.portfolio().holdings() {
            case .incomplete(let ticker, let date):
                return .incomplete(ticker: ticker, date: date)
            case .success(let holdings, let realized):
                let exchanges = try container.portfolio().listingExchanges()
                let service = try container.prices()
                let rows = MainPage.rows(holdings, prices: try service.prices(holdings, lastRefresh: prices, exchanges: exchanges), exchanges: exchanges)
                let rates = try container.fx().rates()
                let dividends = try container.dividendEvents().all()
                let performance = Performance.positions(rows, realized: realized, dividends: dividends)
                return .loaded(LoadedPortfolio(
                    rows: rows,
                    totals: MainPage.usdTotals(rows, rates: rates),
                    performance: performance,
                    summary: Performance.summary(performance, dividends: dividends, rates: rates, today: .today()),
                    rates: rates,
                    hasEuropean: rows.contains { PriceScope.symbol($0.holding, exchange: $0.listingExchange).map(Markets.isEuropean) ?? false },
                    prices: prices,
                    priceSource: try service.source(),
                    dividends: dividends
                ))
            }
        } catch {
            return .storageUnavailable
        }
    }

    /// Refreshes prices for in-scope holdings and the NBP table in parallel (network).
    nonisolated static func refreshMarketData(_ container: AppContainer, onProgress: @escaping (RefreshProgress) -> Void) async -> RefreshOutcome {
        async let rates: Void = { _ = try? await container.fx().refresh() }()
        var outcome = RefreshOutcome.failed(.providerError, startedAt: Date())
        if case .success(let holdings, _)? = try? container.portfolio().holdings(),
           let service = try? container.prices(), let exchanges = try? container.portfolio().listingExchanges(),
           let result = try? await service.refresh(holdings, exchanges: exchanges, onProgress: onProgress) {
            outcome = result
        }
        await rates
        return outcome
    }
}
