import Foundation

/**
 * Replaceable market-data adapter. Requests carry only instrument symbols (and the user's
 * provider key): never quantities, account data, names, or report contents
 * (mobile-market-prices spec). Ported from the Android `prices` package.
 */
protocol MarketDataProvider {
    /// `onProgress` gets how many of `symbols` have been requested so far.
    func latestQuotes(_ symbols: Set<String>, onProgress: @escaping (Int) -> Void) async -> PriceResult
}

extension MarketDataProvider {
    func latestQuotes(_ symbols: Set<String>) async -> PriceResult { await latestQuotes(symbols) { _ in } }
}

struct Quote: Equatable {
    var symbol: String
    var price: ExactDecimal
    var currency: String
    /// When the provider says the price was set (last trade or close).
    var quoteTime: Date
    /// When this app retrieved it.
    var retrievedAt: Date
    /// Previous session's close, for the daily change; nil when the provider has none.
    var previousClose: ExactDecimal?

    var dailyChange: ExactDecimal? { previousClose.map { price.subtracting($0, precision: .max) } }
}

enum PriceResult: Equatable {
    /// Symbols the provider does not know are simply absent from `quotes`.
    case success([String: Quote])

    /// `partial` holds the quotes received before the failure; they are still valid.
    case failure(PriceFailure, partial: [String: Quote] = [:])
}

enum PriceFailure: Equatable {
    case offline
    case providerError
    case rateLimited
    case invalidKey
}

struct HttpResponse: Equatable {
    var status: Int
    var body: Data
}

/// Minimal HTTPS GET seam so tests never touch the network. Throws when offline.
protocol HttpTransport {
    func get(_ url: String, headers: [String: String]) async throws -> HttpResponse
}

/// Ephemeral session: no cookies, no URL cache on disk, so no market data or keys are written to storage.
struct URLSessionTransport: HttpTransport {
    private static let session: URLSession = {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 10
        configuration.urlCache = nil
        return URLSession(configuration: configuration)
    }()

    func get(_ url: String, headers: [String: String]) async throws -> HttpResponse {
        guard let target = URL(string: url) else { throw URLError(.badURL) }
        var request = URLRequest(url: target)
        for (name, value) in headers { request.setValue(value, forHTTPHeaderField: name) }
        let (data, response) = try await Self.session.data(for: request)
        return HttpResponse(status: (response as? HTTPURLResponse)?.statusCode ?? 0, body: data)
    }
}

/// Java `URLEncoder.encode(_, "UTF-8")`: form encoding, so a space becomes "+".
func formEncoded(_ text: String) -> String {
    var allowed = CharacterSet.alphanumerics.intersection(CharacterSet(charactersIn: Unicode.Scalar(0)...Unicode.Scalar(127)))
    allowed.insert(charactersIn: ".-*_ ")
    return (text.addingPercentEncoding(withAllowedCharacters: allowed) ?? "").replacingOccurrences(of: " ", with: "+")
}

/// A JSON number (or numeric string) as an exact decimal, as org.json's `toString()` gives it.
func jsonDecimal(_ value: Any?) -> ExactDecimal? {
    switch value {
    case let number as NSNumber: return ExactDecimal(number.stringValue)
    case let text as String: return ExactDecimal(text)
    default: return nil
    }
}

/**
 * Sliding-window limit on provider calls, shared across refreshes so repeated refreshes
 * cannot exceed the provider's per-minute quota.
 */
actor RateLimiter {
    private let maxCalls: Int
    private let window: TimeInterval
    private let now: () -> Date
    private let sleep: (TimeInterval) async -> Void
    private var calls: [Date] = []

    init(maxCalls: Int, window: TimeInterval, now: @escaping () -> Date = Date.init,
         sleep: @escaping (TimeInterval) async -> Void = { try? await Task.sleep(nanoseconds: UInt64($0 * 1_000_000_000)) }) {
        self.maxCalls = maxCalls
        self.window = window
        self.now = now
        self.sleep = sleep
    }

    /// Waits until another call fits in the window, then records it.
    func acquire() async {
        while true {
            let current = now()
            calls.removeAll { $0 <= current.addingTimeInterval(-window) }
            if calls.count < maxCalls {
                calls.append(current)
                return
            }
            await sleep(calls[0].addingTimeInterval(window).timeIntervalSince(current))
        }
    }

    /// Finnhub's free tier allows 60 calls per minute; keep some headroom.
    static func forFinnhubFreeTier() -> RateLimiter { RateLimiter(maxCalls: 55, window: 60) }
}

/**
 * Recognises text that could be a Finnhub API key (a single alphanumeric token), so the app
 * can offer a just-copied key. It never decides validity: the provider does.
 */
enum FinnhubKeyFormat {
    static func candidate(_ clipboardText: String?) -> String? {
        guard let text = clipboardText?.trimmingCharacters(in: .whitespacesAndNewlines),
              (16...64).contains(text.count),
              text.unicodeScalars.allSatisfy({ ("a"..."z").contains($0) || ("A"..."Z").contains($0) || ("0"..."9").contains($0) }) else { return nil }
        return text
    }
}

enum KeyCheckResult: Equatable {
    case valid

    /// The provider refused the key; it is not saved.
    case rejected

    /// The key could not be checked now (offline, rate limit, provider error); it is saved anyway.
    case unverified(PriceFailure)
}

/**
 * Checks a newly entered key with one request. The request carries only a well-known test
 * symbol and the key, never anything from the user's portfolio.
 */
enum ApiKeyCheck {
    static let testSymbol = "AAPL"

    static func check(_ provider: MarketDataProvider) async -> KeyCheckResult {
        switch await provider.latestQuotes([testSymbol]) {
        // An accepted key may still lack a quote for the symbol; acceptance is what matters.
        case .success: return .valid
        case .failure(.invalidKey, _): return .rejected
        case .failure(let reason, _): return .unverified(reason)
        }
    }
}
