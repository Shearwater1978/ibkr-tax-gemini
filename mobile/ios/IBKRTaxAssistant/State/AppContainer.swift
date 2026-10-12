import Foundation

/**
 * The app's single owner of the encrypted database and the services built on it, like the Android
 * `AppContainer`. The database opens on first use; when the device key or database cannot be
 * opened, every access throws and screens show "storage unavailable".
 */
final class AppContainer {
    static let shared = AppContainer()

    /// The secure folder; nil means the app's own (tests pass a temporary one).
    private let directory: URL?
    private let lock = NSLock()
    private var opened: EncryptedDatabase?
    private var pseudonymizerValue: Pseudonymizer?
    private let finnhubLimiter = RateLimiter.forFinnhubFreeTier()

    init(directory: URL? = nil) {
        self.directory = directory
    }

    func database() throws -> EncryptedDatabase {
        lock.lock()
        defer { lock.unlock() }
        if let opened { return opened }
        let database = try EncryptedDatabase.open(directory: directory)
        opened = database
        return database
    }

    func importer() throws -> ReportImporter {
        lock.lock()
        let cached = pseudonymizerValue
        lock.unlock()
        let pseudonymizer = try cached ?? Pseudonymizer.onDevice(directory: directory)
        lock.lock()
        pseudonymizerValue = pseudonymizer
        lock.unlock()
        return ReportImporter(database: try database(), pseudonymizer: pseudonymizer)
    }

    func importHistory() throws -> ImportHistory { ImportHistory(database: try database()) }

    func portfolio() throws -> PortfolioRepository { PortfolioRepository(database: try database()) }

    func operations() throws -> OperationsRepository { OperationsRepository(database: try database()) }

    func dividendEvents() throws -> DividendEvents { DividendEvents(database: try database()) }

    func priceStore() throws -> PriceStore { PriceStore(database: try database()) }

    func fxStore() throws -> FxStore { FxStore(database: try database()) }

    /// Finnhub with the user's key for US listings, Yahoo without a key and for European listings.
    func prices() throws -> PriceService {
        let store = try priceStore()
        let limiter = finnhubLimiter
        return PriceService(keys: store, cache: store, providerFor: { FinnhubProvider(apiKey: $0, limiter: limiter) },
                            fallback: YahooProvider(), europe: YahooProvider())
    }

    func fx() throws -> FxService { FxService(client: NbpClient(), cache: try fxStore()) }

    private func secureDirectory() throws -> URL { try directory ?? SecureFiles.directory() }

    func backupKeys() throws -> BackupKeys {
        BackupKeys(files: LocalKeyFiles(directory: try secureDirectory(), wrapper: DeviceKeyWrapper(tag: DeviceKeys.databaseWrapTag)))
    }

    func backupLocation() throws -> BackupLocationStore { BackupLocationStore(database: try database()) }

    func restore() throws -> RestoreService { RestoreService(reports: try importer()) }

    /**
     * Erase all data (task 7.6). Afterwards the container starts over as on a fresh install: the next
     * access creates a new database and new device keys. iOS apps cannot relaunch themselves.
     */
    func eraser() -> DataEraser {
        DataEraser(
            eraseFolder: { try self.backup().eraseAll() },
            eraseBackupKeys: { try self.backupKeys().erase() },
            deleteLocalData: {
                let secure = try self.secureDirectory()
                self.forget()
                try EncryptedDatabase.delete(directory: secure)
                for name in [Pseudonymizer.keyFileName, "pending-backups"] {
                    let url = secure.appendingPathComponent(name)
                    if FileManager.default.fileExists(atPath: url.path) { try FileManager.default.removeItem(at: url) }
                }
                // App-wide folders and switch states (USD view, sold positions); a test container leaves them alone.
                if self.directory == nil {
                    TempFiles.clear()
                    if let domain = Bundle.main.bundleIdentifier { UserDefaults.standard.removePersistentDomain(forName: domain) }
                }
            },
            deleteDeviceKeys: { try DeviceKeys.delete(tag: DeviceKeys.databaseWrapTag) }
        )
    }

    /// Closes the database and drops cached services, so nothing from before an erasure stays in use.
    private func forget() {
        lock.lock()
        defer { lock.unlock() }
        opened?.close()
        opened = nil
        pseudonymizerValue = nil
    }

    /// Import with encrypted backup; encrypted copies wait for upload in the protected folder.
    func backup() throws -> BackupService {
        let keys = try backupKeys()
        let location = try backupLocation()
        return BackupService(dataKey: keys.dataKey, manifestJson: keys.manifestJson, folder: location.folder,
                             reports: try importer(), pendingDirectory: try secureDirectory().appendingPathComponent("pending-backups", isDirectory: true))
    }
}

/**
 * Debug builds only: the synthetic fixtures from mobile/fixtures, used to exercise the portfolio
 * screens while report import waits for backups (Android `SampleReports`). Release builds do not
 * package the fixtures, and `available` is false there.
 */
enum SampleReports {
    #if DEBUG
    static let available = true
    #else
    static let available = false
    #endif

    static let names = ["valid_basic.csv", "valid_followup.csv"]

    static func read(_ name: String) -> Data? {
        guard available, let url = Bundle.main.resourceURL?.appendingPathComponent("fixtures/flex-query/\(name)") else { return nil }
        return try? Data(contentsOf: url)
    }

    /// Made-up quotes dated in the past, so the app always labels them stale, never live.
    static func samplePrices() -> [Quote] {
        let time = Date(timeIntervalSince1970: 1_704_315_600) // 2024-01-03 21:00 UTC
        return [Quote(symbol: "AAPL", price: ExactDecimal("190.00")!, currency: "USD", quoteTime: time, retrievedAt: time, previousClose: ExactDecimal("185.00")!)]
    }

    /// Made-up NBP-style rates, for the USD total only.
    static func sampleRates() -> FxRates {
        FxRates(effectiveDate: "2024-01-03", plnPerUnit: ["USD": ExactDecimal("4.0000")!, "EUR": ExactDecimal("4.4000")!])
    }
}
