import SwiftUI

let gainColor = Color(red: 0x2E / 255, green: 0x7D / 255, blue: 0x32 / 255)
let lossColor = Color(red: 0xC6 / 255, green: 0x28 / 255, blue: 0x28 / 255)

func signColor(_ value: ExactDecimal?) -> Color {
    switch value?.signum ?? 0 {
    case 1: return gainColor
    case -1: return lossColor
    default: return .primary
    }
}

/// Per-position breakdown in the position's own currency (Android `PositionDetailsSheet`, mobile-position-performance spec).
struct PositionDetailsSheet: View {
    let position: PositionPerformance
    let onHistory: () -> Void

    var body: some View {
        let currency = position.currency
        func money(_ value: ExactDecimal?) -> String { value.map { Formats.money($0, currency) } ?? Strings.detailsUnavailable }
        func signed(_ value: ExactDecimal?) -> String { value.map { Formats.signedMoney($0, currency) } ?? Strings.detailsUnavailable }
        return List {
            Section {
                line(Strings.detailsQuantity, position.row.map { Formats.quantity($0.holding.quantity) } ?? "0")
                if let row = position.row {
                    line(Strings.detailsAverage, Formats.number(row.holding.averagePrice, 2, 4) + " " + currency)
                    line(Strings.detailsInvested, money(position.invested))
                    line(Strings.detailsValue, money(row.marketValue))
                }
            } header: {
                VStack(alignment: .leading) {
                    Text(position.ticker).font(.title2.bold()).foregroundStyle(.primary)
                    Text(position.isSold ? Strings.detailsSold(currency) : Strings.detailsHeld(currency)).textCase(nil)
                }
            }
            Section {
                line(Strings.detailsPriceGain, signed(position.priceGain), position.priceGain)
                line(Strings.detailsSalesGain, signed(position.realizedGain), position.realizedGain)
                VStack(alignment: .leading, spacing: 2) {
                    line(Strings.detailsDividends, signed(position.dividends.net), position.dividends.net)
                    Text(Strings.dividendsLine(Formats.money(position.dividends.gross, currency), Formats.money(position.dividends.tax, currency)))
                        .font(.footnote).foregroundStyle(.secondary)
                }
                if position.dividends.gross.signum != 0 || position.dividends.tax.signum != 0 {
                    Button(Strings.payerHistoryTitle + " ›", action: onHistory)
                }
            }
            Section {
                let percent = position.totalPercent.map { " (" + Formats.signedPercent($0) + ")" } ?? ""
                line(Strings.detailsTotal, signed(position.total) + percent, position.total).bold()
                if !position.realizedComplete { Text(Strings.detailsSalesIncomplete).font(.footnote) }
            } footer: {
                Text(Strings.detailsNote)
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func line(_ label: String, _ value: String, _ sign: ExactDecimal? = nil) -> some View {
        HStack {
            Text(label)
            Spacer()
            Text(value).foregroundStyle(signColor(sign)).monospacedDigit()
        }
    }
}

/// Every dividend and tax booking of one payer in one currency (Android `PayerHistorySheet`, mobile-payer-history spec).
struct PayerHistorySheet: View {
    let events: [CashEvent]
    let ticker: String
    let currency: String

    var body: some View {
        let history = Dividends.history(events, ticker: ticker, currency: currency)
        List {
            Section {
                AmountsLine(title: currency, amounts: history.total, currency: currency)
            } header: {
                VStack(alignment: .leading) {
                    Text("\(ticker) · \(Strings.payerHistoryTitle)").font(.title2.bold()).foregroundStyle(.primary)
                    Text(Strings.payerHistorySubtitle(currency, history.lines.count)).textCase(nil)
                    Text(Strings.payerHistoryTotals).padding(.top, 12)
                }
            }
            Section {
                ForEach(history.lines, id: \.date) { AmountsLine(title: $0.date, amounts: $0.amounts, currency: currency) }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

/// A net amount with its gross and withholding tax underneath.
struct AmountsLine: View {
    let title: String
    let amounts: DividendAmounts
    let currency: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(title).bold()
                Spacer()
                Text(Formats.money(amounts.net, currency)).monospacedDigit()
            }
            Text(Strings.dividendsLine(Formats.money(amounts.gross, currency), Formats.money(amounts.tax, currency))).font(.footnote).foregroundStyle(.secondary)
        }
    }
}
