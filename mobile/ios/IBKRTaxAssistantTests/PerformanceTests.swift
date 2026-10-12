import XCTest
@testable import IBKRTaxAssistant

private func d(_ text: String) -> ExactDecimal { ExactDecimal(text)! }

private func assertAmount(_ expected: String, _ actual: ExactDecimal?, file: StaticString = #filePath, line: UInt = #line) {
    XCTAssertEqual(actual?.compare(d(expected)), 0, "expected \(expected) but was \(String(describing: actual))", file: file, line: line)
}

// 1 USD = 4 PLN, 1 EUR = 4.4 PLN, so 1 EUR = 1.1 USD.
private let rates = FxRates(effectiveDate: "2026-10-09", plnPerUnit: ["USD": d("4"), "EUR": d("4.4")])

private func dividend(_ date: String, _ ticker: String, _ amount: String, _ currency: String = "USD") -> CashEvent {
    CashEvent(date: date, eventType: "DIVIDEND", ticker: ticker, currency: currency, amount: d(amount))
}

private func tax(_ date: String, _ ticker: String, _ amount: String, _ currency: String = "USD") -> CashEvent {
    CashEvent(date: date, eventType: "TAX", ticker: ticker, currency: currency, amount: d(amount))
}

final class CalendarAndScaleTests: XCTestCase {
    func testWithScaleRoundsHalfEvenLikeBigDecimalSetScale() {
        let cases = ["1.005": "1.00", "1.015": "1.02", "1.0051": "1.01", "-2.345": "-2.34", "0.009": "0.01", "0.0009": "0.00", "7": "7.00", "1E+2": "100.00"]
        for (input, expected) in cases { XCTAssertEqual(d(input).withScale(2).description, expected, input) }
    }

    func testDatesParseOnlyStrictIso() {
        XCTAssertEqual(CalendarDay(iso: "2024-02-29"), CalendarDay(year: 2024, month: 2, day: 29))
        XCTAssertNil(CalendarDay(iso: "2023-02-29"))
        XCTAssertNil(CalendarDay(iso: "bad"))
        XCTAssertEqual(CalendarDay(year: 2024, month: 2, day: 29).minusMonths(12), CalendarDay(year: 2023, month: 2, day: 28))
        XCTAssertEqual(CalendarDay(year: 2026, month: 1, day: 31).minusMonths(2), CalendarDay(year: 2025, month: 11, day: 30))
        XCTAssertEqual(YearMonth(iso: "2025-05-18"), YearMonth(year: 2025, month: 5))
        XCTAssertNil(YearMonth(iso: "2025-13-01"))
    }
}

/// Mirrors the Android `DividendsTest`.
final class DividendsTests: XCTestCase {
    func testMonthsHoldGrossTaxAndNet() {
        let year = Dividends.year([dividend("2025-05-18", "AAPL", "3.60"), tax("2025-05-18", "AAPL", "-0.54"), dividend("2025-08-15", "MSFT", "10")], 2025, rates: rates)

        XCTAssertEqual(year.months.count, 12)
        let may = year.months[4].byCurrency["USD"]!
        assertAmount("3.60", may.gross)
        assertAmount("-0.54", may.tax)
        assertAmount("3.06", may.net)
        assertAmount("10", year.months[7].usdNet.amount)
        assertAmount("0", year.months[0].usdNet.amount)
        assertAmount("13.06", year.usdNet.amount)
        assertAmount("-0.54", year.usdTax.amount)
        XCTAssertNil(year.rateDate, "no conversion needed for USD only")
    }

    func testATaxCorrectionCountsInTheMonthOfItsOwnDate() {
        let year = Dividends.year([dividend("2025-03-10", "AAPL", "10"), tax("2025-03-10", "AAPL", "-3"), tax("2025-06-02", "AAPL", "1.5")], 2025, rates: rates)
        assertAmount("7", year.months[2].usdNet.amount)
        assertAmount("1.5", year.months[5].usdNet.amount)
        assertAmount("8.5", year.positions.first?.amounts.net)
    }

    func testCurrenciesStayApartAndTotalInUsd() {
        let year = Dividends.year([dividend("2025-04-01", "SAP", "10", "EUR"), dividend("2025-04-02", "AAPL", "5")], 2025, rates: rates)
        XCTAssertEqual(Set(year.byCurrency.keys), ["EUR", "USD"])
        assertAmount("10", year.byCurrency["EUR"]?.net)
        assertAmount("16", year.usdNet.amount) // 10 EUR = 11 USD, plus 5 USD
        XCTAssertTrue(year.usdNet.isComplete)
        XCTAssertEqual(year.rateDate, "2026-10-09")
        XCTAssertEqual(year.positions.map(\.ticker), ["SAP", "AAPL"]) // 11 USD before 5 USD
    }

    func testAMissingRateMarksTheTotalIncomplete() {
        let year = Dividends.year([dividend("2025-04-01", "NOVO", "100", "DKK"), dividend("2025-04-02", "AAPL", "5")], 2025, rates: rates)
        assertAmount("5", year.usdNet.amount)
        XCTAssertFalse(year.usdNet.isComplete)
        XCTAssertEqual(year.usdNet.missingCurrencies, ["DKK"])
        assertAmount("100", year.byCurrency["DKK"]?.net)
        XCTAssertEqual(year.positions.last?.ticker, "NOVO")
        XCTAssertNil(year.positions.last?.usdNet)
    }

    func testYearsCoverageAndComparisonUseTheMostRecentThreeYears() {
        let events = [dividend("2022-01-05", "KO", "1"), dividend("2024-05-05", "KO", "2"), dividend("2025-05-05", "KO", "3"), dividend("2026-02-05", "KO", "4")]
        XCTAssertEqual(Dividends.years(events), [2026, 2025, 2024, 2022])
        XCTAssertEqual(Dividends.coverage(events)?.first, YearMonth(year: 2022, month: 1))
        XCTAssertEqual(Dividends.coverage(events)?.last, YearMonth(year: 2026, month: 2))

        let compare = Dividends.netByMonthForRecentYears(events, rates: rates)
        XCTAssertEqual(compare.map(\.year), [2024, 2025, 2026])
        assertAmount("3", compare[1].months[4].amount)
        assertAmount("4", compare[2].months[1].amount)
    }

    func testUsdNetEqualsUsdGrossPlusUsdTax() {
        let awkward = FxRates(effectiveDate: "2026-10-09", plnPerUnit: ["USD": d("4.0123"), "EUR": d("4.3987")])
        let year = Dividends.year([dividend("2024-05-20", "SAP", "15.40", "EUR"), tax("2024-05-20", "SAP", "-4.06", "EUR")], 2024, rates: awkward)
        XCTAssertEqual(year.usdGross.amount.withScale(2).adding(year.usdTax.amount.withScale(2)), year.usdNet.amount.withScale(2))
        XCTAssertEqual(year.months[4].usdNet.amount.compare(year.usdNet.amount), 0)
    }

    func testAllYearsAddEveryYearMonthByMonth() {
        let all = Dividends.period([dividend("2024-05-05", "KO", "2"), dividend("2025-05-05", "KO", "3"), dividend("2025-08-05", "KO", "1")], nil, rates: rates)
        XCTAssertNil(all.year)
        assertAmount("6", all.usdNet.amount)
        assertAmount("5", all.months[4].usdNet.amount)
        assertAmount("1", all.months[7].usdNet.amount)
        assertAmount("6", all.positions.first?.amounts.net)
    }

    func testChangeFromYearBeforeComparesTheSameMonthsForTheRunningYear() {
        let events = [dividend("2025-03-01", "KO", "10"), dividend("2025-11-01", "KO", "10"), dividend("2026-03-01", "KO", "12")]
        let today = CalendarDay(year: 2026, month: 10, day: 11)
        assertAmount("20", Dividends.changeFromYearBefore(events, 2026, rates: rates, today: today)) // 12 vs 10
        // A finished year compares all twelve months: 2026 (12) against 2025 (10 + 10) is −40%.
        assertAmount("-40", Dividends.changeFromYearBefore(events, 2026, rates: rates, today: CalendarDay(year: 2027, month: 1, day: 1)))
        XCTAssertNil(Dividends.changeFromYearBefore(events, 2025, rates: rates, today: today)) // nothing in 2024
    }

    func testRowsWithoutAnIsoDateAreLeftOut() {
        let events = [dividend("bad", "KO", "1"), dividend("2025-01-02", "KO", "2")]
        XCTAssertEqual(Dividends.years(events), [2025])
        assertAmount("2", Dividends.year(events, 2025, rates: nil).usdNet.amount)
    }

    func testPayerHistoryListsAllYearsNewestFirstWithTotals() {
        let events = [dividend("2024-05-16", "AAPL", "1.20"), tax("2024-05-16", "AAPL", "-0.18"),
                      dividend("2025-05-15", "AAPL", "1.30"), tax("2025-05-15", "AAPL", "-0.20"), dividend("2025-06-01", "KO", "5")]
        let history = Dividends.history(events, ticker: "AAPL", currency: "USD")
        XCTAssertEqual(history.lines.map(\.date), ["2025-05-15", "2024-05-16"])
        assertAmount("1.10", history.lines.first?.amounts.net)
        assertAmount("2.50", history.total.gross)
        assertAmount("-0.38", history.total.tax)
        assertAmount("2.12", history.total.net)
    }

    func testALaterTaxCorrectionIsItsOwnLine() {
        let history = Dividends.history([dividend("2025-05-15", "AAPL", "1.00"), tax("2025-05-15", "AAPL", "-0.30"), tax("2025-06-20", "AAPL", "0.15")],
                                        ticker: "AAPL", currency: "USD")
        XCTAssertEqual(history.lines.map(\.date), ["2025-06-20", "2025-05-15"])
        assertAmount("0", history.lines.first?.amounts.gross)
        assertAmount("0.15", history.lines.first?.amounts.tax)
        assertAmount("-0.15", history.total.tax)
        assertAmount("0.85", history.total.net)
    }

    func testPayerHistoryKeepsCurrenciesApart() {
        let events = [dividend("2025-03-01", "SHEL", "2", "GBP"), dividend("2025-04-01", "SHEL", "3", "EUR")]
        assertAmount("2", Dividends.history(events, ticker: "SHEL", currency: "GBP").total.gross)
        assertAmount("3", Dividends.history(events, ticker: "SHEL", currency: "EUR").total.gross)
        XCTAssertEqual(Dividends.history(events, ticker: "SHEL", currency: "EUR").lines.count, 1)
    }

    func testPaymentsOfAPeriodNewestFirstWithTaxOnTheSameDay() {
        let events = [dividend("2024-12-02", "KO", "4"), dividend("2025-05-15", "AAPL", "1.00"), tax("2025-05-15", "AAPL", "-0.15"),
                      dividend("2025-05-15", "KO", "2"), dividend("2025-06-03", "SHEL", "3", "EUR")]
        let y2025 = Dividends.payments(events, 2025)
        XCTAssertEqual(y2025.map(\.ticker), ["SHEL", "AAPL", "KO"])
        assertAmount("0.85", y2025[1].amounts.net)
        XCTAssertEqual(y2025.first?.month, YearMonth(year: 2025, month: 6))
        XCTAssertEqual(Dividends.payments(events, nil).count, 4)
    }
}

/// Mirrors the Android `PerformanceTest`.
final class PerformanceTests: XCTestCase {
    private let today = CalendarDay(year: 2026, month: 10, day: 10)
    private var order: Int64 = 0

    private func event(_ date: String, _ type: String, _ ticker: String, _ quantity: String, _ price: String, _ currency: String = "USD", ratio: String? = nil) -> PortfolioEvent {
        order += 1
        return PortfolioEvent(order: order, date: date, eventType: type, ticker: ticker, quantity: d(quantity), price: d(price), currency: currency, isin: "", splitRatio: ratio.map(d))
    }

    private func sales(_ events: [PortfolioEvent]) -> [RealizedSale] {
        guard case .success(_, let realized) = FifoInventory.openLots(events) else { return [] }
        return realized
    }

    private func rows(_ events: [PortfolioEvent], prices: [String: String]) -> (rows: [PositionRow], realized: [RealizedSale]) {
        guard case .success(let lots, let realized) = FifoInventory.openLots(events) else { return ([], []) }
        let quotes = prices.mapValues { price in
            PricedQuote(quote: Quote(symbol: "", price: d(price), currency: "USD", quoteTime: .distantPast, retrievedAt: .distantPast, previousClose: d(price)), freshness: .latestClose)
        }
        return (MainPage.rows(Holdings.fromLots(lots), prices: quotes), realized)
    }

    func testPartialSaleClosesTheOldestLotsFirst() {
        let sale = sales([event("2024-01-02", "BUY", "AAPL", "10", "100"), event("2024-02-02", "BUY", "AAPL", "10", "120"), event("2024-03-02", "SELL", "AAPL", "-15", "130")])[0]
        assertAmount("1950", sale.proceeds)
        assertAmount("1600", sale.cost)
        assertAmount("350", sale.gain)
    }

    func testSaleAfterASplitUsesSplitAdjustedLots() {
        let sale = sales([event("2024-01-02", "BUY", "SAP", "4", "120", "EUR"), event("2024-02-02", "SPLIT", "SAP", "4", "0", "EUR", ratio: "2"),
                          event("2024-03-02", "SELL", "SAP", "-8", "70", "EUR")])[0]
        assertAmount("80", sale.gain) // 8 × (70 − 60)
        XCTAssertEqual(sale.currency, "EUR")
    }

    func testSaleClosingLotsInAnotherCurrencyHasNoGain() {
        let sale = sales([event("2024-01-02", "BUY", "SHEL", "10", "24", "GBP"), event("2024-03-02", "SELL", "SHEL", "-5", "30", "EUR")])[0]
        XCTAssertNil(sale.cost)
        XCTAssertNil(sale.gain)
    }

    func testHeldPositionCombinesPriceGainSalesAndDividends() throws {
        let (rows, realized) = rows([event("2024-01-02", "BUY", "AAPL", "10", "100"), event("2024-03-02", "SELL", "AAPL", "-4", "130")], prices: ["AAPL": "150"])
        let aapl = try XCTUnwrap(Performance.positions(rows, realized: realized, dividends: [dividend("2024-05-01", "AAPL", "5"), tax("2024-05-01", "AAPL", "-0.75")]).first)

        XCTAssertFalse(aapl.isSold)
        assertAmount("600", aapl.invested) // 6 × 100
        assertAmount("300", aapl.priceGain) // 6 × (150 − 100)
        assertAmount("120", aapl.realizedGain) // 4 × (130 − 100)
        assertAmount("4.25", aapl.dividends.net)
        assertAmount("424.25", aapl.total)
        let percent = try XCTUnwrap(aapl.totalPercent)
        XCTAssertTrue(percent.compare(d("70.70")) > 0 && percent.compare(d("70.71")) < 0) // 424.25 / 600
    }

    func testFullySoldPositionsComeAfterHeldOnes() {
        let (rows, realized) = rows([event("2024-01-02", "BUY", "MSFT", "3", "250"), event("2024-02-02", "SELL", "MSFT", "-3", "300"),
                                     event("2024-01-03", "BUY", "AAPL", "1", "100")], prices: ["AAPL": "110"])
        let all = Performance.positions(rows, realized: realized, dividends: [dividend("2023-12-01", "KO", "2")])

        XCTAssertEqual(all.map(\.ticker), ["AAPL", "KO", "MSFT"])
        XCTAssertTrue(all[2].isSold)
        assertAmount("150", all[2].realizedGain)
        assertAmount("150", all[2].total)
        XCTAssertNil(all[2].totalPercent)
        assertAmount("2", all[1].total) // dividends of a never-held (or earlier sold) ticker
    }

    func testPositionWithoutAPriceStillShowsSalesAndDividends() throws {
        let (rows, realized) = rows([event("2024-01-02", "BUY", "AAPL", "10", "100"), event("2024-03-02", "SELL", "AAPL", "-4", "130")], prices: [:])
        let aapl = try XCTUnwrap(Performance.positions(rows, realized: realized, dividends: [dividend("2024-05-01", "AAPL", "5")]).first)
        XCTAssertNil(aapl.priceGain)
        XCTAssertNil(aapl.total)
        assertAmount("120", aapl.realizedGain)
        assertAmount("5", aapl.dividends.net)
    }

    func testSummaryAddsEverythingInUsd() throws {
        let (rows, realized) = rows([event("2024-01-02", "BUY", "AAPL", "10", "100"), event("2024-03-02", "SELL", "AAPL", "-4", "130"),
                                     event("2024-01-02", "BUY", "MSFT", "3", "250"), event("2024-02-02", "SELL", "MSFT", "-3", "300")], prices: ["AAPL": "150"])
        let divs = [dividend("2026-01-15", "AAPL", "5"), tax("2026-01-15", "AAPL", "-0.75"), dividend("2025-06-01", "SAP", "10", "EUR")] // SAP: older than 12 months
        let summary = Performance.summary(Performance.positions(rows, realized: realized, dividends: divs), dividends: divs, rates: rates, today: today)

        assertAmount("900", summary.value.amount)
        assertAmount("600", summary.invested.amount)
        // 300 price gain + 120 + 150 from sales + 4.25 net AAPL dividends + 11 USD (10 EUR) SAP dividends
        assertAmount("585.25", summary.totalProfit.amount)
        XCTAssertTrue(summary.totalProfit.isComplete)
        assertAmount("4.25", summary.dividends12Months.amount)
        let percent = try XCTUnwrap(summary.totalProfitPercent)
        XCTAssertTrue(percent.compare(d("97.5")) > 0 && percent.compare(d("97.6")) < 0)
    }

    func testMissingPriceMarksValueAndProfitIncompleteOnly() {
        let (rows, realized) = rows([event("2024-01-02", "BUY", "AAPL", "10", "100"), event("2024-03-02", "SELL", "AAPL", "-4", "130")], prices: [:])
        let divs = [dividend("2026-02-01", "AAPL", "5")]
        let summary = Performance.summary(Performance.positions(rows, realized: realized, dividends: divs), dividends: divs, rates: rates, today: today)

        XCTAssertFalse(summary.value.isComplete)
        XCTAssertFalse(summary.totalProfit.isComplete)
        assertAmount("125", summary.totalProfit.amount) // sales and dividends still count
        XCTAssertTrue(summary.invested.isComplete)
        XCTAssertTrue(summary.dividends12Months.isComplete)
    }

    func testASaleAcrossCurrenciesMarksProfitIncomplete() {
        let (rows, realized) = rows([event("2024-01-02", "BUY", "SHEL", "10", "24", "GBP"), event("2024-03-02", "SELL", "SHEL", "-10", "30", "EUR")], prices: [:])
        let positions = Performance.positions(rows, realized: realized, dividends: [])
        XCTAssertFalse(positions[0].realizedComplete)
        XCTAssertFalse(Performance.summary(positions, dividends: [], rates: rates, today: today).totalProfit.isComplete)
    }
}

/// Mirrors the Android `TopMoversTest`.
final class TopMoversTests: XCTestCase {
    /// A held position priced at `price` after closing at `previousClose` (nil: no previous close).
    private func row(_ ticker: String, _ price: String, _ previousClose: String?, _ freshness: PriceFreshness = .live) -> PositionRow {
        let holding = Holdings.fromLots([OpenLot(ticker: ticker, isin: "", date: "2024-01-01", quantity: .one, price: .one, currency: "USD")])[0]
        let quote = PricedQuote(quote: Quote(symbol: ticker, price: d(price), currency: "USD", quoteTime: .distantPast, retrievedAt: .distantPast, previousClose: previousClose.map(d)), freshness: freshness)
        return MainPage.rows([holding], prices: [ticker: quote])[0]
    }

    func testRanksByPercentWithUpToFivePerList() throws {
        let rows = [row("A", "101", "100"), row("B", "110", "100"), row("C", "103", "100"), row("D", "104", "100"), row("E", "105", "100"),
                    row("F", "102", "100"), row("G", "95", "100"), row("H", "99", "100"), row("I", "100", "100")]
        let movers = try XCTUnwrap(TopMoversRanking.rank(rows))
        XCTAssertEqual(movers.gainers.map(\.ticker), ["B", "E", "D", "C", "F"])
        XCTAssertEqual(movers.losers.map(\.ticker), ["G", "H"])
    }

    func testOnlyGainsLeavesTheLosersEmpty() throws {
        let movers = try XCTUnwrap(TopMoversRanking.rank([row("A", "101", "100"), row("B", "102", "100")]))
        XCTAssertEqual(movers.gainers.count, 2)
        XCTAssertTrue(movers.losers.isEmpty)
    }

    func testHiddenWithoutPricesOrPreviousCloses() {
        XCTAssertNil(TopMoversRanking.rank([]))
        XCTAssertNil(TopMoversRanking.rank([row("A", "101", nil)]))
        let unpriced = MainPage.rows(Holdings.fromLots([OpenLot(ticker: "SAP", isin: "", date: "2024-01-01", quantity: .one, price: .one, currency: "EUR")]), prices: [:])
        XCTAssertNil(TopMoversRanking.rank(unpriced))
    }

    func testReportsTheWorstFreshness() {
        XCTAssertEqual(TopMoversRanking.rank([row("A", "101", "100")])?.freshness, .live)
        XCTAssertEqual(TopMoversRanking.rank([row("A", "101", "100"), row("B", "99", "100", .latestClose)])?.freshness, .latestClose)
        XCTAssertEqual(TopMoversRanking.rank([row("A", "101", "100", .stale), row("B", "99", "100", .latestClose)])?.freshness, .stale)
    }
}
