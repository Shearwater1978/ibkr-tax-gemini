import Foundation
import Security

/**
 * Supplies a random 256-bit secret (the SQLCipher key or the pseudonym key) that is stored
 * only wrapped by a device key; the plaintext secret exists in memory only. Counterpart of the
 * Android `DatabaseKeyProvider`.
 */
final class WrappedKeyProvider {
    private static let keyBytes = 32

    private let file: URL
    private let wrapper: KeyWrapper
    private let lock = NSLock()

    init(file: URL, wrapper: KeyWrapper) {
        self.file = file
        self.wrapper = wrapper
    }

    /**
     * Returns the secret, creating it on first use. Throws `KeyUnwrapError` rather than replacing
     * an unreadable key, so existing data is never silently orphaned.
     */
    func getOrCreate() throws -> Data {
        lock.lock()
        defer { lock.unlock() }
        if FileManager.default.fileExists(atPath: file.path) {
            return try wrapper.unwrap(Data(contentsOf: file))
        }
        var key = Data(count: Self.keyBytes)
        let status = key.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, Self.keyBytes, $0.baseAddress!) }
        guard status == errSecSuccess else { throw DeviceKeyUnavailableError(status: status) }
        try wrapper.wrap(key).write(to: file, options: [.atomic, .completeFileProtection])
        return key
    }
}
