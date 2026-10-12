import XCTest
@testable import IBKRTaxAssistant

private func d(_ text: String) -> ExactDecimal { ExactDecimal(text)! }

/// Context arithmetic FIFO relies on; expectations come from Python's `decimal` module.
final class ExactDecimalContextTests: XCTestCase {
    func testAdditionAndSubtractionMatchPython() {
        XCTAssertEqual(d("0.00").adding(d("5")).description, "5.00")
        XCTAssertEqual(d("10.00").subtracting(d("10")).description, "0.00")
        XCTAssertEqual(d("1").adding(d("-1.50")).description, "-0.50")
        // Aligning zero to a smaller exponent adds leading zeros; the magnitude must still compare as zero.
        XCTAssertEqual(ExactDecimal.zero.subtracting(d("0.00000001")).description, "-1E-8")
        XCTAssertEqual(d("9999999999999999999999999999").adding(d("1")).description, "1.000000000000000000000000000E+28")
        XCTAssertEqual(d("1.234567890123456789012345678").adding(d("0.00000000000000000000000000049")).description, "1.234567890123456789012345678")
        XCTAssertEqual(d("3.3333333333333333333333333333").multiplied(by: d("3")).description, "10.00000000000000000000000000")
    }

    func testCompareIgnoresScale() {
        XCTAssertEqual(d("2.0").compare(d("2.00")), 0)
        XCTAssertEqual(d("-1").compare(d("0.5")), -1)
        XCTAssertEqual(d("0.00000002").compare(d("0.00000001")), 1)
    }
}

/// Mirrors the Android `FifoInventoryTest` over the shared scenarios in mobile/fixtures/fifo.
final class FifoInventoryTests: XCTestCase {
    private lazy var scenarios: [String: [PortfolioEvent]] = {
        let array = Fixtures.json("fifo/scenarios.json") as! [[String: Any]]
        return Dictionary(uniqueKeysWithValues: array.map { scenario in
            let rows = scenario["transactions"] as! [[String: String]]
            return (scenario["name"] as! String, rows.enumerated().map { event($0.element, order: Int64($0.offset)) })
        })
    }()

    private func event(_ row: [String: String], order: Int64) -> PortfolioEvent {
        PortfolioEvent(
            order: order,
            date: row["date"]!,
            eventType: row["event_type"]!,
            ticker: row["ticker"]!,
            quantity: d(row["quantity"]!),
            price: d(row["price"]!),
            currency: row["currency"]!,
            isin: row["isin"] ?? "",
            description: row["description"] ?? "",
            splitRatio: row["split_ratio"].flatMap { $0.isEmpty ? nil : d($0) }
        )
    }

    private func result(_ name: String) -> (lots: [OpenLot], realized: [RealizedSale]) {
        guard case .success(let lots, let realized) = FifoInventory.openLots(scenarios[name]!) else {
            XCTFail("\(name) did not match")
            return ([], [])
        }
        return (lots, realized)
    }

    private func lots(_ name: String) -> [OpenLot] { result(name).lots }

    /// Compares numerically: Python and Java may format equal decimals differently (1.0E+2 vs 100).
    private func sameLots(_ a: [OpenLot], _ b: [OpenLot]) -> Bool {
        a.count == b.count && zip(a, b).allSatisfy { x, y in
            x.ticker == y.ticker && x.isin == y.isin && x.date == y.date && x.currency == y.currency
                && x.quantity.compare(y.quantity) == 0 && x.price.compare(y.price) == 0
        }
    }

    func testOpenLotsMatchPythonReferenceForEveryScenario() {
        let expected = Fixtures.json("fifo/expected_open_lots.json") as! [String: [String: Any]]
        XCTAssertEqual(Set(expected.keys), Set(scenarios.keys))

        for (name, events) in scenarios {
            let reference = expected[name]!
            let result = FifoInventory.openLots(events)
            if reference["error"] != nil {
                XCTAssertEqual(result, .unmatchedSell(ticker: reference["ticker"] as! String, date: reference["date"] as! String), name)
                continue
            }
            let expectedLots = (reference["lots"] as! [[String: String]]).map {
                OpenLot(ticker: $0["ticker"]!, isin: $0["isin"]!, date: $0["date"]!, quantity: d($0["quantity"]!), price: d($0["price"]!), currency: $0["currency"]!)
            }
            guard case .success(let actual, _) = result else { return XCTFail("\(name): \(result)") }
            XCTAssertTrue(sameLots(actual, expectedLots), "\(name): \(actual) != \(expectedLots)")
        }
    }

    func testPartialSellLeavesFifoAverage() throws {
        let holding = try XCTUnwrap(Holdings.fromLots(lots("partial_sell_fifo")).first)
        XCTAssertEqual(holding.quantity.compare(d("7")), 0)
        // (2 x 150 + 5 x 160) / 7
        XCTAssertEqual(holding.averagePrice.description, "157.1428571428571428571428571")
    }

    func testRealizedSaleUsesConsumedLots() throws {
        let sale = try XCTUnwrap(result("partial_sell_fifo").realized.first)
        XCTAssertEqual(sale.proceeds.description, "1400.00")
        XCTAssertEqual(sale.cost?.description, "1200.00")
        XCTAssertEqual(sale.gain?.description, "200.00")
    }

    func testFullySoldPositionIsNotAHolding() {
        XCTAssertTrue(Holdings.fromLots(lots("fully_sold")).isEmpty)
    }

    func testCurrenciesBecomeSeparateSubpositions() throws {
        let holdings = Holdings.fromLots(lots("lots_in_two_currencies"))
        XCTAssertEqual(holdings.map(\.currency), ["EUR", "GBP"])
        let gbp = try XCTUnwrap(holdings.first { $0.currency == "GBP" })
        XCTAssertEqual(gbp.quantity.compare(d("14")), 0)
        // (10 x 24 + 4 x 25) / 14, never mixed with the EUR lot
        XCTAssertEqual(gbp.averagePrice.description, "24.28571428571428571428571429")
    }

    func testCorporateActionsKeepInstrumentIdentity() throws {
        XCTAssertEqual(Holdings.fromLots(lots("merger_carries_cost_and_dates")).map(\.ticker), ["NEWCO"])
        XCTAssertEqual(Holdings.fromLots(lots("spinoff_adds_zero_cost_lot")).map(\.ticker), ["MRK", "OGN"])
        XCTAssertEqual(Holdings.fromLots(lots("isin_change_keeps_identities_apart")).map(\.isin), ["RU0009029540", "US80585Y3080"])
        let meta = try XCTUnwrap(Holdings.fromLots(lots("ticker_alias_and_blank_isin")).first)
        XCTAssertEqual(meta.ticker, "META")
        XCTAssertEqual(meta.quantity.compare(d("3")), 0)
    }

    func testDividendsAndTaxesDoNotAffectLots() {
        let events = scenarios["partial_sell_fifo"]! + [
            PortfolioEvent(order: 99, date: "2023-05-18", eventType: "DIVIDEND", ticker: "AAPL", quantity: .zero, price: .zero, currency: "USD", isin: "US0378331005"),
            PortfolioEvent(order: 100, date: "2023-05-18", eventType: "TAX", ticker: "AAPL", quantity: .zero, price: .zero, currency: "USD", isin: "US0378331005"),
        ]
        guard case .success(let withCash, _) = FifoInventory.openLots(events) else { return XCTFail() }
        XCTAssertEqual(withCash, lots("partial_sell_fifo"))
    }
}
