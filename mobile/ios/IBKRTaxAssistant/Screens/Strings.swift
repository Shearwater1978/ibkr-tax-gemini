/// User-facing copy; keep in sync with Android `res/values/strings.xml`.
enum Strings {
    static let appName = "Portfolio Companion"

    static let lockLocked = "Locked to protect your portfolio data."
    static let lockUnlock = "Unlock"
    static let lockPromptReason = "Unlock Portfolio Companion"
    static let lockNoScreenLock = "This iPhone has no passcode, so the app cannot verify it is you. Set a passcode or Face ID in Settings to protect your data."
    static let lockContinue = "Continue without lock"

    static let portfolioEmptyTitle = "No reports imported"
    static let portfolioEmptyBody = "Add reports from your broker to see your holdings."
    static let actionImportReport = "Import report"

    static let dividendsEmpty = "No dividends found. Reports with a Dividends section add them here."
    static let operationsEmpty = "No operations yet. Add reports from your broker to see buys, sells, dividends and corporate actions here."

    static let importsTitle = "Report imports"
    static let importsSupportedFormat = "Supported format: IBKR Activity Flex Query CSV."
    static let importsUnavailable = "Import is unavailable until encrypted backup is set up."
    static let importsEmpty = "No imports yet."

    static let settingsTitle = "Settings"
    static let settingsPrivacy = "Reports are processed on this device. Account identifiers are masked."
}
