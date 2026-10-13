import XCTest
@testable import IBKRTaxAssistant

/// The data flow behind the Imports and Portfolio tabs, on a temporary secure folder.
@MainActor
final class AppStateTests: XCTestCase {
    private var base: URL!
    private var container: AppContainer!

    override func setUpWithError() throws {
        base = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        container = AppContainer(directory: try SecureFiles.directory(base: base), deviceKeyTag: Self.testKeyTag)
    }

    override func tearDownWithError() throws {
        try FileManager.default.removeItem(at: base)
    }

    func testSampleReportsFillThePortfolioWithStalePricesAndApproximateTotal() async throws {
        XCTAssertEqual(PortfolioState.loadPortfolio(container, prices: nil), .noReports)

        let data = DataState(container: container)
        let results = await data.loadSamples()
        XCTAssertEqual(results.map(\.name), SampleReports.names)
        XCTAssertEqual(results.map { $0.result.map { if case .imported = $0 { return true } else { return false } } }, [true, true])

        guard case .loaded(let loaded) = PortfolioState.loadPortfolio(container, prices: .updated) else {
            return XCTFail("portfolio not loaded")
        }
        let (rows, totals, rates) = (loaded.rows, loaded.totals, loaded.rates)
        // MSFT was sold in the samples: it follows the held positions, and the AAPL dividend is counted.
        XCTAssertEqual(loaded.performance.map(\.ticker), ["AAPL", "SAP", "MSFT"])
        XCTAssertEqual(loaded.performance[0].dividends.gross.compare(ExactDecimal("3.60")!), 0)
        XCTAssertEqual(rows.map(\.ticker), ["AAPL", "SAP"])
        // The sample price is from 2024, so it is never shown as current.
        XCTAssertEqual(rows[0].price?.freshness, .stale)
        XCTAssertEqual(rows[0].marketValue?.compare(ExactDecimal("1710")!), 0) // 9 × 190
        XCTAssertEqual(rates?.effectiveDate, "2024-01-03")
        XCTAssertFalse(totals.isComplete, "SAP has no price")

        let again = await data.loadSamples()
        XCTAssertEqual(again.map(\.result), [.alreadyImported, .alreadyImported])

        // No backup keys here, so no passphrase is needed.
        let removed = await data.deleteAll(passphrase: nil)
        XCTAssertEqual(removed?.removedRecords, 11)
        XCTAssertEqual(PortfolioState.loadPortfolio(container, prices: nil), .noReports)
    }

    func testManualOperationsAloneFillThePortfolio() throws {
        XCTAssertEqual(PortfolioState.loadPortfolio(container, prices: nil), .noReports)
        let fields = ["date": "2024-01-02", "ticker": "AAPL", "currency": "USD", "quantity": "3", "price": "150.00", "fee": "0"]
        guard case .saved = try container.manualOperations().save(.buy, fields) else { return XCTFail("not saved") }

        guard case .loaded(let loaded) = PortfolioState.loadPortfolio(container, prices: nil) else { return XCTFail("portfolio not loaded") }
        XCTAssertEqual(loaded.rows.map(\.ticker), ["AAPL"])
        XCTAssertEqual(loaded.rows.first?.holding.quantity.compare(ExactDecimal("3")!), 0)
    }

    func testImportWithKeysAndFolderWritesAVerifiedBackupAndDeleteAllNeedsThePassphrase() async throws {
        let data = DataState(container: container)
        let passphrase = "correct horse battery"
        let keys = try container.backupKeys()
        try keys.confirm(try await keys.prepare(passphrase: passphrase))
        let picked = base.appendingPathComponent("Picked", isDirectory: true)
        try FileManager.default.createDirectory(at: picked, withIntermediateDirectories: true)
        try container.backupLocation().set(BackupLocation(bookmark: try picked.bookmarkData(), subfolder: BackupLocation.subfolderName))
        XCTAssertTrue(try container.backup().isReady)

        // The file as the Files picker would hand it over; its name carries a synthetic account number.
        let report = base.appendingPathComponent("U00000001_2023.csv")
        try Fixtures.flexQuery(Fixtures.validBasic).write(to: report)
        let lines = await data.importFiles([report]) { _, _ in }
        XCTAssertEqual(lines, ["U••••0001_2023.csv: " + Strings.importDone(9, 0)])

        let backups = picked.appendingPathComponent(BackupLocation.subfolderName)
        let files = try FileManager.default.contentsOfDirectory(atPath: backups.path).sorted()
        XCTAssertEqual(files.count, 1)
        XCTAssertTrue(BackupService.isBackupFileName(files[0]))
        _ = try container.backup().syncManifest()
        XCTAssertTrue(FileManager.default.fileExists(atPath: backups.appendingPathComponent(BackupService.manifestFile).path))
        let decrypted = try BackupFileFormat.decrypt(dataKey: try XCTUnwrap(try keys.dataKey()), file: Data(contentsOf: backups.appendingPathComponent(files[0])))
        XCTAssertEqual(decrypted, Fixtures.flexQuery(Fixtures.validBasic))

        let refused = await data.deleteAll(passphrase: "wrong passphrase!")
        XCTAssertNil(refused)
        let deleted = await data.deleteAll(passphrase: passphrase)
        XCTAssertEqual(deleted, DeletionOutcome(removedRecords: 9, backupsDeleted: 1, backupsLeft: 0))
    }

    func testFolderLocationsAreRecognised() {
        XCTAssertEqual(BackupLocationKind.of(path: "/private/var/mobile/Library/Mobile Documents/com~apple~CloudDocs/Backups", ubiquitous: false), .iCloud)
        XCTAssertEqual(BackupLocationKind.of(path: "/private/var/mobile/Containers/Shared/AppGroup/X/File Provider Storage/Backups", ubiquitous: false), .phone)
        XCTAssertEqual(BackupLocationKind.of(path: "/private/var/mobile/Containers/Data/Application/Y/Dropbox/Backups", ubiquitous: false), .other)
        XCTAssertEqual(BackupLocationKind.of(path: "/anything", ubiquitous: true), .iCloud)
    }
}
