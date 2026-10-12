import CryptoKit
import Foundation

/// Kinds of direct identifiers; the kind is mixed into the HMAC input for domain separation.
enum IdentifierKind: String {
    case account = "ACCOUNT"
    case name = "NAME"
    case taxId = "TAX_ID"
    case sourceFile = "SOURCE_FILE"

    /// SHA-256 of a report's bytes; keyed so the fingerprint cannot be matched outside the device.
    case reportContent = "REPORT_CONTENT"
}

/**
 * HMAC-SHA256 pseudonyms keyed by a device-bound secret (data-anonymization spec). The Secure
 * Enclave cannot compute HMACs, so the key is a `WrappedKeyProvider` secret wrapped by a device
 * key. Without it a pseudonym cannot be linked back to its identifier. Input and output match
 * the Android `Pseudonymizer`.
 */
struct Pseudonymizer {
    let keyProvider: () throws -> Data

    /// The app's pseudonymizer: its key is created once and stored only wrapped by the device key.
    static func onDevice(directory: URL? = nil) throws -> Pseudonymizer {
        let keys = WrappedKeyProvider(
            file: try (directory ?? SecureFiles.directory()).appendingPathComponent(keyFileName),
            wrapper: DeviceKeyWrapper(tag: DeviceKeys.databaseWrapTag)
        )
        return Pseudonymizer(keyProvider: keys.getOrCreate)
    }

    static let keyFileName = "pseudonym.key"

    func pseudonym(_ kind: IdentifierKind, _ identifier: String) throws -> String {
        let input = "\(kind.rawValue):\(identifier.trimmingCharacters(in: .whitespacesAndNewlines))"
        let mac = HMAC<SHA256>.authenticationCode(for: Data(input.utf8), using: SymmetricKey(data: try keyProvider()))
        return mac.map { String(format: "%02x", $0) }.joined()
    }
}

/// UI masking: only the last four characters stay visible.
enum Masking {
    private static let visible = 4
    private static let mask = "•"

    static func lastFour(_ value: String) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.count <= visible { return String(repeating: mask, count: visible) }
        return String(repeating: mask, count: trimmed.count - visible) + trimmed.suffix(visible)
    }
}

/**
 * IBKR names exports after the account (e.g. "U1234567_2023.csv"); file names shown on
 * screen keep only the last four digits of account-like tokens (data-anonymization spec).
 */
enum FileNameMasking {
    // One or two capital letters followed by 6+ digits, not inside a longer word or number.
    private static let account = try! NSRegularExpression(pattern: #"(?<![A-Za-z0-9])([A-Z]{1,2})(\d{2,})(\d{4})(?!\d)"#)

    static func mask(_ fileName: String) -> String {
        let name = fileName as NSString
        var result = ""
        var last = 0
        for match in account.matches(in: fileName, range: NSRange(location: 0, length: name.length)) {
            let hidden = match.range(at: 2).length
            result += name.substring(with: NSRange(location: last, length: match.range.location - last))
            result += name.substring(with: match.range(at: 1)) + String(repeating: "•", count: hidden) + name.substring(with: match.range(at: 3))
            last = match.range.location + match.range.length
        }
        return result + name.substring(from: last)
    }
}
