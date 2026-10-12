import Foundation

/**
 * When the app must ask for Face ID, Touch ID or the device passcode again (device-hardening
 * spec, auto-lock). The app always starts locked; after that it locks once it has been in the
 * background for at least `timeout`. Same rule as the Android `AppLockPolicy`.
 */
struct AppLockPolicy {
    static let defaultTimeout: TimeInterval = 5 * 60

    var timeout: TimeInterval = defaultTimeout

    /// `backgroundedAt` is nil when the app has not left the foreground since it was unlocked.
    func shouldLock(backgroundedAt: Date?, now: Date) -> Bool {
        guard let backgroundedAt else { return false }
        return now.timeIntervalSince(backgroundedAt) >= timeout
    }
}

/**
 * No plaintext caches (local-storage spec, task 5.4). The app itself writes nothing to its
 * temporary or cache folders; whatever a framework leaves there is removed when the app starts
 * and when it goes to the background. Encrypted data lives in `SecureFiles` and stays.
 */
enum TempFiles {
    static var appFolders: [URL] {
        [FileManager.default.temporaryDirectory] + FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)
    }

    /// Deletes everything inside each folder, keeping the folders themselves.
    static func clear(_ folders: [URL] = appFolders) {
        for folder in folders {
            let items = (try? FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil)) ?? []
            for item in items { try? FileManager.default.removeItem(at: item) }
        }
    }
}
