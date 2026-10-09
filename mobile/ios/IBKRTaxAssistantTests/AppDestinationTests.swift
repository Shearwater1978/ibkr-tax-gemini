import XCTest
@testable import IBKRTaxAssistant

final class AppDestinationTests: XCTestCase {
    // Must stay identical to the Android AppDestinationTest expectations.
    func testDestinationsMatchSharedNavigationContract() {
        XCTAssertEqual(AppDestination.allCases.map(\.rawValue), ["portfolio", "imports", "settings"])
    }

    func testStartDestinationIsPortfolio() {
        XCTAssertEqual(AppDestination.start, .portfolio)
    }
}
