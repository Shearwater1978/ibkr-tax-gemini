import SwiftUI

/**
 * Classic Portfolio tab, ported from the Android `PortfolioScreen`: summary cards, price status,
 * top movers, P&L and currency switches, sortable positions table, sold positions, and the
 * position details and payer history sheets.
 */
struct PortfolioView: View {
    @ObservedObject var state: PortfolioState
    let onImportReport: () -> Void

    var body: some View {
        switch state.ui {
        case .loading:
            Text(Strings.loading).task { state.refresh() }
        case .noReports:
            VStack(spacing: 16) {
                Text(Strings.portfolioEmptyTitle).font(.title2)
                Text(Strings.portfolioEmptyBody).multilineTextAlignment(.center)
                Button(Strings.actionImportReport, action: onImportReport).buttonStyle(.borderedProminent)
            }
            .padding(24)
        case .storageUnavailable:
            Text(Strings.storageUnavailable).multilineTextAlignment(.center).padding(24)
        case .incomplete(let ticker, let date):
            Text(Strings.portfolioIncomplete(ticker, date)).multilineTextAlignment(.center).padding(24)
        case .loaded(let loaded):
            Positions(state: loaded, progress: state.progress, onRefresh: state.refresh)
        }
    }
}

private extension PositionRow {
    /// Rows in the USD view are converted copies; the holding identifies the position.
    var identity: String { "\(ticker)-\(holding.isin)-\(currency)" }
}

/// What a sheet shows: one position's breakdown, or one payer's dividend history.
private enum SheetTarget: Identifiable {
    case details(PositionPerformance)
    case history(ticker: String, currency: String)

    var id: String {
        switch self {
        case .details(let p): return "details-\(p.ticker)-\(p.currency)-\(p.row?.holding.isin ?? "sold")"
        case .history(let ticker, let currency): return "history-\(ticker)-\(currency)"
        }
    }
}

private struct Positions: View {
    let state: LoadedPortfolio
    let progress: RefreshProgress?
    let onRefresh: () -> Void

    @State private var sort = SortOrder()
    @State private var mode = PnlMode.daily
    @State private var sheet: SheetTarget?
    // Not sensitive: only which view is shown.
    @AppStorage("usd_view") private var usdView = false
    @AppStorage("show_sold_positions") private var showSold = false

    var body: some View {
        let shown = MainPage.sorted(usdView ? MainPage.inUsd(state.rows, rates: state.rates) : state.rows, order: sort, mode: mode)
        let sold = state.performance.filter(\.isSold)
        List {
            Section {
                SummaryCards(summary: state.summary, totals: state.totals)
                Text(notes).font(.footnote).foregroundStyle(.secondary)
                PriceStatus(prices: state.prices, source: state.priceSource, hasEuropean: state.hasEuropean, progress: progress, onRefresh: onRefresh)
            } header: {
                Text(Strings.portfolioTitle)
            }
            if let movers = TopMoversRanking.rank(state.rows) {
                TopMoversSection(movers: movers, onOpen: open)
            }
            Section {
                Picker("", selection: $mode) {
                    Text(Strings.pnlDaily).tag(PnlMode.daily)
                    Text(Strings.pnlUnrealized).tag(PnlMode.unrealized)
                }
                .pickerStyle(.segmented)
                Picker("", selection: $usdView) {
                    Text(Strings.viewOwnCurrency).tag(false)
                    Text(Strings.viewUsd).tag(true)
                }
                .pickerStyle(.segmented)
                if usdView {
                    Text(state.rates.map { Strings.usdViewNote($0.effectiveDate) } ?? Strings.usdViewNoRates).font(.footnote).foregroundStyle(.secondary)
                }
                ColumnHeaders(sort: $sort)
                if shown.isEmpty { Text(Strings.portfolioNoHoldings) }
                ForEach(shown, id: \.identity) { row in
                    PositionLine(row: row, mode: mode).contentShape(Rectangle()).onTapGesture { open(row) }
                }
            }
            if !sold.isEmpty {
                Section {
                    Toggle(Strings.showSoldPositions(sold.count), isOn: $showSold)
                    if showSold {
                        ForEach(sold, id: \.soldIdentity) { position in
                            SoldLine(position: position).contentShape(Rectangle()).onTapGesture { sheet = .details(position) }
                        }
                    }
                }
            }
        }
        .listStyle(.plain)
        .refreshable { onRefresh() }
        .sheet(item: $sheet) { target in
            switch target {
            case .details(let position):
                PositionDetailsSheet(position: position, onHistory: { sheet = .history(ticker: position.ticker, currency: position.currency) })
            case .history(let ticker, let currency):
                PayerHistorySheet(events: state.dividends, ticker: ticker, currency: currency)
            }
        }
    }

    /// Rows in the USD view are converted copies; the holding identifies the position.
    private func open(_ row: PositionRow) {
        if let position = state.performance.first(where: { $0.row?.holding == row.holding }) { sheet = .details(position) }
    }

    private var notes: String {
        var parts = [Strings.headerApproximate]
        if let date = state.totals.rateDate { parts.append(Strings.headerRateDate(date)) }
        if !state.totals.isComplete { parts.append(Strings.headerIncomplete(state.totals.missingValues, state.rows.count)) }
        return parts.joined(separator: " · ")
    }
}

private extension PositionPerformance {
    var soldIdentity: String { "\(ticker)-\(currency)" }
}

/// Value, total profit, today and 12-month dividends, in approximate USD (mobile-position-performance spec).
private struct SummaryCards: View {
    let summary: PortfolioSummary
    let totals: UsdTotals

    var body: some View {
        Grid(horizontalSpacing: 8, verticalSpacing: 8) {
            GridRow {
                Card(title: Strings.cardValue, value: Formats.money(summary.value.amount, "USD"),
                     detail: Strings.cardInvested(Formats.money(summary.invested.amount, "USD")), incomplete: !summary.value.isComplete)
                Card(title: Strings.cardProfit, value: Formats.signedMoney(summary.totalProfit.amount, "USD"),
                     detail: summary.totalProfitPercent.map(Formats.signedPercent), incomplete: !summary.totalProfit.isComplete,
                     color: signColor(summary.totalProfit.amount))
            }
            GridRow {
                Card(title: Strings.cardToday, value: Formats.signedMoney(totals.dailyPnl, "USD"),
                     detail: totals.dailyPnlPercent.map(Formats.signedPercent), incomplete: totals.missingDailyPnl > 0, color: signColor(totals.dailyPnl))
                Card(title: Strings.cardDividends, value: Formats.money(summary.dividends12Months.amount, "USD"),
                     detail: Strings.cardDividendsNet, incomplete: !summary.dividends12Months.isComplete)
            }
        }
    }

    private struct Card: View {
        let title: String
        let value: String
        let detail: String?
        let incomplete: Bool
        var color: Color = .primary

        var body: some View {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.caption)
                Text(value).font(.headline).foregroundStyle(color).lineLimit(1).minimumScaleFactor(0.7)
                if let detail { Text(detail).font(.caption).foregroundStyle(color == .primary ? .secondary : color) }
                // Its own line, so it is clear the headline value is incomplete, not the detail.
                if incomplete { Text(Strings.cardIncomplete).font(.caption).foregroundStyle(.red) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(10)
            .background(.quaternary.opacity(0.5), in: RoundedRectangle(cornerRadius: 10))
        }
    }
}

/// Today's top gainers and losers in their own currency (mobile-top-movers spec); three each until expanded.
private struct TopMoversSection: View {
    let movers: TopMovers
    let onOpen: (PositionRow) -> Void
    @State private var expanded = false

    var body: some View {
        let shown = expanded ? TopMoversRanking.maxPerList : 3
        Section {
            list(Strings.moversGainers, Array(movers.gainers.prefix(shown)), empty: Strings.moversNoGainers)
            list(Strings.moversLosers, Array(movers.losers.prefix(shown)), empty: Strings.moversNoLosers)
            if movers.gainers.count > 3 || movers.losers.count > 3 {
                Button(expanded ? Strings.moversShowLess : Strings.moversShowMore) { expanded.toggle() }
            }
        } header: {
            HStack {
                Text(Strings.moversTitle)
                Spacer()
                switch movers.freshness {
                case .live: EmptyView()
                case .delayed: Text(Strings.moversDelayed)
                case .latestClose: Text(Strings.moversLatestClose)
                case .stale: Text(Strings.moversStale).foregroundStyle(lossColor)
                }
            }
        }
    }

    @ViewBuilder
    private func list(_ title: String, _ rows: [PositionRow], empty: String) -> some View {
        Text(title).font(.caption).foregroundStyle(.secondary)
        if rows.isEmpty { Text(empty).font(.footnote).foregroundStyle(.secondary) }
        ForEach(rows, id: \.identity) { row in
            HStack {
                Text(row.ticker).bold()
                Spacer()
                Text(row.marketValue.map { Formats.money($0, row.currency) } ?? "—").font(.footnote).foregroundStyle(.secondary)
                Text([row.dailyChangePercent.map(Formats.signedPercent), row.dailyPnl.map { "(" + Formats.signedNumber($0) + ")" }].compactMap { $0 }.joined(separator: " "))
                    .foregroundStyle(signColor(row.dailyChangePercent))
            }
            .monospacedDigit()
            .contentShape(Rectangle())
            .onTapGesture { onOpen(row) }
        }
    }
}

private struct SoldLine: View {
    let position: PositionPerformance

    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(position.ticker).bold()
                Text(Strings.soldPosition(position.currency)).font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Text(position.total.map { Formats.signedMoney($0, position.currency) } ?? "—").foregroundStyle(signColor(position.total)).monospacedDigit()
        }
    }
}

private struct PriceStatus: View {
    let prices: RefreshOutcome?
    let source: PriceSource
    let hasEuropean: Bool
    let progress: RefreshProgress?
    let onRefresh: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let message { Text(message).font(.footnote) }
            if prices == nil, let progress, progress.total > 0 {
                Text(progress.source == .finnhub ? Strings.pricesProgressFinnhub(progress.done, progress.total) : Strings.pricesProgressYahoo(progress.done, progress.total))
                    .font(.footnote).foregroundStyle(.secondary)
                Text(Strings.pricesProgressShown).font(.footnote).foregroundStyle(.secondary)
            }
            if prices != nil && prices != .noKey {
                Button(Strings.pricesRefresh, action: onRefresh).font(.footnote).buttonStyle(.borderless)
            }
        }
    }

    private var message: String? {
        let fromYahoo = source == .yahoo ? Strings.pricesFromYahoo : (hasEuropean ? Strings.pricesEuropeFromYahoo : nil)
        switch prices {
        case nil: return Strings.pricesRefreshing
        case .noKey?: return Strings.pricesNoKey
        case .updated?: return fromYahoo
        case .failed(let reason, _)?:
            let text: String
            switch reason {
            case .offline: text = Strings.pricesFailureOffline
            case .invalidKey: text = Strings.pricesFailureInvalidKey
            case .rateLimited: text = Strings.pricesFailureRateLimited
            case .providerError: text = Strings.pricesFailureProvider
            }
            return Strings.pricesFailed(text) + (fromYahoo.map { " " + $0 } ?? "")
        }
    }
}

private struct ColumnHeaders: View {
    @Binding var sort: SortOrder

    var body: some View {
        HStack {
            cell(Strings.columnInstrument, .symbol, alignment: .leading).frame(maxWidth: .infinity, alignment: .leading).layoutPriority(1.5)
            cell(Strings.columnLast, .lastPrice)
            cell(Strings.columnChange, .change)
            cell(Strings.columnPosition, .position)
            cell(Strings.columnPnl, .pnl)
        }
        .font(.caption)
    }

    private func cell(_ title: String, _ column: SortColumn, alignment: Alignment = .trailing) -> some View {
        let arrow = sort.column != column ? "" : (sort.descending ? " ▼" : " ▲")
        return Button(title + arrow) { sort = sort.select(column) }
            .buttonStyle(.borderless)
            .fontWeight(sort.column == column ? .bold : .regular)
            .foregroundStyle(.primary)
            .frame(maxWidth: .infinity, alignment: alignment)
    }
}

private struct PositionLine: View {
    let row: PositionRow
    let mode: PnlMode

    var body: some View {
        let pnl = mode == .daily ? row.dailyPnl : row.unrealizedPnl
        HStack(alignment: .top) {
            VStack(alignment: .leading) {
                Text(row.ticker).bold()
                Text([row.listingExchange, row.displayCurrency].filter { !$0.isEmpty }.joined(separator: " · ")).font(.caption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .layoutPriority(1.5)
            VStack(alignment: .trailing) {
                if let price = row.price {
                    Text(Formats.number(price.quote.price, 2, 4))
                    if let label = freshnessLabel(price.freshness) {
                        Text(label).font(.caption).foregroundStyle(price.freshness == .stale ? lossColor : .secondary)
                    }
                } else {
                    Text("—")
                    Text(!row.inPriceScope ? Strings.priceOutOfScopeShort : (row.rateMissing ? Strings.priceNoRateShort : Strings.priceUnavailableShort))
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
            VStack(alignment: .trailing) {
                Text(row.dailyChange.map(Formats.signedNumber) ?? "—").foregroundStyle(signColor(row.dailyChange))
                if let percent = row.dailyChangePercent { Text(Formats.signedPercent(percent)).font(.caption).foregroundStyle(signColor(percent)) }
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
            Text(Formats.quantity(row.holding.quantity)).frame(maxWidth: .infinity, alignment: .trailing)
            Text(pnl.map(Formats.signedNumber) ?? "—").foregroundStyle(signColor(pnl)).frame(maxWidth: .infinity, alignment: .trailing)
        }
        .font(.subheadline)
        .monospacedDigit()
    }

    private func freshnessLabel(_ freshness: PriceFreshness) -> String? {
        switch freshness {
        case .live: return nil
        case .delayed: return Strings.priceDelayedShort
        case .latestClose: return Strings.priceCloseShort
        case .stale: return Strings.priceStaleShort
        }
    }
}
