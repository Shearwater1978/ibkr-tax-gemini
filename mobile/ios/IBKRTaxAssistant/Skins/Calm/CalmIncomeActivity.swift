import Charts
import SwiftUI

/// Calm's Income (Android `CalmIncome`): the year's net in USD, a monthly chart, top payers or a comparison with the year before.
struct CalmIncome: View {
    @ObservedObject var state: IncomeState
    let onImport: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if let data = state.data {
                    if Dividends.years(data.events).isEmpty {
                        Text(Strings.calmTabIncome).font(.system(size: 28, weight: .bold)).padding(.vertical, 14)
                        Text(Strings.dividendsEmpty).foregroundStyle(CalmColors.muted).padding(.vertical, 12)
                        Button(Strings.calmAddReports, action: onImport).buttonStyle(.borderedProminent)
                    } else {
                        CalmIncomeContent(data: data)
                    }
                } else {
                    Text(Strings.loading).foregroundStyle(CalmColors.muted).padding(.top, 24)
                }
            }
            .padding(.horizontal, 22)
            .padding(.bottom, 24)
        }
        .foregroundStyle(CalmColors.ink)
        .background(Color.white)
        .onAppear { state.reload() }
    }
}

private struct CalmIncomeContent: View {
    let data: DividendData
    /// Nil stands for "all years".
    @State private var year: Int??
    @State private var compare = false
    @State private var month: Int?
    @State private var payer: Payer?

    private struct Payer: Identifiable {
        let ticker: String
        let currency: String
        var id: String { "\(ticker)-\(currency)" }
    }

    var body: some View {
        let years = Dividends.years(data.events)
        let selected: Int? = year.map { $0.flatMap { years.contains($0) ? $0 : years[0] } } ?? years[0]
        let period = Dividends.period(data.events, selected, rates: data.rates)
        let before = (selected != nil && compare) ? Dividends.year(data.events, selected! - 1, rates: data.rates) : nil
        let today = CalendarDay.today()

        HStack {
            Text(Strings.calmTabIncome).font(.system(size: 28, weight: .bold))
            Spacer()
            Menu {
                ForEach(years, id: \.self) { y in Button(String(y)) { year = .some(y); month = nil } }
                Button(Strings.calmAllYears) { year = .some(nil); month = nil; compare = false }
            } label: {
                Text((selected.map(String.init) ?? Strings.calmAllYears) + " ▾").fontWeight(.semibold).foregroundStyle(CalmColors.muted)
                    .padding(.horizontal, 12).padding(.vertical, 6).background(CalmColors.card, in: Capsule())
            }
        }
        .padding(.vertical, 14)
        Text(selected == nil ? Strings.calmDividendsAll : (selected == today.year ? Strings.calmDividendsThisYear : Strings.calmDividendsIn(selected!)))
            .font(.system(size: 14)).foregroundStyle(CalmColors.muted)
        Text(skinMoney(period.usdNet.amount, "USD")).font(.system(size: 36, weight: .bold)).padding(.vertical, 4)
        HStack(spacing: 8) {
            Text(Strings.calmAfterTax(skinMoney(period.usdTax.amount.magnitude, "USD"))).font(.system(size: 13)).foregroundStyle(CalmColors.muted)
            if let selected, let change = Dividends.changeFromYearBefore(data.events, selected, rates: data.rates, today: today) {
                CalmChip(text: Strings.calmVsYear(skinPercent(change), selected - 1), positive: change.signum >= 0)
            }
        }
        Text(note(period)).font(.system(size: 12)).foregroundStyle(CalmColors.muted).padding(.top, 4)

        Chart {
            ForEach(period.months, id: \.month) { m in
                if let before {
                    BarMark(x: .value("Month", monthLabel(m.month)), y: .value("USD", double(before.months[m.month - 1].usdNet.amount)))
                        .foregroundStyle(Color(hex: 0xC8D8FF)).position(by: .value("Year", "before"))
                }
                BarMark(x: .value("Month", monthLabel(m.month)), y: .value("USD", double(m.usdNet.amount)))
                    .foregroundStyle(month == m.month || before != nil ? CalmColors.accent : CalmColors.accentSoft)
                    .position(by: .value("Year", "this"))
                    .accessibilityLabel(monthLabel(m.month))
                    .accessibilityValue(skinMoney(m.usdNet.amount, "USD"))
            }
        }
        .chartLegend(.hidden)
        .chartOverlay { proxy in
            GeometryReader { geometry in
                Rectangle().fill(.clear).contentShape(Rectangle()).onTapGesture { location in
                    let x = location.x - geometry[proxy.plotAreaFrame].origin.x
                    if let label: String = proxy.value(atX: x), let index = (1...12).first(where: { monthLabel($0) == label }) {
                        month = month == index ? nil : index
                    }
                }
            }
        }
        .frame(height: 150)
        .padding(.top, 18)
        if let month {
            HStack {
                Text(DateFormatter().monthSymbols[month - 1] + (selected.map { " \($0)" } ?? ""))
                Spacer()
                Text(Strings.calmNet(skinMoney(period.months[month - 1].usdNet.amount, "USD"))).bold()
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(CalmColors.card, in: RoundedRectangle(cornerRadius: 14))
            .padding(.top, 10)
        }
        if selected != nil {
            Picker("", selection: $compare) {
                Text(Strings.calmTopPayers).tag(false)
                Text(Strings.calmVsLastYear).tag(true)
            }
            .pickerStyle(.segmented)
            .padding(.top, 18)
        }
        ForEach(period.positions, id: \.calmPayerId) { p in
            Button { payer = Payer(ticker: p.ticker, currency: p.currency) } label: {
                HStack(spacing: 12) {
                    TickerTile(ticker: p.ticker)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(p.ticker).fontWeight(.semibold)
                        Text(Strings.dividendsLine(skinMoney(p.amounts.gross, p.currency), skinMoney(p.amounts.tax, p.currency))).font(.system(size: 12)).foregroundStyle(CalmColors.muted)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(skinMoney(p.amounts.net, p.currency)).fontWeight(.semibold)
                        Text(Strings.calmNetShort).font(.system(size: 12)).foregroundStyle(CalmColors.muted)
                    }
                }
                .monospacedDigit()
                .padding(.vertical, 12)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Divider().overlay(CalmColors.line)
        }
        .sheet(item: $payer) { payer in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) { CalmPayerHistory(history: Dividends.history(data.events, ticker: payer.ticker, currency: payer.currency)) }
                    .padding(22).foregroundStyle(CalmColors.ink)
            }
            .background(Color.white)
            .presentationDetents([.medium, .large])
        }
    }

    private func note(_ period: YearDividends) -> String {
        let missing = [period.usdGross, period.usdTax, period.usdNet].reduce(Set<String>()) { $0.union($1.missingCurrencies) }
        var parts = [Strings.headerApproximate]
        if let date = period.rateDate { parts.append(Strings.headerRateDate(date)) }
        if !missing.isEmpty { parts.append(Strings.dividendsIncomplete(missing.sorted().joined(separator: ", "))) }
        return parts.joined(separator: " · ")
    }

    private func monthLabel(_ month: Int) -> String { DateFormatter().veryShortMonthSymbols[month - 1] + String(repeating: "\u{200B}", count: month) }

    private func double(_ value: ExactDecimal) -> Double { Double(value.description) ?? 0 }
}

private extension PositionDividends {
    var calmPayerId: String { "\(ticker)-\(currency)" }
}

/// Calm's Activity (Android `CalmActivity`): operations grouped by month, with search and type chips.
struct CalmActivity: View {
    @ObservedObject var state: ActivityState
    let onImport: () -> Void
    @State private var manual: ManualTarget?

    var body: some View {
        let view = state.view
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text(Strings.calmTabActivity).font(.system(size: 28, weight: .bold))
                    Spacer()
                    Button("＋ " + Strings.manualAdd) { manual = .new }.foregroundStyle(CalmColors.accent)
                }
                .padding(.top, 14).padding(.bottom, 12)
                if view.hasAny == false {
                    Text(Strings.operationsEmpty).foregroundStyle(CalmColors.muted)
                    Button(Strings.calmAddReports, action: onImport).buttonStyle(.borderedProminent).padding(.top, 12)
                    Button(Strings.manualAdd) { manual = .new }.padding(.top, 8)
                } else {
                    TextField("⌕  " + Strings.calmSearch, text: Binding(get: { view.query }, set: state.setQuery))
                        .textInputAutocapitalization(.characters).autocorrectionDisabled()
                        .padding(12).background(CalmColors.card, in: RoundedRectangle(cornerRadius: 14))
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            chip(Strings.calmAll, isOn: view.filters.isEmpty) { if !view.filters.isEmpty { state.clear() } }
                            ForEach(OperationFilter.allCases, id: \.self) { filter in
                                chip(OperationDisplay.filterLabel(filter), isOn: view.filters.contains(filter)) { state.toggle(filter) }
                            }
                        }
                    }
                    .padding(.vertical, 10)
                    if view.items.isEmpty && view.hasAny == true {
                        Text(Strings.operationsNoMatch).foregroundStyle(CalmColors.muted).padding(.top, 20)
                        Button(Strings.operationsClearAll, action: state.clear)
                    }
                    ForEach(groups(view.items), id: \.title) { group in
                        Text(group.title).font(.system(size: 13, weight: .semibold)).foregroundStyle(CalmColors.muted).padding(.top, 14).padding(.bottom, 2)
                        ForEach(group.operations, id: \.id) { operation in
                            CalmActivityRow(operation: operation)
                                .contentShape(Rectangle())
                                .onTapGesture { if let id = operation.entryId { manual = .edit(id) } }
                                // Load the next page shortly before the end of the list.
                                .onAppear { if operation.id == view.items.dropLast(20).last?.id || operation.id == view.items.last?.id { state.loadMore() } }
                            Divider().overlay(CalmColors.line)
                        }
                    }
                }
            }
            .padding(.horizontal, 22)
            .padding(.bottom, 24)
        }
        .foregroundStyle(CalmColors.ink)
        .background(Color.white)
        .onAppear { state.reload() }
        .sheet(item: $manual) { ManualEntryView(target: $0) }
    }

    private func chip(_ title: String, isOn: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).padding(.horizontal, 12).padding(.vertical, 6)
                .foregroundStyle(isOn ? .white : CalmColors.ink)
                .background(isOn ? CalmColors.ink : CalmColors.card, in: Capsule())
        }
        .buttonStyle(.plain)
    }

    /// Consecutive operations of one month, newest month first (the list is newest first).
    private func groups(_ items: [OperationRecord]) -> [(title: String, operations: [OperationRecord])] {
        var result: [(title: String, operations: [OperationRecord])] = []
        for operation in items {
            let title = YearMonth(iso: operation.date).map(monthTitle) ?? "—"
            if result.last?.title == title { result[result.count - 1].operations.append(operation) } else { result.append((title, [operation])) }
        }
        return result
    }

    private func monthTitle(_ month: YearMonth) -> String {
        let formatter = DateFormatter()
        formatter.setLocalizedDateFormatFromTemplate("LLLL yyyy")
        let date = Calendar(identifier: .gregorian).date(from: DateComponents(year: month.year, month: month.month, day: 1))!
        return formatter.string(from: date).capitalized(with: .current)
    }
}

private struct CalmActivityRow: View {
    let operation: OperationRecord

    var body: some View {
        let (symbol, tint, background): (String, Color, Color) = {
            switch operation.eventType {
            case "BUY": return ("＋", CalmColors.accent, CalmColors.accentSoft)
            case "SELL": return ("−", CalmColors.loss, CalmColors.lossSoft)
            case "DIVIDEND": return ("↓", CalmColors.gain, CalmColors.gainSoft)
            case "TAX": return ("%", CalmColors.muted, CalmColors.card)
            default: return ("⇄", CalmColors.ink, CalmColors.card)
            }
        }()
        HStack(spacing: 12) {
            Text(symbol).font(.system(size: 15)).foregroundStyle(tint).frame(width: 36, height: 36).background(background, in: Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text("\(OperationDisplay.typeLabel(operation.eventType)) · \(operation.ticker)").font(.system(size: 15, weight: .semibold))
                if operation.entryId != nil { Text(Strings.manualLabel).font(.system(size: 12, weight: .semibold)).foregroundStyle(CalmColors.accent) }
                Text([Formats.date(operation.date), OperationDisplay.details(operation)].compactMap { $0 }.joined(separator: " · "))
                    .font(.system(size: 13)).foregroundStyle(CalmColors.muted)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(OperationDisplay.amount(operation)).font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(operation.eventType == "DIVIDEND" ? CalmColors.gain : CalmColors.ink)
                if operation.fee.signum != 0 {
                    Text(Strings.operationsCommission(skinMoney(operation.fee.magnitude, operation.currency))).font(.system(size: 12)).foregroundStyle(CalmColors.muted)
                }
            }
        }
        .monospacedDigit()
        .padding(.vertical, 11)
    }
}
