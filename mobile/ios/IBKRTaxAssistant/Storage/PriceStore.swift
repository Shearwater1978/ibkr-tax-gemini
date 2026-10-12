import Foundation

struct EmptyApiKeyError: Error {}

/**
 * Provider key and quote cache in the SQLCipher database, so both are encrypted at rest under
 * the device key. The key is never logged or returned in diagnostics. Port of the Android `PriceStore`.
 */
struct PriceStore: ApiKeyStore, QuoteCache {
    private static let apiKey = "finnhub_api_key"

    let database: EncryptedDatabase

    func get() throws -> String? {
        guard case .text(let value)? = try database.query("SELECT value FROM app_settings WHERE key = ?", [.text(Self.apiKey)]).first?.first else { return nil }
        return value
    }

    func set(_ key: String) throws {
        let trimmed = key.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw EmptyApiKeyError() }
        try database.execute("INSERT OR REPLACE INTO app_settings (key, value) VALUES (?, ?)", [.text(Self.apiKey), .text(trimmed)])
    }

    func clear() throws {
        try database.execute("DELETE FROM app_settings WHERE key = ?", [.text(Self.apiKey)])
    }

    func get(_ symbols: Set<String>) throws -> [String: Quote] {
        if symbols.isEmpty { return [:] }
        let placeholders = Array(repeating: "?", count: symbols.count).joined(separator: ",")
        let rows = try database.query(
            "SELECT symbol, price, currency, quote_time, retrieved_at, previous_close FROM price_cache WHERE symbol IN (\(placeholders))",
            symbols.map { .text($0) }
        )
        var quotes: [String: Quote] = [:]
        for row in rows {
            guard case .text(let symbol)? = row[0], case .text(let price)? = row[1], case .text(let currency)? = row[2],
                  case .text(let quoteTime)? = row[3], case .text(let retrievedAt)? = row[4],
                  let priceValue = ExactDecimal(price), let quoteDate = Self.date(quoteTime), let retrievedDate = Self.date(retrievedAt) else { continue }
            var previousClose: ExactDecimal?
            if case .text(let close)? = row[5] { previousClose = ExactDecimal(close) }
            quotes[symbol] = Quote(symbol: symbol, price: priceValue, currency: currency, quoteTime: quoteDate, retrievedAt: retrievedDate, previousClose: previousClose)
        }
        return quotes
    }

    func putAll(_ quotes: [Quote]) throws {
        try database.transaction {
            for quote in quotes {
                try database.execute(
                    "INSERT OR REPLACE INTO price_cache (symbol, price, currency, quote_time, retrieved_at, previous_close) VALUES (?, ?, ?, ?, ?, ?)",
                    [.text(quote.symbol), .text("\(quote.price)"), .text(quote.currency), .text(Self.text(quote.quoteTime)),
                     .text(Self.text(quote.retrievedAt)), quote.previousClose.map { .text("\($0)") }]
                )
            }
        }
    }

    private static func text(_ date: Date) -> String { StoredTime.text(date) }

    private static func date(_ text: String) -> Date? { StoredTime.date(text) }
}

/// Timestamps as stored text: ISO 8601 in UTC with milliseconds, like the Android `Instant` text.
enum StoredTime {
    static func text(_ date: Date) -> String { formatter.string(from: date) }

    static func date(_ text: String) -> Date? { formatter.date(from: text) }

    private static let formatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()
}
