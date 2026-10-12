import Foundation

/**
 * The folder for everything sensitive on this device: the database and wrapped keys
 * (local-storage spec "Exclude local data from OS cloud backups"). It uses complete file
 * protection and is excluded from iCloud and computer backups.
 */
enum SecureFiles {
    static func directory(base: URL? = nil) throws -> URL {
        let parent = try base ?? FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        var directory = parent.appendingPathComponent("Secure", isDirectory: true)
        try FileManager.default.createDirectory(
            at: directory,
            withIntermediateDirectories: true,
            attributes: [.protectionKey: FileProtectionType.complete]
        )
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try directory.setResourceValues(values)
        return directory
    }

    /// Applies complete file protection to a file created by a library (such as SQLite).
    static func protect(_ file: URL) throws {
        guard FileManager.default.fileExists(atPath: file.path) else { return }
        try FileManager.default.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: file.path)
    }
}
