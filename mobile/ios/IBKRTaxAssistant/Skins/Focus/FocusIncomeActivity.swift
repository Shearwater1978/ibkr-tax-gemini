import SwiftUI

/// Income (mobile-skin-focus): year pills with "All years", one sentence and a timeline of payments.
struct FocusIncome: View {
    @ObservedObject var state: IncomeState
    let onImport: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text(Strings.calmTabIncome).font(.focusSerif(26)).padding(.top, 12)
                if let data = state.data {
                    if Dividends.years(data.events).isEmpty {
                        Text(Strings.dividendsEmpty).foregroundStyle(FocusColors.muted).padding(.vertical, 10)
                        Button(Strings.calmAddReports, action: onImport).buttonStyle(.borderedProminent).foregroundStyle(.white)
                    } else {
                        FocusIncomeContent(data: data)
                    }
                } else {
                    Text(Strings.loading).foregroundStyle(FocusColors.muted)
                }
                Color.clear.frame(height: 24)
            }
            .padding(.horizontal, 22)
        }
        .foregroundStyle(FocusColors.ink)
        .onAppear { state.reload() }
    }
}

private struct FocusPayer: Identifiable {
    let ticker: String
    let currency: String
    var id: String { "\(ticker)-\(currency)" }
}

private struct FocusIncomeContent: View {
    let data: DividendData
    /// Nil until a pill is tapped; `.some(nil)` stands for "all years".
    @State private var year: Int??
    @State private var payer: FocusPayer?

    var body: some View {
        let years = Dividends.years(data.events)
        let selected: Int? = year.map { $0.flatMap { years.contains($0) ? $0 : years[0] } } ?? years[0]
        let today = CalendarDay.today()
        let period = Dividends.period(data.events, selected, rates: data.rates)
        let sentence = selected.map {
            Summaries.income(period, change: Dividends.changeFromYearBefore(data.events, $0, rates: data.rates, today: today), period: .year($0, isCurrent: $0 == today.year))
        } ?? Summaries.income(period, change: nil, period: .allYears(firstYear: years.last!))

        pills(years, selected)
        Text(sentence.attributed).font(.system(size: 17)).lineSpacing(5)
        ForEach(groups(Dividends.payments(data.events, selected)), id: \.month) { group in
            Text(monthName(group.month, withYear: selected == nil).uppercased())
                .font(.system(size: 12, weight: .bold)).tracking(0.7).foregroundStyle(FocusColors.muted).padding(.top, 18).padding(.bottom, 4)
            ForEach(group.payments, id: \.focusId) { p in
                Button { payer = FocusPayer(ticker: p.ticker, currency: p.currency) } label: { paymentRow(p, group.month) }.buttonStyle(.plain)
            }
        }
        .sheet(item: $payer) { payer in
            FocusPayerSheet(events: data.events, ticker: payer.ticker, currency: payer.currency)
        }
    }

    private func pills(_ years: [Int], _ selected: Int?) -> some View {
        let choices = YearChoices.of(years)
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(choices.direct.reversed(), id: \.self) { y in FocusYearPill(text: String(y), on: y == selected) { year = .some(y) } }
                if !choices.older.isEmpty {
                    let olderOn = selected.map(choices.older.contains) ?? false
                    Menu {
                        ForEach(choices.older, id: \.self) { y in Button(String(y)) { year = .some(y) } }
                    } label: {
                        FocusYearPillLabel(text: (olderOn ? String(selected!) : Strings.yearOlder) + " ▾", on: olderOn)
                    }
                }
                FocusYearPill(text: Strings.calmAllYears, on: selected == nil, outlined: true) { year = .some(nil) }
            }
        }
        .padding(.top, 8).padding(.bottom, 14)
    }

    private func paymentRow(_ p: Payment, _ month: YearMonth) -> some View {
        HStack(spacing: 0) {
            Rectangle().fill(FocusColors.line).frame(width: 2, height: 40)
            Circle().fill(FocusColors.gain).frame(width: 12, height: 12).padding(.horizontal, 12)
            VStack(alignment: .leading, spacing: 2) {
                Text(p.ticker).font(.system(size: 15, weight: .bold))
                let day = DateFormatter().shortMonthSymbols[month.month - 1] + " " + String(Int(p.date.dropFirst(8)) ?? 0)
                let tax = p.amounts.tax.signum != 0 ? Strings.focusTaxWithheld(skinMoney(p.amounts.tax.magnitude, p.currency)) : nil
                Text([day, tax].compactMap { $0 }.joined(separator: " · ")).font(.system(size: 13)).foregroundStyle(FocusColors.muted)
            }
            Spacer()
            Text(skinMoney(p.amounts.net, p.currency, signed: true)).bold().foregroundStyle(p.amounts.net.signum >= 0 ? FocusColors.gain : FocusColors.loss)
        }
        .monospacedDigit()
        .padding(.vertical, 10)
        .contentShape(Rectangle())
    }

    /// Consecutive payments of one month (payments are newest first).
    private func groups(_ payments: [Payment]) -> [(month: YearMonth, payments: [Payment])] {
        var result: [(month: YearMonth, payments: [Payment])] = []
        for payment in payments {
            guard let month = payment.month else { continue }
            if result.last?.month == month { result[result.count - 1].payments.append(payment) } else { result.append((month, [payment])) }
        }
        return result
    }

    private func monthName(_ month: YearMonth, withYear: Bool) -> String {
        DateFormatter().monthSymbols[month.month - 1] + (withYear ? " \(month.year)" : "")
    }
}

private extension Payment {
    var focusId: String { "\(date)-\(ticker)-\(currency)" }
}

private struct FocusYearPillLabel: View {
    let text: String
    let on: Bool
    var outlined = false

    var body: some View {
        Text(text).font(.system(size: 13, weight: .semibold))
            .foregroundStyle(on ? .white : FocusColors.muted)
            .padding(.horizontal, 13).padding(.vertical, 7)
            .background(outlined && !on ? Color.clear : (on ? FocusColors.ink : FocusColors.sand), in: Capsule())
            .overlay { if outlined && !on { Capsule().stroke(FocusColors.line) } }
    }
}

private struct FocusYearPill: View {
    let text: String
    let on: Bool
    var outlined = false
    let action: () -> Void

    var body: some View {
        Button(action: action) { FocusYearPillLabel(text: text, on: on, outlined: outlined) }.buttonStyle(.plain)
    }
}

/// One company's dividend history over all years (mobile-payer-history).
struct FocusPayerSheet: View {
    let events: [CashEvent]
    let ticker: String
    let currency: String

    var body: some View {
        let history = Dividends.history(events, ticker: ticker, currency: currency)
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("\(ticker) · " + Strings.payerHistoryTitle).font(.focusSerif(22))
                Text(Strings.payerHistorySubtitle(currency, history.lines.count)).font(.system(size: 13)).foregroundStyle(FocusColors.muted)
                Text(Strings.calmNet(skinMoney(history.total.net, currency))).font(.focusSerif(28, weight: .bold)).padding(.top, 14)
                Text(Strings.dividendsLine(skinMoney(history.total.gross, currency), skinMoney(history.total.tax, currency)))
                    .foregroundStyle(FocusColors.muted).padding(.bottom, 8)
                ForEach(history.lines, id: \.date) { line in
                    let a = line.amounts
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(line.date).fontWeight(.semibold)
                            Text(a.gross.signum == 0 ? Strings.payerHistoryTaxOnly(skinMoney(a.tax, currency))
                                 : Strings.dividendsLine(skinMoney(a.gross, currency), skinMoney(a.tax, currency)))
                                .font(.system(size: 12)).foregroundStyle(FocusColors.muted)
                        }
                        Spacer()
                        Text(skinMoney(a.net, currency)).fontWeight(.semibold).monospacedDigit()
                    }
                    .padding(.vertical, 12)
                    Divider().overlay(FocusColors.line)
                }
            }
            .padding(.horizontal, 22).padding(.top, 24).padding(.bottom, 30)
        }
        .foregroundStyle(FocusColors.ink)
        .background(FocusColors.page)
        .presentationDetents([.medium, .large])
    }
}

/// Operations grouped by month, with search and type chips, opened from the "+" menu (mobile-skin-focus).
struct FocusActivity: View {
    @ObservedObject var state: ActivityState
    let onBack: () -> Void
    let onImport: () -> Void
    @State private var manual: ManualTarget?

    var body: some View {
        let view = state.view
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                FocusBack(label: "‹ " + Strings.calmBack, onBack: onBack)
                HStack {
                    Text(Strings.calmTabActivity).font(.focusSerif(26))
                    Spacer()
                    Button("＋ " + Strings.manualAdd) { manual = .new }.foregroundStyle(FocusColors.deep)
                }
                .padding(.bottom, 12)
                if view.hasAny == false {
                    Text(Strings.operationsEmpty).foregroundStyle(FocusColors.muted)
                    Button(Strings.calmAddReports, action: onImport).buttonStyle(.borderedProminent).foregroundStyle(.white).padding(.top, 12)
                    Button(Strings.manualAdd) { manual = .new }.padding(.top, 8)
                } else {
                    TextField("⌕  " + Strings.calmSearch, text: Binding(get: { view.query }, set: state.setQuery))
                        .textInputAutocapitalization(.characters).autocorrectionDisabled()
                        .padding(12).background(FocusColors.card, in: RoundedRectangle(cornerRadius: 14))
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
                        Text(Strings.operationsNoMatch).foregroundStyle(FocusColors.muted).padding(.top, 20)
                        Button(Strings.operationsClearAll, action: state.clear)
                    }
                    ForEach(groups(view.items), id: \.title) { group in
                        Text(group.title).font(.system(size: 13, weight: .semibold)).foregroundStyle(FocusColors.muted).padding(.top, 14).padding(.bottom, 2)
                        ForEach(group.operations, id: \.id) { operation in
                            FocusActivityRow(operation: operation)
                                .contentShape(Rectangle())
                                .onTapGesture { if let id = operation.entryId { manual = .edit(id) } }
                                // Load the next page shortly before the end of the list.
                                .onAppear { if operation.id == view.items.dropLast(20).last?.id || operation.id == view.items.last?.id { state.loadMore() } }
                            Divider().overlay(FocusColors.line)
                        }
                    }
                }
            }
            .padding(.horizontal, 22)
            .padding(.bottom, 24)
        }
        .foregroundStyle(FocusColors.ink)
        .onAppear { state.reload() }
        .sheet(item: $manual) { ManualEntryView(target: $0) }
    }

    private func chip(_ title: String, isOn: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).padding(.horizontal, 12).padding(.vertical, 6)
                .foregroundStyle(isOn ? .white : FocusColors.ink)
                .background(isOn ? FocusColors.ink : FocusColors.card, in: Capsule())
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

private struct FocusActivityRow: View {
    let operation: OperationRecord

    var body: some View {
        let (symbol, tint, background): (String, Color, Color) = {
            switch operation.eventType {
            case "BUY": return ("＋", FocusColors.deep, FocusColors.sand)
            case "SELL": return ("−", FocusColors.loss, FocusColors.lossSoft)
            case "DIVIDEND": return ("↓", FocusColors.gain, FocusColors.gainSoft)
            case "TAX": return ("%", FocusColors.muted, FocusColors.card)
            default: return ("⇄", FocusColors.ink, FocusColors.card)
            }
        }()
        HStack(spacing: 12) {
            Text(symbol).font(.system(size: 15)).foregroundStyle(tint).frame(width: 36, height: 36).background(background, in: Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text("\(OperationDisplay.typeLabel(operation.eventType)) · \(operation.ticker)").font(.system(size: 15, weight: .semibold))
                if operation.entryId != nil { Text(Strings.manualLabel).font(.system(size: 12, weight: .semibold)).foregroundStyle(FocusColors.deep) }
                Text([Formats.date(operation.date), OperationDisplay.details(operation)].compactMap { $0 }.joined(separator: " · "))
                    .font(.system(size: 13)).foregroundStyle(FocusColors.muted)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(OperationDisplay.amount(operation)).font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(operation.eventType == "DIVIDEND" ? FocusColors.gain : FocusColors.ink)
                if operation.fee.signum != 0 {
                    Text(Strings.operationsCommission(skinMoney(operation.fee.magnitude, operation.currency))).font(.system(size: 12)).foregroundStyle(FocusColors.muted)
                }
            }
        }
        .monospacedDigit()
        .padding(.vertical, 11)
    }
}
