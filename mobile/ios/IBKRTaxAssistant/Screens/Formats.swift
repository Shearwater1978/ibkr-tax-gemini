import Foundation

/// Display formatting only, as the Android `Formats`; amounts are shown in the currency they are given in.
enum Formats {
    static func money(_ value: ExactDecimal, _ currency: String) -> String { "\(number(value, 2, 2)) \(currency)" }

    static func signedMoney(_ value: ExactDecimal, _ currency: String) -> String { "\(signedNumber(value)) \(currency)" }

    static func quantity(_ value: ExactDecimal) -> String { number(value, 0, 8) }

    static func signedNumber(_ value: ExactDecimal) -> String { (value.signum > 0 ? "+" : "") + number(value, 2, 2) }

    static func signedPercent(_ value: ExactDecimal) -> String { (value.signum > 0 ? "+" : "") + number(value, 2, 2) + "%" }

    static func number(_ value: ExactDecimal, _ minFraction: Int, _ maxFraction: Int, locale: Locale = .current) -> String {
        let formatter = NumberFormatter()
        formatter.locale = locale
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = minFraction
        formatter.maximumFractionDigits = maxFraction
        formatter.roundingMode = .halfEven
        return formatter.string(from: NSDecimalNumber(string: value.description, locale: Locale(identifier: "en_US_POSIX"))) ?? value.description
    }

    /// A stored ISO 8601 timestamp in the user's short date and time style.
    static func dateTime(_ stored: String) -> String {
        guard let date = StoredTime.date(stored) else { return stored }
        return DateFormatter.localizedString(from: date, dateStyle: .short, timeStyle: .short)
    }
}
