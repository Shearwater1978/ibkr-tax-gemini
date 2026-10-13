import XCTest
@testable import IBKRTaxAssistant

/// The encrypted manual journal: format, write-verify-replace, retry and restore; mirrors the Android `ManualJournalTest`.
final class ManualJournalTests: XCTestCase {
    private final class MemoryJournalStore: JournalStore {
        var stored: [ManualEntry] = []
        var state = JournalState()

        func entries() throws -> [ManualEntry] { stored }
        func journalState() throws -> JournalState { state }
        func setJournalState(_ state: JournalState) throws { self.state = state }
        func restoreEntries(_ entries: [ManualEntry]) throws { stored = entries }
    }

    private let fixture = Fixtures.raw("manual/journal_v1.json")
    private let dataKey = Data(0..<32)
    private var folder: MemoryFolder!
    private var store: MemoryJournalStore!
    private var key: Data?
    private var target: BackupFolder?
    private var pendingDirectory: URL!

    override func setUpWithError() throws {
        folder = MemoryFolder()
        store = MemoryJournalStore()
        key = dataKey
        target = folder
        pendingDirectory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: pendingDirectory)
    }

    private var backup: ManualJournalBackup {
        ManualJournalBackup(dataKey: { self.key }, folder: { self.target }, store: store, pendingDirectory: pendingDirectory,
                            now: { Date(timeIntervalSince1970: 1_791_878_400) })
    }

    private var fixtureEntries: [ManualEntry] { ManualJournal.decode(fixture)!.entries }

    private func decrypted(_ name: String) throws -> ManualJournal.Decoded {
        try XCTUnwrap(ManualJournal.decode(try BackupFileFormat.decrypt(dataKey: dataKey, file: try XCTUnwrap(folder.files[name]))))
    }

    private func object(_ data: Data) throws -> NSDictionary { try XCTUnwrap(try JSONSerialization.jsonObject(with: data) as? NSDictionary) }

    func testTheFixtureDecodesAndEncodesToTheSameJson() throws {
        let decoded = try XCTUnwrap(ManualJournal.decode(fixture))
        XCTAssertEqual(decoded.sequence, 12)
        XCTAssertEqual(decoded.entries.count, 14)
        XCTAssertEqual(decoded.entries.first?.entryId, "00000000-0000-4000-8000-000000000001")
        XCTAssertTrue(decoded.entries.first?.fields.contains(#""price":"150.00""#) == true)

        let encoded = ManualJournal.encode(decoded.entries, sequence: decoded.sequence, savedAt: "2026-10-13T08:00:00Z")
        XCTAssertEqual(try object(encoded), try object(fixture))
    }

    func testTheAndroidBuiltEncryptedJournalDecrypts() throws {
        let plain = try BackupFileFormat.decrypt(dataKey: dataKey, file: Fixtures.raw("manual/journal_v1.bin"))
        XCTAssertEqual(ManualJournal.decode(plain)?.entries.count, 14)
    }

    func testReportsAndNewerJournalsAreNotJournals() throws {
        XCTAssertNil(ManualJournal.decode(Fixtures.flexQuery(Fixtures.validBasic)))
        let newer = try object(fixture).mutableCopy() as! NSMutableDictionary
        newer["version"] = 2
        XCTAssertNil(ManualJournal.decode(try JSONSerialization.data(withJSONObject: newer)))
    }

    func testEachChangeWritesAVerifiedJournalAndThenRemovesThePreviousOne() throws {
        store.stored = Array(fixtureEntries.prefix(2))
        XCTAssertEqual(try backup.changed(), .backedUp)
        let first = try XCTUnwrap(store.state.file)
        XCTAssertEqual(try decrypted(first).sequence, 1)

        store.stored = Array(fixtureEntries.prefix(1))
        XCTAssertEqual(try backup.changed(), .backedUp)
        let second = try XCTUnwrap(store.state.file)
        XCTAssertEqual(Array(folder.files.keys), [second])
        XCTAssertEqual(try decrypted(second).sequence, 2)
        XCTAssertEqual(try decrypted(second).entries.count, 1)
        XCTAssertFalse(backup.isPending)
    }

    func testAnUnreachableFolderKeepsTheJournalPendingUntilARetry() throws {
        store.stored = Array(fixtureEntries.prefix(1))
        folder.failWrites = true
        XCTAssertEqual(try backup.changed(), .pending)
        XCTAssertTrue(backup.isPending)
        XCTAssertTrue(folder.files.isEmpty)

        folder.failWrites = false
        XCTAssertEqual(try backup.sync(), .backedUp)
        XCTAssertFalse(backup.isPending)
        XCTAssertEqual(folder.files.count, 1)
    }

    func testWithoutBackupKeysTheChangeIsRememberedAndWrittenOnceKeysExist() throws {
        key = nil
        store.stored = Array(fixtureEntries.prefix(1))
        XCTAssertEqual(try backup.changed(), .pending)
        XCTAssertFalse(backup.isPending)
        XCTAssertTrue(store.state.stale)

        key = dataKey
        XCTAssertTrue(backup.isPending)
        XCTAssertEqual(try backup.sync(), .backedUp)
        XCTAssertEqual(try decrypted(try XCTUnwrap(store.state.file)).entries.count, 1)
    }

    func testTheBackupServiceRetriesThePendingJournal() throws {
        let service = BackupService(dataKey: { self.key }, manifestJson: { "{}" }, folder: { self.target }, reports: MemoryReports(),
                                    pendingDirectory: pendingDirectory, journal: backup)
        store.stored = Array(fixtureEntries.prefix(1))
        target = nil
        try backup.changed()
        XCTAssertEqual(try service.pendingCount(), 1)

        target = folder
        let result = try service.retryPending()
        XCTAssertEqual(result.backedUp, 1)
        XCTAssertEqual(result.stillPending, 0)
    }

    func testRestoreBringsBackTheNewestJournalAfterTheReports() async throws {
        let vault = KeyVault(argon2: Argon2idParams(memoryKiB: 1024, iterations: 1, parallelism: 1))
        let oldPhone = try vault.setUp(passphrase: "correct horse battery")
        try folder.write(BackupService.manifestFile, Data(oldPhone.manifest.toJson().utf8))
        try folder.write("aa.bin", try BackupFileFormat.encrypt(dataKey: oldPhone.dataKey, plaintext: Fixtures.flexQuery(Fixtures.validBasic)))
        try folder.write("bb.bin", try BackupFileFormat.encrypt(dataKey: oldPhone.dataKey,
                                                              plaintext: ManualJournal.encode(Array(fixtureEntries.prefix(3)), sequence: 4, savedAt: "x")))
        try folder.write("cc.bin", try BackupFileFormat.encrypt(dataKey: oldPhone.dataKey, plaintext: fixture))
        let reports = MemoryReports()

        let outcome = await RestoreService(reports: reports, vault: vault, journal: backup)
            .restore(folder, secret: .passphrase("correct horse battery")) { _, _ in }

        guard case .restored(let restored, _, _, let unreadable, let manual) = outcome else { return XCTFail("\(outcome)") }
        XCTAssertEqual([restored, unreadable, manual], [1, 0, 14])
        XCTAssertEqual(reports.imported.count, 1)
        XCTAssertEqual(store.stored.count, 14)
        XCTAssertEqual(store.state, JournalState(sequence: 12, file: "cc.bin"))
    }
}
