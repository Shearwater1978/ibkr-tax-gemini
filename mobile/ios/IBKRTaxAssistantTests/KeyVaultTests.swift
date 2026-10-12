import XCTest
@testable import IBKRTaxAssistant

/// Mirrors the Android `KeyVaultTest`, plus a manifest built by independent libraries (mobile/fixtures/keys).
final class KeyVaultTests: XCTestCase {
    // Small Argon2id settings keep the tests fast; production uses Argon2idParams.default.
    private let vault = KeyVault(argon2: Argon2idParams(memoryKiB: 1024, iterations: 1, parallelism: 1))
    private let passphrase = "correct horse battery"

    func testManifestBuiltElsewhereUnlocksWithProductionSettings() throws {
        // Built with argon2-cffi (reference Argon2) and pyca/cryptography the way the Android KeyVault does it.
        let fixture = Fixtures.json("keys/manifest_v1.json") as! [String: Any]
        let manifestJson = String(decoding: try JSONSerialization.data(withJSONObject: fixture["manifest"]!), as: UTF8.self)
        let manifest = try KeyManifest.fromJson(manifestJson)
        let dataKey = Data(base64Encoded: fixture["dataKey"] as! String)!

        XCTAssertEqual(manifest.argon2, .default)
        XCTAssertEqual(try KeyVault().unlock(manifest, passphrase: fixture["passphrase"] as! String), dataKey)
        XCTAssertEqual(try KeyVault().unlock(manifest, recoveryCode: RecoveryCode.format(fixture["recoveryCode"] as! String).lowercased()), dataKey)
        XCTAssertTrue(KeyVault().matches(manifest, dataKey))
        XCTAssertEqual(try KeyManifest.fromJson(manifest.toJson()), manifest)
    }

    func testPassphrasePolicyRejectsShortAndTrivialPassphrases() {
        XCTAssertEqual(PassphrasePolicy.check("short"), .tooShort)
        XCTAssertEqual(PassphrasePolicy.check("aaaaaaaaaaaaaa"), .tooSimple)
        XCTAssertEqual(PassphrasePolicy.check("123456789012"), .tooSimple)
        XCTAssertEqual(PassphrasePolicy.check("210987654321"), .tooSimple)
        XCTAssertEqual(PassphrasePolicy.check("zyxwvutsrqpo"), .tooSimple)
        XCTAssertNil(PassphrasePolicy.check(passphrase))
        XCTAssertThrowsError(try vault.setUp(passphrase: "short")) { XCTAssertEqual($0 as? WeakPassphraseError, WeakPassphraseError(problem: .tooShort)) }
    }

    func testRecoveryCodeIsWellFormedAndForgivingOnInput() {
        let code = RecoveryCode.generate()
        XCTAssertEqual(code.count, 26)
        let shown = RecoveryCode.format(code)
        XCTAssertEqual(shown.split(separator: "-").map(\.count), [4, 4, 4, 4, 5, 5])

        XCTAssertEqual(RecoveryCode.normalize(shown.lowercased()), code)
        XCTAssertEqual(RecoveryCode.normalize(shown.replacingOccurrences(of: "-", with: " ")), code)
        XCTAssertEqual(RecoveryCode.normalize("O" + String(repeating: "IL", count: 12) + "I"), "0" + String(repeating: "1", count: 25))
        XCTAssertNil(RecoveryCode.normalize("too short"))
        XCTAssertNil(RecoveryCode.normalize(String(repeating: "U", count: 26)))
    }

    func testPassphraseAndRecoveryCodeBothUnlockTheSameDataKey() throws {
        let setup = try vault.setUp(passphrase: passphrase)
        XCTAssertEqual(setup.dataKey.count, 32)
        XCTAssertEqual(try vault.unlock(setup.manifest, passphrase: passphrase), setup.dataKey)
        XCTAssertEqual(try vault.unlock(setup.manifest, recoveryCode: RecoveryCode.format(setup.recoveryCode).lowercased()), setup.dataKey)
        XCTAssertTrue(vault.matches(setup.manifest, setup.dataKey))
    }

    func testWrongSecretsAreRejectedWithoutPartialResults() throws {
        let setup = try vault.setUp(passphrase: passphrase)
        XCTAssertThrowsError(try vault.unlock(setup.manifest, passphrase: "wrong passphrase!")) { XCTAssertTrue($0 is WrongSecretError) }
        XCTAssertThrowsError(try vault.unlock(setup.manifest, recoveryCode: RecoveryCode.generate())) { XCTAssertTrue($0 is WrongSecretError) }
        XCTAssertThrowsError(try vault.unlock(setup.manifest, recoveryCode: "not a code")) { XCTAssertTrue($0 is WrongSecretError) }
        XCTAssertFalse(vault.matches(setup.manifest, Data(count: 32)))
    }

    func testChangingThePassphraseRewrapsOnlyThePassphraseKey() throws {
        let setup = try vault.setUp(passphrase: passphrase)
        let newPassphrase = "a different passphrase"
        let changed = try vault.changePassphrase(setup.manifest, dataKey: setup.dataKey, newPassphrase: newPassphrase)

        XCTAssertEqual(try vault.unlock(changed, passphrase: newPassphrase), setup.dataKey)
        XCTAssertThrowsError(try vault.unlock(changed, passphrase: passphrase))
        XCTAssertEqual(try vault.unlock(changed, recoveryCode: setup.recoveryCode), setup.dataKey)
        XCTAssertEqual(changed.recoveryWrappedKey, setup.manifest.recoveryWrappedKey)
        XCTAssertNotEqual(changed.passphraseSalt, setup.manifest.passphraseSalt)
    }

    func testReplacingTheRecoveryCodeRetiresTheOldOne() throws {
        let setup = try vault.setUp(passphrase: passphrase)
        let (changed, newCode) = try vault.replaceRecoveryCode(setup.manifest, dataKey: setup.dataKey)

        XCTAssertNotEqual(newCode, setup.recoveryCode)
        XCTAssertEqual(try vault.unlock(changed, recoveryCode: newCode), setup.dataKey)
        XCTAssertThrowsError(try vault.unlock(changed, recoveryCode: setup.recoveryCode))
        XCTAssertEqual(try vault.unlock(changed, passphrase: passphrase), setup.dataKey)
        XCTAssertEqual(changed.passphraseWrappedKey, setup.manifest.passphraseWrappedKey)
    }

    func testManifestRoundTripsAndContainsNoSecrets() throws {
        let setup = try vault.setUp(passphrase: passphrase)
        let json = setup.manifest.toJson()

        XCTAssertEqual(try KeyManifest.fromJson(json), setup.manifest)
        XCTAssertFalse(json.contains(setup.recoveryCode))
        XCTAssertFalse(json.contains(passphrase))
        XCTAssertFalse(json.contains(setup.dataKey.base64EncodedString()))
        XCTAssertTrue(json.contains("\"memoryKiB\":1024"))
    }

    func testNewerManifestVersionIsReportedNotDecrypted() throws {
        let json = try vault.setUp(passphrase: passphrase).manifest.toJson().replacingOccurrences(of: "\"version\":1", with: "\"version\":2")
        XCTAssertThrowsError(try KeyManifest.fromJson(json)) { XCTAssertEqual($0 as? UnsupportedManifestError, UnsupportedManifestError(version: 2)) }
    }
}

/// Mirrors the Android `BackupKeysTest` (without the Block Store copy, which iOS does not have).
final class BackupKeysTests: XCTestCase {
    private let vault = KeyVault(argon2: Argon2idParams(memoryKiB: 1024, iterations: 1, parallelism: 1))
    private let passphrase = "correct horse battery"
    private var directory: URL!
    private var keys: BackupKeys!

    override func setUpWithError() throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        keys = BackupKeys(files: LocalKeyFiles(directory: directory, wrapper: SoftwareKeyWrapper()), vault: vault)
    }

    override func tearDownWithError() throws {
        try FileManager.default.removeItem(at: directory)
    }

    func testSetupStoresOnlyTheWrappedKeyAfterConfirmation() async throws {
        XCTAssertEqual(keys.status(), .notSetUp)
        let pending = try await keys.prepare(passphrase: passphrase)
        XCTAssertEqual(keys.status(), .notSetUp, "nothing is stored before confirmation")
        try keys.confirm(pending)

        XCTAssertEqual(pending.recoveryCode.count, 26)
        XCTAssertEqual(keys.status(), .ready)
        let dataKey = try XCTUnwrap(try keys.dataKey())
        XCTAssertEqual(try vault.unlock(try XCTUnwrap(try keys.files.manifest()), recoveryCode: pending.recoveryCode), dataKey)
        for file in try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil) {
            XCTAssertNil(try Data(contentsOf: file).range(of: dataKey), "\(file.lastPathComponent) holds the plaintext key")
        }
    }

    func testChangingThePassphraseKeepsTheDataKey() async throws {
        try keys.confirm(try await keys.prepare(passphrase: passphrase))
        let dataKey = try keys.dataKey()
        let newPassphrase = "a different passphrase"

        do {
            try await keys.changePassphrase(current: "wrong passphrase!", new: newPassphrase)
            XCTFail("a wrong passphrase must not change anything")
        } catch {
            XCTAssertTrue(error is WrongSecretError)
        }
        try await keys.changePassphrase(current: passphrase, new: newPassphrase)

        XCTAssertEqual(try vault.unlock(try XCTUnwrap(try keys.files.manifest()), passphrase: newPassphrase), dataKey)
        XCTAssertEqual(try keys.dataKey(), dataKey)
        let verified = await keys.verifyPassphrase(newPassphrase)
        XCTAssertTrue(verified)
    }

    func testNewRecoveryCodeNeedsThePassphraseAndConfirmation() async throws {
        let first = try await keys.prepare(passphrase: passphrase)
        try keys.confirm(first)

        do {
            _ = try await keys.prepareNewRecoveryCode(passphrase: "wrong passphrase!")
            XCTFail("a wrong passphrase must not give a new code")
        } catch {
            XCTAssertTrue(error is WrongSecretError)
        }
        let pending = try await keys.prepareNewRecoveryCode(passphrase: passphrase)
        // Not confirmed yet: the old code still works.
        XCTAssertEqual(try vault.unlock(try XCTUnwrap(try keys.files.manifest()), recoveryCode: first.recoveryCode), try keys.dataKey())

        try keys.confirmRecoveryCode(pending)
        let manifest = try XCTUnwrap(try keys.files.manifest())
        XCTAssertEqual(try vault.unlock(manifest, recoveryCode: pending.recoveryCode), try keys.dataKey())
        XCTAssertThrowsError(try vault.unlock(manifest, recoveryCode: first.recoveryCode))
        XCTAssertEqual(try vault.unlock(manifest, passphrase: passphrase), try keys.dataKey())
    }

    func testEraseRemovesTheLocalKeys() async throws {
        try keys.confirm(try await keys.prepare(passphrase: passphrase))
        try keys.erase()
        XCTAssertEqual(keys.status(), .notSetUp)
        XCTAssertNil(try keys.dataKey())
    }

    func testAdoptKeepsRestoredKeysAndRefusesToReplaceOthers() throws {
        let oldPhone = try vault.setUp(passphrase: passphrase)
        try keys.adopt(oldPhone.manifest, dataKey: oldPhone.dataKey)
        XCTAssertEqual(try keys.dataKey(), oldPhone.dataKey)
        XCTAssertEqual(keys.status(), .ready)

        // Adopting the same keys again is fine; other keys are not overwritten.
        try keys.adopt(oldPhone.manifest, dataKey: oldPhone.dataKey)
        let other = try vault.setUp(passphrase: passphrase)
        XCTAssertThrowsError(try keys.adopt(other.manifest, dataKey: other.dataKey)) { XCTAssertTrue($0 is OtherKeysAlreadySetUpError) }
        XCTAssertEqual(try keys.dataKey(), oldPhone.dataKey)
    }
}
