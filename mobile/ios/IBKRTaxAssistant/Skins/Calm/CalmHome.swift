import SwiftUI

private enum CalmSort: Hashable {
    case value
    case today
    case name
}

private enum CalmSheet: Identifiable {
    case view
    case info
    case totalReturn
    case position(PositionPerformance)
    case history(ticker: String, currency: String)

    var id: String {
        switch self {
        case .view: return "view"
        case .info: return "info"
        case .totalReturn: return "return"
        case .position(let p): return "position-\(p.ticker)-\(p.currency)-\(p.row?.holding.isin ?? "sold")"
        case .history(let ticker, let currency): return "history-\(ticker)-\(currency)"
        }
    }
}

/// Calm's Home (Android `CalmHome`): hero value, today chip, total return, holdings; details open as sheets.
struct CalmHome: View {
    @ObservedObject var state: PortfolioState
    @ObservedObject var income: IncomeState
    let onImport: () -> Void
    let onSettings: () -> Void

    var body: some View {
        Group {
            switch state.ui {
            case .loading:
                message { Text(Strings.loading).foregroundStyle(CalmColors.muted) }.task { state.refresh() }
            // Settings stays reachable in every state; it holds "Erase all data", the way out when storage cannot be opened.
            case .storageUnavailable:
                message { Text(Strings.storageUnavailable) }
            case .incomplete(let ticker, let date):
                message { Text(Strings.portfolioIncomplete(ticker, date)) }
            case .noReports:
                VStack(alignment: .leading, spacing: 10) {
                    CalmTopRow(onImport: onImport, onSettings: onSettings)
                    Spacer().frame(height: 40)
                    Text(Strings.portfolioEmptyTitle).font(.title2.bold())
                    Text(Strings.portfolioEmptyBody).foregroundStyle(CalmColors.muted)
                    Button(Strings.calmAddReports, action: onImport).buttonStyle(.borderedProminent)
                    Spacer()
                }
                .padding(22)
            case .loaded(let loaded):
                CalmLoaded(data: loaded, progress: state.progress.map { "\($0.done) / \($0.total)" }, income: income,
                           onRefresh: state.refresh, onImport: onImport, onSettings: onSettings)
            }
        }
        .foregroundStyle(CalmColors.ink)
        .background(Color.white)
    }
}

extension CalmHome {
    /// A centred message under the avatar and "+" buttons.
    private func message<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack {
            CalmTopRow(onImport: onImport, onSettings: onSettings)
            Spacer()
            content().multilineTextAlignment(.center)
            Spacer()
        }
        .padding(22)
    }
}

struct CalmTopRow: View {
    let onImport: () -> Void
    let onSettings: () -> Void

    var body: some View {
        HStack {
            Button(action: onSettings) {
                Image(systemName: "person.fill").foregroundStyle(CalmColors.ink).frame(width: 38, height: 38).background(CalmColors.card, in: Circle())
            }
            .accessibilityLabel(Strings.settingsTitle)
            Spacer()
            Button(action: onImport) {
                Image(systemName: "plus").foregroundStyle(.white).frame(width: 38, height: 38).background(CalmColors.accent, in: Circle())
            }
            .accessibilityLabel(Strings.calmAddReports)
        }
        .padding(.top, 6)
        .padding(.bottom, 16)
    }
}

private struct CalmLoaded: View {
    let data: LoadedPortfolio
    let progress: String?
    @ObservedObject var income: IncomeState
    let onRefresh: () -> Void
    let onImport: () -> Void
    let onSettings: () -> Void

    @State private var sort = CalmSort.value
    @State private var sheet: CalmSheet?
    @AppStorage("usd_view") private var usd = false
    @AppStorage("show_sold_positions") private var showSold = false

    var body: some View {
        let totals = data.totals
        let sold = data.performance.filter(\.isSold)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                CalmTopRow(onImport: onImport, onSettings: onSettings)
                HStack(spacing: 4) {
                    Text(Strings.calmPortfolioValue).font(.system(size: 14)).foregroundStyle(CalmColors.muted)
                    Button { sheet = .info } label: { Image(systemName: "info.circle").font(.system(size: 14)).foregroundStyle(CalmColors.muted) }
                        .accessibilityLabel(Strings.calmAboutValue)
                }
                heroValue(skinMoney(totals.marketValue, "USD"))
                let day = totals.dailyPnl
                CalmChip(text: "\(day.signum >= 0 ? "▲" : "▼") \(skinMoney(day.magnitude, "USD")) · \(Formats.number(totals.dailyPnlPercent?.magnitude ?? .zero, 2, 2))% \(Strings.calmToday)",
                         positive: day.signum >= 0)
                Text(notes).font(.system(size: 12)).foregroundStyle(CalmColors.muted).padding(.top, 8)
                Button { sheet = .totalReturn } label: { totalReturnLine }.buttonStyle(.plain).padding(.vertical, 14)
                priceLine
                HStack {
                    Text(Strings.calmHoldings).font(.system(size: 18, weight: .bold))
                    Spacer()
                    Button(Strings.calmView + " ▾") { sheet = .view }
                }
                .padding(.top, 10)
                let rows = sortedRows
                if rows.isEmpty { Text(Strings.portfolioNoHoldings).foregroundStyle(CalmColors.muted).padding(.vertical, 16) }
                ForEach(rows, id: \.calmKey) { row in
                    Button { open(row) } label: { CalmHoldingRow(row: row) }.buttonStyle(.plain)
                    Divider().overlay(CalmColors.line)
                }
                if showSold && !sold.isEmpty {
                    Text(Strings.calmSold).font(.system(size: 13, weight: .semibold)).foregroundStyle(CalmColors.muted).padding(.top, 14)
                    ForEach(sold, id: \.ticker) { position in
                        Button { sheet = .position(position) } label: { CalmSoldRow(position: position) }.buttonStyle(.plain)
                        Divider().overlay(CalmColors.line)
                    }
                }
            }
            .padding(.horizontal, 22)
            .padding(.bottom, 24)
        }
        .refreshable { onRefresh() }
        .onAppear { income.reload() }
        .sheet(item: $sheet) { current in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) { content(for: current) }
                    .padding(22)
                    .foregroundStyle(CalmColors.ink)
            }
            .background(Color.white)
            .presentationDetents([.medium, .large])
        }
    }

    private func heroValue(_ whole: String) -> some View {
        let dot = whole.lastIndex(of: ".") ?? whole.endIndex
        return (Text(String(whole[..<dot])).font(.system(size: 42, weight: .bold))
            + Text(String(whole[dot...])).font(.system(size: 24, weight: .bold)).foregroundColor(CalmColors.muted))
            .padding(.vertical, 4)
    }

    private var notes: String {
        var parts = [Strings.headerApproximate]
        if let date = data.totals.rateDate { parts.append(Strings.headerRateDate(date)) }
        if !data.totals.isComplete { parts.append(Strings.headerIncomplete(data.totals.missingValues, data.rows.count)) }
        return parts.joined(separator: " · ")
    }

    private var totalReturnLine: Text {
        let profit = data.summary.totalProfit
        let amount = skinMoney(profit.amount, "USD", signed: true) + (data.summary.totalProfitPercent.map { " (" + skinPercent($0) + ")" } ?? "")
        return Text(Strings.calmTotalReturn + " ").foregroundColor(CalmColors.muted)
            + Text(amount).bold().foregroundColor(calmSignColor(profit.amount))
            + Text(" " + Strings.calmInclSalesDividends + " ›").foregroundColor(CalmColors.muted)
    }

    private var priceLine: some View {
        let text: String
        switch data.prices {
        case nil: text = Strings.pricesRefreshing + (progress.map { " \($0)" } ?? "")
        case .failed(let reason, _)?: text = Strings.pricesFailed(Self.failure(reason))
        default:
            text = data.priceSource == .yahoo ? Strings.pricesFromYahoo : (data.hasEuropean ? Strings.pricesEuropeFromYahoo : Strings.calmPricesUpdated)
        }
        return HStack {
            Text(text).font(.system(size: 12)).foregroundStyle(CalmColors.muted)
            Spacer()
            if data.prices != nil { Button("↻ " + Strings.pricesRefresh, action: onRefresh).font(.system(size: 13)) }
        }
    }

    static func failure(_ reason: PriceFailure) -> String {
        switch reason {
        case .offline: return Strings.pricesFailureOffline
        case .invalidKey: return Strings.pricesFailureInvalidKey
        case .rateLimited: return Strings.pricesFailureRateLimited
        case .providerError: return Strings.pricesFailureProvider
        }
    }

    private var sortedRows: [PositionRow] {
        let rows = usd ? MainPage.inUsd(data.rows, rates: data.rates) : data.rows
        switch sort {
        case .value:
            func usdValue(_ row: PositionRow) -> ExactDecimal {
                row.marketValue.flatMap { UsdConverter.toUsd($0, currency: row.displayCurrency, rates: data.rates) } ?? .zero
            }
            return FifoInventory.stableSorted(rows) { usdValue($0).compare(usdValue($1)) > 0 }
        case .today:
            return FifoInventory.stableSorted(rows) { a, b in
                switch (a.dailyChangePercent, b.dailyChangePercent) {
                case (let x?, let y?): return x.compare(y) > 0
                case (_?, nil): return true
                default: return false
                }
            }
        case .name:
            return FifoInventory.stableSorted(rows) { $0.ticker < $1.ticker }
        }
    }

    private func open(_ row: PositionRow) {
        if let position = data.performance.first(where: { $0.row?.holding == row.holding }) { sheet = .position(position) }
    }

    @ViewBuilder
    private func content(for current: CalmSheet) -> some View {
        switch current {
        case .view: viewSheet
        case .info:
            Text(Strings.calmAboutValue).font(.system(size: 20, weight: .bold))
            Text(Strings.calmAboutValueBody(data.rates?.effectiveDate ?? "—")).padding(.vertical, 10)
            Text(Strings.calmAboutSources).font(.system(size: 13)).foregroundStyle(CalmColors.muted)
        case .totalReturn: returnSheet
        case .position(let position): CalmPositionSheet(p: position) { sheet = .history(ticker: position.ticker, currency: position.currency) }
        case .history(let ticker, let currency): CalmPayerHistory(history: Dividends.history(income.data?.events ?? data.dividends, ticker: ticker, currency: currency))
        }
    }

    @ViewBuilder
    private var viewSheet: some View {
        Text(Strings.calmView).font(.system(size: 20, weight: .bold)).padding(.bottom, 8)
        CalmOptionRow(label: Strings.calmSortBy) {
            Picker("", selection: $sort) {
                Text(Strings.calmSortValue).tag(CalmSort.value)
                Text(Strings.calmSortToday).tag(CalmSort.today)
                Text(Strings.calmSortName).tag(CalmSort.name)
            }
            .pickerStyle(.segmented).frame(width: 200)
        }
        CalmOptionRow(label: Strings.calmAmountsIn) {
            Picker("", selection: $usd) {
                Text(Strings.viewOwnCurrency).tag(false)
                Text(Strings.viewUsd).tag(true)
            }
            .pickerStyle(.segmented).frame(width: 200)
        }
        CalmOptionRow(label: Strings.calmShowSold) { Toggle("", isOn: $showSold).labelsHidden() }
        if let date = data.rates?.effectiveDate { Text(Strings.usdViewNote(date)).font(.system(size: 13)).foregroundStyle(CalmColors.muted).padding(.top, 10) }
    }

    @ViewBuilder
    private var returnSheet: some View {
        let rates = data.rates
        let sum = { (values: [(ExactDecimal?, String)]) in
            values.reduce(ExactDecimal.zero) { total, part in
                total.adding(part.0.flatMap { UsdConverter.toUsd($0, currency: part.1, rates: rates) } ?? .zero)
            }
        }
        let priceGain = sum(data.performance.filter { !$0.isSold }.map { ($0.priceGain, $0.currency) })
        let sales = sum(data.performance.map { ($0.realizedGain, $0.currency) })
        let dividends = sum(data.performance.map { ($0.dividends.net, $0.currency) })
        let summary = data.summary
        Text(Strings.calmTotalReturn).font(.system(size: 20, weight: .bold))
        Text(skinMoney(summary.totalProfit.amount, "USD", signed: true)).font(.system(size: 32, weight: .bold))
            .foregroundStyle(calmSignColor(summary.totalProfit.amount)).padding(.vertical, 8)
        Text(Strings.cardInvested(skinMoney(summary.invested.amount, "USD"))).foregroundStyle(CalmColors.muted)
        if !summary.totalProfit.isComplete { Text(Strings.cardIncomplete).font(.system(size: 13)).foregroundStyle(CalmColors.loss) }
        Spacer().frame(height: 12)
        CalmLegendLine(label: Strings.calmPriceGainHeld, value: skinMoney(priceGain, "USD", signed: true))
        CalmLegendLine(label: Strings.detailsSalesGain, value: skinMoney(sales, "USD", signed: true))
        CalmLegendLine(label: Strings.calmDividendsAfterTax, value: skinMoney(dividends, "USD", signed: true))
        Text(Strings.detailsNote).font(.system(size: 12)).foregroundStyle(CalmColors.muted).padding(.top, 12)
    }
}

private extension PositionRow {
    var calmKey: String { "\(ticker)-\(holding.isin)-\(currency)" }
}

private struct CalmHoldingRow: View {
    let row: PositionRow

    var body: some View {
        HStack(spacing: 12) {
            TickerTile(ticker: row.ticker)
            VStack(alignment: .leading, spacing: 2) {
                Text(row.ticker).font(.system(size: 15, weight: .semibold))
                Text([Strings.calmShares(Formats.quantity(row.holding.quantity)), row.listingExchange.isEmpty ? nil : row.listingExchange, freshness]
                    .compactMap { $0 }.joined(separator: " · "))
                    .font(.system(size: 13)).foregroundStyle(CalmColors.muted).lineLimit(1)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                if let value = row.marketValue {
                    Text(skinMoney(value, row.displayCurrency)).font(.system(size: 15, weight: .semibold))
                    if let percent = row.dailyChangePercent { Text(skinPercent(percent)).font(.system(size: 13, weight: .semibold)).foregroundStyle(calmSignColor(percent)) }
                } else {
                    Text("—").font(.system(size: 15, weight: .semibold))
                    Text(!row.inPriceScope ? Strings.priceOutOfScopeShort : (row.rateMissing ? Strings.priceNoRateShort : Strings.priceUnavailableShort))
                        .font(.system(size: 12)).foregroundStyle(CalmColors.muted)
                }
            }
        }
        .monospacedDigit()
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }

    private var freshness: String? {
        switch row.price?.freshness {
        case .delayed?: return Strings.priceDelayedShort
        case .latestClose?: return Strings.priceCloseShort
        case .stale?: return Strings.priceStaleShort
        default: return nil
        }
    }
}

private struct CalmSoldRow: View {
    let position: PositionPerformance

    var body: some View {
        HStack(spacing: 12) {
            TickerTile(ticker: position.ticker)
            VStack(alignment: .leading) {
                Text(position.ticker).font(.system(size: 15, weight: .semibold))
                Text(Strings.soldPosition(position.currency)).font(.system(size: 13)).foregroundStyle(CalmColors.muted)
            }
            Spacer()
            Text(position.total.map { skinMoney($0, position.currency, signed: true) } ?? "—").font(.system(size: 15, weight: .semibold))
                .foregroundStyle(calmSignColor(position.total))
        }
        .padding(.vertical, 12)
        .contentShape(Rectangle())
    }
}

private struct CalmOptionRow<Control: View>: View {
    let label: String
    @ViewBuilder let control: Control

    var body: some View {
        HStack {
            Text(label)
            Spacer()
            control
        }
        .padding(.vertical, 10)
        Divider().overlay(CalmColors.line)
    }
}

struct CalmLegendLine: View {
    let label: String
    let value: String
    var dot: Color?

    var body: some View {
        HStack(spacing: 8) {
            if let dot { RoundedRectangle(cornerRadius: 3).fill(dot).frame(width: 10, height: 10) }
            Text(label)
            Spacer()
            Text(value).fontWeight(.semibold).monospacedDigit()
        }
        .padding(.vertical, 5)
    }
}

/// One position in Calm (Android `PositionSheet`): price, today, four stats and the total return split.
private struct CalmPositionSheet: View {
    let p: PositionPerformance
    let onHistory: () -> Void

    var body: some View {
        let cur = p.currency
        HStack(spacing: 12) {
            TickerTile(ticker: p.ticker, size: 48)
            VStack(alignment: .leading) {
                Text(p.ticker).font(.system(size: 20, weight: .bold))
                Text([p.row?.listingExchange.isEmpty == false ? p.row?.listingExchange : nil, cur].compactMap { $0 }.joined(separator: " · "))
                    .font(.system(size: 13)).foregroundStyle(CalmColors.muted)
            }
        }
        if let row = p.row {
            Text(row.price.map { skinMoney($0.quote.price, cur) } ?? "—").font(.system(size: 32, weight: .bold)).padding(.top, 14)
            if let change = row.dailyChange {
                CalmChip(text: "\(change.signum >= 0 ? "▲" : "▼") \(skinMoney(change.magnitude, cur)) · \(row.dailyChangePercent.map { Formats.number($0.magnitude, 2, 2) } ?? "—")% \(Strings.calmToday)",
                         positive: change.signum >= 0)
            }
            Grid(horizontalSpacing: 10, verticalSpacing: 10) {
                GridRow {
                    CalmStat(label: Strings.detailsQuantity, value: Formats.quantity(row.holding.quantity))
                    CalmStat(label: Strings.detailsAverage, value: skinMoney(row.holding.averagePrice, cur))
                }
                GridRow {
                    CalmStat(label: Strings.detailsInvested, value: skinMoney(p.invested, cur))
                    CalmStat(label: Strings.detailsValue, value: row.marketValue.map { skinMoney($0, cur) } ?? Strings.detailsUnavailable)
                }
            }
            .padding(.top, 14)
        } else {
            Text(Strings.detailsSold(cur)).foregroundStyle(CalmColors.muted).padding(.top, 12)
        }
        HStack {
            Text(Strings.calmTotalReturn).bold()
            Spacer()
            Text(p.total.map { skinMoney($0, cur, signed: true) + (p.totalPercent.map { " · " + skinPercent($0) } ?? "") } ?? Strings.detailsUnavailable)
                .bold().foregroundStyle(calmSignColor(p.total))
        }
        .padding(.top, 16)
        let parts: [(ExactDecimal?, Color, String)] = [(p.priceGain, CalmColors.accent, Strings.detailsPriceGain),
                                                      (p.realizedGain, Color(hex: 0x8FB0FF), Strings.detailsSalesGain),
                                                      (p.dividends.net, Color(hex: 0xC8D8FF), Strings.detailsDividends)]
        let positive = parts.compactMap { part in part.0.flatMap { $0.signum > 0 ? (Double($0.description) ?? 0, part.1) : nil } }
        if !positive.isEmpty {
            GeometryReader { geometry in
                let total = positive.reduce(0) { $0 + $1.0 }
                HStack(spacing: 0) {
                    ForEach(Array(positive.enumerated()), id: \.offset) { _, part in
                        Rectangle().fill(part.1).frame(width: geometry.size.width * part.0 / max(total, 0.0001))
                    }
                }
            }
            .frame(height: 10)
            .clipShape(RoundedRectangle(cornerRadius: 6))
            .padding(.vertical, 10)
        }
        ForEach(Array(parts.enumerated()), id: \.offset) { _, part in
            CalmLegendLine(label: part.2, value: part.0.map { skinMoney($0, cur, signed: true) } ?? Strings.detailsUnavailable, dot: part.1)
        }
        Text(Strings.dividendsLine(skinMoney(p.dividends.gross, cur), skinMoney(p.dividends.tax, cur))).font(.system(size: 12)).foregroundStyle(CalmColors.muted)
        if p.dividends.gross.signum != 0 || p.dividends.tax.signum != 0 {
            Button(Strings.payerHistoryTitle + " ›", action: onHistory).fontWeight(.semibold).padding(.vertical, 8)
        }
        if !p.realizedComplete { Text(Strings.detailsSalesIncomplete).font(.system(size: 12)).foregroundStyle(CalmColors.muted) }
        Text(Strings.detailsNote).font(.system(size: 12)).foregroundStyle(CalmColors.muted).padding(.top, 10)
    }
}

private struct CalmStat: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.system(size: 12)).foregroundStyle(CalmColors.muted)
            Text(value).font(.system(size: 17, weight: .bold)).monospacedDigit()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(CalmColors.card, in: RoundedRectangle(cornerRadius: 16))
    }
}

/// A payer's bookings over all years, newest first, with totals (mobile-payer-history), in Calm's look.
struct CalmPayerHistory: View {
    let history: PayerHistory

    var body: some View {
        let cur = history.currency
        HStack(spacing: 12) {
            TickerTile(ticker: history.ticker)
            VStack(alignment: .leading) {
                Text(history.ticker + " · " + Strings.payerHistoryTitle).font(.system(size: 20, weight: .bold))
                Text(Strings.payerHistorySubtitle(cur, history.lines.count)).font(.system(size: 13)).foregroundStyle(CalmColors.muted)
            }
        }
        Text(Strings.calmNet(skinMoney(history.total.net, cur))).font(.system(size: 28, weight: .bold)).padding(.top, 16)
        Text(Strings.dividendsLine(skinMoney(history.total.gross, cur), skinMoney(history.total.tax, cur))).foregroundStyle(CalmColors.muted).padding(.bottom, 8)
        ForEach(history.lines, id: \.date) { line in
            HStack {
                VStack(alignment: .leading) {
                    Text(line.date).fontWeight(.semibold)
                    Text(line.amounts.gross.signum == 0 ? Strings.payerHistoryTaxOnly(skinMoney(line.amounts.tax, cur))
                         : Strings.dividendsLine(skinMoney(line.amounts.gross, cur), skinMoney(line.amounts.tax, cur)))
                        .font(.system(size: 12)).foregroundStyle(CalmColors.muted)
                }
                Spacer()
                Text(skinMoney(line.amounts.net, cur)).fontWeight(.semibold).monospacedDigit()
            }
            .padding(.vertical, 12)
            Divider().overlay(CalmColors.line)
        }
    }
}
