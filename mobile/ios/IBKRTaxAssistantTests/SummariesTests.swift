import XCTest
@testable import IBKRTaxAssistant

private func d(_ text: String) -> ExactDecimal { ExactDecimal(text)! }

private func row(_ ticker: String, _ dailyPnl: String?, value: String? = "1000", quantity: String = "10") -> PositionRow {
    PositionRow(
        holding: Holding(ticker: ticker, isin: "US0000000000", currency: "USD", quantity: d(quantity), averagePrice: d("50"), lots: []),
        listingExchange: "NASDAQ", inPriceScope: true, price: nil, cost: d("500"), marketValue: value.map(d),
        dailyChange: nil, dailyChangePercent: nil, dailyPnl: dailyPnl.map(d), unrealizedPnl: nil, displayCurrency: "USD"
    )
}

private func totals(_ day: String, missingValues: Int = 0, missingDaily: Int = 0) -> UsdTotals {
    UsdTotals(marketValue: d("4000"), dailyPnl: d(day), dailyPnlPercent: nil, missingValues: missingValues, missingDailyPnl: missingDaily, rateDate: nil)
}

private func summary(_ percent: String?) -> PortfolioSummary {
    PortfolioSummary(
        value: UsdAmount(amount: d("4000"), isComplete: true), invested: UsdAmount(amount: d("1600"), isComplete: true),
        totalProfit: UsdAmount(amount: d("2400"), isComplete: true), totalProfitPercent: percent.map(d),
        dividends12Months: UsdAmount(amount: .zero, isComplete: true)
    )
}

private let events = [
    CashEvent(date: "2024-05-16", eventType: "DIVIDEND", ticker: "AAPL", currency: "USD", amount: d("10")),
    CashEvent(date: "2025-05-15", eventType: "DIVIDEND", ticker: "AAPL", currency: "USD", amount: d("12")),
    CashEvent(date: "2025-05-15", eventType: "TAX", ticker: "AAPL", currency: "USD", amount: d("-1.80")),
]

/// Mirrors the Android `SummariesTest`.
final class SummariesTests: XCTestCase {
    override func setUp() { Formats.locale = Locale(identifier: "en_US") }
    override func tearDown() { Formats.locale = .current }

    func testUpDayNamesTheTwoLargestContributors() {
        let rows = [row("NVDA", "70"), row("SAP", "45"), row("AAPL", "5"), row("KO", "-10")]
        let s = Summaries.today(rows, totals: totals("110"), summary: summary("149.49"), rates: nil)
        XCTAssertEqual(s.text, "You’re up $110 today, mostly from NVDA and SAP. Since you started, your money grew by 149%.")
        XCTAssertEqual(s.parts[1].tone, .gain)
    }

    func testDownDayNamesTheLargestFallsAndAShrinkingReturn() {
        let rows = [row("NVDA", "-80"), row("SAP", "20"), row("AAPL", "-30")]
        let s = Summaries.today(rows, totals: totals("-90"), summary: summary("-5.4"), rates: nil)
        XCTAssertEqual(s.text, "You’re down $90 today, mostly from NVDA and AAPL. Since you started, your money shrank by 5%.")
        XCTAssertEqual(s.parts[1].tone, .loss)
    }

    func testFlatDayAndUnknownReturn() {
        let s = Summaries.today([row("AAPL", "0.2")], totals: totals("0.2"), summary: summary(nil), rates: nil)
        XCTAssertEqual(s.text, "Your portfolio is flat today.")
    }

    func testMissingPricesAreNoted() {
        let rows = [row("AAPL", "12"), row("SAP", nil, value: nil)]
        let s = Summaries.today(rows, totals: totals("12", missingValues: 1, missingDaily: 1), summary: summary("10"), rates: nil)
        XCTAssertEqual(
            s.text,
            "You’re up $12 today, mostly from AAPL. Since you started, your money grew by 10%. Some prices are missing, so these figures are incomplete."
        )
    }

    func testNoDailyChangeAtAll() {
        let s = Summaries.today([row("AAPL", nil)], totals: totals("0", missingDaily: 1), summary: summary(nil), rates: nil)
        XCTAssertEqual(s.text, "Today’s change is not known yet. Some prices are missing, so these figures are incomplete.")
    }

    func testIncomeChangeForTheCurrentAndAPastYear() {
        let y2025 = Dividends.period(events, 2025, rates: nil)
        XCTAssertEqual(
            Summaries.income(y2025, change: d("2.0"), period: .year(2025, isCurrent: true)).text,
            "This year your companies paid you $10.20 after tax: 2% more than the same months last year."
        )
        XCTAssertEqual(
            Summaries.income(y2025, change: d("-15.2"), period: .year(2025, isCurrent: false)).text,
            "In 2025 your companies paid you $10.20 after tax: 15% less than the year before."
        )
        XCTAssertEqual(
            Summaries.income(Dividends.period(events, 2024, rates: nil), change: nil, period: .year(2024, isCurrent: false)).text,
            "In 2024 your companies paid you $10.00 after tax."
        )
    }

    func testIncomeForAllYears() {
        XCTAssertEqual(
            Summaries.income(Dividends.period(events, nil, rates: nil), change: nil, period: .allYears(firstYear: 2024)).text,
            "Since 2024 your companies paid you $20.20 after $1.80 tax."
        )
    }

    func testStoryTellsInvestedValueSalesDividendsAndTotal() {
        let p = PositionPerformance(
            ticker: "AAPL", currency: "USD", row: row("AAPL", "5", value: "3029.76", quantity: "9"),
            invested: d("1470"), priceGain: d("1559.76"), realizedGain: d("200"), realizedComplete: true,
            dividends: DividendAmounts(gross: d("3.60"), tax: d("-0.54"))
        )
        XCTAssertEqual(Summaries.story(p).map(\.text), [
            "You put in $1,470.00 for your 9 shares. They are now worth $3,029.76.",
            "On top of that, you earned $200.00 from shares you sold, and $3.06 in dividends after $0.54 tax.",
            "All in all: +$1,762.82 · +120%",
        ])
    }

    func testStoryWithoutPriceOrIncome() {
        let p = PositionPerformance(
            ticker: "SAP", currency: "EUR", row: row("SAP", nil, value: nil, quantity: "3"),
            invested: d("400"), priceGain: nil, realizedGain: .zero, realizedComplete: true, dividends: DividendAmounts()
        )
        XCTAssertEqual(Summaries.story(p).map(\.text), [
            "You put in €400.00 for your 3 shares. Their current price is not available.",
            "There were no sales or dividends so far.",
            "All in all: not known yet, because the price is missing.",
        ])
    }

    func testWithScaleHalfUpRoundsHalvesAwayFromZero() {
        XCTAssertEqual(d("2.5").withScale(0, halfUp: true).description, "3")
        XCTAssertEqual(d("-2.5").withScale(0, halfUp: true).description, "-3")
        XCTAssertEqual(d("2.5").withScale(0).description, "2")
    }
}
