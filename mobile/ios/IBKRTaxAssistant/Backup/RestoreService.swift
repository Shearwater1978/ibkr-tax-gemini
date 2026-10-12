import Foundation

/// What the user typed to unlock the backup's data key.
enum RestoreSecret {
    case passphrase(String)
    case recoveryCode(String)
}

enum RestoreOutcome: Equatable {
    /// The folder holds no backup manifest.
    case noBackup

    /// No key on this phone matches: ask for the passphrase or recovery code.
    case needsSecret

    /// The passphrase or recovery code does not unlock the data key; nothing was restored.
    case wrongSecret

    /// The manifest was written by a newer app version.
    case newerVersion

    case accessLost

    /// `unreadable`: backup files that could not be decrypted or imported; the others were restored.
    case restored(reports: Int, inserted: Int, alreadyImported: Int, unreadable: Int)
}

/**
 * Restore on a new device (encrypted-backup "Restore on a new device"), ported from the Android
 * `RestoreService`. iOS has no Block Store copy of the key, so the data key comes from the
 * passphrase or recovery code (or from keys already on this phone). Every backup file is decrypted
 * first, so a wrong key restores nothing; the reports are then imported like new ones (fresh
 * device-bound pseudonyms) and kept as backed up, since their files exist. Works for backups made
 * by the Android app too: the formats are the same.
 */
struct RestoreService {
    let reports: ReportStore
    var vault = KeyVault()

    /// `localKey`: a data key already on this phone, used when it matches. `saveKeys` stores the unlocked key before the reports are imported.
    func restore(_ folder: BackupFolder, secret: RestoreSecret?, localKey: Data? = nil, saveKeys: (KeyManifest, Data) throws -> Void) async -> RestoreOutcome {
        do {
            guard let manifestData = try folder.read(BackupService.manifestFile) else { return .noBackup }
            let manifest: KeyManifest
            do {
                manifest = try KeyManifest.fromJson(String(decoding: manifestData, as: UTF8.self))
            } catch is UnsupportedManifestError {
                return .newerVersion
            } catch {
                return .noBackup
            }

            let dataKey: Data
            switch secret {
            case nil:
                guard let localKey, vault.matches(manifest, localKey) else { return .needsSecret }
                dataKey = localKey
            case let secret?:
                let vault = self.vault
                guard let unlocked = await Task.detached(operation: { () -> Data? in
                    switch secret {
                    case .passphrase(let value): return try? vault.unlock(manifest, passphrase: value)
                    case .recoveryCode(let value): return try? vault.unlock(manifest, recoveryCode: value)
                    }
                }).value else { return .wrongSecret }
                dataKey = unlocked
            }

            var unreadable = 0
            var decrypted: [(name: String, bytes: Data)] = []
            for name in try folder.list().filter({ $0.hasSuffix(".bin") }).sorted() {
                if let file = try folder.read(name), let plain = try? BackupFileFormat.decrypt(dataKey: dataKey, file: file) {
                    decrypted.append((name, plain))
                } else {
                    unreadable += 1
                }
            }
            try saveKeys(manifest, dataKey)

            var restored = 0
            var inserted = 0
            var already = 0
            for (name, bytes) in decrypted {
                switch try reports.importReport(bytes, backupFile: name) {
                case .imported(_, let count, _):
                    try reports.markBackedUp(name)
                    restored += 1
                    inserted += count
                case .alreadyImported:
                    already += 1
                case .rejected:
                    unreadable += 1
                }
            }
            return .restored(reports: restored, inserted: inserted, alreadyImported: already, unreadable: unreadable)
        } catch is BackupFolderError {
            return .accessLost
        } catch {
            return .accessLost
        }
    }

    /**
     * Where a picked folder keeps its backup: the folder itself, or the app's subfolder (current or
     * pre-rename name) inside it. Nil when none of them holds a manifest.
     */
    static func locateBackup(in root: URL) -> PickedBackupFolder? {
        let candidates: [String?] = [nil, BackupLocation.subfolderName, BackupLocation.legacySubfolderName]
        return candidates.lazy.map { PickedBackupFolder(root: root, subfolder: $0) }.first { (try? $0.read(BackupService.manifestFile)) != nil }
    }
}
