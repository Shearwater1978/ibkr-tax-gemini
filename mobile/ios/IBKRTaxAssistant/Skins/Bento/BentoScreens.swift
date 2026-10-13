import SwiftUI

private let yearColors = [BentoColors.amber, BentoColors.cyan, BentoColors.violet]

/// Bento's Income (Android `BentoIncome`): year pills, up to three years side by side per month, and the payers.
struct BentoIncome: View {
    @ObservedObject var state: IncomeState
    let onPayer: (_ ticker: String, _ currency: String) -> Void
    @State private var year: Int?
    @State private var month: Int?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                BentoHeader(title: Strings.calmTabIncome)
                if let data = state.data {
                    let years = Dividends.years(data.events)
                    if years.isEmpty {
                        BentoTile { Text(Strings.dividendsEmpty).foregroundStyle(BentoColors.muted) }
                    } else {
                        content(data, years)
                    }
                } else {
                    Text(Strings.loading).foregroundStyle(BentoColors.muted)
                }
                Color.clear.frame(height: bentoTabBarRoom)
            }
            .padding(.horizontal, 16)
        }
        .onAppear { state.reload() }
    }

    @ViewBuilder
    private func content(_ data: DividendData, _ years: [Int]) -> some View {
        let selected = year.flatMap { years.contains($0) ? $0 : nil } ?? years[0]
        let period = Dividends.year(data.events, selected, rates: data.rates)
        let shown = (selected - 2...selected).filter(years.contains)
        let series = shown.map { y in Dividends.year(data.events, y, rates: data.rates).months.map { Double($0.usdNet.amount.description) ?? 0 } }
        let colors = Array(yearColors.suffix(shown.count))
        let choices = YearChoices.of(years)

        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(choices.direct.reversed(), id: \.self) { y in BentoPill(text: String(y), active: y == selected) { year = y; month = nil } }
                if !choices.older.isEmpty {
                    let olderSelected = choices.older.contains(selected)
                    Menu {
                        ForEach(choices.older, id: \.self) { y in Button(String(y)) { year = y; month = nil } }
                    } label: {
                        Text((olderSelected ? String(selected) : Strings.yearOlder) + " ▾").font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(olderSelected ? .white : BentoColors.ink)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(olderSelected ? BentoColors.violet : BentoColors.tile2, in: Capsule())
                    }
                }
            }
        }
        HStack(spacing: 10) {
            BentoTile {
                BentoTileLabel(text: Strings.bentoNetYear(selected))
                Text(skinMoney(period.usdNet.amount, "USD")).font(.system(size: 20, weight: .bold)).padding(.top, 6)
                let change = Dividends.changeFromYearBefore(data.events, selected, rates: data.rates, today: .today())
                Text(change.map { Strings.calmVsYear(skinPercent($0), selected - 1) } ?? Strings.bentoFirstYear)
                    .font(.system(size: 12, weight: .semibold)).foregroundStyle(bentoSign(change))
            }
            BentoTile {
                BentoTileLabel(text: Strings.bentoTaxWithheld)
                Text(skinMoney(period.usdTax.amount.magnitude, "USD")).font(.system(size: 20, weight: .bold)).padding(.top, 6)
                let gross = Double(period.usdGross.amount.description) ?? 0
                let tax = Double(period.usdTax.amount.magnitude.description) ?? 0
                Text(Strings.bentoOfGross(gross > 0 ? Int(tax * 100 / gross) : 0)).font(.system(size: 12)).foregroundStyle(BentoColors.muted)
            }
        }
        BentoTile {
            BentoTileLabel(text: Strings.bentoNetPerMonth(shown.map(String.init).joined(separator: " · ")))
            let maximum = series.flatMap { $0 }.max().flatMap { $0 > 0 ? $0 : nil } ?? 1
            HStack(alignment: .bottom, spacing: 4) {
                ForEach(0..<12, id: \.self) { m in
                    GeometryReader { geometry in
                        HStack(alignment: .bottom, spacing: 1) {
                            ForEach(Array(series.enumerated()), id: \.offset) { index, values in
                                RoundedRectangle(cornerRadius: 3).fill(colors[index])
                                    .frame(height: geometry.size.height * min(max(values[m] / maximum, 0.01), 1))
                            }
                        }
                        .frame(maxHeight: .infinity, alignment: .bottom)
                    }
                    .background(month == m ? Color.white.opacity(0.06) : .clear, in: RoundedRectangle(cornerRadius: 6))
                    .contentShape(Rectangle())
                    .onTapGesture { month = month == m ? nil : m }
                    .accessibilityLabel(DateFormatter().shortMonthSymbols[m])
                }
            }
            .frame(height: 118)
            .padding(.top, 12)
            HStack(spacing: 4) {
                ForEach(0..<12, id: \.self) { m in
                    Text(DateFormatter().veryShortMonthSymbols[m]).font(.system(size: 10)).foregroundStyle(BentoColors.muted).frame(maxWidth: .infinity)
                }
            }
            .padding(.top, 4)
            HStack(spacing: 14) {
                ForEach(Array(shown.enumerated()), id: \.offset) { index, y in
                    HStack(spacing: 4) {
                        Circle().fill(colors[index]).frame(width: 8, height: 8)
                        Text(String(y)).font(.system(size: 12)).foregroundStyle(BentoColors.muted)
                    }
                }
            }
            .padding(.top, 8)
        }
        if let month {
            BentoTile {
                HStack {
                    Text(DateFormatter().monthSymbols[month])
                    Spacer()
                    ForEach(Array(shown.enumerated()), id: \.offset) { index, y in
                        Text("\(String(y)): \(skinMoney(Dividends.year(data.events, y, rates: data.rates).months[month].usdNet.amount, "USD"))")
                            .font(.system(size: 13, weight: .bold)).foregroundStyle(colors[index])
                    }
                }
            }
        }
        if !period.positions.isEmpty {
            BentoList(items: period.positions) { p in
                Button { onPayer(p.ticker, p.currency) } label: {
                    HStack(spacing: 12) {
                        TickerTile(ticker: p.ticker, size: 38)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(p.ticker).bold()
                            Text(Strings.dividendsLine(skinMoney(p.amounts.gross, p.currency), skinMoney(p.amounts.tax, p.currency))).font(.system(size: 12)).foregroundStyle(BentoColors.muted)
                        }
                        Spacer()
                        Text(skinMoney(p.amounts.net, p.currency)).bold()
                        Text("›").foregroundStyle(BentoColors.muted)
                    }
                    .monospacedDigit()
                    .padding(.vertical, 11)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        Text(note(period)).font(.system(size: 11)).foregroundStyle(BentoColors.muted).padding(.leading, 4)
    }

    private func note(_ period: YearDividends) -> String {
        let missing = [period.usdGross, period.usdTax, period.usdNet].reduce(Set<String>()) { $0.union($1.missingCurrencies) }
        var parts = [Strings.headerApproximate]
        if let date = period.rateDate { parts.append(Strings.headerRateDate(date)) }
        if !missing.isEmpty { parts.append(Strings.dividendsIncomplete(missing.sorted().joined(separator: ", "))) }
        return parts.joined(separator: " · ")
    }
}

/// Bento's Activity (Android `BentoActivity`).
struct BentoActivity: View {
    @ObservedObject var state: ActivityState
    let onImport: () -> Void

    var body: some View {
        let view = state.view
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                BentoHeader(title: Strings.calmTabActivity)
                if view.hasAny == false {
                    BentoTile(action: onImport) {
                        Text(Strings.operationsEmpty).foregroundStyle(BentoColors.muted)
                        Text(Strings.calmAddReports + " ›").bold().foregroundStyle(BentoColors.violet).padding(.top, 8)
                    }
                } else {
                    TextField(Strings.calmSearch, text: Binding(get: { view.query }, set: state.setQuery))
                        .textInputAutocapitalization(.characters).autocorrectionDisabled()
                        .padding(14).background(BentoColors.tile, in: RoundedRectangle(cornerRadius: 16))
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(OperationFilter.allCases, id: \.self) { filter in
                                BentoPill(text: OperationDisplay.filterLabel(filter), active: view.filters.contains(filter)) { state.toggle(filter) }
                            }
                        }
                    }
                    .padding(.vertical, 10)
                    if view.items.isEmpty && view.hasAny == true {
                        BentoTile(action: state.clear) {
                            Text(Strings.operationsNoMatch).foregroundStyle(BentoColors.muted)
                            Text(Strings.operationsClearAll).bold().foregroundStyle(BentoColors.violet).padding(.top, 6)
                        }
                    }
                    ForEach(view.items, id: \.id) { operation in
                        HStack(spacing: 12) {
                            TickerTile(ticker: operation.ticker, size: 38)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(OperationDisplay.typeLabel(operation.eventType)).bold()
                                Text([Formats.date(operation.date), OperationDisplay.details(operation)].compactMap { $0 }.joined(separator: " · "))
                                    .font(.system(size: 12)).foregroundStyle(BentoColors.muted)
                            }
                            Spacer()
                            Text(OperationDisplay.amount(operation)).bold().foregroundStyle(operation.eventType == "DIVIDEND" ? BentoColors.gain : BentoColors.ink)
                        }
                        .monospacedDigit()
                        .padding(.horizontal, 14).padding(.vertical, 11)
                        .background(BentoColors.tile)
                        // Load the next page shortly before the end of the list.
                        .onAppear { if operation.id == view.items.dropLast(20).last?.id || operation.id == view.items.last?.id { state.loadMore() } }
                        Divider().overlay(BentoColors.line)
                    }
                }
                Color.clear.frame(height: bentoTabBarRoom)
            }
            .padding(.horizontal, 16)
        }
        .onAppear { state.reload() }
    }
}

/// Import, status badges and the way into Settings (Android `BentoData`, mobile-skin-bento "Data").
struct BentoData: View {
    @ObservedObject var states: AppStates
    let onImport: () -> Void
    let onReports: () -> Void
    let onSettings: () -> Void

    var body: some View {
        DataContent(data: states.data, portfolio: states.portfolio, onImport: onImport, onReports: onReports, onSettings: onSettings)
    }
}

private struct DataContent: View {
    @ObservedObject var data: DataState
    @ObservedObject var portfolio: PortfolioState
    let onImport: () -> Void
    let onReports: () -> Void
    let onSettings: () -> Void
    @AppStorage(UiPreferenceKey.broker) private var broker: String?

    var body: some View {
        let loaded: LoadedPortfolio? = { if case .loaded(let value) = portfolio.ui { return value } else { return nil } }()
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                BentoHeader(title: Strings.bentoTabData)
                Button(action: onImport) {
                    VStack(spacing: 4) {
                        Text("+").font(.system(size: 30)).foregroundStyle(.white).frame(width: 56, height: 56).background(BentoColors.violet, in: RoundedRectangle(cornerRadius: 18))
                        Text(Strings.bentoAddReports).bold().padding(.top, 8)
                        Text(BrokerId.resolve(broker).map { Strings.bentoAddReportsHint($0.format) } ?? Strings.bentoAddReportsChoose)
                            .font(.system(size: 13)).foregroundStyle(BentoColors.muted)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(24)
                    .background(BentoColors.violet.opacity(0.06), in: RoundedRectangle(cornerRadius: 22))
                    .overlay(RoundedRectangle(cornerRadius: 22).stroke(Color(hex: 0x3A4050), lineWidth: 1.5))
                }
                .buttonStyle(.plain)
                VStack(spacing: 0) {
                    badge(Strings.bentoBackup, data.keyStatus == .ready ? (data.importReady ? (Strings.bentoOn, true) : (Strings.bentoFolderMissing, false)) : (Strings.bentoOff, false))
                    badge(Strings.bentoUsPrices, loaded.map { $0.priceSource == .finnhub ? (Strings.bentoFinnhub, true) : (Strings.bentoYahooNoKey, false) } ?? ("—", false))
                    badge(Strings.bentoEuPrices, (Strings.bentoYahooDelayed, false))
                    badge(Strings.bentoReports, (data.history.map { String($0.count) } ?? "—", true), action: onReports)
                    badge(Strings.bentoFxRates, (loaded?.rates.map { Strings.bentoNbp($0.effectiveDate) } ?? "—", true), last: true)
                }
                .padding(.horizontal, 14).padding(.vertical, 4)
                .background(BentoColors.tile, in: RoundedRectangle(cornerRadius: 22))
                VStack(spacing: 0) {
                    entry(Strings.bentoSettingsEntry, action: onSettings)
                    Divider().overlay(BentoColors.line)
                    entry(Strings.bentoDeleteEntry, color: BentoColors.loss, action: onReports)
                    Divider().overlay(BentoColors.line)
                    entry(Strings.bentoVersion(version), action: onSettings)
                }
                .padding(.horizontal, 14).padding(.vertical, 4)
                .background(BentoColors.tile, in: RoundedRectangle(cornerRadius: 22))
                Color.clear.frame(height: bentoTabBarRoom)
            }
            .padding(.horizontal, 16)
        }
        .onAppear { data.reload() }
    }

    @ViewBuilder
    private func badge(_ label: String, _ value: (String, Bool), last: Bool = false, action: (() -> Void)? = nil) -> some View {
        let tint = value.1 ? BentoColors.gain : BentoColors.amber
        let row = HStack {
            Text(label).font(.system(size: 14))
            Spacer()
            Text(value.0).font(.system(size: 12, weight: .bold)).foregroundStyle(tint)
                .padding(.horizontal, 10).padding(.vertical, 4).background(tint.opacity(0.15), in: Capsule())
            if action != nil { Text("›").foregroundStyle(BentoColors.muted) }
        }
        .padding(.vertical, 12)
        .contentShape(Rectangle())
        if let action { Button(action: action) { row }.buttonStyle(.plain) } else { row }
        if !last { Divider().overlay(BentoColors.line) }
    }

    private func entry(_ label: String, color: Color = BentoColors.ink, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(label).foregroundStyle(color)
                Spacer()
                Text("›").foregroundStyle(BentoColors.muted)
            }
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// All dividend and tax bookings of one payer, newest first, with totals (Android `BentoPayer`, mobile-payer-history).
struct BentoPayer: View {
    @ObservedObject var state: IncomeState
    let ticker: String
    let currency: String
    let onBack: () -> Void

    var body: some View {
        let history = Dividends.history(state.data?.events ?? [], ticker: ticker, currency: currency)
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                Button("‹ " + Strings.calmBack, action: onBack).foregroundStyle(BentoColors.muted).padding(.vertical, 12)
                HStack(spacing: 12) {
                    TickerTile(ticker: ticker, size: 48)
                    VStack(alignment: .leading) {
                        Text(ticker + " · " + Strings.payerHistoryTitle).font(.system(size: 20, weight: .bold))
                        Text(Strings.payerHistorySubtitle(currency, history.lines.count)).font(.system(size: 13)).foregroundStyle(BentoColors.muted)
                    }
                }
                BentoTile {
                    BentoTileLabel(text: Strings.payerHistoryTotals)
                    ForEach(Array([(Strings.dividendsGross, history.total.gross), (Strings.dividendsTax, history.total.tax), (Strings.dividendsNet, history.total.net)].enumerated()), id: \.offset) { index, line in
                        HStack {
                            Text(line.0).foregroundStyle(BentoColors.muted)
                            Spacer()
                            Text(skinMoney(line.1, currency)).fontWeight(index == 2 ? .bold : .regular).monospacedDigit()
                        }
                        .padding(.top, 8)
                    }
                }
                if !history.lines.isEmpty {
                    BentoList(items: history.lines) { line in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(line.date).bold()
                                Text(line.amounts.gross.signum == 0 ? Strings.payerHistoryTaxOnly(skinMoney(line.amounts.tax, currency))
                                     : Strings.dividendsLine(skinMoney(line.amounts.gross, currency), skinMoney(line.amounts.tax, currency)))
                                    .font(.system(size: 12)).foregroundStyle(BentoColors.muted)
                            }
                            Spacer()
                            Text(skinMoney(line.amounts.net, currency)).bold().monospacedDigit()
                        }
                        .padding(.vertical, 11)
                    }
                }
                Color.clear.frame(height: bentoTabBarRoom)
            }
            .padding(.horizontal, 16)
        }
        .foregroundStyle(BentoColors.ink)
        .onAppear { state.reload() }
    }
}
