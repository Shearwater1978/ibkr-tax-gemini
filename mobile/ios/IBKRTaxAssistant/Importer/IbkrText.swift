import Foundation

/**
 * Field helpers ported from `src/parser.py` through the Android `IbkrText`. Keep them
 * behaviorally identical to the Python reference; shared fixtures and IbkrTextTests pin the parity.
 */
enum IbkrText {
    private static let months = ["JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"]

    private static let isoDate = regex(#"^(\d{4})-(\d{1,2})-(\d{1,2})$"#)
    private static let compactDate = regex(#"^(\d{4})(\d{2})(\d{2})$"#)
    private static let slashDate = regex(#"^(\d{1,2})/(\d{1,2})/(\d{4})$"#)
    private static let monthNameDate = regex(#"^(\d{1,2})-([A-Za-z]{3})-(\d{2})$"#)

    private static let leadingSymbol = regex(#"^([A-Za-z0-9.]+)\s*\("#)
    private static let embeddedTicker = regex(#"\(([A-Za-z0-9.]+),\s+[^,]+,\s+[A-Za-z0-9]{9,}\)"#)
    private static let anyIsin = regex(#"\b[A-Z0-9.]+\s*\(([A-Z]{2}[A-Z0-9]{10})\)"#, ignoreCase: true)
    private static let splitRatio = regex(#"\bSPLIT\s+(\d+(?:\.\d+)?)\s+FOR\s+(\d+(?:\.\d+)?)\b"#, ignoreCase: true)

    private static let transferKeywords = ["ACATS", "TRANSFER", "INTERNAL", "POSITION MOVEM", "RECEIVE DELIVER", "CASH IN LIEU"]

    /// Python `parse_decimal`, but returns nil instead of 0 for unparseable input.
    static func parseDecimalOrNull(_ value: String) -> ExactDecimal? {
        let clean = value.replacingOccurrences(of: ",", with: "").replacingOccurrences(of: "\"", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.isEmpty { return .zero }
        return ExactDecimal(clean)
    }

    /// Converts IBKR dates (optionally with ", time") to ISO `YYYY-MM-DD`, or nil.
    static func normalizeDate(_ value: String) -> String? {
        if value.isEmpty { return nil }
        let clean = value.components(separatedBy: ",")[0].trimmingCharacters(in: .whitespacesAndNewlines).components(separatedBy: " ")[0]
        if let g = groups(isoDate, clean), let date = isoString(year: g[0], month: g[1], day: g[2]) { return date }
        if let g = groups(compactDate, clean), let date = isoString(year: g[0], month: g[1], day: g[2]) { return date }
        if let g = groups(slashDate, clean) {
            // US month-first, then day-first, as in the Python reference.
            if let date = isoString(year: g[2], month: g[0], day: g[1]) { return date }
            if let date = isoString(year: g[2], month: g[1], day: g[0]) { return date }
        }
        if let g = groups(monthNameDate, clean), let month = months.firstIndex(of: g[1].uppercased()), let short = Int(g[2]) {
            // Python %y: 69-99 -> 19xx, 00-68 -> 20xx.
            let year = short >= 69 ? 1900 + short : 2000 + short
            if let date = isoString(year: String(year), month: String(month + 1), day: g[0]) { return date }
        }
        return nil
    }

    static func extractTicker(_ description: String, symbolColumn: String, quantity: ExactDecimal) -> String {
        if quantity.signum < 0 {
            if let symbol = firstSymbol(symbolColumn) { return symbol }
            if let g = groups(leadingSymbol, description) { return g[0].trimmingCharacters(in: .whitespacesAndNewlines) }
        }
        if quantity.signum > 0, let g = groups(embeddedTicker, description) {
            return g[0].trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if let symbol = firstSymbol(symbolColumn) { return symbol }
        if let g = groups(leadingSymbol, description) { return g[0].trimmingCharacters(in: .whitespacesAndNewlines) }

        let candidate = words(description.trimmingCharacters(in: .whitespacesAndNewlines)).first ?? ""
        if !candidate.isEmpty && isPythonUpper(candidate) && candidate.count < 12 { return candidate }
        return "UNKNOWN"
    }

    static func extractIsin(_ description: String, ticker: String = "") -> String {
        if !ticker.isEmpty {
            let escaped = NSRegularExpression.escapedPattern(for: ticker.trimmingCharacters(in: .whitespacesAndNewlines))
            if let g = groups(regex(#"\(\s*"# + escaped + #"\s*,\s*[^,]+,\s*([A-Z]{2}[A-Z0-9]{10})\s*\)"#, ignoreCase: true), description) {
                return g[0].uppercased()
            }
            if let g = groups(regex(#"\b"# + escaped + #"\s*\(([A-Z]{2}[A-Z0-9]{10})\)"#, ignoreCase: true), description) {
                return g[0].uppercased()
            }
        }
        return groups(anyIsin, description)?[0].uppercased() ?? ""
    }

    static func classifyTradeType(_ description: String, quantity: ExactDecimal) -> String {
        let upper = description.uppercased()
        if transferKeywords.contains(where: upper.contains) { return "TRANSFER" }
        switch quantity.signum {
        case 1: return "BUY"
        case -1: return "SELL"
        default: return "UNKNOWN"
        }
    }

    static func classifyCorporateAction(_ description: String, quantity: ExactDecimal) -> String {
        if description.uppercased().contains("SPINOFF") { return "SPINOFF" }
        switch quantity.signum {
        case 1: return "STOCK_DIV"
        case -1: return "MERGER"
        default: return "CORP_ACTION_INFO"
        }
    }

    static func extractSplitRatio(_ description: String) -> ExactDecimal? {
        guard let g = groups(splitRatio, description),
              let numerator = ExactDecimal(g[0]), let denominator = ExactDecimal(g[1]) else { return nil }
        return numerator.divided(by: denominator)
    }

    private static func firstSymbol(_ symbolColumn: String) -> String? {
        let trimmed = symbolColumn.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return nil }
        let first = trimmed.components(separatedBy: ",")[0].trimmingCharacters(in: .whitespacesAndNewlines)
        return words(first).first
    }

    /// Words of already trimmed text (Kotlin `split(Regex("\\s+"))`).
    private static func words(_ text: String) -> [String] {
        text.components(separatedBy: .whitespacesAndNewlines).filter { !$0.isEmpty }
    }

    private static func isPythonUpper(_ value: String) -> Bool {
        value.contains(where: \.isUppercase) && !value.contains(where: \.isLowercase)
    }

    private static func isoString(year: String, month: String, day: String) -> String? {
        guard let y = Int(year), let m = Int(month), let d = Int(day), (1...12).contains(m), d >= 1 else { return nil }
        let leap = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
        let lengths = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
        guard d <= lengths[m - 1] else { return nil }
        return String(format: "%04d-%02d-%02d", y, m, d)
    }

    private static func regex(_ pattern: String, ignoreCase: Bool = false) -> NSRegularExpression {
        try! NSRegularExpression(pattern: pattern, options: ignoreCase ? [.caseInsensitive] : [])
    }

    /// Capture groups of the first match (Kotlin `Regex.find`), or nil.
    private static func groups(_ regex: NSRegularExpression, _ text: String) -> [String]? {
        let range = NSRange(text.startIndex..., in: text)
        guard let match = regex.firstMatch(in: text, range: range) else { return nil }
        return (1..<match.numberOfRanges).map { index in
            Range(match.range(at: index), in: text).map { String(text[$0]) } ?? ""
        }
    }
}
