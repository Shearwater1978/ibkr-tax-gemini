import Foundation

/**
 * An exchange's regular session on weekdays (holidays are not modelled). `delayed` feeds get a
 * "delayed" label instead of the live rule (mobile-market-prices spec).
 */
struct Market: Equatable {
    let yahooSuffix: String?
    let zone: TimeZone
    /// Session open and close, in minutes after local midnight.
    let open: Int
    let close: Int
    let delayed: Bool

    private var calendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = zone
        return calendar
    }

    func isOpen(_ now: Date) -> Bool {
        let local = calendar.dateComponents([.weekday, .hour, .minute, .second], from: now)
        let seconds = local.hour! * 3600 + local.minute! * 60 + local.second!
        return Self.isWeekday(local.weekday!) && seconds >= open * 60 && seconds < close * 60
    }

    /// Close of the most recent session that has ended at `now`.
    func lastSessionClose(_ now: Date) -> Date {
        let calendar = self.calendar
        var day = calendar.startOfDay(for: now)
        while true {
            let sessionClose = calendar.date(bySettingHour: close / 60, minute: close % 60, second: 0, of: day)!
            if Self.isWeekday(calendar.component(.weekday, from: sessionClose)) && sessionClose <= now { return sessionClose }
            day = calendar.date(byAdding: .day, value: -1, to: day)!
        }
    }

    /// Gregorian weekdays run from 1 (Sunday) to 7 (Saturday).
    private static func isWeekday(_ weekday: Int) -> Bool { weekday != 1 && weekday != 7 }
}

enum Markets {
    /// NYSE/Nasdaq regular session; Finnhub and Yahoo's US quotes are treated as live.
    static let us = Market(yahooSuffix: nil, zone: TimeZone(identifier: "America/New_York")!, open: 9 * 60 + 30, close: 16 * 60, delayed: false)

    private static func europe(_ suffix: String, _ zone: String, _ open: String, _ close: String) -> Market {
        func minutes(_ time: String) -> Int {
            let parts = time.split(separator: ":").map { Int($0)! }
            return parts[0] * 60 + parts[1]
        }
        return Market(yahooSuffix: suffix, zone: TimeZone(identifier: zone)!, open: minutes(open), close: minutes(close), delayed: true)
    }

    private static let xetra = europe(".DE", "Europe/Berlin", "09:00", "17:30")
    private static let frankfurt = europe(".F", "Europe/Berlin", "08:00", "22:00")
    private static let london = europe(".L", "Europe/London", "08:00", "16:30")

    /// IBKR listing exchange code -> market; the table in the mobile-market-prices spec.
    private static let european: [String: Market] = [
        "IBIS": xetra,
        "IBIS2": xetra,
        "FWB": frankfurt,
        "FWB2": frankfurt,
        "SWB": europe(".SG", "Europe/Berlin", "08:00", "22:00"),
        "LSE": london,
        "LSEETF": london,
        "AEB": europe(".AS", "Europe/Amsterdam", "09:00", "17:30"),
        "SBF": europe(".PA", "Europe/Paris", "09:00", "17:30"),
        "ENEXT.BE": europe(".BR", "Europe/Brussels", "09:00", "17:30"),
        "BVL": europe(".LS", "Europe/Lisbon", "08:00", "16:30"),
        "EBS": europe(".SW", "Europe/Zurich", "09:00", "17:30"),
        "BVME": europe(".MI", "Europe/Rome", "09:00", "17:30"),
        "BM": europe(".MC", "Europe/Madrid", "09:00", "17:30"),
        "VSE": europe(".VI", "Europe/Vienna", "09:00", "17:30"),
        "SFB": europe(".ST", "Europe/Stockholm", "09:00", "17:30"),
        "CPH": europe(".CO", "Europe/Copenhagen", "09:00", "17:00"),
        "OSE": europe(".OL", "Europe/Oslo", "09:00", "16:20"),
        "HEX": europe(".HE", "Europe/Helsinki", "10:00", "18:30"),
        "WSE": europe(".WA", "Europe/Warsaw", "09:00", "17:00"),
    ]
    private static let bySuffix = Dictionary(european.values.map { ($0.yahooSuffix!, $0) }) { first, _ in first }

    /// The European market for an IBKR listing exchange code, or nil when it is not supported.
    static func european(_ exchange: String) -> Market? {
        european[exchange.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()]
    }

    /// The market a requested symbol belongs to: European by its Yahoo suffix, otherwise US.
    static func forSymbol(_ symbol: String) -> Market {
        guard let dot = symbol.lastIndex(of: "."), dot > symbol.startIndex else { return us }
        return bySuffix[String(symbol[dot...])] ?? us
    }

    static func isEuropean(_ symbol: String) -> Bool { forSymbol(symbol).yahooSuffix != nil }

    /// IBKR ticker to Yahoo symbol: "BP." -> "BP.L", "NOVO B" -> "NOVO-B.CO".
    static func europeanSymbol(_ ticker: String, market: Market) -> String {
        var base = ticker.trimmingCharacters(in: .whitespacesAndNewlines)
        while base.hasSuffix(".") { base.removeLast() }
        return base.replacingOccurrences(of: "[ .]+", with: "-", options: .regularExpression) + market.yahooSuffix!
    }
}

enum PriceFreshness: Equatable {
    /// Market open and the quote is at most 15 minutes old.
    case live

    /// Delayed feed (Yahoo's European quotes): market open and the quote is at most 30 minutes old.
    case delayed

    /// Market closed and the quote is from the most recent session: shown as the latest close.
    case latestClose

    /// Too old, or the last refresh failed: never presented as current.
    case stale
}

enum PriceFreshnessRules {
    static let maxLiveAge: TimeInterval = 15 * 60
    static let maxDelayedAge: TimeInterval = 30 * 60

    /// A quote this close to the session end is treated as the closing price.
    private static let closeTolerance: TimeInterval = 5 * 60

    /// `market` is the quote's exchange (see `Markets.forSymbol`); its session decides live, delayed or close.
    static func classify(quoteTime: Date, now: Date, lastRefreshFailed: Bool, market: Market = Markets.us) -> PriceFreshness {
        if lastRefreshFailed { return .stale }
        if market.isOpen(now) {
            let age = now.timeIntervalSince(quoteTime)
            if market.delayed && age <= maxDelayedAge { return .delayed }
            if !market.delayed && age <= maxLiveAge { return .live }
            return .stale
        }
        return quoteTime >= market.lastSessionClose(now).addingTimeInterval(-closeTolerance) ? .latestClose : .stale
    }
}
