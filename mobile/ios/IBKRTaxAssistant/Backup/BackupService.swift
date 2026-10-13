import CryptoKit
import Foundation

/// Where imported reports and their backup state live (the encrypted database in the app).
protocol ReportStore {
    /// Imports atomically and records `backupFile` as pending in the same transaction.
    func importReport(_ bytes: Data, backupFile: String?) throws -> ImportResult
    func pendingBackups() throws -> [String]
    func markBackedUp(_ backupFile: String) throws
    /// Nil when the import does not exist.
    func deleteReport(_ reportId: Int64) throws -> DeletedImports?
    func deleteAll() throws -> DeletedImports
}

extension ReportImporter: ReportStore {}

/// `backupsLeft`: backup files that could not be deleted because the folder was unreachable.
struct DeletionOutcome: Equatable {
    var removedRecords: Int
    var backupsDeleted: Int
    var backupsLeft: Int
}

/// `left`: files that stayed because deleting them failed; unreadable anyway once the keys are destroyed.
struct FolderErasure: Equatable {
    var removedRecords: Int
    var filesDeleted: Int
    var folderReachable: Bool
    var left = 0
}

enum BackupResult: Equatable {
    case backedUp

    /// Kept encrypted on the phone; retried later.
    case pending

    /// The folder access is gone; the user must choose the folder again.
    case accessLost
}

enum ImportOutcome: Equatable {
    /// Backup encryption or the backup folder is not set up, so import stays off (mobile-app-core task 1.2).
    case notReady
    case imported(inserted: Int, skipped: Int, backup: BackupResult)
    case alreadyImported
    case rejected(ImportRejection)
}

struct RetryResult: Equatable {
    var backedUp: Int
    var stillPending: Int
    var accessLost: Bool
}

/**
 * Import with encrypted backup (encrypted-backup and mobile-report-upload specs), ported from the
 * Android `BackupService`: the report is validated, encrypted with the data key and kept in the
 * app's protected folder, imported atomically, then written to the backup folder and verified by
 * reading it back. A backup that cannot be written stays pending; the local import is never lost.
 */
struct BackupService {
    /// Fixed name so a restore can find it; it contains only wrapped keys and KDF parameters.
    static let manifestFile = "ibkr-tax-backup-manifest.json"

    let dataKey: () throws -> Data?
    let manifestJson: () throws -> String?
    let folder: () throws -> BackupFolder?
    let reports: ReportStore
    /// Encrypted copies waiting for upload, in the protected folder.
    let pendingDirectory: URL
    /// The manual-entry journal, retried together with the report backups.
    var journal: ManualJournalBackup?

    var isReady: Bool { ((try? dataKey()) ?? nil) != nil && ((try? folder()) ?? nil) != nil }

    func importReport(_ bytes: Data) throws -> ImportOutcome {
        guard let key = try dataKey(), let target = try folder() else { return .notReady }
        // Unsupported or broken files get neither a backup nor records.
        if case .failure(let error) = FlexQueryParser.parse(bytes, sourceFile: "") { return .rejected(.unparseable(error)) }

        let name = Self.randomFileName()
        let encrypted = try BackupFileFormat.encrypt(dataKey: key, plaintext: bytes)
        try FileManager.default.createDirectory(at: pendingDirectory, withIntermediateDirectories: true)
        let pending = pendingDirectory.appendingPathComponent(name)
        try encrypted.write(to: pending, options: [.atomic, .completeFileProtection])

        switch try reports.importReport(bytes, backupFile: name) {
        case .imported(_, let inserted, let skipped):
            return .imported(inserted: inserted, skipped: skipped, backup: try upload(target, name, encrypted))
        case .alreadyImported:
            try? FileManager.default.removeItem(at: pending)
            return .alreadyImported
        case .rejected(let reason):
            try? FileManager.default.removeItem(at: pending)
            return .rejected(reason)
        }
    }

    /// Report backups and the manual journal still waiting for the folder.
    func pendingCount() throws -> Int { try reports.pendingBackups().count + (journal?.isPending == true ? 1 : 0) }

    /// Uploads backups that could not be written earlier, and refreshes the manifest.
    func retryPending() throws -> RetryResult {
        guard let target = try folder() else { return RetryResult(backedUp: 0, stillPending: try pendingCount(), accessLost: false) }
        var backedUp = 0
        var accessLost = false
        for name in try reports.pendingBackups() {
            guard let encrypted = try? Data(contentsOf: pendingDirectory.appendingPathComponent(name)) else { continue }
            switch try upload(target, name, encrypted) {
            case .backedUp: backedUp += 1
            case .accessLost: accessLost = true
            case .pending: break
            }
            if accessLost { break }
        }
        if !accessLost { _ = try syncManifest() }
        if !accessLost, let journal, journal.isPending {
            switch try journal.sync() {
            case .backedUp: backedUp += 1
            case .accessLost: accessLost = true
            case .pending: break
            }
        }
        return RetryResult(backedUp: backedUp, stillPending: try pendingCount(), accessLost: accessLost)
    }

    /// Deletes one import, the records only it contains, and its backup file (mobile-report-upload "Delete imported reports").
    func deleteImport(_ reportId: Int64) throws -> DeletionOutcome? {
        try reports.deleteReport(reportId).map(deleteBackups)
    }

    /// Deletes all imports and their backup files; the keys and manifest stay.
    func deleteAllImports() throws -> DeletionOutcome { deleteBackups(try reports.deleteAll()) }

    private func deleteBackups(_ deleted: DeletedImports) -> DeletionOutcome {
        let target = (try? folder()) ?? nil
        var removed = 0
        var left = 0
        for name in deleted.backupFiles {
            try? FileManager.default.removeItem(at: pendingDirectory.appendingPathComponent(name))
            do {
                guard let target else { left += 1; continue }
                try target.delete(name)
                removed += 1
            } catch {
                left += 1
            }
        }
        return DeletionOutcome(removedRecords: deleted.removedRecords, backupsDeleted: removed, backupsLeft: left)
    }

    /**
     * Erasure (data-anonymization "Erasure and minimization"): all imports, then every backup file and
     * the manifest in the backup folder, including files no import refers to any more.
     */
    func eraseAll() throws -> FolderErasure {
        let removedRecords = try reports.deleteAll().removedRecords
        try? FileManager.default.removeItem(at: pendingDirectory)
        guard let target = (try? folder()) ?? nil, let names = try? target.list() else {
            return FolderErasure(removedRecords: removedRecords, filesDeleted: 0, folderReachable: false)
        }
        let ours = names.filter { $0 == Self.manifestFile || Self.isBackupFileName($0) }
        let deleted = ours.filter { (try? target.delete($0)) != nil }.count
        return FolderErasure(removedRecords: removedRecords, filesDeleted: deleted, folderReachable: true, left: ours.count - deleted)
    }

    /// Writes the wrapped-key manifest; needed for restore and after any key change.
    func syncManifest() throws -> BackupResult {
        guard let target = try folder(), let manifest = try manifestJson() else { return .pending }
        do {
            try target.write(Self.manifestFile, Data(manifest.utf8))
            return .backedUp
        } catch BackupFolderError.accessLost {
            return .accessLost
        } catch {
            return .pending
        }
    }

    private func upload(_ target: BackupFolder, _ name: String, _ encrypted: Data) throws -> BackupResult {
        do {
            try target.write(name, encrypted)
            if let readBack = try target.read(name), readBack.count == encrypted.count, SHA256.hash(data: readBack) == SHA256.hash(data: encrypted) {
                try reports.markBackedUp(name)
                try? FileManager.default.removeItem(at: pendingDirectory.appendingPathComponent(name))
                return .backedUp
            }
            // Verification failed: remove the bad copy, keep the local one (encrypted-backup spec).
            try? target.delete(name)
            return .pending
        } catch BackupFolderError.accessLost {
            return .accessLost
        } catch is BackupFolderError {
            return .pending
        }
    }

    static func randomFileName() -> String { KeyVault.randomBytes(16).map { String(format: "%02x", $0) }.joined() + ".bin" }

    /// Backup file names as written by `randomFileName`.
    static func isBackupFileName(_ name: String) -> Bool {
        name.count == 36 && name.hasSuffix(".bin") && name.dropLast(4).allSatisfy { "0123456789abcdef".contains($0) }
    }
}

/**
 * Automatic retries for pending backups with exponential backoff (encrypted-backup "Write failure"),
 * as the Android `BackupRetry`: 30 s, then doubling up to 15 minutes, while the app runs. Stops when
 * nothing is pending or the folder access is lost (the user has to choose the folder again).
 */
@MainActor
final class BackupRetry {
    private let pending: () async -> Int
    private let retry: () async -> RetryResult?
    private let onChange: () -> Void
    private let sleep: (TimeInterval) async -> Void
    private let firstDelay: TimeInterval
    private let maxDelay: TimeInterval
    private var task: Task<Void, Never>?

    init(pending: @escaping () async -> Int, retry: @escaping () async -> RetryResult?, onChange: @escaping () -> Void = {},
         sleep: @escaping (TimeInterval) async -> Void = { try? await Task.sleep(nanoseconds: UInt64($0 * 1_000_000_000)) },
         firstDelay: TimeInterval = 30, maxDelay: TimeInterval = 15 * 60) {
        self.pending = pending
        self.retry = retry
        self.onChange = onChange
        self.sleep = sleep
        self.firstDelay = firstDelay
        self.maxDelay = maxDelay
    }

    /// Starts retrying if backups are pending and no retry loop is running.
    func kick() {
        if task != nil { return }
        task = Task {
            defer { task = nil }
            var wait = firstDelay
            while await pending() > 0 {
                await sleep(wait)
                if Task.isCancelled { return }
                let result = await retry()
                if let result, result.backedUp > 0 || result.accessLost { onChange() }
                if let result, result.accessLost || result.stillPending == 0 { break }
                wait = min(wait * 2, maxDelay)
            }
        }
    }

    /// Finishes when the current loop (if any) has ended; for tests.
    func idle() async { await task?.value }
}
