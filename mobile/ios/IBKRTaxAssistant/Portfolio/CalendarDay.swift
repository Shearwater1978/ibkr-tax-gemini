import Foundation

/// A date without time, as Java's `LocalDate`; parsed only from strict ISO "yyyy-MM-dd".
struct CalendarDay: Comparable, Hashable {
    let year: Int
    let month: Int
    let day: Int

    /// The first ten characters as an ISO date, or nil when they are not a valid one.
    init?(iso text: String) {
        let part = text.prefix(10)
        let fields = part.split(separator: "-", omittingEmptySubsequences: false)
        guard part.count == 10, fields.count == 3, fields[0].count == 4, fields[1].count == 2, fields[2].count == 2,
              let year = Int(fields[0]), let month = Int(fields[1]), let day = Int(fields[2]),
              (1...12).contains(month), day >= 1, day <= Self.length(year, month) else { return nil }
        self.init(year: year, month: month, day: day)
    }

    init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    /// Today in the device's time zone.
    static func today(_ now: Date = Date()) -> CalendarDay {
        let parts = Calendar.current.dateComponents([.year, .month, .day], from: now)
        return CalendarDay(year: parts.year!, month: parts.month!, day: parts.day!)
    }

    /// Same day `months` earlier, clamped to the month's length (Java `minusMonths`).
    func minusMonths(_ months: Int) -> CalendarDay {
        let index = year * 12 + (month - 1) - months
        let (y, m) = (index / 12, index % 12 + 1)
        return CalendarDay(year: y, month: m, day: min(day, Self.length(y, m)))
    }

    static func < (a: CalendarDay, b: CalendarDay) -> Bool { (a.year, a.month, a.day) < (b.year, b.month, b.day) }

    private static func length(_ year: Int, _ month: Int) -> Int {
        let leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
        return [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][month - 1]
    }
}

/// A month of a year, as Java's `YearMonth`; parsed from the first seven characters "yyyy-MM".
struct YearMonth: Comparable, Hashable {
    let year: Int
    let month: Int

    init?(iso text: String) {
        let part = text.prefix(7)
        let fields = part.split(separator: "-", omittingEmptySubsequences: false)
        guard part.count == 7, fields.count == 2, fields[0].count == 4, fields[1].count == 2,
              let year = Int(fields[0]), let month = Int(fields[1]), (1...12).contains(month) else { return nil }
        self.init(year: year, month: month)
    }

    init(year: Int, month: Int) {
        self.year = year
        self.month = month
    }

    static func < (a: YearMonth, b: YearMonth) -> Bool { (a.year, a.month) < (b.year, b.month) }
}
