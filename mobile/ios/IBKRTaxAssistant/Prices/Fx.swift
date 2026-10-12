import Foundation

/**
 * NBP table A mid rates (PLN per one unit of each currency) and their table date. Used only for
 * the informational USD total, never for tax results (which use the desktop T-1 rule).
 * Ported from the Android `fx` package.
 */
struct FxRates: Equatable {
    var effectiveDate: String
    var plnPerUnit: [String: ExactDecimal]

    /// Converts through PLN cross rates; nil when a needed rate is missing.
    func toUsd(_ amount: ExactDecimal, currency: String) -> ExactDecimal? {
        if currency == UsdConverter.usd { return amount }
        guard let usd = rate(UsdConverter.usd), let from = rate(currency) else { return nil }
        return amount.multiplied(by: from).divided(by: usd)
    }

    private func rate(_ currency: String) -> ExactDecimal? { currency == "PLN" ? .one : plnPerUnit[currency] }
}

/**
 * The one place where amounts become approximate USD: header totals, summary cards, dividends
 * and the USD view all use it, so they cannot disagree. Nil when a needed NBP rate is missing.
 */
enum UsdConverter {
    static let usd = "USD"

    static func toUsd(_ amount: ExactDecimal, currency: String, rates: FxRates?) -> ExactDecimal? {
        currency == usd ? amount : rates?.toUsd(amount, currency: currency)
    }
}

/// Cached NBP rates; implementations keep them in the encrypted database.
protocol FxRateCache {
    func get() throws -> FxRates?
    func put(_ rates: FxRates) throws
}

enum FxRefreshOutcome: Equatable {
    case updated

    /// NBP could not be reached or answered unexpectedly; cached rates (if any) stay in use.
    case failed
}

/**
 * Reads the latest NBP table A. The request names no currency, account, or holding: the whole
 * public table is fetched, and no key is needed.
 */
struct NbpClient {
    private static let url = "https://api.nbp.pl/api/exchangerates/tables/A/?format=json"

    var transport: HttpTransport = URLSessionTransport()

    func latestTableA() async -> FxRates? {
        guard let response = try? await transport.get(Self.url, headers: ["Accept": "application/json"]),
              (200...299).contains(response.status),
              let tables = (try? JSONSerialization.jsonObject(with: response.body)) as? [[String: Any]],
              let table = tables.first, let date = table["effectiveDate"] as? String,
              let rates = table["rates"] as? [[String: Any]] else { return nil }
        var mids: [String: ExactDecimal] = [:]
        for rate in rates {
            guard let code = rate["code"] as? String, let mid = jsonDecimal(rate["mid"]) else { return nil }
            if mid.signum > 0 { mids[code] = mid }
        }
        return mids.isEmpty ? nil : FxRates(effectiveDate: date, plnPerUnit: mids)
    }
}

struct FxService {
    let client: NbpClient
    let cache: FxRateCache

    func refresh() async throws -> FxRefreshOutcome {
        guard let rates = await client.latestTableA() else { return .failed }
        try cache.put(rates)
        return .updated
    }

    func rates() throws -> FxRates? { try cache.get() }
}
