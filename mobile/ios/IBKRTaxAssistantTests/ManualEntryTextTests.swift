import XCTest
@testable import IBKRTaxAssistant

/// What the manual-entry form accepts as a number before validation; mirrors the Android `ManualEntryTextTest`.
final class ManualEntryTextTests: XCTestCase {
    func testACommaWorksAsTheDecimalSeparator() {
        XCTAssertEqual(plainDecimal(" 150,25 "), "150.25")
        XCTAssertEqual(plainDecimal("150.25"), "150.25")
        XCTAssertEqual(plainDecimal("1 500"), "1500")
        // Ambiguous text stays as typed, so validation rejects it instead of guessing.
        XCTAssertEqual(plainDecimal("1,500.25"), "1,500.25")
        XCTAssertEqual(plainDecimal("1,2,3"), "1,2,3")
    }

    func testCommonCurrenciesAndOneOutsideTheList() {
        XCTAssertEqual(ManualCurrencies.choices(including: "USD"), ManualCurrencies.common)
        XCTAssertEqual(ManualCurrencies.choices(including: nil).first, "USD")
        XCTAssertEqual(ManualCurrencies.choices(including: "ZAR"), ["ZAR"] + ManualCurrencies.common)
        XCTAssertEqual(Set(ManualCurrencies.common).count, ManualCurrencies.common.count)
    }

    func testCalendarDayIso() {
        XCTAssertEqual(CalendarDay(year: 2026, month: 3, day: 7).iso, "2026-03-07")
    }
}
