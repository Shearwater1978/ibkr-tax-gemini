import CryptoKit
import XCTest
@testable import IBKRTaxAssistant

/// AES-GCM with an in-memory key, standing in for the device key in unit tests.
struct SoftwareKeyWrapper: KeyWrapper {
    var key = SymmetricKey(size: .bits256)

    func wrap(_ secret: Data) throws -> Data { try AES.GCM.seal(secret, using: key).combined! }

    func unwrap(_ wrapped: Data) throws -> Data {
        do { return try AES.GCM.open(AES.GCM.SealedBox(combined: wrapped), using: key) } catch { throw KeyUnwrapError() }
    }
}

/// Same cases as the Android `SecurityPrimitivesTest` and `FileNameMaskingTest`.
final class SecurityPrimitivesTests: XCTestCase {
    private var directory: URL!

    override func setUpWithError() throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try FileManager.default.removeItem(at: directory)
    }

    func testWrappedKeyIsCreatedOnceAndStoredOnlyWrapped() throws {
        let wrapper = SoftwareKeyWrapper()
        let file = directory.appendingPathComponent("db.key")

        let first = try WrappedKeyProvider(file: file, wrapper: wrapper).getOrCreate()
        XCTAssertEqual(first.count, 32)
        XCTAssertEqual(try WrappedKeyProvider(file: file, wrapper: wrapper).getOrCreate(), first)
        XCTAssertNil(try Data(contentsOf: file).range(of: first))
    }

    func testUnreadableWrappedKeyIsNeverReplaced() throws {
        let file = directory.appendingPathComponent("db.key")
        _ = try WrappedKeyProvider(file: file, wrapper: SoftwareKeyWrapper()).getOrCreate()
        let before = try Data(contentsOf: file)

        XCTAssertThrowsError(try WrappedKeyProvider(file: file, wrapper: SoftwareKeyWrapper()).getOrCreate()) {
            XCTAssertTrue($0 is KeyUnwrapError)
        }
        XCTAssertEqual(try Data(contentsOf: file), before)
    }

    func testPseudonymsAreStableKeyedAndDomainSeparated() throws {
        let key = Data(repeating: 7, count: 32)
        let pseudonymizer = Pseudonymizer { key }
        let pseudonym = try pseudonymizer.pseudonym(.account, "U00000001")

        XCTAssertEqual(pseudonym.count, 64)
        XCTAssertFalse(pseudonym.contains("00000001"))
        XCTAssertEqual(try pseudonymizer.pseudonym(.account, " U00000001 "), pseudonym)
        XCTAssertNotEqual(try pseudonymizer.pseudonym(.taxId, "U00000001"), pseudonym)
        XCTAssertNotEqual(try Pseudonymizer { Data(repeating: 8, count: 32) }.pseudonym(.account, "U00000001"), pseudonym)
    }

    func testPseudonymInputMatchesAndroid() throws {
        // HMAC-SHA256(key, "ACCOUNT:U00000001"), as the Android Pseudonymizer computes it.
        let key = Data(repeating: 7, count: 32)
        let expected = HMAC<SHA256>.authenticationCode(for: Data("ACCOUNT:U00000001".utf8), using: SymmetricKey(data: key))
            .map { String(format: "%02x", $0) }.joined()
        XCTAssertEqual(try Pseudonymizer { key }.pseudonym(.account, "U00000001"), expected)
    }

    func testMaskingShowsOnlyLastFourCharacters() {
        XCTAssertEqual(Masking.lastFour("U00000001"), "•••••0001")
        XCTAssertEqual(Masking.lastFour("1234"), "••••")
        XCTAssertEqual(Masking.lastFour(""), "••••")
        XCTAssertTrue(Masking.lastFour("Synthetic Test User").hasSuffix("User"))
    }

    func testMasksAccountNumbersInIbkrFileNames() {
        XCTAssertEqual(FileNameMasking.mask("U1234567_2023.csv"), "U•••4567_2023.csv")
        XCTAssertEqual(FileNameMasking.mask("U12345678.csv"), "U••••5678.csv")
        XCTAssertEqual(FileNameMasking.mask("DU1234567_activity.csv"), "DU•••4567_activity.csv")
        XCTAssertEqual(FileNameMasking.mask("Flex U1234567 2023.csv"), "Flex U•••4567 2023.csv")
    }

    func testLeavesOtherFileNamesAlone() {
        XCTAssertEqual(FileNameMasking.mask("valid_basic.csv"), "valid_basic.csv")
        XCTAssertEqual(FileNameMasking.mask("report_20230101_20231231.csv"), "report_20230101_20231231.csv")
        XCTAssertEqual(FileNameMasking.mask("Q12.csv"), "Q12.csv")
    }
}
