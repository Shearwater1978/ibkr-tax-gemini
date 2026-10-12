import CryptoKit
import Foundation

struct UnsupportedBackupVersionError: Error, Equatable {
    let version: Int
}

/// The file is not a backup, was damaged, or was encrypted with another data key.
struct CorruptBackupError: Error {}

/**
 * Encrypted backup file (encrypted-backup spec "Versioned encrypted file format"), byte for byte as
 * the Android `BackupFileFormat`: magic "IBKB", format version, random 16-byte file id, 12-byte
 * nonce, then AES-256-GCM ciphertext and tag. The whole header is authenticated, so a changed
 * version or id fails to decrypt.
 */
enum BackupFileFormat {
    static let version: UInt8 = 1
    private static let magic = Data("IBKB".utf8)
    private static let fileIdBytes = 16
    private static let nonceBytes = 12
    private static let headerBytes = 4 + 1 + fileIdBytes + nonceBytes

    static func encrypt(dataKey: Data, plaintext: Data) throws -> Data {
        let nonce = KeyVault.randomBytes(nonceBytes)
        let header = magic + Data([version]) + KeyVault.randomBytes(fileIdBytes) + nonce
        let sealed = try AES.GCM.seal(plaintext, using: SymmetricKey(data: dataKey), nonce: AES.GCM.Nonce(data: nonce), authenticating: header)
        return header + sealed.ciphertext + sealed.tag
    }

    static func decrypt(dataKey: Data, file: Data) throws -> Data {
        let bytes = Data(file)
        guard bytes.count >= headerBytes + 16, bytes.prefix(4) == magic else { throw CorruptBackupError() }
        guard bytes[4] == version else { throw UnsupportedBackupVersionError(version: Int(bytes[4])) }
        let header = bytes.prefix(headerBytes)
        do {
            let box = try AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: header.suffix(nonceBytes)),
                                           ciphertext: bytes.dropFirst(headerBytes).dropLast(16), tag: bytes.suffix(16))
            return try AES.GCM.open(box, using: SymmetricKey(data: dataKey), authenticating: header)
        } catch {
            throw CorruptBackupError()
        }
    }
}

enum BackupFolderError: Error, Equatable {
    /// The folder's permission was revoked or the folder is gone (backup-location spec "Lost folder access").
    case accessLost
    /// Any other failure (offline file provider, full disk); worth retrying.
    case unavailable
}

/**
 * The user-chosen backup folder. The app only writes ciphertext and the wrapped-key manifest here;
 * the file provider (iCloud Drive, another cloud app or the phone) does any syncing.
 */
protocol BackupFolder {
    /// Creates or replaces `name`.
    func write(_ name: String, _ bytes: Data) throws
    /// Nil when the file does not exist.
    func read(_ name: String) throws -> Data?
    func list() throws -> [String]
    func delete(_ name: String) throws
}

/**
 * A folder picked with the system folder picker (the iOS equivalent of the Android SAF tree, task
 * 1.5). Access lasts through a bookmark; every operation runs inside the security scope and
 * through file coordination, so iCloud Drive and other file providers see consistent files.
 */
struct PickedBackupFolder: BackupFolder {
    /// The folder the user confirmed.
    let root: URL
    /// The app's subfolder inside it, or nil when the confirmed folder itself holds the backups.
    let subfolder: String?

    func write(_ name: String, _ bytes: Data) throws {
        try access { folder in
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            try coordinate(writing: folder.appendingPathComponent(name)) { try bytes.write(to: $0, options: .atomic) }
        }
    }

    func read(_ name: String) throws -> Data? {
        try access { folder in
            let file = folder.appendingPathComponent(name)
            guard FileManager.default.fileExists(atPath: file.path) else { return nil }
            return try coordinate(reading: file) { try Data(contentsOf: $0) }
        }
    }

    func list() throws -> [String] {
        try access { folder in
            guard FileManager.default.fileExists(atPath: folder.path) else { return [] }
            return try FileManager.default.contentsOfDirectory(atPath: folder.path)
        }
    }

    func delete(_ name: String) throws {
        try access { folder in
            let file = folder.appendingPathComponent(name)
            guard FileManager.default.fileExists(atPath: file.path) else { return }
            try coordinate(writing: file, options: .forDeleting) { try FileManager.default.removeItem(at: $0) }
        }
    }

    private func access<T>(_ body: (URL) throws -> T) throws -> T {
        let scoped = root.startAccessingSecurityScopedResource()
        defer { if scoped { root.stopAccessingSecurityScopedResource() } }
        guard FileManager.default.fileExists(atPath: root.path) else { throw BackupFolderError.accessLost }
        do {
            return try body(subfolder.map { root.appendingPathComponent($0, isDirectory: true) } ?? root)
        } catch let error as BackupFolderError {
            throw error
        } catch let error as CocoaError where [.fileReadNoPermission, .fileWriteNoPermission].contains(error.code) {
            throw BackupFolderError.accessLost
        } catch {
            throw BackupFolderError.unavailable
        }
    }

    private func coordinate<T>(reading url: URL, _ body: (URL) throws -> T) throws -> T {
        var result: Result<T, Error> = .failure(BackupFolderError.unavailable)
        var coordinationError: NSError?
        NSFileCoordinator().coordinate(readingItemAt: url, options: [], error: &coordinationError) { coordinated in result = Result { try body(coordinated) } }
        if let coordinationError { throw coordinationError }
        return try result.get()
    }

    private func coordinate(writing url: URL, options: NSFileCoordinator.WritingOptions = .forReplacing, _ body: (URL) throws -> Void) throws {
        var result: Result<Void, Error> = .failure(BackupFolderError.unavailable)
        var coordinationError: NSError?
        NSFileCoordinator().coordinate(writingItemAt: url, options: options, error: &coordinationError) { coordinated in result = Result { try body(coordinated) } }
        if let coordinationError { throw coordinationError }
        try result.get()
    }
}

/// The confirmed folder (as a bookmark) and, if any, the app's subfolder inside it.
struct BackupLocation: Equatable {
    var bookmark: Data
    var subfolder: String?

    /// The app's own folder inside the folder the user confirms (mobile-neutral-presentation).
    static let subfolderName = "Portfolio Companion backups"
    /// The folder name used before the app was renamed.
    static let legacySubfolderName = "IBKR Tax Assistant backups"

    /// Reuses a folder from before the rename if the confirmed folder has one, so backups are not split.
    static func subfolder(hasFolder: (String) -> Bool) -> String { hasFolder(legacySubfolderName) ? legacySubfolderName : subfolderName }
}

/// The user-chosen backup location, kept in the encrypted database (Android `BackupLocationStore`).
struct BackupLocationStore {
    private static let bookmarkKey = "backup_folder_bookmark"
    private static let subfolderKey = "backup_subfolder"

    let database: EncryptedDatabase

    func get() throws -> BackupLocation? {
        guard let bookmark = try read(Self.bookmarkKey).flatMap({ Data(base64Encoded: $0) }) else { return nil }
        let subfolder = try read(Self.subfolderKey)
        return BackupLocation(bookmark: bookmark, subfolder: subfolder?.isEmpty == false ? subfolder : nil)
    }

    func set(_ location: BackupLocation) throws {
        try database.transaction {
            try write(Self.bookmarkKey, location.bookmark.base64EncodedString())
            // Empty means "use the confirmed folder itself" (it already held a manifest).
            try write(Self.subfolderKey, location.subfolder ?? "")
        }
    }

    /// The folder to write to, or nil when none is set or the bookmark no longer resolves (access lost).
    func folder() throws -> PickedBackupFolder? {
        guard let location = try get() else { return nil }
        var stale = false
        guard let url = try? URL(resolvingBookmarkData: location.bookmark, bookmarkDataIsStale: &stale) else { return nil }
        if stale, url.startAccessingSecurityScopedResource() {
            defer { url.stopAccessingSecurityScopedResource() }
            if let fresh = try? url.bookmarkData() { try set(BackupLocation(bookmark: fresh, subfolder: location.subfolder)) }
        }
        return PickedBackupFolder(root: url, subfolder: location.subfolder)
    }

    private func read(_ key: String) throws -> String? {
        guard case .text(let value)? = try database.query("SELECT value FROM app_settings WHERE key = ?", [.text(key)]).first?.first else { return nil }
        return value
    }

    private func write(_ key: String, _ value: String) throws {
        try database.execute("INSERT OR REPLACE INTO app_settings (key, value) VALUES (?, ?)", [.text(key), .text(value)])
    }
}
