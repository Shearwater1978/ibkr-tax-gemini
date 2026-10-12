import XCTest
@testable import IBKRTaxAssistant

/**
 * Runs on the simulator (counterpart of the Android `EncryptedStorageInstrumentedTest`). The
 * simulator has no Data Protection, so file protection is recorded, not asserted;
 * physical-device checks are separate (mobile-app-core task 5.2).
 */
final class EncryptedStorageTests: XCTestCase {
    private let testTag = "ibkrtax.test.wrap"
    private var directory: URL!

    override func setUpWithError() throws {
        directory = try SecureFiles.directory(base: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString))
        try DeviceKeys.delete(tag: testTag)
    }

    override func tearDownWithError() throws {
        try FileManager.default.removeItem(at: directory.deletingLastPathComponent())
        try DeviceKeys.delete(tag: testTag)
    }

    func testDeviceKeyIsNonExportableAndReused() throws {
        let key = try DeviceKeys.privateKey(tag: testTag)
        var error: Unmanaged<CFError>?
        XCTAssertNil(SecKeyCopyExternalRepresentation(key, &error), "the private key must not be exportable")
        error?.release()
        XCTAssertEqual(SecKeyCopyPublicKey(try DeviceKeys.privateKey(tag: testTag)).map(publicBytes), SecKeyCopyPublicKey(key).map(publicBytes))
        XCTAssertTrue(DeviceKeys.isSecureEnclave(key))
    }

    func testDeviceKeyWrapperRoundTripsAndRejectsTampering() throws {
        let wrapper = DeviceKeyWrapper(tag: testTag)
        let secret = Data((0..<32).map { UInt8($0) })
        var wrapped = try wrapper.wrap(secret)

        XCTAssertEqual(try wrapper.unwrap(wrapped), secret)
        wrapped[wrapped.count - 1] ^= 1
        XCTAssertThrowsError(try wrapper.unwrap(wrapped))
        XCTAssertThrowsError(try wrapper.unwrap(Data()))
    }

    func testSecureFolderIsProtectedAndExcludedFromBackup() throws {
        let values = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(values.isExcludedFromBackup, true)
        _ = try EncryptedDatabase.open(directory: directory)
        let protection = try FileManager.default.attributesOfItem(atPath: directory.appendingPathComponent(EncryptedDatabase.name).path)[.protectionKey]
        print("EncryptedStorageTests dbProtection=\(String(describing: protection))")
    }

    func testDatabaseFileIsEncryptedAtRest() throws {
        let database = try EncryptedDatabase.open(directory: directory)
        try database.execute("INSERT INTO schema_info (key, value) VALUES ('marker', ?)", [.text("SYNTHETIC_PLAINTEXT_MARKER")])
        database.close()

        let bytes = try Data(contentsOf: directory.appendingPathComponent(EncryptedDatabase.name))
        XCTAssertNotEqual(bytes.prefix(15), Data("SQLite format 3".utf8), "SQLite plaintext header found")
        XCTAssertNil(bytes.range(of: Data("SYNTHETIC_PLAINTEXT_MARKER".utf8)), "plaintext value found in file")
        XCTAssertTrue(FileManager.default.fileExists(atPath: directory.appendingPathComponent("\(EncryptedDatabase.name).key").path))
    }

    func testReopeningWithTheDeviceKeyReadsData() throws {
        try EncryptedDatabase.open(directory: directory).execute("INSERT INTO schema_info (key, value) VALUES ('marker', 'kept')")
        let rows = try EncryptedDatabase.open(directory: directory).query("SELECT value FROM schema_info WHERE key = 'marker'")
        XCTAssertEqual(rows, [[.text("kept")]])
    }

    func testDatabaseCannotBeReadWithoutTheKey() throws {
        _ = try EncryptedDatabase.open(directory: directory)
        XCTAssertThrowsError(try EncryptedDatabase(file: directory.appendingPathComponent(EncryptedDatabase.name), key: Data(count: 32)))
    }

    func testFailedTransactionLeavesNoRecords() throws {
        let database = try EncryptedDatabase.open(directory: directory)
        XCTAssertThrowsError(try database.transaction {
            try database.execute("INSERT INTO app_settings (key, value) VALUES ('a', '1')")
            try database.execute("INSERT INTO app_settings (key, value) VALUES ('a', '2')")
        })
        XCTAssertEqual(try database.query("SELECT count(*) FROM app_settings"), [[.integer(0)]])
    }

    func testDeleteRemovesDatabaseAndKey() throws {
        _ = try EncryptedDatabase.open(directory: directory)
        try EncryptedDatabase.delete(directory: directory)
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: directory.path), [])
    }

    private func publicBytes(_ key: SecKey) -> Data? {
        SecKeyCopyExternalRepresentation(key, nil) as Data?
    }
}

/**
 * Every stored column has a classification, and no stored column holds a direct identifier
 * (data-anonymization spec "Data classification", scenario "New field added").
 */
final class DataClassificationTests: XCTestCase {
    func testEveryStoredColumnIsClassified() throws {
        let base = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: base) }
        let database = try EncryptedDatabase(file: try SecureFiles.directory(base: base).appendingPathComponent("schema.db"), key: Data(count: 32))

        var schema = Set<String>()
        for case .text(let table)? in try database.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'").map(\.first) {
            for row in try database.query("SELECT name FROM pragma_table_info(?)", [.text(table)]) {
                if case .text(let column)? = row.first { schema.insert("\(table).\(column)") }
            }
        }
        XCTAssertGreaterThan(schema.count, 30, "schema not read: \(schema)")
        XCTAssertEqual(schema.subtracting(DataClassification.columns.keys), [], "unclassified columns")
        XCTAssertEqual(Set(DataClassification.columns.keys).subtracting(schema), [], "classified columns that do not exist")
    }

    func testDirectIdentifiersAreNeverStored() {
        XCTAssertTrue(DataClassification.columns.values.allSatisfy { $0.dataClass != .directIdentifier })
        XCTAssertTrue(DataClassification.reportFields.values.allSatisfy { $0.dataClass == .directIdentifier })
    }
}
