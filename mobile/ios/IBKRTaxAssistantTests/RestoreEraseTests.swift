import XCTest
@testable import IBKRTaxAssistant

/// Mirrors the Android `RestoreServiceTest` (without the Block Store key, which iOS does not have).
final class RestoreServiceTests: XCTestCase {
    private let vault = KeyVault(argon2: Argon2idParams(memoryKiB: 1024, iterations: 1, parallelism: 1))
    private let passphrase = "correct horse battery"
    private var oldPhone: KeySetup!
    private let folder = MemoryFolder()
    private let reports = MemoryReports()
    private var saved: (KeyManifest, Data)?

    override func setUpWithError() throws {
        oldPhone = try vault.setUp(passphrase: passphrase)
    }

    /// The backup folder as the old phone left it: the manifest and two encrypted reports.
    private func backedUp(extra: [String: Data] = [:]) throws {
        try folder.write(BackupService.manifestFile, Data(oldPhone.manifest.toJson().utf8))
        try folder.write("aa.bin", try BackupFileFormat.encrypt(dataKey: oldPhone.dataKey, plaintext: Fixtures.flexQuery(Fixtures.validBasic)))
        try folder.write("bb.bin", try BackupFileFormat.encrypt(dataKey: oldPhone.dataKey, plaintext: Fixtures.flexQuery(Fixtures.validFollowup)))
        for (name, bytes) in extra { try folder.write(name, bytes) }
    }

    private func restore(_ secret: RestoreSecret?, localKey: Data? = nil) async -> RestoreOutcome {
        await RestoreService(reports: reports, vault: vault).restore(folder, secret: secret, localKey: localKey) { saved = ($0, $1) }
    }

    func testRestoresWithThePassphraseAndKeepsTheFilesAsBackedUp() async throws {
        try backedUp()
        let outcome = await restore(.passphrase(passphrase))
        XCTAssertEqual(outcome, .restored(reports: 2, inserted: 18, alreadyImported: 0, unreadable: 0))
        XCTAssertEqual(reports.status.map(\.name), ["aa.bin", "bb.bin"])
        XCTAssertEqual(reports.status.map(\.state), ["backed_up", "backed_up"])
        XCTAssertEqual(saved?.1, oldPhone.dataKey)
        XCTAssertEqual(saved?.0, oldPhone.manifest)
    }

    func testRestoresWithTheRecoveryCode() async throws {
        try backedUp()
        let outcome = await restore(.recoveryCode(RecoveryCode.format(oldPhone.recoveryCode)))
        XCTAssertEqual(outcome, .restored(reports: 2, inserted: 18, alreadyImported: 0, unreadable: 0))
    }

    func testAMatchingKeyOnThisPhoneNeedsNoSecretOtherwiseItIsAskedFor() async throws {
        try backedUp()
        let none = await restore(nil)
        let other = await restore(nil, localKey: KeyVault.randomBytes(32))
        XCTAssertEqual(none, .needsSecret)
        XCTAssertEqual(other, .needsSecret)
        XCTAssertTrue(reports.imported.isEmpty)
        let matching = await restore(nil, localKey: oldPhone.dataKey)
        XCTAssertEqual(matching, .restored(reports: 2, inserted: 18, alreadyImported: 0, unreadable: 0))
    }

    func testAWrongSecretRestoresNothing() async throws {
        try backedUp()
        let wrongPassphrase = await restore(.passphrase("wrong horse battery"))
        let wrongCode = await restore(.recoveryCode("not a code"))
        XCTAssertEqual(wrongPassphrase, .wrongSecret)
        XCTAssertEqual(wrongCode, .wrongSecret)
        XCTAssertTrue(reports.imported.isEmpty)
        XCTAssertNil(saved)
    }

    func testADamagedFileIsCountedAndTheRestRestored() async throws {
        try backedUp(extra: ["cc.bin": Data(count: 80), "notes.txt": Data(count: 3)])
        let outcome = await restore(.passphrase(passphrase))
        XCTAssertEqual(outcome, .restored(reports: 2, inserted: 18, alreadyImported: 0, unreadable: 1))
    }

    func testRunningItAgainImportsNothingTwice() async throws {
        try backedUp()
        _ = await restore(.passphrase(passphrase))
        let again = await restore(.passphrase(passphrase))
        XCTAssertEqual(again, .restored(reports: 0, inserted: 0, alreadyImported: 2, unreadable: 0))
    }

    func testAFolderWithoutBackupOrAccessAndANewerManifest() async throws {
        let empty = await restore(.passphrase(passphrase))
        XCTAssertEqual(empty, .noBackup)
        try folder.write(BackupService.manifestFile, Data("{\"version\":99}".utf8))
        let newer = await restore(.passphrase(passphrase))
        XCTAssertEqual(newer, .newerVersion)
        try backedUp()
        folder.accessLost = true
        let lost = await restore(.passphrase(passphrase))
        XCTAssertEqual(lost, .accessLost)
    }
}

/// The erase order and failure handling of the Android `DataEraser`.
final class DataEraserTests: XCTestCase {
    struct StepFailed: Error {}

    func testEveryStepRunsInOrderEvenAfterAFailure() {
        var steps: [String] = []
        let eraser = DataEraser(
            eraseFolder: { steps.append("folder"); throw StepFailed() },
            eraseBackupKeys: { steps.append("backup keys") },
            deleteLocalData: { steps.append("local data") },
            deleteDeviceKeys: { steps.append("device keys") }
        )
        let result = eraser.eraseAll()
        XCTAssertEqual(steps, ["folder", "backup keys", "local data", "device keys"])
        XCTAssertEqual(result.failedSteps, ["backup folder"])
        XCTAssertFalse(result.complete)
    }
}

/// Erase and restore through the app's own services, on a temporary secure folder.
@MainActor
final class EraseAndRestoreFlowTests: XCTestCase {
    private var base: URL!

    override func setUpWithError() throws {
        base = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    }

    override func tearDownWithError() throws {
        try? FileManager.default.removeItem(at: base)
    }

    func testEraseLeavesAWorkingEmptyAppAndTheBackupRestoresWithTheRecoveryCode() async throws {
        let container = AppContainer(directory: try SecureFiles.directory(base: base), deviceKeyTag: Self.testKeyTag)
        let data = DataState(container: container)
        let keys = try container.backupKeys()
        let pending = try await keys.prepare(passphrase: "correct horse battery")
        try keys.confirm(pending)
        let picked = base.appendingPathComponent("Picked", isDirectory: true)
        try FileManager.default.createDirectory(at: picked, withIntermediateDirectories: true)
        try container.backupLocation().set(BackupLocation(bookmark: try picked.bookmarkData(), subfolder: BackupLocation.subfolderName))
        XCTAssertEqual(try container.backup().importReport(Fixtures.flexQuery(Fixtures.validBasic)), .imported(inserted: 9, skipped: 0, backup: .backedUp))
        XCTAssertEqual(try container.backup().syncManifest(), .backedUp)
        try FileManager.default.createDirectory(at: picked.appendingPathComponent(BackupLocation.subfolderName), withIntermediateDirectories: true)
        try Data("mine".utf8).write(to: picked.appendingPathComponent(BackupLocation.subfolderName).appendingPathComponent("holiday.jpg"))

        // A copy of the backup, as if it had also been synced elsewhere before the erase.
        let copy = base.appendingPathComponent("Copy", isDirectory: true)
        try FileManager.default.copyItem(at: picked, to: copy)

        let result = await data.eraseAll()
        XCTAssertEqual(result.failedSteps, [])
        XCTAssertEqual(result.folder?.filesDeleted, 2) // the backup file and the manifest; holiday.jpg stays
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: picked.appendingPathComponent(BackupLocation.subfolderName).path), ["holiday.jpg"])
        XCTAssertEqual(try container.backupKeys().status(), .notSetUp)
        XCTAssertEqual(try container.importHistory().list(), [], "a fresh, working database")
        XCTAssertNil(try container.backupLocation().get())

        let restored = await data.restore(from: copy, secret: .recoveryCode(pending.recoveryCode))
        XCTAssertEqual(restored, .restored(reports: 1, inserted: 9, alreadyImported: 0, unreadable: 0))
        XCTAssertEqual(try container.backupKeys().status(), .ready)
        XCTAssertNotNil(try container.backupLocation().get(), "the restored folder becomes the backup folder")
    }
}
