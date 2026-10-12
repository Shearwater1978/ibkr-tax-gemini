import SwiftUI

/**
 * Classic Portfolio tab, ported from the Android `PortfolioScreen`: approximate USD header, P&L
 * and currency switches, sortable positions table and price status. The summary cards, top movers,
 * position details and sold positions follow with the performance port.
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
        case .loaded(let rows, let totals, let rates, let hasEuropean, let prices, let source):
            Positions(rows: rows, totals: totals, rates: rates, hasEuropean: hasEuropean, prices: prices, source: source,
                      progress: state.progress, onRefresh: state.refresh)
        }
    }
}

private extension PositionRow {
    /// Rows in the USD view are converted copies; the holding identifies the position.
    var identity: String { "\(ticker)-\(holding.isin)-\(currency)" }
}

private let gain = Color(red: 0x2E / 255, green: 0x7D / 255, blue: 0x32 / 255)
private let loss = Color(red: 0xC6 / 255, green: 0x28 / 255, blue: 0x28 / 255)

private func signColor(_ value: ExactDecimal?) -> Color {
    switch value?.signum ?? 0 {
    case 1: return gain
    case -1: return loss
    default: return .primary
    }
}

private struct Positions: View {
    let rows: [PositionRow]
    let totals: UsdTotals
    let rates: FxRates?
    let hasEuropean: Bool
    let prices: RefreshOutcome?
    let source: PriceSource
    let progress: RefreshProgress?
    let onRefresh: () -> Void

    @State private var sort = SortOrder()
    @State private var mode = PnlMode.daily
    // Not sensitive: only which view is shown.
    @AppStorage("usd_view") private var usdView = false

    var body: some View {
        let shown = MainPage.sorted(usdView ? MainPage.inUsd(rows, rates: rates) : rows, order: sort, mode: mode)
        List {
            Section {
                Header(totals: totals, rowCount: rows.count)
                PriceStatus(prices: prices, source: source, hasEuropean: hasEuropean, progress: progress, onRefresh: onRefresh)
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
                    Text(rates.map { Strings.usdViewNote($0.effectiveDate) } ?? Strings.usdViewNoRates).font(.footnote).foregroundStyle(.secondary)
                }
            } header: {
                Text(Strings.portfolioTitle)
            }
            Section {
                ColumnHeaders(sort: $sort)
                if shown.isEmpty { Text(Strings.portfolioNoHoldings) }
                ForEach(shown, id: \.identity) { row in PositionLine(row: row, mode: mode) }
            }
        }
        .listStyle(.plain)
        .refreshable { onRefresh() }
    }
}

private struct Header: View {
    let totals: UsdTotals
    let rowCount: Int

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top) {
                VStack(alignment: .leading) {
                    Text(Strings.cardValue).font(.caption)
                    Text(Formats.money(totals.marketValue, "USD")).font(.title3.bold())
                }
                Spacer()
                VStack(alignment: .trailing) {
                    Text(Strings.cardToday).font(.caption)
                    Text(Formats.signedMoney(totals.dailyPnl, "USD")).font(.title3.bold()).foregroundStyle(signColor(totals.dailyPnl))
                    if let percent = totals.dailyPnlPercent { Text(Formats.signedPercent(percent)).font(.footnote).foregroundStyle(signColor(percent)) }
                }
            }
            Text(notes).font(.footnote).foregroundStyle(.secondary)
        }
    }

    private var notes: String {
        var parts = [Strings.headerApproximate]
        if let date = totals.rateDate { parts.append(Strings.headerRateDate(date)) }
        if !totals.isComplete { parts.append(Strings.headerIncomplete(totals.missingValues, rowCount)) }
        return parts.joined(separator: " · ")
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
                        Text(label).font(.caption).foregroundStyle(price.freshness == .stale ? loss : .secondary)
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
