import XCTest
@testable import IBKRTaxAssistant

/// The data flow behind the Imports and Portfolio tabs, on a temporary secure folder.
@MainActor
final class AppStateTests: XCTestCase {
    private var base: URL!
    private var container: AppContainer!

    override func setUpWithError() throws {
        base = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        container = AppContainer(directory: try SecureFiles.directory(base: base))
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

        guard case .loaded(let rows, let totals, let rates, _, _, _) = PortfolioState.loadPortfolio(container, prices: .updated) else {
            return XCTFail("portfolio not loaded")
        }
        XCTAssertEqual(rows.map(\.ticker), ["AAPL", "SAP"])
        // The sample price is from 2024, so it is never shown as current.
        XCTAssertEqual(rows[0].price?.freshness, .stale)
        XCTAssertEqual(rows[0].marketValue?.compare(ExactDecimal("1710")!), 0) // 9 × 190
        XCTAssertEqual(rates?.effectiveDate, "2024-01-03")
        XCTAssertFalse(totals.isComplete, "SAP has no price")

        let again = await data.loadSamples()
        XCTAssertEqual(again.map(\.result), [.alreadyImported, .alreadyImported])

        let removed = await data.deleteAll()
        XCTAssertEqual(removed, 11)
        XCTAssertEqual(PortfolioState.loadPortfolio(container, prices: nil), .noReports)
    }
}
