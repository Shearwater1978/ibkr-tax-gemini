import XCTest
@testable import IBKRTaxAssistant

/// In-memory folder that can fail, lose access, or corrupt what it stores.
final class MemoryFolder: BackupFolder {
    var files: [String: Data] = [:]
    var order: [String] = []
    var failWrites = false
    var accessLost = false
    var corrupt = false
    var failDeletes = false

    func write(_ name: String, _ bytes: Data) throws {
        if accessLost { throw BackupFolderError.accessLost }
        if failWrites { throw BackupFolderError.unavailable }
        if files[name] == nil { order.append(name) }
        files[name] = corrupt ? bytes.dropLast() : bytes
    }

    func read(_ name: String) throws -> Data? {
        if accessLost { throw BackupFolderError.accessLost }
        return files[name]
    }

    func list() throws -> [String] {
        if accessLost { throw BackupFolderError.accessLost }
        return order.filter { files[$0] != nil }
    }

    func delete(_ name: String) throws {
        if accessLost || failDeletes { throw BackupFolderError.accessLost }
        files[name] = nil
    }
}

/// The report store as the backup service sees it.
final class MemoryReports: ReportStore {
    var imported: [Data] = []
    var status: [(name: String, state: String)] = []

    func importReport(_ bytes: Data, backupFile: String?) throws -> ImportResult {
        if imported.contains(bytes) { return .alreadyImported }
        imported.append(bytes)
        status.append((backupFile!, "pending"))
        return .imported(reportId: Int64(imported.count), inserted: 9, skipped: 0)
    }

    func pendingBackups() -> [String] { status.filter { $0.state == "pending" }.map(\.name) }

    func markBackedUp(_ backupFile: String) {
        if let index = status.firstIndex(where: { $0.name == backupFile }) { status[index].state = "backed_up" }
    }

    func deleteReport(_ reportId: Int64) -> DeletedImports? {
        guard reportId >= 1, Int(reportId) <= status.count else { return nil }
        let name = status.remove(at: Int(reportId) - 1).name
        return DeletedImports(removedRecords: 9, backupFiles: [name])
    }

    func deleteAll() -> DeletedImports {
        let names = status.map(\.name)
        status = []
        imported = []
        return DeletedImports(removedRecords: 11, backupFiles: names)
    }
}

/// Mirrors the Android `BackupServiceTest`.
final class BackupServiceTests: XCTestCase {
    private let dataKey = KeyVault.randomBytes(32)
    private let folder = MemoryFolder()
    private let reports = MemoryReports()
    private var pendingDirectory: URL!
    private let report = Fixtures.flexQuery(Fixtures.validBasic)

    override func setUp() {
        pendingDirectory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: pendingDirectory)
    }

    private func service(key: Data?? = .none, target: BackupFolder?? = .none) -> BackupService {
        let key = key ?? dataKey
        let target = target ?? folder
        return BackupService(dataKey: { key }, manifestJson: { "{\"version\":1}" }, folder: { target }, reports: reports, pendingDirectory: pendingDirectory)
    }

    private var pendingFiles: [String] { (try? FileManager.default.contentsOfDirectory(atPath: pendingDirectory.path)) ?? [] }

    func testFileFormatRoundTripsAndRejectsTamperingAndNewerVersions() throws {
        let encrypted = try BackupFileFormat.encrypt(dataKey: dataKey, plaintext: report)
        XCTAssertEqual(try BackupFileFormat.decrypt(dataKey: dataKey, file: encrypted), report)
        XCTAssertNil(encrypted.range(of: Data("U00000001".utf8)), "ciphertext must not contain the report")
        XCTAssertEqual(encrypted.prefix(5), Data("IBKB".utf8) + Data([1]))
        XCTAssertEqual(encrypted.count, 33 + report.count + 16)
        XCTAssertNotEqual(try BackupFileFormat.encrypt(dataKey: dataKey, plaintext: report), encrypted, "each file gets its own id and nonce")

        XCTAssertThrowsError(try BackupFileFormat.decrypt(dataKey: Data(count: 32), file: encrypted)) { XCTAssertTrue($0 is CorruptBackupError) }
        var tampered = encrypted
        tampered[30] ^= 1
        XCTAssertThrowsError(try BackupFileFormat.decrypt(dataKey: dataKey, file: tampered)) { XCTAssertTrue($0 is CorruptBackupError) }
        var newer = encrypted
        newer[4] = 2
        XCTAssertThrowsError(try BackupFileFormat.decrypt(dataKey: dataKey, file: newer)) { XCTAssertEqual($0 as? UnsupportedBackupVersionError, UnsupportedBackupVersionError(version: 2)) }
        XCTAssertThrowsError(try BackupFileFormat.decrypt(dataKey: dataKey, file: Data("not a backup".utf8))) { XCTAssertTrue($0 is CorruptBackupError) }
    }

    func testBackupFileBuiltElsewhereDecrypts() throws {
        // Encrypted with pyca/cryptography in the Android BackupFileFormat layout (mobile/fixtures/keys).
        let file = Fixtures.raw("keys/valid_basic_v1.bin")
        XCTAssertEqual(try BackupFileFormat.decrypt(dataKey: Data(0..<32), file: file), report)
    }

    func testImportIsOffUntilKeysAndFolderExist() throws {
        XCTAssertEqual(try service(key: .some(nil)).importReport(report), .notReady)
        XCTAssertEqual(try service(target: .some(nil)).importReport(report), .notReady)
        XCTAssertTrue(reports.imported.isEmpty)
    }

    func testImportWritesAVerifiedEncryptedBackupWithANeutralName() throws {
        XCTAssertEqual(try service().importReport(report), .imported(inserted: 9, skipped: 0, backup: .backedUp))
        let name = try XCTUnwrap(folder.files.keys.first)
        XCTAssertEqual(folder.files.count, 1)
        XCTAssertTrue(BackupService.isBackupFileName(name))
        XCTAssertEqual(try BackupFileFormat.decrypt(dataKey: dataKey, file: folder.files[name]!), report)
        XCTAssertEqual(reports.pendingBackups(), [])
        XCTAssertEqual(pendingFiles, [], "local copy removed after verification")
    }

    func testUnsupportedFilesGetNeitherBackupNorRecords() throws {
        XCTAssertEqual(try service().importReport(Fixtures.flexQuery(Fixtures.unsupportedFormat)), .rejected(.unparseable(.unsupportedFormat)))
        XCTAssertTrue(folder.files.isEmpty)
        XCTAssertTrue(reports.imported.isEmpty)
        XCTAssertEqual(pendingFiles, [])
    }

    func testDuplicateImportLeavesNoBackup() throws {
        _ = try service().importReport(report)
        XCTAssertEqual(try service().importReport(report), .alreadyImported)
        XCTAssertEqual(folder.files.count, 1)
        XCTAssertEqual(pendingFiles, [])
    }

    func testOfflineFolderKeepsTheImportAndAPendingEncryptedCopy() throws {
        folder.failWrites = true
        XCTAssertEqual(try service().importReport(report), .imported(inserted: 9, skipped: 0, backup: .pending))
        XCTAssertEqual(reports.imported.count, 1)
        let name = try XCTUnwrap(reports.pendingBackups().first)
        XCTAssertEqual(try BackupFileFormat.decrypt(dataKey: dataKey, file: Data(contentsOf: pendingDirectory.appendingPathComponent(name))), report)

        folder.failWrites = false
        XCTAssertEqual(try service().retryPending(), RetryResult(backedUp: 1, stillPending: 0, accessLost: false))
        XCTAssertNotNil(folder.files[name])
        XCTAssertNotNil(folder.files[BackupService.manifestFile], "manifest written on retry")
    }

    func testFailedVerificationRemovesTheBadCopyAndStaysPending() throws {
        folder.corrupt = true
        XCTAssertEqual(try service().importReport(report), .imported(inserted: 9, skipped: 0, backup: .pending))
        XCTAssertTrue(folder.files.isEmpty, "bad copy deleted from the folder")
        XCTAssertEqual(reports.pendingBackups().count, 1)
    }

    func testLostFolderAccessIsReported() throws {
        folder.accessLost = true
        XCTAssertEqual(try service().importReport(report), .imported(inserted: 9, skipped: 0, backup: .accessLost))
        XCTAssertEqual(try service().retryPending(), RetryResult(backedUp: 0, stillPending: 1, accessLost: true))
        XCTAssertEqual(try service().syncManifest(), .accessLost)
    }

    func testDeletingAnImportRemovesItsBackupFile() throws {
        _ = try service().importReport(report)
        let name = try XCTUnwrap(folder.files.keys.first)

        XCTAssertEqual(try service().deleteImport(1), DeletionOutcome(removedRecords: 9, backupsDeleted: 1, backupsLeft: 0))
        XCTAssertTrue(folder.files.isEmpty)
        XCTAssertNil(try service().deleteImport(1))
        XCTAssertFalse(FileManager.default.fileExists(atPath: pendingDirectory.appendingPathComponent(name).path))
    }

    func testDeletingAllKeepsTheManifestAndCountsBackupsLeftWhenTheFolderIsGone() throws {
        _ = try service().importReport(report)
        _ = try service().importReport(Fixtures.flexQuery(Fixtures.validFollowup))
        _ = try service().syncManifest()
        folder.failDeletes = true

        XCTAssertEqual(try service().deleteAllImports(), DeletionOutcome(removedRecords: 11, backupsDeleted: 0, backupsLeft: 2))
        XCTAssertTrue(reports.pendingBackups().isEmpty)
        XCTAssertNotNil(folder.files[BackupService.manifestFile])
    }

    func testEraseAllRemovesRecordsEveryBackupFileAndTheManifestButNothingElse() throws {
        _ = try service().importReport(report)
        _ = try service().syncManifest()
        try folder.write("0123456789abcdef0123456789abcdef.bin", Data(count: 8)) // orphan from an earlier deletion
        try folder.write("holiday.jpg", Data(count: 8)) // not ours

        XCTAssertEqual(try service().eraseAll(), FolderErasure(removedRecords: 11, filesDeleted: 3, folderReachable: true))
        XCTAssertEqual(try folder.list(), ["holiday.jpg"])
        XCTAssertTrue(reports.status.isEmpty)
        XCTAssertFalse(FileManager.default.fileExists(atPath: pendingDirectory.path))
    }

    func testEraseAllWithoutAReachableFolderStillRemovesRecords() throws {
        _ = try service().importReport(report)
        folder.accessLost = true
        XCTAssertEqual(try service().eraseAll(), FolderErasure(removedRecords: 11, filesDeleted: 0, folderReachable: false))
        XCTAssertEqual(try service(target: .some(nil)).eraseAll(), FolderErasure(removedRecords: 11, filesDeleted: 0, folderReachable: false))
    }
}

/// A real folder through a bookmark, as the system folder picker would hand it over.
final class PickedBackupFolderTests: XCTestCase {
    private var base: URL!

    override func setUpWithError() throws {
        base = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: base.appendingPathComponent("Picked"), withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        try FileManager.default.removeItem(at: base)
    }

    func testBookmarkedFolderStoresFilesInTheAppSubfolder() throws {
        let database = try EncryptedDatabase(file: try SecureFiles.directory(base: base).appendingPathComponent("location.db"), key: Data(repeating: 4, count: 32))
        let store = BackupLocationStore(database: database)
        XCTAssertNil(try store.folder())

        let picked = base.appendingPathComponent("Picked")
        let subfolder = BackupLocation.subfolder { FileManager.default.fileExists(atPath: picked.appendingPathComponent($0).path) }
        try store.set(BackupLocation(bookmark: try picked.bookmarkData(), subfolder: subfolder))
        let folder = try XCTUnwrap(try store.folder())

        try folder.write("a.bin", Data([1, 2, 3]))
        XCTAssertEqual(try folder.read("a.bin"), Data([1, 2, 3]))
        XCTAssertNil(try folder.read("missing.bin"))
        XCTAssertEqual(try folder.list(), ["a.bin"])
        XCTAssertTrue(FileManager.default.fileExists(atPath: picked.appendingPathComponent("Portfolio Companion backups/a.bin").path))
        try folder.delete("a.bin")
        XCTAssertEqual(try folder.list(), [])

        // The folder is gone: access is lost, not a temporary failure.
        try FileManager.default.removeItem(at: picked)
        XCTAssertThrowsError(try PickedBackupFolder(root: picked, subfolder: subfolder).write("b.bin", Data())) {
            XCTAssertEqual($0 as? BackupFolderError, .accessLost)
        }
    }

    func testALegacySubfolderIsReused() {
        XCTAssertEqual(BackupLocation.subfolder { $0 == BackupLocation.legacySubfolderName }, "IBKR Tax Assistant backups")
        XCTAssertEqual(BackupLocation.subfolder { _ in false }, "Portfolio Companion backups")
    }
}

/// Mirrors the Android `BackupRetryTest` with a virtual clock.
@MainActor
final class BackupRetryTests: XCTestCase {
    private final class Folder {
        var pending: Int
        var failures: Int
        let accessLostAt: Int?
        var now: TimeInterval = 0
        var rounds: [TimeInterval] = []
        var changes = 0

        init(pending: Int, failures: Int, accessLostAt: Int? = nil) {
            self.pending = pending
            self.failures = failures
            self.accessLostAt = accessLostAt
        }
    }

    private func retry(_ folder: Folder) -> BackupRetry {
        BackupRetry(
            pending: { folder.pending },
            retry: {
                folder.rounds.append(folder.now)
                if folder.accessLostAt == folder.rounds.count { return RetryResult(backedUp: 0, stillPending: folder.pending, accessLost: true) }
                if folder.failures > 0 {
                    folder.failures -= 1
                    return RetryResult(backedUp: 0, stillPending: folder.pending, accessLost: false)
                }
                defer { folder.pending = 0 }
                return RetryResult(backedUp: folder.pending, stillPending: 0, accessLost: false)
            },
            onChange: { folder.changes += 1 },
            sleep: { folder.now += $0 }
        )
    }

    func testRetriesWithDoublingDelaysUntilSaved() async {
        let folder = Folder(pending: 2, failures: 3)
        let retrier = retry(folder)
        retrier.kick()
        await retrier.idle()
        // 30 s, +1 min, +2 min, +4 min: the fourth round saves both.
        XCTAssertEqual(folder.rounds, [30, 90, 210, 450])
        XCTAssertEqual(folder.pending, 0)
        XCTAssertEqual(folder.changes, 1)
    }

    func testTheDelayIsCappedAtFifteenMinutes() async {
        let folder = Folder(pending: 1, failures: 7)
        let retrier = retry(folder)
        retrier.kick()
        await retrier.idle()
        XCTAssertEqual(zip(folder.rounds, folder.rounds.dropFirst()).map { $1 - $0 }, [60, 120, 240, 480, 900, 900, 900])
    }

    func testStopsWhenFolderAccessIsLost() async {
        let folder = Folder(pending: 1, failures: 99, accessLostAt: 2)
        let retrier = retry(folder)
        retrier.kick()
        await retrier.idle()
        XCTAssertEqual(folder.rounds.count, 2)
        XCTAssertEqual(folder.changes, 1)
    }

    func testNothingPendingMeansNoRoundAndKickDoesNotStartASecondLoop() async {
        let idle = Folder(pending: 0, failures: 0)
        let first = retry(idle)
        first.kick()
        await first.idle()
        XCTAssertEqual(idle.rounds, [])

        let folder = Folder(pending: 1, failures: 1)
        let retrier = retry(folder)
        retrier.kick()
        retrier.kick()
        await retrier.idle()
        XCTAssertEqual(folder.rounds, [30, 90])
    }
}
