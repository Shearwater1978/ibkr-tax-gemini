import Foundation

/// Imports, keys and backup readiness shared by every screen (Android `DataState`).
@MainActor
final class DataState: ObservableObject {
    /// Nil until loaded.
    @Published private(set) var history: [ImportedReport]?
    @Published private(set) var storageUnavailable = false
    @Published private(set) var keyStatus: BackupKeyStatus?
    /// Backup encryption and a backup folder are set up, so import is allowed.
    @Published private(set) var importReady = false

    private let container: AppContainer
    /// Called after any change, so the other screens reload too.
    var onChange: () -> Void = {}
    private lazy var retry = BackupRetry(
        pending: { [container] in await Task.detached { (try? container.backup().isReady == true ? container.backup().pendingCount() : 0) ?? 0 }.value },
        retry: { [container] in await Task.detached { try? container.backup().retryPending() }.value },
        onChange: { [weak self] in self?.reload() }
    )

    init(container: AppContainer) {
        self.container = container
    }

    /// Reloads everything; call after an import, a deletion or a change of keys or folder.
    func reload() {
        Task {
            let (history, status, ready) = await Task.detached { [container] in
                (Result { try container.importHistory().list() }, try? container.backupKeys().status(), (try? container.backup().isReady) ?? false)
            }.value
            switch history {
            case .success(let reports):
                self.history = reports
                storageUnavailable = false
            case .failure:
                self.history = nil
                storageUnavailable = true
            }
            keyStatus = status
            importReady = ready
            // Every import, deletion and key or folder change comes through here.
            retry.kick()
        }
    }

    /// Imports each file on its own (one bad file does not stop the rest); returns one line per file.
    func importFiles(_ urls: [URL], progress: @escaping (Int, Int) -> Void) async -> [String] {
        var lines: [String] = []
        for (index, url) in urls.enumerated() {
            progress(index + 1, urls.count)
            let line = await Task.detached { [container] () -> String in
                let name = FileNameMasking.mask(url.lastPathComponent)
                guard let bytes = Self.readLimited(url) else { return "\(name): \(Strings.importTooLarge)" }
                guard let outcome = try? container.backup().importReport(bytes) else { return "\(name): \(Strings.storageUnavailable)" }
                return "\(name): \(Strings.describe(outcome))"
            }.value
            lines.append(line)
        }
        changed()
        return lines
    }

    /// Reads a picked file inside its security scope; nil when it is larger than any plausible report.
    nonisolated private static func readLimited(_ url: URL) -> Data? {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let size = try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize, size <= 20 * 1024 * 1024 else { return nil }
        return try? Data(contentsOf: url)
    }

    func retryBackups() async -> RetryResult? {
        let result = await Task.detached { [container] in try? container.backup().retryPending() }.value
        changed()
        return result
    }

    /// Deletes one import, the records only it contains, and its backup file.
    func deleteReport(_ id: Int64) async -> DeletionOutcome? {
        let outcome = await Task.detached { [container] in try? container.backup().deleteImport(id) }.value
        changed()
        return outcome
    }

    /// Needs the backup passphrase once backup encryption is set up (mobile-report-upload spec); nil when it is wrong.
    func deleteAll(passphrase: String?) async -> DeletionOutcome? {
        if let keys = try? container.backupKeys(), keys.status() == .ready {
            guard let passphrase, await keys.verifyPassphrase(passphrase) else { return nil }
        }
        let outcome = await Task.detached { [container] in try? container.backup().deleteAllImports() }.value
        changed()
        return outcome
    }

    /// Restores from a picked folder; on success the folder becomes this phone's backup folder.
    func restore(from root: URL, secret: RestoreSecret?) async -> RestoreOutcome {
        let outcome = await Task.detached { [container] () -> RestoreOutcome in
            let scoped = root.startAccessingSecurityScopedResource()
            defer { if scoped { root.stopAccessingSecurityScopedResource() } }
            guard let found = RestoreService.locateBackup(in: root) else { return .noBackup }
            guard let restore = try? container.restore(), let keys = try? container.backupKeys(), let bookmark = try? root.bookmarkData() else { return .accessLost }
            let outcome = await restore.restore(found, secret: secret, localKey: (try? keys.dataKey()) ?? nil) { manifest, key in
                try keys.adopt(manifest, dataKey: key)
            }
            if case .restored = outcome { try? container.backupLocation().set(BackupLocation(bookmark: bookmark, subfolder: found.subfolder)) }
            return outcome
        }.value
        changed()
        return outcome
    }

    /// Erase all data; afterwards every screen reloads empty, as after a fresh install.
    func eraseAll() async -> ErasureResult {
        let result = await Task.detached { [container] in container.eraser().eraseAll() }.value
        changed()
        return result
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

    func changed() {
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
