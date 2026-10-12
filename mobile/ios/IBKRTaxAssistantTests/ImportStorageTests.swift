import XCTest
@testable import IBKRTaxAssistant

/// Atomic import and duplicate detection against a real SQLCipher database (Android `ReportImporterInstrumentedTest`).
final class ReportImporterTests: XCTestCase {
    private var base: URL!
    private var database: EncryptedDatabase!

    override func setUpWithError() throws {
        base = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        database = try EncryptedDatabase(file: try SecureFiles.directory(base: base).appendingPathComponent("importer.db"), key: Data(repeating: 9, count: 32))
    }

    override func tearDownWithError() throws {
        database.close()
        try FileManager.default.removeItem(at: base)
    }

    private func importer(beforeCommit: @escaping () throws -> Void = {}) -> ReportImporter {
        ReportImporter(database: database, pseudonymizer: Pseudonymizer { Data(repeating: 1, count: 32) }, beforeCommit: beforeCommit)
    }

    private func count(_ table: String) throws -> Int {
        guard case .integer(let value)? = try database.query("SELECT count(*) FROM \(table)").first?.first else { return -1 }
        return Int(value)
    }

    private func imported(_ result: ImportResult) throws -> (id: Int64, inserted: Int, skipped: Int) {
        struct NotImported: Error { let result: ImportResult }
        guard case .imported(let id, let inserted, let skipped) = result else { throw NotImported(result: result) }
        return (id, inserted, skipped)
    }

    func testValidReportImportsAllRecords() throws {
        let result = try imported(try importer().importReport(Fixtures.flexQuery(Fixtures.validBasic)))
        XCTAssertEqual(result.inserted, 9)
        XCTAssertEqual(result.skipped, 0)
        XCTAssertEqual(try count("transactions"), 9)
        XCTAssertEqual(try count("reports"), 1)
    }

    func testSameReportAgainIsAlreadyImportedAndChangesNothing() throws {
        _ = try importer().importReport(Fixtures.flexQuery(Fixtures.validBasic))
        XCTAssertEqual(try importer().importReport(Fixtures.flexQuery(Fixtures.validBasic)), .alreadyImported)
        XCTAssertEqual(try count("transactions"), 9)
        XCTAssertEqual(try count("reports"), 1)
    }

    func testFollowUpReportAddsOnlyNewRecords() throws {
        _ = try importer().importReport(Fixtures.flexQuery(Fixtures.validBasic))
        XCTAssertEqual(try imported(try importer().importReport(Fixtures.flexQuery(Fixtures.validFollowup))).inserted, 2)
        XCTAssertEqual(try count("transactions"), 11)
        XCTAssertEqual(try count("reports"), 2)
    }

    func testOverlappingReportSkipsExistingRecords() throws {
        _ = try importer().importReport(Fixtures.flexQuery(Fixtures.validBasic))
        // Different bytes (extra blank line) but the same records: a new report with nothing new.
        let result = try imported(try importer().importReport(Fixtures.flexQuery(Fixtures.validBasic) + Data("\n".utf8)))
        XCTAssertEqual(result.inserted, 0)
        XCTAssertEqual(result.skipped, 9)
        XCTAssertEqual(try count("transactions"), 9)
    }

    func testRejectedReportsStoreNothing() throws {
        _ = try importer().importReport(Fixtures.flexQuery(Fixtures.validBasic))

        XCTAssertEqual(try importer().importReport(Fixtures.flexQuery(Fixtures.unsupportedFormat)), .rejected(.unparseable(.unsupportedFormat)))
        XCTAssertEqual(try importer().importReport(Fixtures.flexQuery(Fixtures.empty)), .rejected(.unparseable(.emptyFile)))
        XCTAssertEqual(try importer().importReport(Fixtures.flexQuery(Fixtures.malformedTrades)),
                       .rejected(.unparseable(.malformed(.invalidDate, line: 5, section: "Trades"))))
        XCTAssertEqual(try count("transactions"), 9)
        XCTAssertEqual(try count("reports"), 1)
    }

    func testFailureBeforeCommitRollsBackEverything() throws {
        struct SimulatedCrash: Error {}
        XCTAssertThrowsError(try importer(beforeCommit: { throw SimulatedCrash() }).importReport(Fixtures.flexQuery(Fixtures.validBasic)))
        XCTAssertEqual(try count("transactions"), 0)
        XCTAssertEqual(try count("reports"), 0)
        XCTAssertEqual(try count("report_records"), 0)

        // A retry after the failure imports normally.
        XCTAssertEqual(try imported(try importer().importReport(Fixtures.flexQuery(Fixtures.validBasic))).inserted, 9)
    }

    func testHoldingsAggregateAcrossImportedReports() throws {
        _ = try importer().importReport(Fixtures.flexQuery(Fixtures.validBasic))
        _ = try importer().importReport(Fixtures.flexQuery(Fixtures.validFollowup))

        guard case .success(let holdings, _) = try PortfolioRepository(database: database).holdings() else { return XCTFail() }
        // MSFT was fully sold; SAP split 2-for-1 (4 -> 8) then 3 were sold.
        XCTAssertEqual(holdings.map { "\($0.ticker) \($0.currency)" }, ["AAPL USD", "SAP EUR"])

        let aapl = holdings[0]
        XCTAssertEqual(aapl.quantity.compare(ExactDecimal("9")!), 0)
        // Lots left: 2 @ 150, 5 @ 160, 2 @ 185 -> 1470 / 9
        XCTAssertEqual(aapl.averagePrice.compare(ExactDecimal("163.3333333333333333333333333")!), 0)
        XCTAssertEqual(try PortfolioRepository(database: database).listingExchanges(), [
            InstrumentKey(ticker: "AAPL", isin: "US0378331005"): "NASDAQ",
            InstrumentKey(ticker: "SAP", isin: "DE0007164600"): "IBIS",
            InstrumentKey(ticker: "MSFT", isin: "US5949181045"): "NASDAQ",
        ])

        let sap = holdings[1]
        XCTAssertEqual(sap.quantity.compare(ExactDecimal("5")!), 0)
        XCTAssertEqual(sap.averagePrice.compare(ExactDecimal("60")!), 0)
    }

    func testBackupStateIsRecordedWithTheImport() throws {
        let file = "0123456789abcdef0123456789abcdef.bin"
        _ = try importer().importReport(Fixtures.flexQuery(Fixtures.validBasic), backupFile: file)
        _ = try importer().importReport(Fixtures.flexQuery(Fixtures.validFollowup))

        XCTAssertEqual(try importer().pendingBackups(), [file])
        XCTAssertEqual(try ImportHistory(database: database).list().map(\.backupStatus), [nil, ReportImporter.backupPending])

        try importer().markBackedUp(file)
        XCTAssertEqual(try importer().pendingBackups(), [])
        XCTAssertEqual(try ImportHistory(database: database).list().last?.backupStatus, ReportImporter.backupDone)
    }

    func testDeletingAnImportKeepsRecordsThatAnOverlappingImportContains() throws {
        let first = try imported(try importer().importReport(Fixtures.flexQuery(Fixtures.validBasic), backupFile: "a.bin"))
        // Same records, different bytes: a second, fully overlapping report.
        let overlap = try imported(try importer().importReport(Fixtures.flexQuery(Fixtures.validBasic) + Data([10]), backupFile: "b.bin"))
        XCTAssertEqual(overlap.inserted, 0)

        XCTAssertEqual(try importer().deleteReport(first.id), DeletedImports(removedRecords: 0, backupFiles: ["a.bin"]))
        XCTAssertEqual(try count("transactions"), 9, "the overlapping report still contains all 9 records")
        XCTAssertEqual(try count("reports"), 1)

        XCTAssertEqual(try importer().deleteReport(overlap.id)?.removedRecords, 9)
        XCTAssertEqual(try count("transactions"), 0)
        XCTAssertEqual(try count("reports"), 0)
        XCTAssertNil(try importer().deleteReport(overlap.id))
    }

    func testDeletingOneImportRemovesOnlyItsOwnRecords() throws {
        _ = try importer().importReport(Fixtures.flexQuery(Fixtures.validBasic))
        let followup = try imported(try importer().importReport(Fixtures.flexQuery(Fixtures.validFollowup)))

        XCTAssertEqual(try importer().deleteReport(followup.id)?.removedRecords, 2)
        XCTAssertEqual(try count("transactions"), 9)
    }

    func testDeleteAllRemovesEverythingAndListsBackupFiles() throws {
        _ = try importer().importReport(Fixtures.flexQuery(Fixtures.validBasic), backupFile: "a.bin")
        _ = try importer().importReport(Fixtures.flexQuery(Fixtures.validFollowup), backupFile: "b.bin")

        let deleted = try importer().deleteAll()
        XCTAssertEqual(deleted.removedRecords, 11)
        XCTAssertEqual(Set(deleted.backupFiles), ["a.bin", "b.bin"])
        for table in ["transactions", "reports", "report_records"] { XCTAssertEqual(try count(table), 0, table) }
    }

    func testReportStoresOnlyPseudonymousAccountData() throws {
        _ = try importer().importReport(Fixtures.flexQuery(Fixtures.validBasic))

        let row = try XCTUnwrap(try database.query("SELECT account_pseudonym, account_masked, fingerprint FROM reports").first)
        guard case .text(let pseudonym)? = row[0], case .text(let masked)? = row[1], case .text(let fingerprint)? = row[2] else { return XCTFail() }
        XCTAssertEqual(pseudonym.count, 64)
        XCTAssertFalse(pseudonym.contains("00000001"))
        XCTAssertEqual(masked, "•••••0001")
        XCTAssertEqual(fingerprint.count, 64)
    }

    func testDevicePseudonymKeyIsStoredOnlyWrapped() throws {
        let directory = try SecureFiles.directory(base: base)
        let first = try Pseudonymizer.onDevice(directory: directory).pseudonym(.account, "U00000001")
        XCTAssertEqual(try Pseudonymizer.onDevice(directory: directory).pseudonym(.account, "U00000001"), first)
        XCTAssertTrue(FileManager.default.fileExists(atPath: directory.appendingPathComponent(Pseudonymizer.keyFileName).path))
    }
}

/// Paging and filters of the Operations list against a real SQLCipher database (Android `OperationsRepositoryInstrumentedTest`).
final class OperationsRepositoryTests: XCTestCase {
    private var base: URL!
    private var database: EncryptedDatabase!
    private var repository: OperationsRepository!

    private let types = ["BUY", "SELL", "DIVIDEND", "TAX", "SPLIT"]
    private let tickers = ["AAPL", "MSFT", "BRK B", "SAP", "T_X"]

    override func setUpWithError() throws {
        base = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        database = try EncryptedDatabase(file: try SecureFiles.directory(base: base).appendingPathComponent("operations.db"), key: Data(repeating: 8, count: 32))
        repository = OperationsRepository(database: database)
    }

    override func tearDownWithError() throws {
        database.close()
        try FileManager.default.removeItem(at: base)
    }

    /// Row i: ticker i % 5, type i % 5 shifted by i / 5 so every ticker gets every type; one row per day from 2000.
    private func insertRows(_ count: Int) throws {
        let start = ISO8601DateFormatter().date(from: "2000-01-01T00:00:00Z")!
        let format = ISO8601DateFormatter()
        format.formatOptions = [.withFullDate]
        try database.transaction {
            try database.execute("INSERT INTO reports (id, fingerprint, imported_at, inserted_count, skipped_count) VALUES (1, 'f', '2026-01-01', ?, 0)", [.integer(Int64(count))])
            for i in 0..<count {
                try database.execute(
                    "INSERT INTO transactions (report_id, source_key, date, event_type, ticker, quantity, price, currency, amount, fee, description) VALUES (1, ?, ?, ?, ?, '1', '10', 'USD', '10', '0', '')",
                    [.text("k\(i)"), .text(format.string(from: start.addingTimeInterval(Double(i) * 86_400))), .text(types[(i + i / 5) % 5]), .text(tickers[i % 5])]
                )
            }
        }
    }

    func testEmptyDatabaseHasNoOperations() throws {
        XCTAssertFalse(try repository.hasAny())
        XCTAssertTrue(try repository.page(filters: [], tickerQuery: "", limit: 100, offset: 0).isEmpty)
    }

    func testPagesAreNewestFirstAndDoNotOverlap() throws {
        try insertRows(250)
        let pages = try [0, 100, 200].map { try repository.page(filters: [], tickerQuery: "", limit: 100, offset: $0) }

        XCTAssertTrue(try repository.hasAny())
        XCTAssertEqual(pages.map(\.count), [100, 100, 50])
        let all = pages.flatMap { $0 }
        XCTAssertEqual(all.map(\.date), all.map(\.date).sorted(by: >))
        XCTAssertEqual(Set(all.map(\.id)).count, 250)
    }

    func testSearchAndFiltersCombine() throws {
        try insertRows(500)
        let dividendsOfAapl = try repository.page(filters: [.dividends], tickerQuery: "aap", limit: 1000, offset: 0)
        XCTAssertEqual(dividendsOfAapl.count, 20)
        XCTAssertTrue(dividendsOfAapl.allSatisfy { $0.ticker == "AAPL" && $0.eventType == "DIVIDEND" })

        let other = try repository.page(filters: [.other, .sells], tickerQuery: "", limit: 1000, offset: 0)
        XCTAssertEqual(Set(other.map(\.eventType)), ["SPLIT", "SELL"])
        XCTAssertEqual(other.count, 200)
    }

    func testWildcardsInTheSearchAreLiteral() throws {
        try insertRows(50)
        XCTAssertEqual(Set(try repository.page(filters: [], tickerQuery: "_", limit: 100, offset: 0).map(\.ticker)), ["T_X"])
        XCTAssertEqual(Set(try repository.page(filters: [], tickerQuery: "k b", limit: 100, offset: 0).map(\.ticker)), ["BRK B"])
        XCTAssertTrue(try repository.page(filters: [], tickerQuery: "%", limit: 100, offset: 0).isEmpty)
    }

    func testAPageOfTwentyThousandRowsLoadsQuickly() throws {
        try insertRows(20_000)
        _ = try repository.page(filters: [], tickerQuery: "", limit: 100, offset: 0) // warm up
        for (filters, query, offset) in [(Set<OperationFilter>(), "", 0), ([.dividends], "ms", 0), ([], "", 19_000)] {
            let start = Date()
            _ = try repository.page(filters: filters, tickerQuery: query, limit: 100, offset: offset)
            let millis = Date().timeIntervalSince(start) * 1000
            XCTAssertLessThan(millis, 200, "page \(filters) '\(query)' at \(offset) took \(millis) ms")
        }
    }
}
