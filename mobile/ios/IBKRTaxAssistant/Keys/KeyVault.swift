import Clibsodium
import CryptoKit
import Foundation
import Security

/// Argon2id settings; stored with each passphrase wrap so they can change later.
struct Argon2idParams: Equatable {
    var memoryKiB: Int
    var iterations: Int
    var parallelism: Int

    /// Design Decision 2: 64 MiB, 3 iterations, parallelism 1.
    static let `default` = Argon2idParams(memoryKiB: 64 * 1024, iterations: 3, parallelism: 1)
}

/**
 * The backup manifest's key section: the data key (DK) wrapped by the passphrase KEK and,
 * independently, by the recovery-code KEK, plus a key check value. It holds no secret in
 * plaintext (key-management spec "Manifest inspection"). Same JSON as the Android `KeyManifest`,
 * so either app can restore the other's backups.
 */
struct KeyManifest: Equatable {
    static let version = 1

    var passphraseSalt: Data
    var argon2: Argon2idParams
    var passphraseWrappedKey: Data
    var recoverySalt: Data
    var recoveryWrappedKey: Data
    var keyCheck: Data

    func toJson() -> String {
        let object: [String: Any] = [
            "version": Self.version,
            "passphrase": [
                "kdf": "argon2id", "memoryKiB": argon2.memoryKiB, "iterations": argon2.iterations, "parallelism": argon2.parallelism,
                "salt": passphraseSalt.base64EncodedString(), "wrappedKey": passphraseWrappedKey.base64EncodedString(),
            ] as [String: Any],
            "recovery": ["kdf": "hkdf-sha256", "salt": recoverySalt.base64EncodedString(), "wrappedKey": recoveryWrappedKey.base64EncodedString()],
            "keyCheck": keyCheck.base64EncodedString(),
        ]
        let data = try! JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])
        return String(decoding: data, as: UTF8.self)
    }

    /// Throws `UnsupportedManifestError` for a newer format (encrypted-backup spec "Unknown format version").
    static func fromJson(_ json: String) throws -> KeyManifest {
        guard let root = (try? JSONSerialization.jsonObject(with: Data(json.utf8))) as? [String: Any],
              let version = root["version"] as? Int else { throw MalformedManifestError() }
        guard version == Self.version else { throw UnsupportedManifestError(version: version) }
        guard let passphrase = root["passphrase"] as? [String: Any], let recovery = root["recovery"] as? [String: Any],
              let memory = passphrase["memoryKiB"] as? Int, let iterations = passphrase["iterations"] as? Int,
              let parallelism = passphrase["parallelism"] as? Int,
              let passphraseSalt = data(passphrase["salt"]), let passphraseWrapped = data(passphrase["wrappedKey"]),
              let recoverySalt = data(recovery["salt"]), let recoveryWrapped = data(recovery["wrappedKey"]),
              let keyCheck = data(root["keyCheck"]) else { throw MalformedManifestError() }
        return KeyManifest(passphraseSalt: passphraseSalt, argon2: Argon2idParams(memoryKiB: memory, iterations: iterations, parallelism: parallelism),
                           passphraseWrappedKey: passphraseWrapped, recoverySalt: recoverySalt, recoveryWrappedKey: recoveryWrapped, keyCheck: keyCheck)
    }

    private static func data(_ value: Any?) -> Data? { (value as? String).flatMap { Data(base64Encoded: $0) } }
}

struct UnsupportedManifestError: Error, Equatable {
    let version: Int
}

struct MalformedManifestError: Error {}

struct WeakPassphraseError: Error, Equatable {
    let problem: PassphraseProblem
}

/// The secret did not unwrap the data key: wrong passphrase or recovery code, or a damaged manifest.
struct WrongSecretError: Error {}

/// Result of first-time setup: show `recoveryCode` once, keep `dataKey` on the device, store `manifest` with backups.
struct KeySetup {
    let manifest: KeyManifest
    let recoveryCode: String
    let dataKey: Data
}

/**
 * Data key management (mobile-encrypted-drive-backup Decision 2), ported from the Android `KeyVault`:
 * a random 256-bit DK, wrapped with AES-256-GCM under a passphrase KEK (Argon2id, libsodium) and a
 * recovery-code KEK (HKDF-SHA256). Changing the passphrase re-wraps DK only. Argon2id is
 * deliberately slow; call it off the main thread.
 */
struct KeyVault {
    private static let keyBytes = 32
    private static let saltBytes = 16
    private static let keyCheckBytes = 16
    private static let recoveryInfo = Data("ibkrtax recovery kek v1".utf8)
    private static let keyCheckLabel = Data("ibkrtax key check v1".utf8)

    var argon2 = Argon2idParams.default

    func setUp(passphrase: String) throws -> KeySetup {
        if let problem = PassphrasePolicy.check(passphrase) { throw WeakPassphraseError(problem: problem) }
        let dataKey = Self.randomBytes(Self.keyBytes)
        let recoveryCode = RecoveryCode.generate()
        let recoverySalt = Self.randomBytes(Self.saltBytes)
        let (salt, wrapped) = try passphraseWrap(dataKey, passphrase)
        let manifest = KeyManifest(
            passphraseSalt: salt,
            argon2: argon2,
            passphraseWrappedKey: wrapped,
            recoverySalt: recoverySalt,
            recoveryWrappedKey: try Self.wrap(Self.recoveryKek(recoveryCode, recoverySalt), dataKey, purpose: "recovery"),
            keyCheck: Self.keyCheck(dataKey)
        )
        return KeySetup(manifest: manifest, recoveryCode: recoveryCode, dataKey: dataKey)
    }

    func unlock(_ manifest: KeyManifest, passphrase: String) throws -> Data {
        let kek = try Self.argon2Kek(passphrase, manifest.passphraseSalt, manifest.argon2)
        return try verified(manifest, Self.unwrap(kek, manifest.passphraseWrappedKey, purpose: "passphrase"))
    }

    /// Throws `WrongSecretError` for a malformed or wrong code.
    func unlock(_ manifest: KeyManifest, recoveryCode: String) throws -> Data {
        guard let code = RecoveryCode.normalize(recoveryCode) else { throw WrongSecretError() }
        return try verified(manifest, Self.unwrap(Self.recoveryKek(code, manifest.recoverySalt), manifest.recoveryWrappedKey, purpose: "recovery"))
    }

    /// New salt and wrap for the passphrase; the recovery wrap and DK stay the same.
    func changePassphrase(_ manifest: KeyManifest, dataKey: Data, newPassphrase: String) throws -> KeyManifest {
        if let problem = PassphrasePolicy.check(newPassphrase) { throw WeakPassphraseError(problem: problem) }
        _ = try verified(manifest, dataKey)
        var changed = manifest
        (changed.passphraseSalt, changed.passphraseWrappedKey) = try passphraseWrap(dataKey, newPassphrase)
        changed.argon2 = argon2
        return changed
    }

    /// A new recovery code and recovery wrap; the old code stops working, the passphrase wrap stays.
    func replaceRecoveryCode(_ manifest: KeyManifest, dataKey: Data) throws -> (manifest: KeyManifest, recoveryCode: String) {
        _ = try verified(manifest, dataKey)
        let code = RecoveryCode.generate()
        var changed = manifest
        changed.recoverySalt = Self.randomBytes(Self.saltBytes)
        changed.recoveryWrappedKey = try Self.wrap(Self.recoveryKek(code, changed.recoverySalt), dataKey, purpose: "recovery")
        return (changed, code)
    }

    /// True when `dataKey` belongs to `manifest`, e.g. a key kept on this phone.
    func matches(_ manifest: KeyManifest, _ dataKey: Data) -> Bool {
        let check = Self.keyCheck(dataKey)
        guard check.count == manifest.keyCheck.count else { return false }
        return zip(check, manifest.keyCheck).reduce(UInt8(0)) { $0 | ($1.0 ^ $1.1) } == 0
    }

    private func verified(_ manifest: KeyManifest, _ dataKey: Data) throws -> Data {
        guard matches(manifest, dataKey) else { throw WrongSecretError() }
        return dataKey
    }

    private func passphraseWrap(_ dataKey: Data, _ passphrase: String) throws -> (Data, Data) {
        let salt = Self.randomBytes(Self.saltBytes)
        return (salt, try Self.wrap(Self.argon2Kek(passphrase, salt, argon2), dataKey, purpose: "passphrase"))
    }

    /// Argon2id v1.3 over the passphrase's UTF-8 bytes, as Bouncy Castle does on Android. libsodium
    /// fixes parallelism at 1, the only value the Android app writes.
    static func argon2Kek(_ passphrase: String, _ salt: Data, _ params: Argon2idParams) throws -> Data {
        guard params.parallelism == 1, salt.count == Int(crypto_pwhash_saltbytes()), sodium_init() >= 0 else { throw WrongSecretError() }
        let password = passphrase.utf8.map { CChar(bitPattern: $0) }
        let saltBytes = [UInt8](salt)
        var out = [UInt8](repeating: 0, count: keyBytes)
        let status = crypto_pwhash(&out, UInt64(keyBytes), password, UInt64(password.count), saltBytes, UInt64(params.iterations),
                                   params.memoryKiB * 1024, crypto_pwhash_alg_argon2id13())
        guard status == 0 else { throw WrongSecretError() }
        return Data(out)
    }

    private static func recoveryKek(_ code: String, _ salt: Data) -> SymmetricKey {
        HKDF<SHA256>.deriveKey(inputKeyMaterial: SymmetricKey(data: Data(code.utf8)), salt: salt, info: recoveryInfo, outputByteCount: keyBytes)
    }

    private static func wrap(_ kek: Data, _ dataKey: Data, purpose: String) throws -> Data {
        try wrap(SymmetricKey(data: kek), dataKey, purpose: purpose)
    }

    /// Layout: 12-byte nonce, ciphertext, 16-byte tag (Java's AES/GCM output after the nonce).
    private static func wrap(_ kek: SymmetricKey, _ dataKey: Data, purpose: String) throws -> Data {
        try AES.GCM.seal(dataKey, using: kek, nonce: AES.GCM.Nonce(), authenticating: aad(purpose)).combined!
    }

    private static func unwrap(_ kek: Data, _ wrapped: Data, purpose: String) throws -> Data {
        try unwrap(SymmetricKey(data: kek), wrapped, purpose: purpose)
    }

    private static func unwrap(_ kek: SymmetricKey, _ wrapped: Data, purpose: String) throws -> Data {
        do {
            return try AES.GCM.open(AES.GCM.SealedBox(combined: wrapped), using: kek, authenticating: aad(purpose))
        } catch {
            throw WrongSecretError()
        }
    }

    private static func keyCheck(_ dataKey: Data) -> Data {
        Data(HMAC<SHA256>.authenticationCode(for: keyCheckLabel, using: SymmetricKey(data: dataKey))).prefix(keyCheckBytes)
    }

    private static func aad(_ purpose: String) -> Data { Data("ibkrtax.dk.v\(KeyManifest.version):\(purpose)".utf8) }

    static func randomBytes(_ count: Int) -> Data {
        var bytes = Data(count: count)
        let status = bytes.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, count, $0.baseAddress!) }
        precondition(status == errSecSuccess, "No secure random bytes")
        return bytes
    }
}

enum PassphraseProblem: Equatable {
    case tooShort

    /// One repeated character or a plain ascending/descending sequence.
    case tooSimple
}

/// Backup passphrase rules (key-management spec "Weak passphrase"), counted in UTF-16 units like the Android `PassphrasePolicy`.
enum PassphrasePolicy {
    static let minLength = 12

    static func check(_ passphrase: String) -> PassphraseProblem? {
        let units = Array(passphrase.utf16)
        if units.count < minLength { return .tooShort }
        if units.allSatisfy({ $0 == units[0] }) { return .tooSimple }
        let steps = Set(zip(units, units.dropFirst()).map(step))
        if steps == [1] || steps == [-1] { return .tooSimple }
        return nil
    }

    /// Digits wrap around, so "1234567890" and "...8901" count as one sequence.
    private static func step(_ from: UInt16, _ to: UInt16) -> Int {
        let zero = UInt16(UInt8(ascii: "0")), nine = UInt16(UInt8(ascii: "9"))
        if from == nine && to == zero { return 1 }
        if from == zero && to == nine { return -1 }
        return Int(to) - Int(from)
    }
}

/**
 * 26 Crockford base32 characters (130 random bits), shown as six groups for writing down. Input
 * is forgiving: case, spaces and dashes are ignored, and the look-alikes O, I and L are read as
 * 0, 1 and 1. Same format as the Android `RecoveryCode`.
 */
enum RecoveryCode {
    private static let alphabet = Array("0123456789ABCDEFGHJKMNPQRSTVWXYZ")
    static let length = 26
    private static let groups = [4, 4, 4, 4, 5, 5]

    /// 32 symbols divide 256 evenly, so the low five bits of a random byte are uniform.
    static func generate() -> String {
        String(KeyVault.randomBytes(length).map { alphabet[Int($0 & 31)] })
    }

    /// Groups for display, e.g. "K7QM-2HXP-…".
    static func format(_ code: String) -> String {
        var rest = Substring(code)
        return groups.map { size in
            defer { rest = rest.dropFirst(size) }
            return String(rest.prefix(size))
        }.joined(separator: "-")
    }

    /// Canonical form of what the user typed, or nil when it cannot be a recovery code.
    static func normalize(_ input: String) -> String? {
        let cleaned = input.uppercased().filter { $0 != "-" && !$0.isWhitespace }.map { character -> Character in
            switch character {
            case "O": return "0"
            case "I", "L": return "1"
            default: return character
            }
        }
        guard cleaned.count == length, cleaned.allSatisfy(alphabet.contains) else { return nil }
        return String(cleaned)
    }
}
