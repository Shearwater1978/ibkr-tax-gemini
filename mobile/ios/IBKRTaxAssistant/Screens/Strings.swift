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

    static let yearOlder = "Older"
    static let dividendsTitle = "Dividends"
    static let dividendsEmpty = "No dividends found. Reports with a Dividends section add them here."
    static let dividendsGross = "Gross"
    static let dividendsTax = "Withholding tax"
    static let dividendsNet = "Net"
    static func dividendsByMonth(_ year: Int) -> String { "Net per month, \(year) (USD)" }
    static let dividendsByCurrency = "Per currency"
    static let dividendsCompare = "Net per month by year (USD)"
    static func dividendsByPosition(_ year: Int) -> String { "Per position, \(year)" }
    static func dividendsIncomplete(_ currencies: String) -> String { "incomplete: no rate for \(currencies)" }
    static func dividendsCoverage(_ first: String, _ last: String) -> String { "Imported dividend data from \(first) to \(last)." }

    static let operationsTitle = "Operations"
    static let operationsSearch = "Ticker"
    static let operationsFilterBuys = "Buys"
    static let operationsFilterSells = "Sells"
    static let operationsFilterDividends = "Dividends"
    static let operationsFilterTaxes = "Withholding tax"
    static let operationsFilterOther = "Corporate actions"
    static let operationsNoMatch = "No operations match the search and filters."
    static let operationsClearAll = "Clear search and filters"
    static let operationsEmpty = "No operations yet. Add reports from your broker to see buys, sells, dividends and corporate actions here."
    static func operationsCommission(_ amount: String) -> String { "commission \(amount)" }
    static let operationBuy = "Buy"
    static let operationSell = "Sell"
    static let operationDividend = "Dividend"
    static let operationTax = "Withholding tax"
    static let operationTransfer = "Transfer"
    static let operationSplit = "Split"
    static let operationStockDividend = "Stock dividend"
    static let operationMerger = "Merger"
    static let operationSpinoff = "Spin-off"

    static let importsTitle = "Report imports"
    static let importsSupportedFormat = "Supported format: IBKR Activity Statement (CSV, English)."
    static let importsUnavailableKeys = "Import needs working backups first. In Settings: 1. set up backup encryption, 2. choose a backup folder."
    static let importsUnavailableFolder = "Backup encryption is set up. Choose a backup folder in Settings to enable import."
    static let importMultipleHint = "You can select several reports at once; each is imported on its own."
    static let importing = "Importing…"
    static func importProgress(_ done: Int, _ total: Int) -> String { "Importing file \(done) of \(total)…" }
    static func importDone(_ inserted: Int, _ skipped: Int) -> String { "Imported: \(inserted) new records, \(skipped) already present. Encrypted backup saved and verified." }
    static func importDonePending(_ inserted: Int, _ skipped: Int) -> String {
        "Imported: \(inserted) new records, \(skipped) already present. The backup folder is not reachable right now; the encrypted backup is kept on this phone and retried."
    }
    static func importDoneAccessLost(_ inserted: Int, _ skipped: Int) -> String {
        "Imported: \(inserted) new records, \(skipped) already present. The app lost access to the backup folder: choose it again in Settings."
    }
    static let importAlready = "This report was already imported. Nothing changed."
    static let importNotReady = "Set up backup encryption and a backup folder in Settings first."
    static let importUnsupported = "This is not a IBKR Activity Statement (CSV, English) report. Nothing was imported."
    static let importEmpty = "The file is empty. Nothing was imported."
    static func importMalformed(_ line: Int) -> String { "The report could not be read (problem near line \(line)). Nothing was imported." }
    static let importInvalidRecord = "The report contains an entry without date, ticker or currency, or of an unknown type. Nothing was imported."
    static let importTooLarge = "The file is larger than 20 MB, which is too big for a broker report. Nothing was imported."
    static func backupPendingCount(_ count: Int) -> String { "\(count) backup(s) waiting for the backup folder." }
    static let backupRetry = "Retry backup"
    static func backupRetried(_ saved: Int, _ waiting: Int) -> String { "Backup retried: \(saved) saved, \(waiting) still waiting." }
    static let backupAccessLost = "The app lost access to the backup folder. Choose it again in Settings."

    static func describe(_ outcome: ImportOutcome) -> String {
        switch outcome {
        case .notReady: return importNotReady
        case .alreadyImported: return importAlready
        case .imported(let inserted, let skipped, .backedUp): return importDone(inserted, skipped)
        case .imported(let inserted, let skipped, .pending): return importDonePending(inserted, skipped)
        case .imported(let inserted, let skipped, .accessLost): return importDoneAccessLost(inserted, skipped)
        case .rejected(.invalidRecord): return importInvalidRecord
        case .rejected(.unparseable(.emptyFile)): return importEmpty
        case .rejected(.unparseable(.unsupportedFormat)): return importUnsupported
        case .rejected(.unparseable(.malformed(_, let line, _))): return importMalformed(line)
        }
    }
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
    static let deleteAllBody = "All imported reports and records are removed from the app, and their encrypted backup files are deleted from the backup folder. Your backup encryption keys stay. Enter your backup passphrase to confirm."
    static let deleteAllBodyNoKeys = "All imported reports and records are removed from the app."
    static let deleteAllPassphrase = "Backup passphrase"
    static let deleteConfirm = "Delete"
    static let deleteWrongPassphrase = "The passphrase is wrong. Nothing was deleted."
    static func deleteDone(_ outcome: DeletionOutcome) -> String {
        outcome.backupsLeft == 0
            ? "Deleted: \(outcome.removedRecords) records removed, \(outcome.backupsDeleted) backup file(s) deleted."
            : "Deleted: \(outcome.removedRecords) records removed. \(outcome.backupsLeft) backup file(s) could not be deleted because the backup folder is not reachable; delete them there yourself."
    }

    static let debugSamplesTitle = "Debug build"
    static let debugSamplesAction = "Load synthetic sample reports"
    static func debugSamplesImported(_ name: String, _ inserted: Int, _ skipped: Int) -> String { "\(name): \(inserted) new, \(skipped) skipped" }
    static func debugSamplesAlready(_ name: String) -> String { "\(name): already imported" }
    static func debugSamplesRejected(_ name: String) -> String { "\(name): rejected" }

    static let encryptionTitle = "Backup encryption"
    static let encryptionBody = "Backups are encrypted on this phone with a key protected by your passphrase. Neither the storage provider nor anyone else can read them without it."
    static let encryptionSetUp = "Set up backup encryption"
    static let encryptionCreate = "Create keys"
    static let encryptionWorking = "Creating keys… this takes a few seconds."
    static let encryptionReady = "Backup encryption is set up."
    static let encryptionNoCloudCopy = "On a new iPhone you will need your passphrase or recovery code to restore backups."
    static let encryptionChange = "Change passphrase"
    static let encryptionChangeSubmit = "Change"
    static func passphraseRule(_ minimum: Int) -> String { "Use at least \(minimum) characters. A short sentence is easy to remember and hard to guess." }
    static let passphraseCurrent = "Current passphrase"
    static let passphraseNew = "New passphrase"
    static let passphraseConfirm = "Repeat passphrase"
    static func passphraseTooShort(_ minimum: Int) -> String { "The passphrase needs at least \(minimum) characters." }
    static let passphraseTooSimple = "This passphrase is too simple. Avoid repeated characters and plain sequences."
    static let passphraseMismatch = "The two passphrases do not match."
    static let passphraseWrongCurrent = "The current passphrase is wrong."
    static let recoveryTitle = "Your recovery code"
    static let recoveryBody = "Write this code down and keep it somewhere safe, separate from your phone. It unlocks your backups if you forget the passphrase. It is shown only once; iOS cannot block screenshots, so do not keep one in your photos."
    static let recoverySaved = "I have written down the recovery code"
    static let recoveryDone = "Done"
    static let recoveryCopy = "Copy code"
    static let recoveryCopied = "Copied. Paste it into your password manager now: the clipboard is cleared after 1 minute and stays on this iPhone."
    static let recoveryNew = "Create new recovery code"
    static let recoveryNewHint = "Lost your recovery code or never saved it? Create a new one; your backups and passphrase stay the same."
    static let recoveryNewSubmit = "Create code"
    static let recoveryNewReplaces = "When you tap Done, this code replaces your previous one, which then stops working."

    static let locationTitle = "Backup location"
    static let folderBody = "Encrypted backups of your imported reports are saved in a folder you choose, for example in iCloud Drive. Only this app can read them, with your passphrase or recovery code."
    static let locationChoose = "Choose backup folder"
    static let locationChooseHint = "In the picker, go to where the backups should be kept (iCloud Drive is safest) and tap Open. The app creates its own folder \"Portfolio Companion backups\" there."
    static func locationCurrent(_ place: String, _ path: String) -> String { "Backups go to \(place): \(path)" }
    static let locationLabelICloud = "iCloud Drive"
    static let locationLabelPhone = "this iPhone"
    static let locationLabelOther = "another storage app"
    static let locationChangeHint = "Choosing a new location keeps existing backups where they are."
    static let folderLocalOnly = "This folder is only on this iPhone. If the phone is lost or reset, the backups are lost too. A folder in iCloud Drive or another cloud app is safer."
    static let folderSaved = "Backup folder saved."
    static let folderSavedNoKeys = "Backup folder saved. Set up backup encryption above to enable import."
    static let folderNotUsable = "That folder cannot be used. Choose another one. Nothing was changed."

    static let restoreTitle = "Restore from a backup"
    static let restoreBody = "New iPhone? Choose the folder that holds your backups (also backups made by the Android app). The app asks for your backup passphrase or recovery code."
    static let restoreChoose = "Choose backup folder"
    static let restoreRunning = "Restoring…"
    static let restoreUnlockTitle = "Unlock your backup"
    static let restoreUnlockBody = "Enter the backup passphrase or the recovery code you saved when you set up backups."
    static let restoreUsePassphrase = "Passphrase"
    static let restoreUseCode = "Recovery code"
    static let restoreAction = "Restore"
    static let restoreWrong = "This does not unlock the backup. Nothing was restored."
    static let restoreNoBackup = "No backup found in this folder. Choose the folder that contains the backup files."
    static let restoreAccessLost = "The folder could not be read. Choose it again."
    static let restoreNewer = "This backup was made by a newer version of the app. Update the app first."
    static func restoreDone(_ reports: Int, _ records: Int, already: Int, unreadable: Int) -> String {
        var parts = ["Restored \(reports) \(reports == 1 ? "report" : "reports") with \(records) records."]
        if already > 0 { parts.append("\(already) already on this phone.") }
        if unreadable > 0 { parts.append("\(unreadable) backup files could not be read.") }
        return parts.joined(separator: " ")
    }

    static let eraseTitle = "Erase all data"
    static let eraseBody = "Deletes everything this app stores: imported reports and their records, every backup file in your backup folder, the backup keys, your price key and all settings. Without the keys, any backup copy left in the storage provider's trash cannot be read. This cannot be undone."
    static let eraseAction = "Erase all data…"
    static let eraseConfirmTitle = "Erase all data?"
    static let eraseConfirmWord = "ERASE"
    static func eraseConfirmBody(_ word: String) -> String { "Type \(word) to confirm. The app starts empty afterwards." }
    static let eraseConfirmButton = "Erase"
    static let eraseRunning = "Erasing…"
    static let eraseDoneTitle = "All data erased"
    static func eraseDoneBody(_ records: Int, _ files: Int) -> String { "Deleted \(records) records and \(files) backup files. The backup keys are destroyed." }
    static let eraseFolderUnreachable = "The backup folder could not be reached, so its files were not deleted; without the keys they cannot be read."
    static func eraseFilesLeft(_ count: Int) -> String { "\(count) backup files could not be deleted; without the keys they cannot be read." }
    static func eraseStepsFailed(_ steps: String) -> String { "Some steps failed (\(steps)). Reinstall the app to remove what is left." }

    static let settingsPricesTitle = "Market prices"
    static let settingsPricesBody = "Prices for US listings come from Finnhub, using your own free personal API key. Prices for listings on the main European exchanges always come from Yahoo Finance, because Finnhub's free plan has none; they are delayed by about 15 to 20 minutes. The key is stored encrypted on this phone and sent only to Finnhub, together with ticker symbols. Without a key, prices come from Yahoo Finance's public pages: no key is needed, but that source is unofficial and may be delayed, limited or stop working at any time. Only ticker symbols are sent there."
    static let keyGuideTitle = "How to get a free key"
    static let keyGuideStep1 = "1. Tap Get key from Finnhub. Finnhub opens in a browser view; create a free account or sign in. This app never sees your Finnhub password."
    static let keyGuideStep2 = "2. On Finnhub, copy your API key."
    static let keyGuideStep3 = "3. Close the browser view and tap Paste below. The key is checked with one test request."
    static let keyGuideOpen = "Get key from Finnhub"
    static let keyChecking = "Checking…"
    static let keyCheckValid = "Key works. Prices will refresh on the Portfolio page."
    static let keyCheckRejected = "Finnhub rejected this key. Check that you copied the whole key and try again."
    static let keyCheckUnverified = "Key saved, but it could not be checked right now (offline or busy). It will be used on the next refresh."
    static let settingsApiKeyLabel = "Finnhub API key"
    static let settingsApiKeySave = "Save key"
    static func settingsApiKeySaved(_ masked: String) -> String { "Key saved: \(masked)" }
    static let settingsApiKeyRemove = "Remove key"

    static let privacyTitle = "Privacy"
    static let privacyOnDevice = "Reports are read and kept encrypted on this phone. The publisher runs no server and receives none of your data. There are no accounts, ads, analytics or crash reports."
    static let privacyLeaves = "Only ticker symbols leave the phone, to get prices (Finnhub with your own key, Yahoo Finance). Backups are encrypted on this phone before they reach your storage provider."
    static let privacyChoices = "Delete imports on the import screen, or erase everything below. The full privacy notice comes with the app documentation (PRIVACY.md)."

    static let settingsTitle = "Settings"
    static let settingsPrivacy = "Reports are processed on this device. Account identifiers are masked."
}
