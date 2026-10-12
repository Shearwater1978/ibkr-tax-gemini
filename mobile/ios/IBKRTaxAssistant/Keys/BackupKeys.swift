import Foundation

/// On-device storage: DK wrapped by a device key, and the manifest (no plaintext secrets). Port of the Android `LocalKeyFiles`.
struct LocalKeyFiles {
    let directory: URL
    let wrapper: KeyWrapper

    private var wrappedKeyFile: URL { directory.appendingPathComponent("data-key.wrapped") }
    private var manifestFile: URL { directory.appendingPathComponent("key-manifest.json") }

    func exists() -> Bool {
        FileManager.default.fileExists(atPath: manifestFile.path) && FileManager.default.fileExists(atPath: wrappedKeyFile.path)
    }

    func manifest() throws -> KeyManifest? {
        guard FileManager.default.fileExists(atPath: manifestFile.path) else { return nil }
        return try KeyManifest.fromJson(String(decoding: try Data(contentsOf: manifestFile), as: UTF8.self))
    }

    func dataKey() throws -> Data? {
        guard FileManager.default.fileExists(atPath: wrappedKeyFile.path) else { return nil }
        return try wrapper.unwrap(Data(contentsOf: wrappedKeyFile))
    }

    func save(_ manifest: KeyManifest, dataKey: Data) throws {
        try wrapper.wrap(dataKey).write(to: wrappedKeyFile, options: [.atomic, .completeFileProtection])
        try saveManifest(manifest)
    }

    func saveManifest(_ manifest: KeyManifest) throws {
        try Data(manifest.toJson().utf8).write(to: manifestFile, options: [.atomic, .completeFileProtection])
    }

    func delete() throws {
        for file in [wrappedKeyFile, manifestFile] where FileManager.default.fileExists(atPath: file.path) {
            try FileManager.default.removeItem(at: file)
        }
    }
}

enum BackupKeyStatus: Equatable {
    case notSetUp
    case ready
}

/**
 * Keys created but not stored yet: nothing is saved until the user confirms they wrote down
 * `recoveryCode` (key-management spec "Recovery code display"). The code is never stored.
 */
struct PendingKeySetup {
    fileprivate let setup: KeySetup
    var recoveryCode: String { setup.recoveryCode }
}

/// A replacement recovery code waiting for confirmation; nothing changes until it is confirmed.
struct PendingRecoveryCode {
    fileprivate let manifest: KeyManifest
    let recoveryCode: String
}

struct OtherKeysAlreadySetUpError: Error {}

/**
 * Backup encryption keys on this device (mobile-encrypted-drive-backup tasks 2.2-2.7), ported from
 * the Android `BackupKeys`. Android also keeps an end-to-end encrypted Block Store copy of the data
 * key; iOS has none yet, so a new iPhone restores with the passphrase or recovery code. Argon2id is
 * deliberately slow, so the passphrase work runs off the calling task.
 */
struct BackupKeys {
    let files: LocalKeyFiles
    var vault = KeyVault()

    func status() -> BackupKeyStatus { files.exists() ? .ready : .notSetUp }

    func prepare(passphrase: String) async throws -> PendingKeySetup {
        let vault = self.vault
        return PendingKeySetup(setup: try await Task.detached { try vault.setUp(passphrase: passphrase) }.value)
    }

    /// Stores the keys after the user confirmed the recovery code.
    func confirm(_ pending: PendingKeySetup) throws {
        try files.save(pending.setup.manifest, dataKey: pending.setup.dataKey)
    }

    /// Keeps keys unlocked during a restore on this device. Refuses to replace different keys that are already set up here.
    func adopt(_ manifest: KeyManifest, dataKey: Data) throws {
        if let existing = try files.manifest(), !vault.matches(existing, dataKey) { throw OtherKeysAlreadySetUpError() }
        try files.save(manifest, dataKey: dataKey)
    }

    /// Throws `WrongSecretError` when `current` is wrong.
    func changePassphrase(current: String, new: String) async throws {
        guard let manifest = try files.manifest() else { throw WrongSecretError() }
        let vault = self.vault
        let changed = try await Task.detached {
            try vault.changePassphrase(manifest, dataKey: try vault.unlock(manifest, passphrase: current), newPassphrase: new)
        }.value
        try files.saveManifest(changed)
    }

    /// Throws `WrongSecretError` when `passphrase` is wrong. The old code keeps working until `confirmRecoveryCode`.
    func prepareNewRecoveryCode(passphrase: String) async throws -> PendingRecoveryCode {
        guard let manifest = try files.manifest() else { throw WrongSecretError() }
        let vault = self.vault
        let (changed, code) = try await Task.detached { try vault.replaceRecoveryCode(manifest, dataKey: try vault.unlock(manifest, passphrase: passphrase)) }.value
        return PendingRecoveryCode(manifest: changed, recoveryCode: code)
    }

    func confirmRecoveryCode(_ pending: PendingRecoveryCode) throws {
        try files.saveManifest(pending.manifest)
    }

    /// True when `passphrase` unlocks the stored keys; used to confirm destructive actions.
    func verifyPassphrase(_ passphrase: String) async -> Bool {
        guard let manifest = try? files.manifest() else { return false }
        let vault = self.vault
        return await Task.detached { (try? vault.unlock(manifest, passphrase: passphrase)) != nil }.value
    }

    /// The data key for encrypting backups, or nil when backup encryption is not set up.
    func dataKey() throws -> Data? { files.exists() ? try files.dataKey() : nil }

    /// The manifest (wrapped keys only) to store next to the backups.
    func manifestJson() throws -> String? { try files.manifest()?.toJson() }

    /// Erasure: the local key files.
    func erase() throws { try files.delete() }
}
