/// A DIVIDEND or TAX (withholding tax) row as stored; amounts keep their sign, so tax is usually negative.
struct CashEvent: Equatable {
    var date: String
    var eventType: String
    var ticker: String
    var currency: String
    var amount: ExactDecimal

    /// Nil when the stored date is not ISO; such rows are left out rather than guessed.
    var month: YearMonth? { YearMonth(iso: date) }
}

/// Gross dividends and withholding tax in one currency; net = gross + tax.
struct DividendAmounts: Equatable {
    var gross: ExactDecimal = .zero
    var tax: ExactDecimal = .zero

    var net: ExactDecimal { gross.adding(tax) }

    static func + (a: DividendAmounts, b: DividendAmounts) -> DividendAmounts {
        DividendAmounts(gross: a.gross.adding(b.gross), tax: a.tax.adding(b.tax))
    }

    static func of(_ event: CashEvent) -> DividendAmounts {
        event.eventType == Dividends.tax ? DividendAmounts(tax: event.amount) : DividendAmounts(gross: event.amount)
    }

    static func sum(_ events: [CashEvent]) -> DividendAmounts { events.map(of).reduce(DividendAmounts(), +) }
}

/// An approximate USD sum; `missingCurrencies` had no NBP rate and are left out, so the sum is incomplete.
struct UsdSum: Equatable {
    var amount: ExactDecimal
    var missingCurrencies: Set<String> = []

    var isComplete: Bool { missingCurrencies.isEmpty }
}

struct MonthDividends: Equatable {
    var month: Int
    var byCurrency: [String: DividendAmounts]
    var usdNet: UsdSum
}

struct PositionDividends: Equatable {
    var ticker: String
    var currency: String
    var amounts: DividendAmounts
    var usdNet: ExactDecimal?
}

/// One booking date of a payer: the dividend and any tax booked that day, in the payment currency.
struct PayerLine: Equatable {
    var date: String
    var amounts: DividendAmounts
}

/// One instrument's dividend and tax booked on one date, in the payment currency.
struct Payment: Equatable {
    var date: String
    var ticker: String
    var currency: String
    var amounts: DividendAmounts

    var month: YearMonth? { YearMonth(iso: date) }
}

/// Every dividend and tax booking of one instrument in one currency (mobile-payer-history spec).
struct PayerHistory: Equatable {
    var ticker: String
    var currency: String
    var lines: [PayerLine]
    var total: DividendAmounts
}

struct YearDividends: Equatable {
    /// Nil for all years together.
    var year: Int?
    /// Always twelve entries, January first.
    var months: [MonthDividends]
    var byCurrency: [String: DividendAmounts]
    var usdGross: UsdSum
    var usdTax: UsdSum
    var usdNet: UsdSum
    /// Largest USD net first; positions without a rate go last.
    var positions: [PositionDividends]
    /// NBP table date, when any non-USD amount was converted.
    var rateDate: String?
}

/**
 * Dividend views (mobile-dividends spec), ported from the Android `Dividends`. Rows count in the
 * month of their own date, so a withholding-tax correction lands in the month it was booked.
 * Currencies are only added together after conversion to USD at the latest NBP cross rates.
 */
enum Dividends {
    static let dividend = "DIVIDEND"
    static let tax = "TAX"

    private struct PayerKey: Hashable {
        let ticker: String
        let currency: String
    }

    /// Years that contain dividend or tax rows, newest first.
    static func years(_ events: [CashEvent]) -> [Int] { Array(Set(events.compactMap { $0.month?.year })).sorted(by: >) }

    /// First and last month with dividend data, or nil when there is none.
    static func coverage(_ events: [CashEvent]) -> (first: YearMonth, last: YearMonth)? {
        let months = events.compactMap(\.month)
        guard let first = months.min(), let last = months.max() else { return nil }
        return (first, last)
    }

    static func year(_ events: [CashEvent], _ year: Int, rates: FxRates?) -> YearDividends { period(events, year, rates: rates) }

    /// Like `year`; a nil `year` adds every year together (months then sum across years).
    static func period(_ events: [CashEvent], _ year: Int?, rates: FxRates?) -> YearDividends {
        let inYear = events.filter { event in event.month.map { year == nil || $0.year == year } ?? false }
        let byCurrency = sumByCurrency(inYear)
        let months = (1...12).map { month -> MonthDividends in
            let amounts = sumByCurrency(inYear.filter { $0.month?.month == month })
            return MonthDividends(month: month, byCurrency: amounts, usdNet: usdNet(amounts, rates))
        }
        let grouped = Dictionary(grouping: inYear) { PayerKey(ticker: $0.ticker, currency: $0.currency) }
        let positions = FifoInventory.stableSorted(grouped.map { key, rows in
            let amounts = DividendAmounts.sum(rows)
            return PositionDividends(ticker: key.ticker, currency: key.currency, amounts: amounts,
                                     usdNet: UsdConverter.toUsd(amounts.net, currency: key.currency, rates: rates))
        }) { a, b in
            switch (a.usdNet, b.usdNet) {
            case (nil, nil): return (a.ticker, a.currency) < (b.ticker, b.currency)
            case (nil, _): return false
            case (_, nil): return true
            case (let x?, let y?):
                let compared = x.compare(y)
                return compared != 0 ? compared > 0 : (a.ticker, a.currency) < (b.ticker, b.currency)
            }
        }
        let converted = rates.map { r in byCurrency.keys.contains { $0 != UsdConverter.usd && r.toUsd(.one, currency: $0) != nil } } ?? false
        return YearDividends(
            year: year,
            months: months,
            byCurrency: byCurrency,
            usdGross: usd(byCurrency, rates, \.gross),
            usdTax: usd(byCurrency, rates, \.tax),
            usdNet: usdNet(byCurrency, rates),
            positions: positions,
            rateDate: converted ? rates?.effectiveDate : nil
        )
    }

    /**
     * Net USD change of `year` against the year before, in percent; for the year that is still
     * running (`today`'s year) only the months up to `today`'s month are compared. Nil when the
     * year before has no net dividends.
     */
    static func changeFromYearBefore(_ events: [CashEvent], _ year: Int, rates: FxRates?, today: CalendarDay) -> ExactDecimal? {
        let lastMonth = year == today.year ? today.month : 12
        func net(_ y: Int) -> ExactDecimal {
            Self.year(events, y, rates: rates).months.filter { $0.month <= lastMonth }.reduce(ExactDecimal.zero) { $0.adding($1.usdNet.amount) }
        }
        let before = net(year - 1)
        guard before.signum > 0 else { return nil }
        return net(year).subtracting(before).divided(by: before)?.multiplied(by: ExactDecimal("100")!)
    }

    /// Every booking date per instrument and currency in `year` (nil: all years), newest first.
    static func payments(_ events: [CashEvent], _ year: Int?) -> [Payment] {
        struct Key: Hashable {
            let date: String
            let ticker: String
            let currency: String
        }
        let inPeriod = events.filter { event in event.month.map { year == nil || $0.year == year } ?? false }
        let grouped = Dictionary(grouping: inPeriod) { Key(date: String($0.date.prefix(10)), ticker: $0.ticker, currency: $0.currency) }
        let payments = grouped.map { Payment(date: $0.key.date, ticker: $0.key.ticker, currency: $0.key.currency, amounts: DividendAmounts.sum($0.value)) }
        return payments.sorted { $0.date != $1.date ? $0.date > $1.date : ($0.ticker, $0.currency) < ($1.ticker, $1.currency) }
    }

    /// All years of one instrument in one currency, newest booking date first; rows with a non-ISO date are left out.
    static func history(_ events: [CashEvent], ticker: String, currency: String) -> PayerHistory {
        let rows = events.filter { $0.ticker == ticker && $0.currency == currency && $0.month != nil }
        let lines = Dictionary(grouping: rows) { String($0.date.prefix(10)) }
            .map { PayerLine(date: $0.key, amounts: DividendAmounts.sum($0.value)) }
            .sorted { $0.date > $1.date }
        return PayerHistory(ticker: ticker, currency: currency, lines: lines, total: lines.map(\.amounts).reduce(DividendAmounts(), +))
    }

    /// Net USD per month (twelve entries) for each of up to `count` most recent years, oldest year first.
    static func netByMonthForRecentYears(_ events: [CashEvent], rates: FxRates?, count: Int = 3) -> [(year: Int, months: [UsdSum])] {
        years(events).prefix(count).sorted().map { y in (y, year(events, y, rates: rates).months.map(\.usdNet)) }
    }

    private static func sumByCurrency(_ events: [CashEvent]) -> [String: DividendAmounts] {
        Dictionary(grouping: events, by: \.currency).mapValues(DividendAmounts.sum)
    }

    private static func usd(_ byCurrency: [String: DividendAmounts], _ rates: FxRates?, _ pick: (DividendAmounts) -> ExactDecimal) -> UsdSum {
        var sum = ExactDecimal.zero
        var missing = Set<String>()
        for currency in byCurrency.keys.sorted() {
            if let usd = UsdConverter.toUsd(pick(byCurrency[currency]!), currency: currency, rates: rates) { sum = sum.adding(usd) } else { missing.insert(currency) }
        }
        return UsdSum(amount: sum, missingCurrencies: missing)
    }

    /// USD gross plus USD tax, each in cents, so the net shown always equals the gross and tax shown beside it.
    private static func usdNet(_ byCurrency: [String: DividendAmounts], _ rates: FxRates?) -> UsdSum {
        let gross = usd(byCurrency, rates, \.gross)
        let tax = usd(byCurrency, rates, \.tax)
        return UsdSum(amount: gross.amount.withScale(2).adding(tax.amount.withScale(2)), missingCurrencies: gross.missingCurrencies.union(tax.missingCurrencies))
    }
}
