import SwiftUI

private let currencyColors = [BentoColors.violet, BentoColors.cyan, BentoColors.amber, BentoColors.gain, BentoColors.loss]

func bentoPositionKey(_ row: PositionRow) -> String { "\(row.ticker)|\(row.currency)|\(row.holding.isin)" }

func bentoSoldKey(_ position: PositionPerformance) -> String { "\(position.ticker)|\(position.currency)|sold" }

/// Bento's Home (Android `BentoHome`): value tile, total return, 12-month dividends, best and worst today, currency mix and holdings.
struct BentoHome: View {
    @ObservedObject var state: PortfolioState
    @ObservedObject var income: IncomeState
    let onOpen: (String) -> Void
    let onIncome: () -> Void
    @AppStorage("usd_view") private var usd = false
    @State private var byToday = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                BentoHeader(title: Strings.bentoOverview) {
                    BentoPill(text: usd ? Strings.viewUsd : Strings.viewOwnCurrency, active: usd) { usd.toggle() }
                }
                switch state.ui {
                case .loading:
                    Text(Strings.loading).foregroundStyle(BentoColors.muted)
                case .storageUnavailable:
                    Text(Strings.storageUnavailable)
                case .incomplete(let ticker, let date):
                    Text(Strings.portfolioIncomplete(ticker, date))
                case .noReports:
                    BentoTile {
                        Text(Strings.portfolioEmptyTitle).font(.system(size: 18, weight: .bold))
                        Text(Strings.bentoEmptyBody).foregroundStyle(BentoColors.muted).padding(.top, 6)
                    }
                case .loaded(let data):
                    BentoDashboard(data: data, lastYear: income.data.map { lastTwelveMonths($0.events, $0.rates) } ?? [], usd: usd,
                                   byToday: $byToday, onOpen: onOpen, onIncome: onIncome, onRefresh: state.refresh)
                }
                Color.clear.frame(height: bentoTabBarRoom)
            }
            .padding(.horizontal, 16)
        }
        .refreshable { state.refresh() }
        .task {
            if case .loading = state.ui { state.refresh() }
            income.reload()
        }
    }

    /// Net USD dividends of the last twelve months, oldest first, for the small chart.
    private func lastTwelveMonths(_ events: [CashEvent], _ rates: FxRates?) -> [ExactDecimal] {
        let today = CalendarDay.today()
        return (0..<12).reversed().map { back in
            let day = today.minusMonths(back)
            let month = YearMonth(year: day.year, month: day.month)
            return Dividends.period(events.filter { $0.month == month }, nil, rates: rates).usdNet.amount
        }
    }
}

private struct BentoDashboard: View {
    let data: LoadedPortfolio
    let lastYear: [ExactDecimal]
    let usd: Bool
    @Binding var byToday: Bool
    let onOpen: (String) -> Void
    let onIncome: () -> Void
    let onRefresh: () -> Void

    var body: some View {
        let totals = data.totals
        let summary = data.summary
        let valueUsd = Dictionary(data.rows.map { row in
            (bentoPositionKey(row), row.marketValue.flatMap { UsdConverter.toUsd($0, currency: row.currency, rates: data.rates) })
        }) { first, _ in first }
        let total = valueUsd.values.compactMap { $0 }.reduce(ExactDecimal.zero) { $0.adding($1) }
        let ranked = data.rows.filter { $0.dailyChangePercent != nil }
        let best = ranked.max { $0.dailyChangePercent!.compare($1.dailyChangePercent!) < 0 }
        let worst = ranked.min { $0.dailyChangePercent!.compare($1.dailyChangePercent!) < 0 }
        let rows = sorted(data.rows, valueUsd)
        let sold = data.performance.filter(\.isSold)

        BentoTile(gradient: true, action: onRefresh) {
            BentoTileLabel(text: Strings.bentoValueLabel)
            Text(skinMoney(totals.marketValue, "USD")).font(.system(size: 34, weight: .bold)).padding(.top, 6)
            Text("\(skinMoney(totals.dailyPnl, "USD", signed: true)) · \(totals.dailyPnlPercent.map(skinPercent) ?? "—") \(Strings.calmToday)")
                .font(.system(size: 13, weight: .semibold)).foregroundStyle(bentoSign(totals.dailyPnl))
            Text(notes).font(.system(size: 11)).foregroundStyle(BentoColors.muted).padding(.top, 6)
        }
        HStack(spacing: 10) {
            BentoTile {
                BentoTileLabel(text: Strings.bentoTotalReturn)
                Text(skinMoney(summary.totalProfit.amount, "USD", signed: true)).font(.system(size: 20, weight: .bold))
                    .foregroundStyle(bentoSign(summary.totalProfit.amount)).padding(.top, 6)
                if let percent = summary.totalProfitPercent { Text(skinPercent(percent)).font(.system(size: 13, weight: .semibold)).foregroundStyle(bentoSign(percent)) }
                if !summary.totalProfit.isComplete { Text(Strings.cardIncomplete).font(.system(size: 11)).foregroundStyle(BentoColors.amber) }
                Spacer(minLength: 0)
            }
            .frame(height: 124)
            BentoTile(action: onIncome) {
                BentoTileLabel(text: Strings.bentoDividends12m)
                Text(skinMoney(summary.dividends12Months.amount, "USD")).font(.system(size: 20, weight: .bold)).padding(.top, 6)
                MiniBars(values: lastYear)
            }
            .frame(height: 124)
        }
        if let best, let worst {
            HStack(spacing: 10) {
                moverTile(Strings.bentoBestToday, best)
                moverTile(Strings.bentoWorstToday, worst)
            }
        }
        currencyMix(valueUsd, total)
        HStack {
            Text(Strings.calmHoldings).font(.system(size: 15, weight: .bold))
            Spacer()
            BentoPill(text: byToday ? Strings.bentoSortToday : Strings.bentoSortValue, active: false) { byToday.toggle() }
        }
        .padding(.top, 8).padding(.leading, 4)
        if rows.isEmpty {
            BentoTile { Text(Strings.portfolioNoHoldings).foregroundStyle(BentoColors.muted) }
        } else {
            BentoList(items: rows) { row in
                Button { onOpen(bentoPositionKey(row)) } label: { BentoHoldingItem(row: row, usd: usd, valueUsd: valueUsd[bentoPositionKey(row)] ?? nil, total: total, rates: data.rates) }
                    .buttonStyle(.plain)
            }
        }
        if !sold.isEmpty {
            Text(Strings.calmSold).font(.system(size: 15, weight: .bold)).padding(.top, 8).padding(.leading, 4)
            BentoList(items: sold) { p in
                Button { onOpen(bentoSoldKey(p)) } label: {
                    HStack(spacing: 12) {
                        TickerTile(ticker: p.ticker, size: 38)
                        VStack(alignment: .leading) {
                            Text(p.ticker).bold()
                            Text(Strings.soldPosition(p.currency)).font(.system(size: 12)).foregroundStyle(BentoColors.muted)
                        }
                        Spacer()
                        Text(p.total.map { skinMoney($0, p.currency, signed: true) } ?? "—").bold().foregroundStyle(bentoSign(p.total))
                    }
                    .padding(.vertical, 11).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var notes: String {
        var parts = [Strings.headerApproximate]
        if let date = data.totals.rateDate { parts.append(Strings.headerRateDate(date)) }
        if !data.totals.isComplete { parts.append(Strings.headerIncomplete(data.totals.missingValues, data.rows.count)) }
        if data.prices == nil { parts.append(Strings.pricesRefreshing) }
        return parts.joined(separator: " · ")
    }

    private func sorted(_ rows: [PositionRow], _ valueUsd: [String: ExactDecimal?]) -> [PositionRow] {
        if byToday {
            return FifoInventory.stableSorted(rows) { a, b in
                switch (a.dailyChangePercent, b.dailyChangePercent) {
                case (let x?, let y?): return x.compare(y) > 0
                case (_?, nil): return true
                default: return false
                }
            }
        }
        return FifoInventory.stableSorted(rows) {
            ((valueUsd[bentoPositionKey($0)] ?? nil) ?? .zero).compare((valueUsd[bentoPositionKey($1)] ?? nil) ?? .zero) > 0
        }
    }

    private func moverTile(_ label: String, _ row: PositionRow) -> some View {
        BentoTile(action: { onOpen(bentoPositionKey(row)) }) {
            BentoTileLabel(text: label)
            Text(row.ticker).font(.system(size: 22, weight: .bold)).padding(.top, 6)
            Text(row.dailyChangePercent.map(skinPercent) ?? "—").font(.system(size: 13, weight: .semibold)).foregroundStyle(bentoSign(row.dailyChangePercent))
            if let freshness = bentoFreshness(row.price?.freshness) { Text(freshness).font(.system(size: 11)).foregroundStyle(BentoColors.muted) }
        }
    }

    /// Share of the total in whole percent, cut off like the Android `toInt()`; exact decimals, so one currency shows 100.
    private func currencyPercent(_ value: ExactDecimal, _ total: ExactDecimal) -> Int {
        Int(Double((value.multiplied(by: ExactDecimal("100")!).divided(by: total) ?? .zero).description) ?? 0)
    }

    @ViewBuilder
    private func currencyMix(_ valueUsd: [String: ExactDecimal?], _ total: ExactDecimal) -> some View {
        let byCurrency = Dictionary(grouping: data.rows, by: \.currency)
            .mapValues { rows in rows.compactMap { valueUsd[bentoPositionKey($0)] ?? nil }.reduce(ExactDecimal.zero) { $0.adding($1) } }
            .filter { $0.value.signum > 0 }
            .sorted { $0.value.compare($1.value) > 0 }
        if !byCurrency.isEmpty, total.signum > 0 {
            let totalValue = Double(total.description) ?? 1
            BentoTile {
                BentoTileLabel(text: Strings.bentoByCurrency)
                GeometryReader { geometry in
                    HStack(spacing: 2) {
                        ForEach(Array(byCurrency.enumerated()), id: \.offset) { index, entry in
                            currencyColors[index % currencyColors.count]
                                .frame(width: max(0, geometry.size.width * (Double(entry.value.description) ?? 0) / totalValue - 2))
                        }
                    }
                }
                .frame(height: 12)
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .padding(.vertical, 10)
                HStack(spacing: 14) {
                    ForEach(Array(byCurrency.enumerated()), id: \.offset) { index, entry in
                        HStack(spacing: 4) {
                            Circle().fill(currencyColors[index % currencyColors.count]).frame(width: 8, height: 8)
                            Text("\(entry.key) \(currencyPercent(entry.value, total))%").font(.system(size: 12)).foregroundStyle(BentoColors.muted)
                        }
                    }
                }
            }
        }
    }
}

private struct MiniBars: View {
    let values: [ExactDecimal]

    var body: some View {
        let doubles = values.map { Double($0.description) ?? 0 }
        let maximum = max(doubles.max() ?? 0, 0) > 0 ? doubles.max()! : 1
        GeometryReader { geometry in
            HStack(alignment: .bottom, spacing: 3) {
                ForEach(Array(doubles.enumerated()), id: \.offset) { _, value in
                    RoundedRectangle(cornerRadius: 3).fill(BentoColors.violet)
                        .frame(height: geometry.size.height * min(max(value / maximum, 0.06), 1))
                }
            }
            .frame(maxHeight: .infinity, alignment: .bottom)
        }
        .frame(height: 26)
        .padding(.top, 8)
    }
}

private struct BentoHoldingItem: View {
    let row: PositionRow
    let usd: Bool
    let valueUsd: ExactDecimal?
    let total: ExactDecimal
    let rates: FxRates?

    var body: some View {
        HStack(spacing: 12) {
            TickerTile(ticker: row.ticker, size: 38)
            VStack(alignment: .leading, spacing: 6) {
                Text(row.ticker).font(.system(size: 15, weight: .bold))
                let weight = valueUsd.flatMap { v in total.signum > 0 ? Double(v.description).map { $0 / (Double(total.description) ?? 1) } : nil } ?? 0
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 2).fill(BentoColors.tile2)
                    RoundedRectangle(cornerRadius: 2).fill(BentoColors.cyan).frame(width: 110 * min(max(weight, 0), 1))
                }
                .frame(width: 110, height: 4)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text((usd ? valueUsd.map { skinMoney($0, "USD") } : row.marketValue.map { skinMoney($0, row.currency) }) ?? "—").font(.system(size: 15, weight: .bold))
                let change = row.dailyChangePercent
                Text(change.map(skinPercent) ?? (!row.inPriceScope ? Strings.priceOutOfScopeShort : (usd && rates == nil ? Strings.priceNoRateShort : Strings.priceUnavailableShort)))
                    .font(.system(size: 12, weight: .bold)).foregroundStyle(bentoSign(change))
            }
        }
        .monospacedDigit()
        .padding(.vertical, 11)
        .contentShape(Rectangle())
    }
}

/// Full-screen position detail with the return as steps (Android `BentoPosition`, mobile-skin-bento).
struct BentoPosition: View {
    @ObservedObject var state: PortfolioState
    let key: String
    let onBack: () -> Void
    let onDividends: (_ ticker: String, _ currency: String) -> Void

    var body: some View {
        if case .loaded(let data) = state.ui, let p = position(in: data) {
            content(p)
        } else {
            Button("‹ " + Strings.calmBack, action: onBack).padding()
        }
    }

    private func position(in data: LoadedPortfolio) -> PositionPerformance? {
        let parts = key.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
        let (ticker, currency, isin) = (parts[0], parts.count > 1 ? parts[1] : "", parts.count > 2 ? parts[2] : "")
        if isin == "sold" { return data.performance.first { $0.isSold && $0.ticker == ticker && $0.currency == currency } }
        return data.performance.first { $0.row?.ticker == ticker && $0.row?.currency == currency && $0.row?.holding.isin == isin }
    }

    private func content(_ p: PositionPerformance) -> some View {
        let cur = p.currency
        let row = p.row
        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Button("‹ " + Strings.calmBack, action: onBack).foregroundStyle(BentoColors.muted).padding(.vertical, 6)
                        Spacer()
                        let badges = [row?.listingExchange.isEmpty == false ? row?.listingExchange : nil, bentoFreshness(row?.price?.freshness)].compactMap { $0 }
                        if !badges.isEmpty { BentoPill(text: badges.joined(separator: " · "), active: false) {} }
                    }
                    HStack(spacing: 12) {
                        TickerTile(ticker: p.ticker, size: 48)
                        VStack(alignment: .leading) {
                            Text(p.ticker).font(.system(size: 20, weight: .bold))
                            Text(row == nil ? Strings.detailsSold(cur) : cur).font(.system(size: 13)).foregroundStyle(BentoColors.muted)
                        }
                    }
                    .padding(.top, 16)
                    if let row, let quote = row.price?.quote {
                        Text(skinMoney(quote.price, cur)).font(.system(size: 38, weight: .heavy)).padding(.top, 12)
                        if let change = row.dailyChange {
                            Text("\(skinMoney(change, cur, signed: true)) · \(row.dailyChangePercent.map(skinPercent) ?? "") \(Strings.calmToday)").bold().foregroundStyle(bentoSign(change))
                        }
                    }
                }
                .padding(.horizontal, 20).padding(.vertical, 14)
                .background(LinearGradient(colors: [Color(hex: 0x13306B), BentoColors.page], startPoint: .top, endPoint: .bottom))
                VStack(alignment: .leading, spacing: 10) {
                    if let row {
                        HStack(spacing: 8) {
                            small(Strings.detailsQuantity, Formats.quantity(row.holding.quantity))
                            small(Strings.detailsAverage, skinMoney(row.holding.averagePrice, cur))
                            small(Strings.detailsValue, row.marketValue.map { skinMoney($0, cur) } ?? "—")
                        }
                    }
                    BentoTile {
                        BentoTileLabel(text: Strings.bentoReturnSteps)
                        returnSteps(p)
                    }
                    let history: (() -> Void)? = (p.dividends.gross.signum != 0 || p.dividends.tax.signum != 0) ? { onDividends(p.ticker, cur) } : nil
                    HStack(spacing: 10) {
                        // Both tiles open the dividend history in Income (mobile-payer-history).
                        small(Strings.bentoDividendsGross, skinMoney(p.dividends.gross, cur), action: history)
                        small(Strings.bentoTaxWithheld, skinMoney(p.dividends.tax.magnitude, cur), action: history)
                    }
                    if !p.realizedComplete { Text(Strings.detailsSalesIncomplete).font(.system(size: 12)).foregroundStyle(BentoColors.muted) }
                    Text(Strings.detailsNote).font(.system(size: 12)).foregroundStyle(BentoColors.muted)
                    Color.clear.frame(height: bentoTabBarRoom)
                }
                .padding(.horizontal, 16)
            }
        }
        .foregroundStyle(BentoColors.ink)
    }

    private func returnSteps(_ p: PositionPerformance) -> some View {
        let cur = p.currency
        let steps: [(String, ExactDecimal?, Color)] = [
            (Strings.bentoInvestedShort, p.invested, BentoColors.muted),
            (Strings.bentoPriceShort, p.priceGain, BentoColors.violet),
            (Strings.bentoSalesShort, p.realizedGain, BentoColors.cyan),
            (Strings.bentoDividendsShort, p.dividends.net, BentoColors.amber),
            (Strings.bentoTotalShort, p.total, BentoColors.gain),
        ]
        let maximum = steps.compactMap { $0.1.flatMap { Double($0.magnitude.description) } }.max().flatMap { $0 > 0 ? $0 : nil } ?? 1
        // One row per step (label, bar, amount), so long amounts always fit.
        return VStack(spacing: 10) {
            ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                let last = index == steps.count - 1
                let width = min(max((step.1.flatMap { Double($0.magnitude.description) } ?? 0) / maximum, 0.02), 1)
                let bar = index > 0 && (step.1?.signum ?? 0) < 0 ? BentoColors.loss : step.2
                HStack {
                    Text(step.0).font(.system(size: 12)).foregroundStyle(BentoColors.muted).lineLimit(1).frame(width: 80, alignment: .leading)
                    GeometryReader { geometry in RoundedRectangle(cornerRadius: 6).fill(bar).frame(width: geometry.size.width * width) }.frame(height: 12)
                    Text(step.1.map { skinMoney($0, cur, signed: index > 0) } ?? "—")
                        .font(.system(size: 12, weight: last ? .bold : .regular)).foregroundStyle(last ? bentoSign(step.1) : BentoColors.ink)
                        .lineLimit(1).frame(width: 104, alignment: .trailing)
                }
            }
        }
        .monospacedDigit()
        .padding(.top, 12)
    }

    private func small(_ label: String, _ value: String, action: (() -> Void)? = nil) -> some View {
        BentoTile(action: action) {
            BentoTileLabel(text: label)
            Text(value + (action != nil ? " ›" : "")).font(.system(size: 15, weight: .bold)).lineLimit(1).minimumScaleFactor(0.7).padding(.top, 4)
        }
    }
}
