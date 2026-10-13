import XCTest
@testable import IBKRTaxAssistant

/// Schema version 7 and manual entries (mobile-manual-operations tasks 6.1–6.2); mirrors the Android `ManualStorageInstrumentedTest`.
final class ManualStorageTests: XCTestCase {
    private var base: URL!
    private var file: URL!
    private let key = Data(repeating: 7, count: 32)
    private var database: EncryptedDatabase!

    override func setUpWithError() throws {
        base = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        file = try SecureFiles.directory(base: base).appendingPathComponent("manual.db")
        database = try EncryptedDatabase(file: file, key: key)
    }

    override func tearDownWithError() throws {
        database.close()
        try FileManager.default.removeItem(at: base)
    }

    private var manual: ManualEntries { ManualEntries(database: database) }
    private var importer: ReportImporter { ReportImporter(database: database, pseudonymizer: Pseudonymizer { Data(repeating: 1, count: 32) }) }

    private func count(_ condition: String = "") throws -> Int {
        guard case .integer(let value)? = try database.query("SELECT count(*) FROM transactions \(condition)").first?.first else { return -1 }
        return Int(value)
    }

    private func buy(_ entryId: String, quantity: String = "3", suffix: String = "") -> StoredTransaction {
        StoredTransaction(sourceKey: "manual:\(entryId)\(suffix)", date: "2024-02-01", eventType: "BUY", ticker: "AAPL", quantity: quantity, price: "180.00",
                          currency: "USD", amount: "540.00", fee: "-1.00", description: "Manual", splitRatio: nil, isin: "", conid: "", instrumentDescription: "")
    }

    private func entry(_ entryId: String, _ rows: StoredTransaction...) -> ManualEntry {
        ManualEntry(entryId: entryId, type: "BUY", fields: #"{"quantity":"3"}"#, enteredAt: "2026-10-13T08:00:00Z", rows: rows)
    }

    private func reportId(_ result: ImportResult) throws -> Int64 {
        struct NotImported: Error {}
        guard case .imported(let id, _, _) = result else { throw NotImported() }
        return id
    }

    func testVersionSixDatabaseMigratesWithEveryRowKept() throws {
        // Turn the new database back into version 6, as an app update finds it.
        try database.execute("DROP TABLE transactions")
        try database.execute("DROP TABLE manual_entries")
        try database.execute(
            """
            CREATE TABLE transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, report_id INTEGER NOT NULL REFERENCES reports(id),
                source_key TEXT NOT NULL UNIQUE, date TEXT NOT NULL, event_type TEXT NOT NULL, ticker TEXT NOT NULL, quantity TEXT NOT NULL,
                price TEXT NOT NULL, currency TEXT NOT NULL, amount TEXT NOT NULL, fee TEXT NOT NULL, description TEXT NOT NULL,
                split_ratio TEXT, isin TEXT NOT NULL DEFAULT '', conid TEXT NOT NULL DEFAULT '',
                instrument_description TEXT NOT NULL DEFAULT '', listing_exchange TEXT NOT NULL DEFAULT '')
            """
        )
        try database.execute("CREATE INDEX transactions_ticker_date ON transactions (ticker, date)")
        try database.execute("INSERT INTO reports (id, fingerprint, imported_at, inserted_count, skipped_count) VALUES (1, 'fp', '2026-01-01T00:00:00Z', 2, 0)")
        try database.execute(
            """
            INSERT INTO transactions (id, report_id, source_key, date, event_type, ticker, quantity, price, currency, amount, fee,
                description, split_ratio, isin, conid, instrument_description, listing_exchange) VALUES
                (5, 1, 'k1', '2023-01-10', 'BUY', 'AAPL', '10', '150.00', 'USD', '1500.00', '-1.00', 'IBKR Trade', NULL, 'US0378331005', '900001', 'APPLE INC', 'NASDAQ'),
                (9, 1, 'k2', '2023-07-03', 'SPLIT', 'SAP', '4', '0', 'EUR', '0', '0', 'SAP split', '2', 'DE0007164600', '', '', 'IBIS')
            """
        )
        try database.execute("PRAGMA user_version = 6")
        database.close()

        database = try EncryptedDatabase(file: file, key: key)
        let rows = try database.query("SELECT id, origin, report_id, entry_id, source_key, split_ratio, listing_exchange FROM transactions ORDER BY id")
        XCTAssertEqual(rows, [
            [.integer(5), .text("report"), .integer(1), nil, .text("k1"), nil, .text("NASDAQ")],
            [.integer(9), .text("report"), .integer(1), nil, .text("k2"), .text("2"), .text("IBIS")],
        ])
        XCTAssertEqual(try database.query("PRAGMA user_version").first?.first, .integer(7))
        // New rows continue after the copied ids.
        try manual.save(entry("e1", buy("e1")))
        guard case .integer(let id)? = try database.query("SELECT id FROM transactions WHERE entry_id = 'e1'").first?.first else { return XCTFail("no row") }
        XCTAssertGreaterThan(id, 9)
    }

    func testManualEntriesAreSavedReplacedAndDeletedWithTheirRows() throws {
        try manual.save(entry("e1", buy("e1")))
        XCTAssertEqual(try manual.get("e1")?.rows, [buy("e1")])
        XCTAssertEqual(try count("WHERE origin = 'manual' AND report_id IS NULL"), 1)

        try manual.save(entry("e1", buy("e1", quantity: "5")))
        XCTAssertEqual(try manual.get("e1")?.rows, [buy("e1", quantity: "5")])
        XCTAssertEqual(try count(), 1)

        XCTAssertTrue(try manual.delete("e1"))
        XCTAssertFalse(try manual.delete("e1"))
        XCTAssertNil(try manual.get("e1"))
        XCTAssertEqual(try count(), 0)
    }

    func testRowsMustBelongToTheirEntryAndBeConsistent() throws {
        XCTAssertThrowsError(try manual.save(entry("e1", buy("other")))) { XCTAssertEqual($0 as? ManualEntryError, .foreignSourceKey("manual:other")) }
        XCTAssertThrowsError(try database.execute(
            """
            INSERT INTO transactions (origin, source_key, date, event_type, ticker, quantity, price, currency, amount, fee, description)
            VALUES ('report', 'x', '2024-01-01', 'BUY', 'AAPL', '1', '1', 'USD', '1', '0', '')
            """
        ))
        XCTAssertEqual(try count(), 0)
    }

    func testDeletingImportsKeepsManualOperations() throws {
        let id = try reportId(try importer.importReport(Fixtures.flexQuery(Fixtures.validBasic)))
        try manual.save(entry("e1", buy("e1")))
        let reportRows = try count("WHERE origin = 'report'")

        XCTAssertEqual(try importer.deleteReport(id)?.removedRecords, reportRows)
        XCTAssertEqual(try count(), 1)

        _ = try importer.importReport(Fixtures.flexQuery(Fixtures.validBasic))
        XCTAssertEqual(try importer.deleteAll().removedRecords, reportRows)
        XCTAssertEqual(try manual.all().flatMap { $0.rows.map(\.sourceKey) }, ["manual:e1"])
    }

    func testAllRowsMarkManualRowsWithTheirEntry() throws {
        _ = try importer.importReport(Fixtures.flexQuery(Fixtures.validBasic))
        try manual.save(entry("e1", buy("e1")))

        let rows = try manual.allRows()
        XCTAssertEqual(rows.count, try count())
        XCTAssertEqual(rows.filter { $0.entryId == "e1" }.map(\.row.sourceKey), ["manual:e1"])
        XCTAssertEqual(rows.filter { $0.entryId == nil }.count, try count("WHERE origin = 'report'"))
        XCTAssertEqual(rows.map(\.row.date), rows.map(\.row.date).sorted())
    }

    func testADividendAndItsTaxAreOneEntry() throws {
        var dividend = buy("d1")
        (dividend.eventType, dividend.quantity, dividend.price, dividend.amount, dividend.fee) = ("DIVIDEND", "0", "0", "2.40", "0")
        var tax = buy("d1", suffix: ":tax")
        (tax.eventType, tax.quantity, tax.price, tax.amount, tax.fee) = ("TAX", "0", "0", "-0.36", "0")
        try manual.save(entry("d1", dividend, tax))

        XCTAssertEqual(try manual.get("d1")?.rows, [dividend, tax])
        try manual.delete("d1")
        XCTAssertEqual(try count(), 0)
    }
}
