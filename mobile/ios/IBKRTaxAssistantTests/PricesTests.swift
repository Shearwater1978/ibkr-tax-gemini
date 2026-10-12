import XCTest
@testable import IBKRTaxAssistant

private func d(_ text: String) -> ExactDecimal { ExactDecimal(text)! }

private func t(_ iso: String) -> Date { ISO8601DateFormatter().date(from: iso)! }

/// Records every request; never touches the network.
final class RecordingTransport: HttpTransport {
    private(set) var calls: [(url: String, headers: [String: String])] = []
    private let respond: (String) throws -> HttpResponse

    init(_ respond: @escaping (String) throws -> HttpResponse) { self.respond = respond }

    func get(_ url: String, headers: [String: String]) async throws -> HttpResponse {
        calls.append((url, headers))
        return try respond(url)
    }
}

/// Programmable price source that records every request for data-minimization checks.
final class FakeMarketDataProvider: MarketDataProvider {
    var quotes: [String: Quote] = [:]
    var failure: PriceFailure?
    private(set) var requests: [Set<String>] = []

    func setQuote(_ quote: Quote) { quotes[quote.symbol] = quote }

    func latestQuotes(_ symbols: Set<String>, onProgress: @escaping (Int) -> Void) async -> PriceResult {
        requests.append(symbols)
        if let failure { return .failure(failure) }
        onProgress(symbols.count)
        return .success(quotes.filter { symbols.contains($0.key) })
    }
}

final class MemoryKeys: ApiKeyStore {
    var key: String?
    init(_ key: String? = nil) { self.key = key }
    func get() -> String? { key }
    func set(_ key: String) { self.key = key }
    func clear() { key = nil }
}

final class MemoryCache: QuoteCache {
    var quotes: [String: Quote] = [:]
    func get(_ symbols: Set<String>) -> [String: Quote] { quotes.filter { symbols.contains($0.key) } }
    func putAll(_ quotes: [Quote]) { for quote in quotes { self.quotes[quote.symbol] = quote } }
}

private func holding(_ ticker: String, _ currency: String, isin: String = "") -> Holding {
    Holdings.fromLots([OpenLot(ticker: ticker, isin: isin, date: "2023-01-01", quantity: .one, price: d("10"), currency: currency)])[0]
}

private func response(_ status: Int, _ body: String) -> HttpResponse { HttpResponse(status: status, body: Data(body.utf8)) }

/// Mirrors the Android `PricesTest`.
final class PricesTests: XCTestCase {
    // 2024-01-03 is a Wednesday; New York is UTC-5 in January.
    private let marketOpen = t("2024-01-03T16:00:00Z") // 11:00 New York
    private let afterClose = t("2024-01-03T23:00:00Z") // 18:00 New York
    private let saturday = t("2024-01-06T16:00:00Z")
    private lazy var holdings = [holding("AAPL", "USD"), holding("SAP", "EUR")]

    private func quoteJson(_ price: String, _ epochSeconds: Int) -> HttpResponse {
        response(200, #"{"c":\#(price),"d":1.2,"dp":0.6,"h":191,"l":188,"o":189,"pc":189.5,"t":\#(epochSeconds)}"#)
    }

    /// Spark series for `symbol`: an empty bar (null close) after the last trade, as Yahoo sends.
    private func sparkSeries(_ symbol: String, _ price: String, _ epochSeconds: Int) -> String {
        #""\#(symbol)":{"symbol":"\#(symbol)","timestamp":[\#(epochSeconds - 300),\#(epochSeconds),\#(epochSeconds + 300)],"close":[189.0,\#(price),null],"previousClose":189.5,"chartPreviousClose":189.5}"#
    }

    private func spark(_ series: [String]) -> HttpResponse { response(200, "{" + series.joined(separator: ",") + "}") }

    private func requestedSymbols(_ url: String) -> [String] {
        String(url.split(separator: "=")[1].split(separator: "&")[0]).components(separatedBy: ",")
    }

    private var openSeconds: Int { Int(marketOpen.timeIntervalSince1970) }

    // --- Yahoo fallback ---

    func testYahooSendsOnlyTheSymbolsAndNoKey() async throws {
        let transport = RecordingTransport { _ in self.spark([self.sparkSeries("AAPL", "190.1", self.openSeconds)]) }
        let result = await YahooProvider(transport: transport, now: { self.marketOpen }).latestQuotes(["AAPL"])

        XCTAssertEqual(transport.calls.map(\.url), ["https://query1.finance.yahoo.com/v8/finance/spark?symbols=AAPL&range=1d&interval=5m"])
        XCTAssertEqual(transport.calls.map { Set($0.headers.keys) }, [["User-Agent"]])
        guard case .success(let quotes) = result, let quote = quotes["AAPL"] else { return XCTFail("\(result)") }
        XCTAssertEqual(quote.price.compare(d("190.1")), 0)
        XCTAssertEqual(quote.dailyChange?.compare(d("0.6")), 0)
        XCTAssertEqual(quote.currency, "USD")
        XCTAssertEqual(quote.quoteTime, marketOpen)
    }

    func testYahooAsksForTwentySymbolsPerRequest() async {
        let symbols = Set((1...45).map { "S\($0)" })
        let transport = RecordingTransport { url in self.spark(self.requestedSymbols(url).map { self.sparkSeries($0, "10", self.openSeconds) }) }
        let result = await YahooProvider(transport: transport).latestQuotes(symbols)

        XCTAssertEqual(transport.calls.map { requestedSymbols($0.url).count }, [20, 20, 5])
        guard case .success(let quotes) = result else { return XCTFail() }
        XCTAssertEqual(Set(quotes.keys), symbols)
    }

    func testRefreshReportsProgressPerYahooBatch() async throws {
        let transport = RecordingTransport { url in self.spark(self.requestedSymbols(url).map { self.sparkSeries($0, "10", self.openSeconds) }) }
        let service = PriceService(keys: MemoryKeys(), cache: MemoryCache(), providerFor: { _ in fatalError("no key") }, now: { self.marketOpen },
                                   fallback: YahooProvider(transport: transport))
        var seen: [RefreshProgress] = []

        _ = try await service.refresh((1...45).map { holding("S\($0)", "USD") }) { seen.append($0) }

        XCTAssertEqual(seen.map(\.done), [0, 20, 40, 45])
        XCTAssertTrue(seen.allSatisfy { $0.total == 45 && $0.source == .yahoo })
    }

    func testFinnhubReportsProgressPerSymbol() async {
        var seen: [Int] = []
        _ = await FinnhubProvider(apiKey: "k", transport: RecordingTransport { _ in self.quoteJson("190.1", self.openSeconds) })
            .latestQuotes(["AAPL", "MSFT", "NVDA"]) { seen.append($0) }
        XCTAssertEqual(seen, [1, 2, 3])
    }

    func testYahooMapsShareClassSymbols() async {
        XCTAssertEqual(YahooProvider.yahooSymbol("BRK B"), "BRK-B")
        XCTAssertEqual(YahooProvider.yahooSymbol("BRK.B"), "BRK-B")
        XCTAssertEqual(YahooProvider.yahooSymbol("AAPL"), "AAPL")

        let result = await YahooProvider(transport: RecordingTransport { _ in self.spark([self.sparkSeries("BRK-B", "515.62", self.openSeconds)]) })
            .latestQuotes(["BRK B"])
        guard case .success(let quotes) = result else { return XCTFail() }
        XCTAssertEqual(Set(quotes.keys), ["BRK B"])
    }

    func testYahooLeavesUnknownSymbolsUnavailable() async {
        let some = await YahooProvider(transport: RecordingTransport { _ in self.spark([self.sparkSeries("AAPL", "190.1", self.openSeconds)]) })
            .latestQuotes(["AAPL", "NOPE"])
        guard case .success(let quotes) = some else { return XCTFail() }
        XCTAssertEqual(Set(quotes.keys), ["AAPL"])

        let none = await YahooProvider(transport: RecordingTransport { _ in response(404, #"{"spark":{"result":null}}"#) }).latestQuotes(["NOPE"])
        XCTAssertEqual(none, .success([:]))
    }

    func testYahooReportsThrottlingAndOutages() async {
        let limited = await YahooProvider(transport: RecordingTransport { _ in response(429, "") }).latestQuotes(["AAPL"])
        XCTAssertEqual(limited, .failure(.rateLimited))
        let offline = await YahooProvider(transport: RecordingTransport { _ in throw URLError(.notConnectedToInternet) }).latestQuotes(["AAPL"])
        XCTAssertEqual(offline, .failure(.offline))
    }

    func testServiceUsesYahooOnlyWithoutAKey() async throws {
        let finnhub = FakeMarketDataProvider()
        let yahoo = FakeMarketDataProvider()
        let aapl = [holding("AAPL", "USD")]

        let noKey = PriceService(keys: MemoryKeys(), cache: MemoryCache(), providerFor: { _ in finnhub }, now: { self.marketOpen }, fallback: yahoo)
        XCTAssertEqual(try noKey.source(), .yahoo)
        _ = try await noKey.refresh(aapl)
        XCTAssertEqual(yahoo.requests.count, 1)
        XCTAssertEqual(finnhub.requests.count, 0)

        let withKey = PriceService(keys: MemoryKeys("k"), cache: MemoryCache(), providerFor: { _ in finnhub }, now: { self.marketOpen }, fallback: yahoo)
        XCTAssertEqual(try withKey.source(), .finnhub)
        _ = try await withKey.refresh(aapl)
        XCTAssertEqual(yahoo.requests.count, 1)
        XCTAssertEqual(finnhub.requests.count, 1)
    }

    // --- Finnhub adapter ---

    func testFinnhubSendsOnlyTheSymbolAndTheKeyHeader() async {
        let transport = RecordingTransport { _ in self.quoteJson("190.1", self.openSeconds) }
        let result = await FinnhubProvider(apiKey: "user-key", transport: transport, now: { self.marketOpen }).latestQuotes(["AAPL"])

        XCTAssertEqual(transport.calls.map(\.url), ["https://finnhub.io/api/v1/quote?symbol=AAPL"])
        XCTAssertEqual(transport.calls.map(\.headers), [["X-Finnhub-Token": "user-key"]])
        XCTAssertFalse(transport.calls[0].url.contains("user-key"), "key must not be in the URL")
        guard case .success(let quotes) = result, let quote = quotes["AAPL"] else { return XCTFail() }
        XCTAssertEqual(quote.price.compare(d("190.1")), 0)
        XCTAssertEqual(quote.currency, "USD")
        XCTAssertEqual(quote.quoteTime, marketOpen)
    }

    func testFinnhubPreviousCloseGivesTheDailyChange() async {
        let withClose = await FinnhubProvider(apiKey: "k", transport: RecordingTransport { _ in self.quoteJson("190.1", self.openSeconds) }).latestQuotes(["AAPL"])
        guard case .success(let quotes) = withClose, let quote = quotes["AAPL"] else { return XCTFail() }
        XCTAssertEqual(quote.previousClose?.compare(d("189.5")), 0)
        XCTAssertEqual(quote.dailyChange?.compare(d("0.6")), 0)

        let noClose = await FinnhubProvider(apiKey: "k", transport: RecordingTransport { _ in response(200, #"{"c":190.1,"pc":0,"t":\#(self.openSeconds)}"#) })
            .latestQuotes(["AAPL"])
        guard case .success(let other) = noClose else { return XCTFail() }
        XCTAssertNil(other["AAPL"]?.dailyChange)
        XCTAssertNotNil(other["AAPL"])
    }

    func testFinnhubUnknownSymbolIsLeftOut() async {
        let result = await FinnhubProvider(apiKey: "k", transport: RecordingTransport { _ in self.quoteJson("0", 0) }).latestQuotes(["NOPE"])
        XCTAssertEqual(result, .success([:]))
    }

    func testFinnhubMapsErrors() async {
        func provider(_ status: Int) -> FinnhubProvider { FinnhubProvider(apiKey: "k", transport: RecordingTransport { _ in response(status, "{}") }) }

        let invalid = await provider(401).latestQuotes(["AAPL"])
        let limited = await provider(429).latestQuotes(["AAPL"])
        let error = await provider(500).latestQuotes(["AAPL"])
        let notInPlan = await provider(403).latestQuotes(["AAPL"])
        let offline = await FinnhubProvider(apiKey: "k", transport: RecordingTransport { _ in throw URLError(.timedOut) }).latestQuotes(["AAPL"])
        XCTAssertEqual(invalid, .failure(.invalidKey))
        XCTAssertEqual(limited, .failure(.rateLimited))
        XCTAssertEqual(error, .failure(.providerError))
        XCTAssertEqual(notInPlan, .success([:]))
        XCTAssertEqual(offline, .failure(.offline))
    }

    // --- Freshness rules ---

    func testLiveForFifteenMinutesDuringMarketHours() {
        XCTAssertTrue(Markets.us.isOpen(marketOpen))
        XCTAssertEqual(PriceFreshnessRules.classify(quoteTime: marketOpen.addingTimeInterval(-15 * 60), now: marketOpen, lastRefreshFailed: false), .live)
        XCTAssertEqual(PriceFreshnessRules.classify(quoteTime: marketOpen.addingTimeInterval(-15 * 60 - 1), now: marketOpen, lastRefreshFailed: false), .stale)
    }

    func testOutsideMarketHoursTheLastSessionIsTheLatestClose() {
        XCTAssertFalse(Markets.us.isOpen(afterClose))
        XCTAssertEqual(PriceFreshnessRules.classify(quoteTime: t("2024-01-03T21:00:00Z"), now: afterClose, lastRefreshFailed: false), .latestClose)
        // Friday's close is still the latest close on Saturday; Thursday's is not.
        XCTAssertEqual(PriceFreshnessRules.classify(quoteTime: t("2024-01-05T21:00:00Z"), now: saturday, lastRefreshFailed: false), .latestClose)
        XCTAssertEqual(PriceFreshnessRules.classify(quoteTime: t("2024-01-04T21:00:00Z"), now: saturday, lastRefreshFailed: false), .stale)
    }

    func testFailedRefreshMakesEveryPriceStale() {
        XCTAssertEqual(PriceFreshnessRules.classify(quoteTime: marketOpen, now: marketOpen, lastRefreshFailed: true), .stale)
    }

    func testAnIntradayQuoteSeenAfterTheCloseIsStaleNotTheClose() {
        XCTAssertEqual(PriceFreshnessRules.classify(quoteTime: t("2024-01-03T20:50:00Z"), now: afterClose, lastRefreshFailed: false), .stale)
    }

    // --- Price service ---

    func testWithoutAProviderNothingIsRequested() async throws {
        let provider = FakeMarketDataProvider()
        let service = PriceService(keys: MemoryKeys(), cache: MemoryCache(), providerFor: { _ in provider })

        let outcome = try await service.refresh(holdings)
        XCTAssertEqual(outcome, .noKey)
        XCTAssertTrue(provider.requests.isEmpty)
    }

    func testOnlyInScopeSymbolsAreRequestedAndCached() async throws {
        let provider = FakeMarketDataProvider()
        provider.setQuote(Quote(symbol: "AAPL", price: d("190"), currency: "USD", quoteTime: marketOpen, retrievedAt: marketOpen))
        var usedKey: String?
        let service = PriceService(keys: MemoryKeys("user-key"), cache: MemoryCache(), providerFor: { usedKey = $0; return provider }, now: { self.marketOpen })

        let outcome = try await service.refresh(holdings)
        XCTAssertEqual(outcome, .updated)
        XCTAssertEqual(provider.requests, [["AAPL"]])
        XCTAssertEqual(usedKey, "user-key")
        XCTAssertEqual(try service.prices(holdings, lastRefresh: .updated)["AAPL"]?.freshness, .live)
    }

    func testFailedRefreshKeepsTheLastKnownPriceAsStale() async throws {
        let earlier = marketOpen.addingTimeInterval(-600)
        let cache = MemoryCache()
        cache.putAll([Quote(symbol: "AAPL", price: d("190"), currency: "USD", quoteTime: earlier, retrievedAt: earlier)])
        let provider = FakeMarketDataProvider()
        provider.failure = .offline
        let service = PriceService(keys: MemoryKeys("k"), cache: cache, providerFor: { _ in provider }, now: { self.marketOpen })

        let outcome = try await service.refresh(holdings)
        XCTAssertEqual(outcome, .failed(.offline, startedAt: marketOpen))
        XCTAssertEqual(try service.prices(holdings, lastRefresh: outcome)["AAPL"]?.freshness, .stale)
    }

    // --- Rate limit and partial refreshes ---

    func testRateLimiterWaitsOnceTheWindowIsFull() async {
        final class Clock { var now = Date(timeIntervalSince1970: 1_704_297_600); var waits: [TimeInterval] = [] }
        let clock = Clock()
        let limiter = RateLimiter(maxCalls: 2, window: 60, now: { clock.now }, sleep: { clock.waits.append($0); clock.now += $0 })

        await limiter.acquire()
        clock.now += 10
        await limiter.acquire()
        await limiter.acquire() // third call must wait until the first leaves the window

        XCTAssertEqual(clock.waits, [50])
    }

    func testRateLimitKeepsTheQuotesAlreadyReceived() async {
        let transport = RecordingTransport { url in url.hasSuffix("AAPL") ? self.quoteJson("190.1", self.openSeconds) : response(429, "{}") }
        let result = await FinnhubProvider(apiKey: "k", transport: transport).latestQuotes(["AAPL", "MSFT"])
        guard case .failure(let reason, let partial) = result else { return XCTFail() }
        XCTAssertEqual(reason, .rateLimited)
        XCTAssertEqual(Set(partial.keys), ["AAPL"])
    }

    func testAfterAPartialRefreshOnlyQuotesItDidNotUpdateAreStale() async throws {
        struct PartialProvider: MarketDataProvider {
            let fresh: Quote
            func latestQuotes(_ symbols: Set<String>, onProgress: @escaping (Int) -> Void) async -> PriceResult {
                .failure(.rateLimited, partial: ["AAPL": fresh])
            }
        }
        let earlier = marketOpen.addingTimeInterval(-600)
        let cache = MemoryCache()
        cache.putAll([Quote(symbol: "MSFT", price: d("400"), currency: "USD", quoteTime: earlier, retrievedAt: earlier)])
        let provider = PartialProvider(fresh: Quote(symbol: "AAPL", price: d("190"), currency: "USD", quoteTime: marketOpen, retrievedAt: marketOpen))
        let both = holdings + [holding("MSFT", "USD")]
        let service = PriceService(keys: MemoryKeys("k"), cache: cache, providerFor: { _ in provider }, now: { self.marketOpen })

        let outcome = try await service.refresh(both)
        let prices = try service.prices(both, lastRefresh: outcome)
        XCTAssertEqual(prices["AAPL"]?.freshness, .live)
        XCTAssertEqual(prices["MSFT"]?.freshness, .stale)
    }

    func testRefreshSkipsJustFetchedQuotesAndClosesWhileTheMarketIsClosed() async throws {
        let sessionClose = t("2024-01-03T21:00:00Z")
        let cache = MemoryCache()
        cache.putAll([Quote(symbol: "AAPL", price: d("190"), currency: "USD", quoteTime: sessionClose, retrievedAt: sessionClose.addingTimeInterval(60))])
        let provider = FakeMarketDataProvider()

        // Evening: AAPL already has the closing price, so nothing is requested.
        _ = try await PriceService(keys: MemoryKeys("k"), cache: cache, providerFor: { _ in provider }, now: { self.afterClose }).refresh(holdings)
        XCTAssertTrue(provider.requests.isEmpty)

        // Market hours, quote fetched 30 seconds ago: skipped as well.
        cache.putAll([Quote(symbol: "AAPL", price: d("191"), currency: "USD", quoteTime: marketOpen, retrievedAt: marketOpen)])
        _ = try await PriceService(keys: MemoryKeys("k"), cache: cache, providerFor: { _ in provider }, now: { self.marketOpen.addingTimeInterval(30) }).refresh(holdings)
        XCTAssertTrue(provider.requests.isEmpty)
    }

    // --- API key ---

    func testAcceptedKeyIsValidAndOnlyTheTestSymbolIsSent() async {
        let provider = FakeMarketDataProvider()
        let result = await ApiKeyCheck.check(provider)
        XCTAssertEqual(result, .valid)
        XCTAssertEqual(provider.requests, [["AAPL"]])
    }

    func testRejectedOrUncheckedKeys() async {
        for (failure, expected) in [(PriceFailure.invalidKey, KeyCheckResult.rejected), (.offline, .unverified(.offline)),
                                    (.rateLimited, .unverified(.rateLimited)), (.providerError, .unverified(.providerError))] {
            let provider = FakeMarketDataProvider()
            provider.failure = failure
            let result = await ApiKeyCheck.check(provider)
            XCTAssertEqual(result, expected)
        }
    }

    func testKeyFormatAcceptsOnlyASingleAlphanumericToken() {
        XCTAssertEqual(FinnhubKeyFormat.candidate("abcdefghij0123456789"), "abcdefghij0123456789")
        XCTAssertEqual(FinnhubKeyFormat.candidate("  abcdefghij0123456789\n"), "abcdefghij0123456789")
        for other in [nil, "", "short123", "two words abcdefghij0123456789", "https://finnhub.io/dashboard", "someone@example.com"] {
            XCTAssertNil(FinnhubKeyFormat.candidate(other), String(describing: other))
        }
    }
}

/// Mirrors the Android `EuropeanPricesTest` (the main-page row case follows with the main page).
final class EuropeanPricesTests: XCTestCase {
    // Wednesday 2024-01-03: 11:00 in Berlin (Xetra open), 05:00 in New York (US closed).
    private let xetraOpen = t("2024-01-03T10:00:00Z")
    private let xetraClosed = t("2024-01-03T18:00:00Z") // 19:00 Berlin

    private func chart(_ currency: String, _ price: String, _ previousClose: String, _ time: Date) -> HttpResponse {
        response(200, #"{"chart":{"result":[{"meta":{"currency":"\#(currency)","regularMarketPrice":\#(price),"regularMarketTime":\#(Int(time.timeIntervalSince1970)),"chartPreviousClose":\#(previousClose)}}],"error":null}}"#)
    }

    func testIbkrExchangesMapToYahooSymbols() {
        XCTAssertEqual(Markets.europeanSymbol("SAP", market: Markets.european("IBIS")!), "SAP.DE")
        XCTAssertEqual(Markets.europeanSymbol("BP.", market: Markets.european("LSE")!), "BP.L")
        XCTAssertEqual(Markets.europeanSymbol("NOVO B", market: Markets.european("cph")!), "NOVO-B.CO")
        XCTAssertEqual(Markets.europeanSymbol("PKO", market: Markets.european("WSE")!), "PKO.WA")
        XCTAssertNil(Markets.european("NASDAQ"))
        XCTAssertNil(Markets.european("ISED"))
        XCTAssertTrue(Markets.isEuropean("SAP.DE"))
        XCTAssertFalse(Markets.isEuropean("BRK B"))
        XCTAssertFalse(Markets.isEuropean("AAPL"))
    }

    func testScopeUsesTheListingExchangeBeforeTheCurrency() {
        XCTAssertEqual(PriceScope.symbol(holding("SAP", "EUR"), exchange: "IBIS"), "SAP.DE")
        XCTAssertEqual(PriceScope.symbol(holding("CSPX", "USD"), exchange: "LSE"), "CSPX.L") // USD fund on a European exchange
        XCTAssertEqual(PriceScope.symbol(holding("AAPL", "USD"), exchange: "NASDAQ"), "AAPL")
        XCTAssertEqual(PriceScope.symbol(holding("AAPL", "USD"), exchange: nil), "AAPL")
        XCTAssertNil(PriceScope.symbol(holding("SHOP", "CAD"), exchange: "TSE"))
        XCTAssertNil(PriceScope.symbol(holding("SAP", "EUR"), exchange: nil))
    }

    func testEuropeanSymbolsUseTheChartEndpointAndPenceBecomePounds() async {
        let open = Int(xetraOpen.timeIntervalSince1970)
        let transport = RecordingTransport { url in
            if url.contains("chart/SHEL.L") { return self.chart("GBp", "3793.5", "3800", self.xetraOpen) }
            if url.contains("chart/SAP.DE") { return self.chart("EUR", "191.46", "190", self.xetraOpen) }
            if url.contains("chart/NOPE.DE") { return response(404, "") }
            return response(200, #"{"AAPL":{"timestamp":[\#(open)],"close":[190.0],"previousClose":189.0}}"#)
        }
        let result = await YahooProvider(transport: transport).latestQuotes(["SHEL.L", "SAP.DE", "NOPE.DE", "AAPL"])

        XCTAssertEqual(transport.calls.filter { $0.url.contains("/spark?") }.count, 1)
        XCTAssertTrue(transport.calls.contains { $0.url.hasSuffix("/chart/SAP.DE?range=1d&interval=1d") })
        guard case .success(let quotes) = result, let shell = quotes["SHEL.L"] else { return XCTFail("\(result)") }
        XCTAssertEqual(shell.currency, "GBP")
        XCTAssertEqual(shell.price.compare(d("37.935")), 0)
        XCTAssertEqual(shell.previousClose?.compare(d("38")), 0)
        XCTAssertEqual(quotes["SAP.DE"]?.currency, "EUR")
        XCTAssertEqual(Set(quotes.keys), ["SHEL.L", "SAP.DE", "AAPL"])
    }

    func testEuropeanQuotesAreDelayedDuringTheirSessionAndCloseAfterIt() {
        let xetra = Markets.forSymbol("SAP.DE")
        XCTAssertTrue(xetra.isOpen(xetraOpen))
        XCTAssertFalse(Markets.us.isOpen(xetraOpen))

        XCTAssertEqual(PriceFreshnessRules.classify(quoteTime: xetraOpen.addingTimeInterval(-20 * 60), now: xetraOpen, lastRefreshFailed: false, market: xetra), .delayed)
        XCTAssertEqual(PriceFreshnessRules.classify(quoteTime: xetraOpen.addingTimeInterval(-31 * 60), now: xetraOpen, lastRefreshFailed: false, market: xetra), .stale)
        XCTAssertEqual(PriceFreshnessRules.classify(quoteTime: t("2024-01-03T16:30:00Z"), now: xetraClosed, lastRefreshFailed: false, market: xetra), .latestClose)
        // A US quote is never "delayed".
        let usOpen = t("2024-01-03T16:00:00Z")
        XCTAssertEqual(PriceFreshnessRules.classify(quoteTime: usOpen.addingTimeInterval(-20 * 60), now: usOpen, lastRefreshFailed: false), .stale)
    }

    func testWithAKeyUsGoesToFinnhubAndEuropeToYahoo() async throws {
        let finnhub = FakeMarketDataProvider()
        let yahooUs = FakeMarketDataProvider()
        let europe = FakeMarketDataProvider()
        let exchanges = [InstrumentKey(ticker: "AAPL", isin: ""): "NASDAQ", InstrumentKey(ticker: "SAP", isin: ""): "IBIS"]
        let service = PriceService(keys: MemoryKeys("k"), cache: MemoryCache(), providerFor: { _ in finnhub }, now: { self.xetraOpen }, fallback: yahooUs, europe: europe)

        _ = try await service.refresh([holding("AAPL", "USD"), holding("SAP", "EUR")], exchanges: exchanges)

        XCTAssertEqual(finnhub.requests, [["AAPL"]])
        XCTAssertEqual(europe.requests, [["SAP.DE"]])
        XCTAssertTrue(yahooUs.requests.isEmpty)
    }

    func testAFailingFinnhubKeyStillLetsEuropeanPricesThrough() async throws {
        let finnhub = FakeMarketDataProvider()
        finnhub.failure = .invalidKey
        let europe = FakeMarketDataProvider()
        europe.setQuote(Quote(symbol: "SAP.DE", price: d("191"), currency: "EUR", quoteTime: xetraOpen, retrievedAt: xetraOpen, previousClose: d("190")))
        let cache = MemoryCache()
        let service = PriceService(keys: MemoryKeys("k"), cache: cache, providerFor: { _ in finnhub }, now: { self.xetraOpen }, europe: europe)
        var progress: [RefreshProgress] = []

        let outcome = try await service.refresh([holding("AAPL", "USD"), holding("SAP", "EUR")], exchanges: [InstrumentKey(ticker: "SAP", isin: ""): "IBIS"]) {
            progress.append($0)
        }

        XCTAssertEqual(outcome, .failed(.invalidKey, startedAt: xetraOpen))
        XCTAssertNotNil(cache.quotes["SAP.DE"])
        XCTAssertEqual(progress.first?.total, 2)
        XCTAssertEqual(progress.last?.source, .yahoo)
    }
}

/// Counterpart of the price part of the Android `EncryptedStorageInstrumentedTest`.
final class PriceStoreTests: XCTestCase {
    func testProviderKeyAndQuotesAreStoredOnlyInsideTheEncryptedDatabase() throws {
        let base = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: base) }
        let file = try SecureFiles.directory(base: base).appendingPathComponent("prices.db")
        let apiKey = "SYNTHETIC_TEST_API_KEY_1234"
        let time = t("2024-01-03T16:00:00Z")
        do {
            let store = PriceStore(database: try EncryptedDatabase(file: file, key: Data(repeating: 3, count: 32)))
            XCTAssertNil(try store.get(), "a new install must not contain a provider key")
            XCTAssertThrowsError(try store.set("   "))

            try store.set("  \(apiKey)  ")
            try store.putAll([Quote(symbol: "AAPL", price: d("190.10"), currency: "USD", quoteTime: time, retrievedAt: time, previousClose: d("189.50"))])

            XCTAssertEqual(try store.get(), apiKey)
            let quote = try XCTUnwrap(try store.get(["AAPL", "MSFT"])["AAPL"])
            XCTAssertEqual(quote, Quote(symbol: "AAPL", price: d("190.10"), currency: "USD", quoteTime: time, retrievedAt: time, previousClose: d("189.50")))
            XCTAssertEqual(quote.dailyChange?.description, "0.60")
            try store.clear()
            XCTAssertNil(try store.get())
            try store.set(apiKey)
        }
        XCTAssertNil(try Data(contentsOf: file).range(of: Data(apiKey.utf8)), "API key found in plaintext")
    }
}
