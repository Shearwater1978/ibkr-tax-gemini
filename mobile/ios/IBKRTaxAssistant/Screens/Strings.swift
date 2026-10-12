/// User-facing copy; keep in sync with Android `res/values/strings.xml`.
enum Strings {
    static let appName = "Portfolio Companion"

    static let lockLocked = "Locked to protect your portfolio data."
    static let lockUnlock = "Unlock"
    static let lockPromptReason = "Unlock Portfolio Companion"
    static let lockNoScreenLock = "This iPhone has no passcode, so the app cannot verify it is you. Set a passcode or Face ID in Settings to protect your data."
    static let lockContinue = "Continue without lock"

    static let loading = "Loading…"
    static let cancel = "Cancel"
    static let storageUnavailable = "Secure storage is unavailable on this device, so imported data cannot be opened."

    static let portfolioEmptyTitle = "No reports imported"
    static let portfolioEmptyBody = "Add reports from your broker to see your holdings."
    static let actionImportReport = "Import report"
    static let portfolioTitle = "Positions"
    static let portfolioNoHoldings = "All imported positions are fully sold."
    static func portfolioIncomplete(_ ticker: String, _ date: String) -> String {
        "Imported reports sell more \(ticker) than they buy (on \(date)). Import the missing earlier reports to see holdings."
    }

    static let cardValue = "Value"
    static let cardToday = "Today"
    static func cardInvested(_ amount: String) -> String { "invested \(amount)" }
    static let cardProfit = "Total profit"
    static let cardDividends = "Dividends, 12 months"
    static let cardDividendsNet = "net of withholding tax"
    static let cardIncomplete = "incomplete"

    static let moversTitle = "Today's top movers"
    static let moversGainers = "Top gainers"
    static let moversLosers = "Top losers"
    static let moversNoGainers = "Nothing rose today."
    static let moversNoLosers = "Nothing fell today."
    static let moversDelayed = "delayed prices"
    static let moversLatestClose = "latest close"
    static let moversStale = "stale prices"
    static let moversShowMore = "Show more"
    static let moversShowLess = "Show less"

    static func showSoldPositions(_ count: Int) -> String { "Show sold positions (\(count))" }
    static func soldPosition(_ currency: String) -> String { "sold · \(currency)" }
    static func detailsHeld(_ currency: String) -> String { "Held position, in \(currency)" }
    static func detailsSold(_ currency: String) -> String { "Fully sold, in \(currency)" }
    static let detailsQuantity = "Quantity"
    static let detailsAverage = "Average price"
    static let detailsInvested = "Invested"
    static let detailsValue = "Value"
    static let detailsPriceGain = "Price gain"
    static let detailsSalesGain = "Gain from sales"
    static let detailsDividends = "Dividends, net"
    static let detailsTotal = "Total"
    static let detailsUnavailable = "unavailable"
    static let detailsSalesIncomplete = "A sale closed shares bought in another currency; its gain is not included."
    static let detailsNote = "FIFO, commissions excluded. Informational; not a tax result."
    static func dividendsLine(_ gross: String, _ tax: String) -> String { "gross \(gross) · withholding tax \(tax)" }
    static let payerHistoryTitle = "Dividend history"
    static func payerHistorySubtitle(_ currency: String, _ count: Int) -> String {
        "\(currency) · all years · \(count) \(count == 1 ? "booking" : "bookings")"
    }
    static let payerHistoryTotals = "Totals"

    static let headerApproximate = "Approximate, in USD"
    static func headerRateDate(_ date: String) -> String { "exchange rates of \(date)" }
    static func headerIncomplete(_ missing: Int, _ total: Int) -> String { "incomplete: \(missing) of \(total) positions without a price or rate" }
    static let pnlDaily = "Daily P&L"
    static let pnlUnrealized = "Unrealized P&L"
    static let viewOwnCurrency = "Own currency"
    static let viewUsd = "USD"
    static func usdViewNote(_ date: String) -> String { "Converted to USD, approximate (exchange rates of \(date))." }
    static let usdViewNoRates = "No exchange rates yet: non-USD values are unavailable until the rates load."
    static let columnInstrument = "Instrument"
    static let columnLast = "Last"
    static let columnChange = "Change"
    static let columnPosition = "Position"
    static let columnPnl = "P&L"
    static let priceUnavailableShort = "no price"
    static let priceOutOfScopeShort = "not covered"
    static let priceNoRateShort = "no rate"
    static let priceCloseShort = "close"
    static let priceDelayedShort = "delayed"
    static let priceStaleShort = "stale"

    static let pricesRefresh = "Refresh prices"
    static let pricesRefreshing = "Refreshing prices…"
    static let pricesNoKey = "Add your Finnhub API key in Settings to see prices for US listings."
    static let pricesFromYahoo = "Prices from Yahoo Finance (no Finnhub key set)."
    static let pricesEuropeFromYahoo = "European prices come from Yahoo Finance."
    static func pricesFailed(_ reason: String) -> String { "Prices could not be refreshed (\(reason)). Last known prices are shown as stale." }
    static let pricesFailureOffline = "offline"
    static let pricesFailureInvalidKey = "API key rejected"
    static let pricesFailureRateLimited = "rate limit reached"
    static let pricesFailureProvider = "provider error"
    static func pricesProgressYahoo(_ done: Int, _ total: Int) -> String { "\(done) of \(total) prices requested from Yahoo Finance (20 per request)." }
    static func pricesProgressFinnhub(_ done: Int, _ total: Int) -> String { "\(done) of \(total) prices requested from Finnhub." }
    static let pricesProgressShown = "Until the refresh finishes, the prices shown are from the last one."

    static let dividendsEmpty = "No dividends found. Reports with a Dividends section add them here."
    static let operationsEmpty = "No operations yet. Add reports from your broker to see buys, sells, dividends and corporate actions here."

    static let importsTitle = "Report imports"
    static let importsSupportedFormat = "Supported format: IBKR Activity Statement (CSV, English)."
    static let importsUnavailableBackup = "Import of your own reports turns on once encrypted backups are available on iPhone."
    static let importsHistoryTitle = "Imported reports"
    static func importsHistoryItem(_ time: String, _ account: String) -> String { "\(time) · account \(account)" }
    static func importsHistoryCounts(_ inserted: Int, _ skipped: Int) -> String {
        "\(inserted) new records, \(skipped) already present · stored encrypted on this device"
    }
    static let importsAccountUnknown = "unknown"
    static let importsEmpty = "No imports yet."
    static let backupStatusDone = "Encrypted backup: saved"
    static let backupStatusPending = "Encrypted backup: pending"
    static let backupStatusNone = "Encrypted backup: none (sample data)"

    static let deleteOneAction = "Delete this import"
    static let deleteOneTitle = "Delete this import?"
    static let deleteOneBody = "Records that only this import contains are removed from the app, and its encrypted backup file is deleted from the backup folder. Records that other imports also contain stay."
    static let deleteAllAction = "Delete all imported data"
    static let deleteAllTitle = "Delete all imported data?"
    static let deleteAllBodyNoKeys = "All imported reports and records are removed from the app."
    static let deleteConfirm = "Delete"
    static func deleteDone(_ records: Int) -> String { "Deleted: \(records) records removed." }

    static let debugSamplesTitle = "Debug build"
    static let debugSamplesAction = "Load synthetic sample reports"
    static func debugSamplesImported(_ name: String, _ inserted: Int, _ skipped: Int) -> String { "\(name): \(inserted) new, \(skipped) skipped" }
    static func debugSamplesAlready(_ name: String) -> String { "\(name): already imported" }
    static func debugSamplesRejected(_ name: String) -> String { "\(name): rejected" }

    static let settingsTitle = "Settings"
    static let settingsPrivacy = "Reports are processed on this device. Account identifiers are masked."
}
