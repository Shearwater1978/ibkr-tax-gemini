import Foundation
import Security

/// Raised when the Secure Enclave or Keychain cannot provide a key; callers must never fall back to plaintext keys.
struct DeviceKeyUnavailableError: Error {
    let status: OSStatus
}

/// Encrypts small secrets (such as the database key) under a device key.
protocol KeyWrapper {
    func wrap(_ secret: Data) throws -> Data
    func unwrap(_ wrapped: Data) throws -> Data
}

struct KeyUnwrapError: Error {}

/**
 * Non-exportable device keys (key-management spec "Device-bound key protection"). The Secure
 * Enclave holds only P-256 keys, so a device key wraps secrets with ECIES (AES-GCM) instead of
 * holding them itself, the counterpart of the Android Keystore AES key. Keys are usable only
 * while the device is unlocked and never leave this device or its backups.
 */
enum DeviceKeys {
    static let databaseWrapTag = "ibkrtax.device.dbkey-wrap.v1"

    /// The private key for [tag], created on first use.
    static func privateKey(tag: String) throws -> SecKey {
        if let existing = try find(tag: tag) { return existing }
        return try create(tag: tag)
    }

    /// True when the key lives in the Secure Enclave.
    static func isSecureEnclave(_ key: SecKey) -> Bool {
        let attributes = SecKeyCopyAttributes(key) as? [CFString: Any]
        return attributes?[kSecAttrTokenID] as? String == kSecAttrTokenIDSecureEnclave as String
    }

    /// Removes a device key; used by erasure. Data protected by it becomes unreadable.
    static func delete(tag: String) throws {
        let status = SecItemDelete(query(tag: tag) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw DeviceKeyUnavailableError(status: status) }
    }

    private static func find(tag: String) throws -> SecKey? {
        var query = query(tag: tag)
        query[kSecReturnRef] = true
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let item else { throw DeviceKeyUnavailableError(status: status) }
        return (item as! SecKey)
    }

    /// Secure Enclave only, also on the simulator (Apple silicon Macs provide one); no software fallback.
    private static func create(tag: String) throws -> SecKey {
        var error: Unmanaged<CFError>?
        guard let access = SecAccessControlCreateWithFlags(nil, kSecAttrAccessibleWhenUnlockedThisDeviceOnly, .privateKeyUsage, &error) else {
            throw DeviceKeyUnavailableError(status: errSecParam)
        }
        let attributes: [CFString: Any] = [
            kSecAttrKeyType: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrKeySizeInBits: 256,
            kSecAttrTokenID: kSecAttrTokenIDSecureEnclave,
            kSecPrivateKeyAttrs: [
                kSecAttrIsPermanent: true,
                kSecAttrApplicationTag: Data(tag.utf8),
                kSecAttrAccessControl: access,
            ] as [CFString: Any],
        ]
        guard let key = SecKeyCreateRandomKey(attributes as CFDictionary, &error) else {
            let status = error.map { OSStatus(CFErrorGetCode($0.takeRetainedValue())) } ?? errSecInternalError
            throw DeviceKeyUnavailableError(status: status)
        }
        return key
    }

    private static func query(tag: String) -> [CFString: Any] {
        [
            kSecClass: kSecClassKey,
            kSecAttrKeyClass: kSecAttrKeyClassPrivate,
            kSecAttrKeyType: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrApplicationTag: Data(tag.utf8),
        ]
    }
}

/// ECIES (X9.63 SHA-256, AES-GCM) with a device key. Blob layout: version byte, ECIES output.
struct DeviceKeyWrapper: KeyWrapper {
    private static let version: UInt8 = 1
    private static let algorithm = SecKeyAlgorithm.eciesEncryptionCofactorVariableIVX963SHA256AESGCM

    let tag: String

    func wrap(_ secret: Data) throws -> Data {
        let privateKey = try DeviceKeys.privateKey(tag: tag)
        guard let publicKey = SecKeyCopyPublicKey(privateKey) else { throw DeviceKeyUnavailableError(status: errSecInternalError) }
        var error: Unmanaged<CFError>?
        guard let sealed = SecKeyCreateEncryptedData(publicKey, Self.algorithm, secret as CFData, &error) as Data? else {
            error?.release()
            throw DeviceKeyUnavailableError(status: errSecInternalError)
        }
        return Data([Self.version]) + sealed
    }

    func unwrap(_ wrapped: Data) throws -> Data {
        guard wrapped.first == Self.version else { throw KeyUnwrapError() }
        let privateKey = try DeviceKeys.privateKey(tag: tag)
        var error: Unmanaged<CFError>?
        guard let secret = SecKeyCreateDecryptedData(privateKey, Self.algorithm, wrapped.dropFirst() as CFData, &error) as Data? else {
            error?.release()
            throw KeyUnwrapError()
        }
        return secret
    }
}
