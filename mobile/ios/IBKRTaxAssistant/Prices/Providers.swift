import Foundation

/**
 * Finnhub `/quote` adapter for US-listed symbols (quoted in USD). The user's key is sent as a
 * header so it never appears in URLs; the only query parameter is the symbol.
 */
struct FinnhubProvider: MarketDataProvider {
    private static let baseUrl = "https://finnhub.io/api/v1"
    private static let tokenHeader = "X-Finnhub-Token"

    let apiKey: String
    var transport: HttpTransport = URLSessionTransport()
    var now: () -> Date = Date.init
    var limiter: RateLimiter?

    func latestQuotes(_ symbols: Set<String>, onProgress: @escaping (Int) -> Void) async -> PriceResult {
        var quotes: [String: Quote] = [:]
        for (index, symbol) in symbols.sorted().enumerated() {
            await limiter?.acquire()
            let response: HttpResponse
            do {
                response = try await transport.get("\(Self.baseUrl)/quote?symbol=\(formEncoded(symbol))", headers: [Self.tokenHeader: apiKey])
            } catch {
                return .failure(.offline, partial: quotes)
            }
            switch response.status {
            case 200...299: quotes[symbol] = parse(symbol, response.body)
            case 401: return .failure(.invalidKey, partial: quotes)
            // Symbol not covered by the user's plan: leave it unavailable.
            case 403: break
            case 429: return .failure(.rateLimited, partial: quotes)
            default: return .failure(.providerError, partial: quotes)
            }
            onProgress(index + 1)
        }
        return .success(quotes)
    }

    private func parse(_ symbol: String, _ body: Data) -> Quote? {
        guard let json = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any],
              let price = jsonDecimal(json["c"]), let time = (json["t"] as? NSNumber)?.int64Value,
              // Finnhub answers unknown symbols with zeros instead of an error.
              price.signum > 0, time > 0 else { return nil }
        let previousClose = jsonDecimal(json["pc"]).flatMap { $0.signum > 0 ? $0 : nil }
        return Quote(symbol: symbol, price: price, currency: "USD", quoteTime: Date(timeIntervalSince1970: TimeInterval(time)), retrievedAt: now(), previousClose: previousClose)
    }
}

/**
 * Keyless fallback used while the user has no Finnhub key: Yahoo Finance's public spark
 * endpoint. It is unofficial and not licensed for this use (mobile-market-prices spec), so it
 * can throttle or change without notice. The only data sent is the symbols.
 *
 * US symbols: one spark request covers up to `batchSize` of them. Spark has no currency field;
 * symbols without an exchange suffix are US listings, which Yahoo quotes in USD.
 * European symbols (e.g. SAP.DE): one chart request each, because the chart reports the currency.
 */
struct YahooProvider: MarketDataProvider {
    private static let baseUrl = "https://query1.finance.yahoo.com/v8/finance/spark"
    private static let chartUrl = "https://query1.finance.yahoo.com/v8/finance/chart"
    private static let pence: Set = ["GBp", "GBX"]

    /// Yahoo refuses spark requests with more than 20 symbols.
    static let batchSize = 20

    // Yahoo answers 429 to requests without a browser-like user agent.
    private static let headers = ["User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 Safari/604.1"]

    var transport: HttpTransport = URLSessionTransport()
    var now: () -> Date = Date.init
    var limiter: RateLimiter?

    /// IBKR writes share classes as "BRK B" (Finnhub: "BRK.B"); Yahoo uses "BRK-B".
    static func yahooSymbol(_ symbol: String) -> String {
        symbol.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "[ .]+", with: "-", options: .regularExpression)
    }

    func latestQuotes(_ symbols: Set<String>, onProgress: @escaping (Int) -> Void) async -> PriceResult {
        var quotes: [String: Quote] = [:]
        var requested = 0
        let sorted = symbols.sorted()
        let european = sorted.filter(Markets.isEuropean)
        let us = sorted.filter { !Markets.isEuropean($0) }
        for start in stride(from: 0, to: us.count, by: Self.batchSize) {
            let batch = Array(us[start..<min(start + Self.batchSize, us.count)])
            await limiter?.acquire()
            var seen = Set<String>()
            let query = batch.map(Self.yahooSymbol).filter { seen.insert($0).inserted }.map(formEncoded).joined(separator: ",")
            let response: HttpResponse
            do {
                response = try await transport.get("\(Self.baseUrl)?symbols=\(query)&range=1d&interval=5m", headers: Self.headers)
            } catch {
                return .failure(.offline, partial: quotes)
            }
            switch response.status {
            case 200...299: quotes.merge(parse(batch, response.body)) { $1 }
            // None of the symbols is known: leave them unavailable.
            case 404: break
            case 429: return .failure(.rateLimited, partial: quotes)
            default: return .failure(.providerError, partial: quotes)
            }
            requested += batch.count
            onProgress(requested)
        }
        for symbol in european {
            await limiter?.acquire()
            let response: HttpResponse
            do {
                response = try await transport.get("\(Self.chartUrl)/\(formEncoded(symbol))?range=1d&interval=1d", headers: Self.headers)
            } catch {
                return .failure(.offline, partial: quotes)
            }
            switch response.status {
            case 200...299: quotes[symbol] = chartQuote(symbol, response.body)
            case 404: break
            case 429: return .failure(.rateLimited, partial: quotes)
            default: return .failure(.providerError, partial: quotes)
            }
            requested += 1
            onProgress(requested)
        }
        return .success(quotes)
    }

    /// A European quote in its own currency; pence (GBp) become pounds so they match GBP holdings.
    private func chartQuote(_ symbol: String, _ body: Data) -> Quote? {
        guard let json = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any],
              let results = (json["chart"] as? [String: Any])?["result"] as? [[String: Any]],
              let meta = results.first?["meta"] as? [String: Any] else { return nil }
        let currencyCode = meta["currency"] as? String ?? ""
        let isPence = Self.pence.contains(currencyCode)
        func amount(_ name: String) -> ExactDecimal? { jsonDecimal(meta[name]).map { isPence ? $0.movedPointLeft(2) : $0 } }
        let currency = isPence ? "GBP" : currencyCode
        guard let price = amount("regularMarketPrice"), let time = (meta["regularMarketTime"] as? NSNumber)?.int64Value,
              price.signum > 0, time > 0, !currency.isEmpty else { return nil }
        let previousClose = (amount("previousClose") ?? amount("chartPreviousClose")).flatMap { $0.signum > 0 ? $0 : nil }
        return Quote(symbol: symbol, price: price, currency: currency, quoteTime: Date(timeIntervalSince1970: TimeInterval(time)), retrievedAt: now(), previousClose: previousClose)
    }

    /// Unknown symbols are absent from the response and stay unavailable.
    private func parse(_ batch: [String], _ body: Data) -> [String: Quote] {
        guard let json = (try? JSONSerialization.jsonObject(with: body)) as? [String: Any] else { return [:] }
        let retrievedAt = now()
        var quotes: [String: Quote] = [:]
        for symbol in batch {
            if let series = json[Self.yahooSymbol(symbol)] as? [String: Any], let quote = quote(symbol, series, retrievedAt) { quotes[symbol] = quote }
        }
        return quotes
    }

    private func quote(_ symbol: String, _ series: [String: Any], _ retrievedAt: Date) -> Quote? {
        guard let times = series["timestamp"] as? [Any], let closes = series["close"] as? [Any] else { return nil }
        // The latest 5-minute bar with a trade; bars without trades have a null close.
        guard let last = (0..<min(times.count, closes.count)).reversed().first(where: { !(closes[$0] is NSNull) }),
              let price = jsonDecimal(closes[last]), let time = (times[last] as? NSNumber)?.int64Value,
              price.signum > 0, time > 0 else { return nil }
        let previousClose = (jsonDecimal(series["previousClose"]) ?? jsonDecimal(series["chartPreviousClose"])).flatMap { $0.signum > 0 ? $0 : nil }
        return Quote(symbol: symbol, price: price, currency: "USD", quoteTime: Date(timeIntervalSince1970: TimeInterval(time)), retrievedAt: retrievedAt, previousClose: previousClose)
    }
}
