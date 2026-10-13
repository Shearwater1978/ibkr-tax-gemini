import Foundation

/// How a part of a sentence is shown: plain, bold, or bold in the gain or loss colour.
enum Tone: Equatable {
    case plain
    case strong
    case gain
    case loss
}

struct Part: Equatable {
    var text: String
    var tone: Tone = .plain
}

struct Sentence: Equatable {
    var parts: [Part]
    var text: String { parts.map(\.text).joined() }
}

/// Which income period a sentence describes.
enum IncomePeriod {
    case year(Int, isCurrent: Bool)
    case allYears(firstYear: Int)
}

/**
 * The Focus sentences (mobile-skin-focus "Focus summaries"), built only from the app's own numbers.
 * English only, like the rest of the app. Port of the Android `Summaries`; same wording.
 */
enum Summaries {
    /// Value line under the portfolio total: today's move, its main drivers and the total return.
    static func today(_ rows: [PositionRow], totals: UsdTotals, summary: PortfolioSummary, rates: FxRates?) -> Sentence {
        var parts: [Part] = []
        let moves = rows.compactMap { row in row.dailyPnl.flatMap { UsdConverter.toUsd($0, currency: row.displayCurrency, rates: rates) }.map { (row.ticker, $0) } }
        let day = totals.dailyPnl.withScale(0, halfUp: true)
        if moves.isEmpty {
            parts.append(Part(text: "Today’s change is not known yet."))
        } else if day.signum == 0 {
            parts.append(Part(text: "Your portfolio is flat today."))
        } else {
            let up = day.signum > 0
            parts.append(Part(text: "You’re "))
            parts.append(Part(text: "\(up ? "up" : "down") \(dollars(day.magnitude)) today", tone: up ? .gain : .loss))
            let drivers = FifoInventory.stableSorted(moves.filter { $0.1.signum == day.signum }) { $0.1.magnitude.compare($1.1.magnitude) > 0 }.prefix(2).map(\.0)
            if !drivers.isEmpty { parts.append(Part(text: ", mostly from \(drivers.joined(separator: " and "))")) }
            parts.append(Part(text: "."))
        }
        if let percent = summary.totalProfitPercent {
            let whole = percent.withScale(0, halfUp: true)
            parts.append(Part(text: whole.signum >= 0 ? " Since you started, your money grew by " : " Since you started, your money shrank by "))
            parts.append(Part(text: "\(Formats.number(whole.magnitude, 0, 0))%", tone: whole.signum >= 0 ? .gain : .loss))
            parts.append(Part(text: "."))
        }
        if !totals.isComplete || totals.missingDailyPnl > 0 { parts.append(Part(text: " Some prices are missing, so these figures are incomplete.")) }
        return Sentence(parts: parts)
    }

    /// The income headline: net dividends of the period and, for a year, the change against the year before.
    static func income(_ dividends: YearDividends, change: ExactDecimal?, period: IncomePeriod) -> Sentence {
        var parts: [Part] = []
        let net = Part(text: skinMoney(dividends.usdNet.amount, "USD"), tone: .gain)
        switch period {
        case .allYears(let firstYear):
            parts += [Part(text: "Since \(firstYear) your companies paid you "), net, Part(text: " after \(skinMoney(dividends.usdTax.amount.magnitude, "USD")) tax.")]
        case .year(let year, let isCurrent):
            parts += [Part(text: "\(isCurrent ? "This year" : "In \(year)") your companies paid you "), net, Part(text: " after tax")]
            let whole = change?.withScale(0, halfUp: true)
            let than = isCurrent ? "the same months last year" : "the year before"
            if let whole {
                if whole.signum == 0 {
                    parts.append(Part(text: ", about the same as \(than)."))
                } else {
                    parts.append(Part(text: ": "))
                    parts.append(Part(text: "\(Formats.number(whole.magnitude, 0, 0))% \(whole.signum > 0 ? "more" : "less")", tone: .strong))
                    parts.append(Part(text: " than \(than)."))
                }
            } else {
                parts.append(Part(text: "."))
            }
        }
        let missing = dividends.usdNet.missingCurrencies.union(dividends.usdTax.missingCurrencies).sorted()
        if !missing.isEmpty { parts.append(Part(text: " Amounts in \(missing.joined(separator: ", ")) could not be converted and are left out.")) }
        return Sentence(parts: parts)
    }

    /// A position's story: what was put in and what it is worth, sales and dividends, then the total (last).
    static func story(_ p: PositionPerformance) -> [Sentence] {
        let cur = p.currency
        let worth: Sentence
        if let row = p.row {
            let start = [Part(text: "You put in "), Part(text: skinMoney(p.invested, cur), tone: .strong), Part(text: " for your \(Formats.quantity(row.holding.quantity)) shares.")]
            let value = row.marketValue.map { [Part(text: " They are now worth "), Part(text: skinMoney($0, cur), tone: .strong), Part(text: ".")] }
                ?? [Part(text: " Their current price is not available.")]
            worth = Sentence(parts: start + value)
        } else {
            worth = Sentence(parts: [Part(text: "You put in "), Part(text: skinMoney(p.invested, cur), tone: .strong), Part(text: " and have sold all your shares.")])
        }

        var extra: [Part] = []
        let sold = p.realizedGain.signum != 0
        let paid = p.dividends.gross.signum != 0 || p.dividends.tax.signum != 0
        if sold || paid {
            extra.append(Part(text: "On top of that, "))
            if sold {
                let gain = p.realizedGain.signum > 0
                extra.append(Part(text: gain ? "you earned " : "you lost "))
                extra.append(Part(text: skinMoney(p.realizedGain.magnitude, cur), tone: gain ? .gain : .loss))
                extra.append(Part(text: gain ? " from shares you sold" : " on shares you sold"))
            }
            if paid {
                extra.append(Part(text: !sold ? "you earned " : (p.realizedGain.signum > 0 ? ", and " : ", and earned ")))
                extra.append(Part(text: skinMoney(p.dividends.net, cur), tone: .gain))
                extra.append(Part(text: " in dividends after \(skinMoney(p.dividends.tax.magnitude, cur)) tax"))
            }
            extra.append(Part(text: "."))
        } else {
            extra.append(Part(text: "There were no sales or dividends so far."))
        }
        if !p.realizedComplete { extra.append(Part(text: " Some sales could not be matched, so this may be incomplete.")) }

        let all: Sentence
        if let total = p.total {
            var percent = ""
            if let whole = p.totalPercent?.withScale(0, halfUp: true) {
                let sign = whole.signum > 0 ? "+" : (whole.signum < 0 ? "−" : "")
                percent = " · \(sign)\(Formats.number(whole.magnitude, 0, 0))%"
            }
            all = Sentence(parts: [Part(text: "All in all: "), Part(text: skinMoney(total, cur, signed: true) + percent, tone: total.signum >= 0 ? .gain : .loss)])
        } else {
            all = Sentence(parts: [Part(text: "All in all: not known yet, because the price is missing.")])
        }
        return [worth, Sentence(parts: extra), all]
    }

    /// Whole dollars, e.g. "$110".
    private static func dollars(_ value: ExactDecimal) -> String { "$" + Formats.number(value.withScale(0, halfUp: true), 0, 0) }
}
