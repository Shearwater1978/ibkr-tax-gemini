import XCTest
@testable import IBKRTaxAssistant

private func d(_ text: String) -> ExactDecimal { ExactDecimal(text)! }

private func assertDecimal(_ expected: String, _ actual: ExactDecimal?, file: StaticString = #filePath, line: UInt = #line) {
    XCTAssertEqual(actual?.compare(d(expected)), 0, "expected \(expected) but was \(String(describing: actual))", file: file, line: line)
}

/// Mirrors the Android `FxTest`.
final class FxTests: XCTestCase {
    // Synthetic table in NBP's format; the rates are made up.
    private let table = """
    [{"table":"A","no":"196/A/NBP/2026","effectiveDate":"2026-10-08","rates":[
      {"currency":"dolar amerykański","code":"USD","mid":4.0000},
      {"currency":"euro","code":"EUR","mid":4.4000},
      {"currency":"funt szterling","code":"GBP","mid":5.0000}
    ]}]
    """
    private let rates = FxRates(effectiveDate: "2026-10-08", plnPerUnit: ["USD": d("4.0000"), "EUR": d("4.4000")])

    private func client(_ status: Int, _ body: String) -> NbpClient {
        NbpClient(transport: RecordingTransport { _ in HttpResponse(status: status, body: Data(body.utf8)) })
    }

    func testNbpRequestNamesNoCurrencyOrHolding() async throws {
        let transport = RecordingTransport { _ in HttpResponse(status: 200, body: Data(self.table.utf8)) }
        let result = await NbpClient(transport: transport).latestTableA()

        XCTAssertEqual(transport.calls.map(\.url), ["https://api.nbp.pl/api/exchangerates/tables/A/?format=json"])
        XCTAssertEqual(result?.effectiveDate, "2026-10-08")
        assertDecimal("4.4", result?.plnPerUnit["EUR"])
    }

    func testNbpFailuresReturnNoRates() async {
        let notFound = await client(404, "Not Found").latestTableA()
        let notJson = await client(200, "not json").latestTableA()
        let offline = await NbpClient(transport: RecordingTransport { _ in throw URLError(.notConnectedToInternet) }).latestTableA()
        XCTAssertNil(notFound)
        XCTAssertNil(notJson)
        XCTAssertNil(offline)
    }

    func testConvertsThroughPlnCrossRates() {
        // 100 EUR x 4.40 PLN / 4.00 PLN per USD = 110 USD
        assertDecimal("110", rates.toUsd(d("100"), currency: "EUR"))
        assertDecimal("25", rates.toUsd(d("100"), currency: "PLN"))
        assertDecimal("100", rates.toUsd(d("100"), currency: "USD"))
        XCTAssertNil(rates.toUsd(d("100"), currency: "GBP"))
        XCTAssertNil(FxRates(effectiveDate: "2026-10-08", plnPerUnit: ["EUR": d("4.4")]).toUsd(.one, currency: "EUR"))
    }

    func testFailedRefreshKeepsCachedRates() async throws {
        final class Cache: FxRateCache {
            var stored: FxRates?
            init(_ rates: FxRates) { stored = rates }
            func get() -> FxRates? { stored }
            func put(_ rates: FxRates) { stored = rates }
        }
        let service = FxService(client: NbpClient(transport: RecordingTransport { _ in throw URLError(.timedOut) }), cache: Cache(rates))

        let outcome = try await service.refresh()
        XCTAssertEqual(outcome, .failed)
        XCTAssertEqual(try service.rates(), rates)
    }

    func testRatesAreStoredInTheEncryptedDatabaseAndReplacedAsAWhole() throws {
        let base = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: base) }
        let store = FxStore(database: try EncryptedDatabase(file: try SecureFiles.directory(base: base).appendingPathComponent("fx.db"), key: Data(repeating: 5, count: 32)))

        XCTAssertNil(try store.get())
        try store.put(FxRates(effectiveDate: "2026-10-08", plnPerUnit: ["USD": d("4.0"), "EUR": d("4.4")]))
        try store.put(FxRates(effectiveDate: "2026-10-09", plnPerUnit: ["USD": d("4.1")]))
        XCTAssertEqual(try store.get(), FxRates(effectiveDate: "2026-10-09", plnPerUnit: ["USD": d("4.1")]))
    }
}

/// Mirrors the Android `MainPageTest`.
final class MainPageTests: XCTestCase {
    private let time = ISO8601DateFormatter().date(from: "2024-01-03T16:00:00Z")!

    private func holding(_ ticker: String, _ isin: String, _ currency: String, _ quantity: String, _ price: String) -> Holding {
        Holdings.fromLots([OpenLot(ticker: ticker, isin: isin, date: "2023-01-01", quantity: d(quantity), price: d(price), currency: currency)])[0]
    }

    private func priced(_ symbol: String, _ price: String, _ previousClose: String?, _ freshness: PriceFreshness = .live) -> PricedQuote {
        PricedQuote(quote: Quote(symbol: symbol, price: d(price), currency: "USD", quoteTime: time, retrievedAt: time, previousClose: previousClose.map(d)), freshness: freshness)
    }

    // Synthetic positions and prices.
    private lazy var aapl = holding("AAPL", "US0378331005", "USD", "10", "150")
    private lazy var msft = holding("MSFT", "US5949181045", "USD", "2", "300")
    private lazy var sap = holding("SAP", "DE0007164600", "EUR", "5", "60")
    private lazy var prices = ["AAPL": priced("AAPL", "190", "185"), "MSFT": priced("MSFT", "400", "410")]
    private let rates = FxRates(effectiveDate: "2026-10-08", plnPerUnit: ["USD": d("4.0"), "EUR": d("4.4")])
    private let usdOnly = FxRates(effectiveDate: "2026-10-08", plnPerUnit: ["USD": d("4.0")])

    /// A EUR row with a price (1 EUR = 1.1 USD with `rates`).
    private func pricedSapRow() -> PositionRow {
        var row = MainPage.rows([sap], prices: [:])[0]
        row.price = PricedQuote(quote: Quote(symbol: "SAP", price: d("70"), currency: "EUR", quoteTime: .distantPast, retrievedAt: .distantPast, previousClose: d("68")), freshness: .latestClose)
        row.marketValue = d("350")
        row.dailyChange = d("2")
        row.dailyChangePercent = d("2.94")
        row.dailyPnl = d("10")
        row.unrealizedPnl = d("50")
        return row
    }

    func testUsdViewConvertsEveryAmountAndKeepsPercentagesAndQuantity() throws {
        let eur = MainPage.inUsd([pricedSapRow()], rates: rates)[0]

        XCTAssertEqual(eur.displayCurrency, "USD")
        XCTAssertEqual(eur.currency, "EUR")
        XCTAssertFalse(eur.rateMissing)
        assertDecimal("77", eur.price?.quote.price)
        assertDecimal("74.8", eur.price?.quote.previousClose)
        assertDecimal("385", eur.marketValue)
        assertDecimal("330", eur.cost)
        assertDecimal("2.2", eur.dailyChange)
        assertDecimal("11", eur.dailyPnl)
        assertDecimal("55", eur.unrealizedPnl)
        assertDecimal("2.94", eur.dailyChangePercent)
        assertDecimal("5", eur.holding.quantity)
    }

    func testUsdViewLeavesUsdRowsAloneAndMarksMissingRates() {
        let usdRow = MainPage.rows([aapl], prices: prices)[0]
        XCTAssertEqual(MainPage.inUsd([usdRow], rates: rates), [usdRow])

        let noRate = MainPage.inUsd([pricedSapRow()], rates: usdOnly)[0]
        XCTAssertTrue(noRate.rateMissing)
        XCTAssertNil(noRate.price)
        XCTAssertNil(noRate.marketValue)
        XCTAssertNil(noRate.unrealizedPnl)
        XCTAssertTrue(MainPage.inUsd([pricedSapRow()], rates: nil)[0].rateMissing)
    }

    func testUsdViewSortsByUsdValuesWithUnavailableLast() {
        // 5 SAP: unrealized 50 EUR = 55 USD; MSFT: 2 × (400 − 300) = 200 USD; AAPL 400 USD; a EUR row without a rate goes last.
        let rows = MainPage.rows([aapl, msft], prices: prices) + [pricedSapRow()]
        let ascending = MainPage.sorted(MainPage.inUsd(rows, rates: rates), order: SortOrder(column: .pnl), mode: .unrealized)
        XCTAssertEqual(ascending.map(\.ticker), ["SAP", "MSFT", "AAPL"])

        let descending = MainPage.sorted(MainPage.inUsd(rows, rates: usdOnly), order: SortOrder(column: .pnl, descending: true), mode: .unrealized)
        XCTAssertEqual(descending.map(\.ticker), ["AAPL", "MSFT", "SAP"])
    }

    func testRowsCarryDailyAndUnrealizedPnlInTheirOwnCurrency() throws {
        let rows = MainPage.rows([aapl, sap], prices: prices, exchanges: [InstrumentKey(ticker: "AAPL", isin: "US0378331005"): "NASDAQ"])
        let row = try XCTUnwrap(rows.first { $0.ticker == "AAPL" })

        XCTAssertEqual(row.listingExchange, "NASDAQ")
        assertDecimal("1900", row.marketValue)
        assertDecimal("5", row.dailyChange)
        assertDecimal("2.702702702702702702702702703", row.dailyChangePercent)
        assertDecimal("50", row.dailyPnl)
        assertDecimal("400", row.unrealizedPnl)

        let eurRow = try XCTUnwrap(rows.first { $0.ticker == "SAP" })
        XCTAssertFalse(eurRow.inPriceScope)
        XCTAssertNil(eurRow.marketValue)
        assertDecimal("300", eurRow.cost)
    }

    func testUsdTotalAddsUsdPositionsAndMarksMissingPricesIncomplete() {
        let totals = MainPage.usdTotals(MainPage.rows([aapl, msft, sap], prices: prices), rates: rates)

        assertDecimal("2700", totals.marketValue) // 1900 + 800; SAP has no price
        assertDecimal("30", totals.dailyPnl) // +50 - 20
        // 30 / (2700 - 30)
        assertDecimal("1.123595505617977528089887640", totals.dailyPnlPercent)
        XCTAssertEqual(totals.missingValues, 1)
        XCTAssertFalse(totals.isComplete)
        XCTAssertNil(totals.rateDate, "no non-USD amount was converted")
    }

    func testNonUsdValuesConvertAtNbpCrossRates() {
        var eurRow = MainPage.rows([aapl], prices: prices)[0]
        eurRow.holding = sap
        eurRow.marketValue = d("100")
        eurRow.dailyPnl = d("10")
        let totals = MainPage.usdTotals([eurRow], rates: rates)

        assertDecimal("110", totals.marketValue) // 100 EUR x 4.4 / 4.0
        assertDecimal("11", totals.dailyPnl)
        XCTAssertEqual(totals.rateDate, "2026-10-08")
        XCTAssertTrue(totals.isComplete)
    }

    func testMissingRateMarksTheTotalIncomplete() {
        var eurRow = MainPage.rows([aapl], prices: prices)[0]
        eurRow.holding = sap
        eurRow.marketValue = d("100")
        let totals = MainPage.usdTotals([eurRow], rates: nil)

        XCTAssertEqual(totals.missingValues, 1)
        assertDecimal("0", totals.marketValue)
    }

    func testSortsByEachColumnWithMissingValuesLast() {
        let rows = MainPage.rows([msft, sap, aapl], prices: prices)
        func order(_ column: SortColumn, descending: Bool = false, mode: PnlMode = .daily) -> [String] {
            MainPage.sorted(rows, order: SortOrder(column: column, descending: descending), mode: mode).map(\.ticker)
        }

        XCTAssertEqual(order(.symbol), ["AAPL", "MSFT", "SAP"])
        XCTAssertEqual(order(.symbol, descending: true), ["SAP", "MSFT", "AAPL"])
        XCTAssertEqual(order(.lastPrice), ["AAPL", "MSFT", "SAP"])
        XCTAssertEqual(order(.lastPrice, descending: true), ["MSFT", "AAPL", "SAP"])
        XCTAssertEqual(order(.change), ["MSFT", "AAPL", "SAP"])
        XCTAssertEqual(order(.position), ["MSFT", "SAP", "AAPL"])
        XCTAssertEqual(order(.pnl, mode: .daily), ["MSFT", "AAPL", "SAP"])
        // Unrealized: MSFT +200, AAPL +400
        XCTAssertEqual(order(.pnl, descending: true, mode: .unrealized), ["AAPL", "MSFT", "SAP"])
    }

    func testSelectingTheActiveColumnReversesIt() {
        let order = SortOrder().select(.symbol)
        XCTAssertTrue(order.descending)
        XCTAssertEqual(order.select(.pnl), SortOrder(column: .pnl))
    }

    func testStalePricesAreFlagged() {
        XCTAssertTrue(MainPage.rows([aapl], prices: ["AAPL": priced("AAPL", "190", "185", .stale)])[0].isStale)
    }

    /// From the Android `EuropeanPricesTest`: rows look prices up by symbol, so the same ticker in two markets does not mix.
    func testRowsUseTheSymbolSoSameTickersInOtherMarketsDoNotMix() throws {
        let sapEur = holding("SAP", "DE0007164600", "EUR", "1", "10")
        let sapAdr = holding("SAP", "US8030542042", "USD", "1", "10")
        let quotes = [
            "SAP.DE": PricedQuote(quote: Quote(symbol: "SAP.DE", price: d("191"), currency: "EUR", quoteTime: time, retrievedAt: time, previousClose: d("190")), freshness: .delayed),
            "SAP": PricedQuote(quote: Quote(symbol: "SAP", price: d("210"), currency: "USD", quoteTime: time, retrievedAt: time, previousClose: d("209")), freshness: .live),
        ]
        let exchanges = [InstrumentKey(ticker: "SAP", isin: "DE0007164600"): "IBIS", InstrumentKey(ticker: "SAP", isin: "US8030542042"): "NYSE"]
        let rows = MainPage.rows([sapEur, sapAdr], prices: quotes, exchanges: exchanges)

        assertDecimal("191", rows.first { $0.currency == "EUR" }?.price?.quote.price)
        assertDecimal("210", rows.first { $0.currency == "USD" }?.price?.quote.price)
    }
}
