import XCTest
@testable import IBKRTaxAssistant

/// The shared manual-journal fixture (mobile-manual-operations Decisions 2 and 6); mirrors the Android `ManualJournalFixtureTest`.
final class ManualJournalFixtureTests: XCTestCase {
    func testEncryptedJournalDecryptsToThePlainOne() throws {
        let file = Fixtures.raw("manual/journal_v1.bin")
        XCTAssertEqual(try BackupFileFormat.decrypt(dataKey: Data(0..<32), file: file), Fixtures.raw("manual/journal_v1.json"))
    }

    func testManualBuyRowMatchesTheImportedRowForTheSameTrade() throws {
        let journal = Fixtures.json("manual/journal_v1.json") as! [String: Any]
        let entries = journal["entries"] as! [[String: Any]]
        let manual = (entries[0]["rows"] as! [[String: Any]])[0]
        let report = try FlexQueryParser.parse(Fixtures.flexQuery(Fixtures.validBasic), sourceFile: Fixtures.validBasic).get()
        guard case .ready(let rows, _) = ImportPlan.build(report) else { return XCTFail("plan not ready") }
        let imported = try XCTUnwrap(rows.first { $0.date == "2023-01-10" && $0.ticker == "AAPL" && $0.eventType == "BUY" })

        XCTAssertEqual(manual["date"] as? String, imported.date)
        XCTAssertEqual(manual["eventType"] as? String, imported.eventType)
        XCTAssertEqual(manual["ticker"] as? String, imported.ticker)
        XCTAssertEqual(manual["quantity"] as? String, imported.quantity)
        XCTAssertEqual(manual["price"] as? String, imported.price)
        XCTAssertEqual(manual["currency"] as? String, imported.currency)
        XCTAssertEqual(manual["amount"] as? String, imported.amount)
        XCTAssertEqual(manual["fee"] as? String, imported.fee)
    }
}
