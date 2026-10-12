import XCTest
@testable import IBKRTaxAssistant

/// Mirrors the Android `YearChoicesTest`.
final class YearChoicesTests: XCTestCase {
    func testFiveYearsShowTheNewestThreeDirectly() {
        XCTAssertEqual(YearChoices.of([2022, 2023, 2024, 2025, 2026]), YearChoices(direct: [2026, 2025, 2024], older: [2023, 2022]))
    }

    func testYearsMoreThanTwoBeforeTheNewestGoToTheSelector() {
        XCTAssertEqual(YearChoices.of([2021, 2023, 2026]), YearChoices(direct: [2026], older: [2023, 2021]))
    }

    func testFewYearsAreAllDirectAndNoneIsEmpty() {
        XCTAssertEqual(YearChoices.of([2024, 2025, 2025]), YearChoices(direct: [2025, 2024], older: []))
        XCTAssertEqual(YearChoices.of([]), YearChoices(direct: [], older: []))
    }
}

/// Mirrors the Android `OperationsTest` (US number format).
final class OperationDisplayTests: XCTestCase {
    override func setUp() { Formats.locale = Locale(identifier: "en_US") }
    override func tearDown() { Formats.locale = .current }

    private func operation(_ type: String, _ quantity: String, _ price: String, _ amount: String) -> OperationRecord {
        OperationRecord(id: 1, date: "2024-03-15", eventType: type, ticker: "AAPL", quantity: ExactDecimal(quantity)!, price: ExactDecimal(price)!,
                  currency: "USD", amount: ExactDecimal(amount)!, fee: .zero)
    }

    func testEveryEventTypeHasAFilter() {
        XCTAssertEqual(OperationFilter.of("BUY"), .buys)
        XCTAssertEqual(OperationFilter.of("SELL"), .sells)
        XCTAssertEqual(OperationFilter.of("DIVIDEND"), .dividends)
        XCTAssertEqual(OperationFilter.of("TAX"), .taxes)
        for type in ["SPLIT", "STOCK_DIV", "MERGER", "SPINOFF", "TRANSFER"] { XCTAssertEqual(OperationFilter.of(type), .other, type) }
    }

    func testTradesShowAbsoluteAmountsAndQuantityTimesPrice() {
        let sell = operation("SELL", "-15", "130", "-1950")
        XCTAssertEqual(OperationDisplay.amount(sell), "1,950.00 USD")
        XCTAssertEqual(OperationDisplay.details(sell), "15 × 130.00 USD")
    }

    func testCashKeepsItsSign() {
        XCTAssertEqual(OperationDisplay.amount(operation("DIVIDEND", "0", "0", "12.5")), "+12.50 USD")
        XCTAssertEqual(OperationDisplay.amount(operation("TAX", "0", "0", "-1.875")), "-1.88 USD")
        XCTAssertNil(OperationDisplay.details(operation("TAX", "0", "0", "-1.875")))
    }

    func testCorporateActionsShowTheChangeInShares() {
        XCTAssertEqual(OperationDisplay.amount(operation("SPLIT", "30", "0", "0")), "+30")
        XCTAssertEqual(OperationDisplay.amount(operation("MERGER", "-5", "0", "0")), "-5")
    }
}

/// Mirrors the activity and income parts of the Android `StateHoldersTest`.
@MainActor
final class ActivityStateTests: XCTestCase {
    private func op(_ id: Int) -> OperationRecord {
        OperationRecord(id: Int64(id), date: "2024-01-01", eventType: "BUY", ticker: "T\(id)", quantity: .one, price: ExactDecimal("10")!, currency: "USD", amount: ExactDecimal("10")!, fee: .zero)
    }

    /// Waits until `condition` holds; the state loads on its own tasks.
    private func waitFor(_ condition: @escaping () -> Bool) async {
        for _ in 0..<200 where !condition() { try? await Task.sleep(nanoseconds: 5_000_000) }
    }

    func testActivityPagesSearchesAndDropsStalePages() async {
        var calls: [(filters: Set<OperationFilter>, query: String, offset: Int)] = []
        let state = ActivityState(page: { filters, query, limit, offset in
            calls.append((filters, query, offset))
            return offset >= 150 ? [] : (offset..<min(offset + limit, 150)).map(self.op)
        }, hasAny: { true }, debounce: 0.05)

        state.reload()
        await waitFor { state.view.items.count == 100 }
        XCTAssertEqual(state.view.hasAny, true)
        XCTAssertFalse(state.view.endReached)

        state.loadMore()
        await waitFor { state.view.items.count == 150 }
        XCTAssertTrue(state.view.endReached)

        // Typing is debounced: only the last query is requested.
        let before = calls.count
        state.setQuery("a")
        state.setQuery("aa")
        state.setQuery("aap")
        await waitFor { calls.count > before }
        try? await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertEqual(calls.dropFirst(before).map(\.query), ["aap"])

        state.toggle(.dividends)
        await waitFor { calls.last?.filters == [.dividends] }
        XCTAssertEqual(calls.last?.filters, [.dividends])
        state.clear()
        XCTAssertEqual(state.view.query, "")
        XCTAssertEqual(state.view.filters, Set<OperationFilter>())
    }

    func testActivityRechecksForDataOnReload() async {
        var stored = true
        let state = ActivityState(page: { _, _, _, _ in stored ? [self.op(1)] : [] }, hasAny: { stored })
        state.reload()
        await waitFor { state.view.hasAny == true }
        XCTAssertEqual(state.view.hasAny, true)

        stored = false // everything deleted elsewhere
        state.reload()
        await waitFor { state.view.hasAny == false }
        XCTAssertEqual(state.view.hasAny, false)
    }

    func testIncomeLoadsOnReload() async {
        let income = IncomeState { DividendData(events: [], rates: nil) }
        XCTAssertNil(income.data)
        income.reload()
        await waitFor { income.data != nil }
        XCTAssertEqual(income.data, DividendData(events: [], rates: nil))
    }
}
