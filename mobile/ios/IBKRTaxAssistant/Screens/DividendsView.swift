import Charts
import SwiftUI

// One colour per compared year, oldest first (amber, blue, purple), readable in light and dark themes.
private let yearColors = [Color(red: 0xF9 / 255, green: 0xA8 / 255, blue: 0x25 / 255),
                          Color(red: 0x29 / 255, green: 0xB6 / 255, blue: 0xF6 / 255),
                          Color(red: 0x95 / 255, green: 0x75 / 255, blue: 0xCD / 255)]

/// Received dividends per month, year and position (Android Classic `DividendsScreen`, mobile-dividends spec).
struct DividendsView: View {
    @ObservedObject var state: IncomeState
    let onImportReport: () -> Void

    var body: some View {
        Group {
            if let data = state.data {
                if Dividends.years(data.events).isEmpty {
                    VStack(spacing: 16) {
                        Text(Strings.dividendsEmpty).multilineTextAlignment(.center)
                        Button(Strings.actionImportReport, action: onImportReport).buttonStyle(.borderedProminent)
                    }
                    .padding(24)
                } else {
                    DividendContent(data: data)
                }
            } else {
                Text(Strings.loading)
            }
        }
        .onAppear { state.reload() }
    }
}

private struct Payer: Identifiable {
    let ticker: String
    let currency: String
    var id: String { "\(ticker)-\(currency)" }
}

private struct DividendContent: View {
    let data: DividendData
    @State private var selected: Int?
    @State private var payer: Payer?

    var body: some View {
        let years = Dividends.years(data.events)
        let year = selected.flatMap { years.contains($0) ? $0 : nil } ?? years[0]
        let totals = Dividends.year(data.events, year, rates: data.rates)
        List {
            Section {
                YearPicker(years: years, selected: Binding(get: { year }, set: { selected = $0 }))
                HStack(alignment: .top) {
                    total(Strings.dividendsGross, totals.usdGross)
                    total(Strings.dividendsTax, totals.usdTax)
                    total(Strings.dividendsNet, totals.usdNet, bold: true)
                }
                Text(usdNote(totals)).font(.footnote).foregroundStyle(.secondary)
            } header: {
                Text(Strings.dividendsTitle)
            }
            Section(Strings.dividendsByMonth(year)) {
                Chart(totals.months, id: \.month) { month in
                    BarMark(x: .value("Month", monthLabel(month.month)), y: .value("USD", double(month.usdNet.amount)))
                        .accessibilityLabel("\(monthLabel(month.month)) \(year)")
                        .accessibilityValue(Formats.money(month.usdNet.amount, "USD"))
                }
                .frame(height: 140)
            }
            if totals.byCurrency.count > 1 || totals.byCurrency.keys.first != "USD" {
                Section(Strings.dividendsByCurrency) {
                    ForEach(totals.byCurrency.keys.sorted(), id: \.self) { currency in
                        AmountsLine(title: currency, amounts: totals.byCurrency[currency]!, currency: currency)
                    }
                }
            }
            let recent = Dividends.netByMonthForRecentYears(data.events, rates: data.rates)
            if recent.count > 1 {
                Section(Strings.dividendsCompare) {
                    Chart {
                        ForEach(Array(recent.enumerated()), id: \.offset) { index, entry in
                            ForEach(1...12, id: \.self) { month in
                                BarMark(x: .value("Month", monthLabel(month)), y: .value("USD", double(entry.months[month - 1].amount)))
                                    .foregroundStyle(by: .value("Year", String(entry.year)))
                                    .position(by: .value("Year", String(entry.year)))
                            }
                        }
                    }
                    .chartForegroundStyleScale(domain: recent.map { String($0.year) }, range: Array(yearColors.prefix(recent.count)))
                    .frame(height: 130)
                }
            }
            Section {
                ForEach(totals.positions, id: \.payerId) { position in
                    AmountsLine(title: position.ticker, amounts: position.amounts, currency: position.currency)
                        .contentShape(Rectangle())
                        .onTapGesture { payer = Payer(ticker: position.ticker, currency: position.currency) }
                }
            } header: {
                Text(Strings.dividendsByPosition(year))
            } footer: {
                if let coverage = Dividends.coverage(data.events) {
                    Text(Strings.dividendsCoverage(monthYear(coverage.first), monthYear(coverage.last)))
                }
            }
        }
        .sheet(item: $payer) { PayerHistorySheet(events: data.events, ticker: $0.ticker, currency: $0.currency) }
    }

    private func total(_ label: String, _ sum: UsdSum, bold: Bool = false) -> some View {
        VStack(alignment: .leading) {
            Text(label).font(.caption)
            Text(Formats.money(sum.amount, "USD")).font(bold ? .headline : .body).monospacedDigit().lineLimit(1).minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func usdNote(_ year: YearDividends) -> String {
        let missing = [year.usdGross, year.usdTax, year.usdNet].reduce(Set<String>()) { $0.union($1.missingCurrencies) }
        var parts = [Strings.headerApproximate]
        if let date = year.rateDate { parts.append(Strings.headerRateDate(date)) }
        if !missing.isEmpty { parts.append(Strings.dividendsIncomplete(missing.sorted().joined(separator: ", "))) }
        return parts.joined(separator: " · ")
    }

    private func double(_ value: ExactDecimal) -> Double { Double(value.description) ?? 0 }

    private func monthLabel(_ month: Int) -> String { DateFormatter().shortMonthSymbols[month - 1] }

    private func monthYear(_ month: YearMonth) -> String {
        let formatter = DateFormatter()
        formatter.setLocalizedDateFormatFromTemplate("MMM yyyy")
        return formatter.string(from: Calendar(identifier: .gregorian).date(from: DateComponents(year: month.year, month: month.month, day: 1))!)
    }
}

/// Newest years directly, older ones in a menu (mobile-year-selection spec).
struct YearPicker: View {
    let years: [Int]
    @Binding var selected: Int

    var body: some View {
        let choices = YearChoices.of(years)
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(choices.direct, id: \.self) { year in
                    chip(String(year), isOn: year == selected) { selected = year }
                }
                if !choices.older.isEmpty {
                    Menu {
                        ForEach(choices.older, id: \.self) { year in Button(String(year)) { selected = year } }
                    } label: {
                        chipLabel((choices.older.contains(selected) ? String(selected) : Strings.yearOlder) + " ▾", isOn: choices.older.contains(selected))
                    }
                }
            }
        }
    }

    private func chip(_ title: String, isOn: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) { chipLabel(title, isOn: isOn) }.buttonStyle(.plain)
    }

    private func chipLabel(_ title: String, isOn: Bool) -> some View {
        Text(title)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(isOn ? Color.accentColor.opacity(0.2) : Color.clear, in: Capsule())
            .overlay(Capsule().stroke(isOn ? Color.accentColor : Color.secondary.opacity(0.4)))
    }
}

private extension PositionDividends {
    var payerId: String { "\(ticker)-\(currency)" }
}
