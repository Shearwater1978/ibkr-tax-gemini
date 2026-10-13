import SwiftUI

func focusRowKey(_ row: PositionRow) -> String { "\(row.ticker)|\(row.currency)|\(row.holding.isin)" }

struct FocusBack: View {
    let label: String
    let onBack: () -> Void

    var body: some View {
        Button(label, action: onBack).font(.system(size: 15)).foregroundStyle(FocusColors.muted).padding(.vertical, 10)
    }
}

private struct ChangePill: View {
    let percent: ExactDecimal?

    var body: some View {
        let (fg, bg): (Color, Color) = {
            switch percent?.signum {
            case 1: return (FocusColors.gain, FocusColors.gainSoft)
            case -1: return (FocusColors.loss, FocusColors.lossSoft)
            default: return (FocusColors.muted, FocusColors.sand)
            }
        }()
        Text(percent.map(skinPercent) ?? "—").font(.system(size: 13, weight: .bold)).foregroundStyle(fg)
            .padding(.horizontal, 10).padding(.vertical, 5).background(bg, in: Capsule())
    }
}

private struct Mono: View {
    let ticker: String

    var body: some View {
        Text(ticker.prefix(2)).font(.system(size: 12, weight: .bold)).frame(width: 40, height: 40).background(FocusColors.sand, in: Circle())
    }
}

private struct FocusMessage: View {
    let text: String

    var body: some View {
        Text(text).multilineTextAlignment(.center).foregroundStyle(FocusColors.muted).padding(24).frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Today (mobile-skin-focus): value, the summary sentence, insight cards and what you own.
struct FocusToday: View {
    @ObservedObject var state: PortfolioState
    @ObservedObject var income: IncomeState
    let onHoldings: () -> Void
    let onOpen: (String) -> Void
    let onIncome: () -> Void
    let onImport: () -> Void
    let onSettings: () -> Void

    var body: some View {
        content
            .task {
                state.refresh()
                income.reload()
            }
    }

    @ViewBuilder
    private var content: some View {
        switch state.ui {
        case .loading:
            FocusMessage(text: Strings.loading)
        // Settings stays reachable here; it holds "Erase all data", the way out when storage cannot be opened.
        case .storageUnavailable:
            VStack(alignment: .leading) {
                TopLine(text: "", onSettings: onSettings)
                FocusMessage(text: Strings.storageUnavailable)
            }
            .padding(.horizontal, 22)
        case .incomplete(let ticker, let date):
            FocusMessage(text: Strings.portfolioIncomplete(ticker, date))
        case .noReports:
            VStack(alignment: .leading, spacing: 0) {
                TopLine(text: "", onSettings: onSettings)
                Text(Strings.portfolioEmptyTitle).font(.focusSerif(28)).padding(.top, 12)
                Text(Strings.portfolioEmptyBody).foregroundStyle(FocusColors.muted).padding(.vertical, 10)
                Button(Strings.calmAddReports, action: onImport).buttonStyle(.borderedProminent).foregroundStyle(.white)
                Spacer()
            }
            .padding(22)
        case .loaded(let data):
            ScrollView {
                FocusTodayLoaded(data: data, progress: state.progress, dividends: income.data, onHoldings: onHoldings, onOpen: onOpen,
                                 onIncome: onIncome, onSettings: onSettings, onRefresh: state.refresh)
                    .padding(.vertical, 6)
            }
            .refreshable { state.refresh() }
        }
    }
}

/// The greeting line with the Settings button at its end, so Settings is one tap from Today.
private struct TopLine: View {
    let text: String
    let onSettings: () -> Void

    var body: some View {
        HStack {
            Text(text).font(.system(size: 15)).foregroundStyle(FocusColors.muted)
            Spacer()
            Button(action: onSettings) {
                Image(systemName: "gearshape.fill").foregroundStyle(FocusColors.ink).frame(width: 40, height: 40).background(FocusColors.sand, in: Circle())
            }
            .accessibilityLabel(Strings.settingsTitle)
        }
    }
}

private struct FocusTodayLoaded: View {
    let data: LoadedPortfolio
    let progress: RefreshProgress?
    let dividends: DividendData?
    let onHoldings: () -> Void
    let onOpen: (String) -> Void
    let onIncome: () -> Void
    let onSettings: () -> Void
    let onRefresh: () -> Void
    @State private var card = 0

    var body: some View {
        let totals = data.totals
        let freshWord = freshness
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                TopLine(text: "\(greeting) · \(freshWord.lowercased())", onSettings: onSettings)
                Text("$" + Formats.number(totals.marketValue.withScale(0, halfUp: true), 0, 0))
                    .font(.focusSerif(46, weight: .semibold)).padding(.top, 2).padding(.bottom, 6)
                Text(Summaries.today(data.rows, totals: totals, summary: data.summary, rates: data.rates).attributed).font(.system(size: 17)).lineSpacing(5)
                Text(([Strings.headerApproximate] + [totals.rateDate.map(Strings.headerRateDate)].compactMap { $0 }).joined(separator: " · "))
                    .font(.system(size: 12)).foregroundStyle(FocusColors.muted).padding(.top, 6)
            }
            .padding(.horizontal, 22)
            let cards = insightCards(freshWord)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(Array(cards.enumerated()), id: \.offset) { index, item in
                        item.background(GeometryReader { geometry in
                            Color.clear.preference(key: CardOffsets.self, value: [index: geometry.frame(in: .named("cards")).minX])
                        })
                    }
                }
                .padding(.horizontal, 22)
            }
            .coordinateSpace(name: "cards")
            .onPreferenceChange(CardOffsets.self) { offsets in
                // The first card that starts inside the row; the last one once the row is scrolled to its end.
                let visible = offsets.filter { $0.value >= 0 }.min { $0.key < $1.key }?.key ?? 0
                card = (offsets[cards.count - 1] ?? .infinity) < 22 + 1 ? cards.count - 1 : visible
            }
            .padding(.top, 18)
            PageDots(count: cards.count, current: card)
            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .bottom) {
                    Text(Strings.focusOwn).font(.focusSerif(21))
                    Spacer()
                    Button(Strings.focusSeeAll, action: onHoldings).font(.system(size: 14)).foregroundStyle(FocusColors.muted).padding(4)
                }
                .padding(.top, 22).padding(.bottom, 10)
                Allocation(data: data, onClick: onHoldings)
            }
            .padding(.horizontal, 22)
            Color.clear.frame(height: 24)
        }
    }

    private var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        return hour < 12 ? Strings.focusMorning : (hour < 18 ? Strings.focusAfternoon : Strings.focusEvening)
    }

    /// The worst freshness of any price, as one word.
    private var freshness: String {
        let all = data.rows.compactMap { $0.price?.freshness }
        if all.contains(.stale) { return Strings.focusPricesStale }
        if all.contains(.latestClose) { return Strings.focusPricesClose }
        if all.contains(.delayed) { return Strings.focusPricesDelayed }
        return Strings.focusPricesFresh
    }

    private func insightCards(_ freshWord: String) -> [AnyView] {
        var cards: [AnyView] = []
        let today = CalendarDay.today()
        let month = YearMonth(year: today.year, month: today.month)
        let events = (dividends?.events ?? []).filter { $0.month == month }
        let net = Dividends.period(events, nil, rates: dividends?.rates).usdNet.amount
        let companies = Set(events.filter { $0.eventType == Dividends.dividend }.map(\.ticker)).count
        cards.append(AnyView(InsightCard(label: Strings.focusCardMonth, value: skinMoney(net, "USD"), body: Strings.focusCardMonthBody(companies), dark: true, action: onIncome)))
        let ranked = data.rows.filter { $0.dailyChangePercent != nil }
        if let best = ranked.max(by: { $0.dailyChangePercent!.magnitude.compare($1.dailyChangePercent!.magnitude) < 0 }) {
            cards.append(AnyView(InsightCard(
                label: Strings.focusCardMove,
                value: "\(best.ticker) \(skinPercent(best.dailyChangePercent!))",
                body: Strings.focusCardMoveBody(best.dailyPnl.map { skinMoney($0, best.currency, signed: true) } ?? "—", Formats.quantity(best.holding.quantity))
            ) { onOpen(focusRowKey(best)) }))
        }
        cards.append(AnyView(InsightCard(
            label: Strings.focusCardPrices, value: progress.map { Strings.focusPricesRefreshing("\($0.done) / \($0.total)") } ?? freshWord,
            body: Strings.focusPricesTap, action: onRefresh
        )))
        cards.append(AnyView(InsightCard(label: Strings.focusCard12m, value: skinMoney(data.summary.dividends12Months.amount, "USD"), body: Strings.focusCard12mBody, action: onIncome)))
        return cards
    }
}

private struct CardOffsets: PreferenceKey {
    static var defaultValue: [Int: CGFloat] = [:]
    static func reduce(value: inout [Int: CGFloat], nextValue: () -> [Int: CGFloat]) { value.merge(nextValue()) { $1 } }
}

/// One dot per card; the dot of the first fully visible card is dark, so the row reads as swipeable.
private struct PageDots: View {
    let count: Int
    let current: Int

    var body: some View {
        if count > 1 {
            HStack(spacing: 6) {
                ForEach(0..<count, id: \.self) { index in
                    Capsule().fill(index == current ? FocusColors.ink : FocusColors.line).frame(width: index == current ? 18 : 6, height: 6)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 10)
            .accessibilityElement()
            .accessibilityLabel(Strings.focusCardPosition(current + 1, count))
        }
    }
}

private struct InsightCard: View {
    let label: String
    let value: String
    let body_: String
    let dark: Bool
    let action: () -> Void

    init(label: String, value: String, body: String, dark: Bool = false, action: @escaping () -> Void) {
        self.label = label
        self.value = value
        self.body_ = body
        self.dark = dark
        self.action = action
    }

    var body: some View {
        let muted = dark ? FocusColors.deepMuted : FocusColors.muted
        Button(action: action) {
            VStack(alignment: .leading, spacing: 0) {
                Text(label).font(.system(size: 12, weight: .semibold)).foregroundStyle(muted)
                Text(value).font(.system(size: 22, weight: .bold)).foregroundStyle(dark ? .white : FocusColors.ink).lineLimit(1).minimumScaleFactor(0.7).padding(.top, 6)
                Text(body_).font(.system(size: 13)).foregroundStyle(muted).padding(.top, 4)
                Spacer(minLength: 0)
            }
            .frame(width: 210, alignment: .leading)
            .padding(16)
            .background(dark ? FocusColors.deep : FocusColors.card, in: RoundedRectangle(cornerRadius: 22))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// The four largest holdings and the rest by USD weight, as a donut with a legend.
private struct Allocation: View {
    let data: LoadedPortfolio
    let onClick: () -> Void

    var body: some View {
        let total = Double(data.totals.marketValue.description) ?? 0
        if total > 0 {
            let weights = data.rows
                .compactMap { row in row.marketValue.flatMap { UsdConverter.toUsd($0, currency: row.currency, rates: data.rates) }.map { (row.ticker, Double($0.description) ?? 0) } }
                .sorted { $0.1 > $1.1 }
                .map { ($0.0, $0.1 / total) }
            let slices = Array(weights.prefix(4)) + (weights.count > 4 ? [(Strings.focusOthers, weights.dropFirst(4).reduce(0) { $0 + $1.1 })] : [])
            Button(action: onClick) {
                HStack(spacing: 18) {
                    Donut(weights: slices.map(\.1))
                        .frame(width: 112, height: 112)
                        .accessibilityLabel(slices.map { "\($0.0) \(Int(($0.1 * 100).rounded()))%" }.joined(separator: ", "))
                    VStack(spacing: 6) {
                        ForEach(Array(slices.enumerated()), id: \.offset) { index, slice in
                            HStack {
                                Circle().fill(FocusColors.slices[index]).frame(width: 9, height: 9)
                                Text(slice.0).font(.system(size: 14)).padding(.leading, 2)
                                Spacer()
                                Text("\(Int((slice.1 * 100).rounded()))%").font(.system(size: 14, weight: .bold)).monospacedDigit()
                            }
                        }
                    }
                }
                .padding(16)
                .background(FocusColors.card, in: RoundedRectangle(cornerRadius: 22))
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }
}

private struct Donut: View {
    let weights: [Double]

    var body: some View {
        ZStack {
            ForEach(Array(weights.enumerated()), id: \.offset) { index, weight in
                let start = weights.prefix(index).reduce(0, +)
                Circle().trim(from: start, to: min(start + weight, 1))
                    .stroke(FocusColors.slices[index], style: StrokeStyle(lineWidth: 21))
                    .rotationEffect(.degrees(-90))
            }
        }
        .padding(10.5)
    }
}

/// All holdings grouped by today's direction, then sold positions.
struct FocusHoldings: View {
    @ObservedObject var state: PortfolioState
    let usd: Bool
    let onOpen: (String) -> Void
    let onBack: () -> Void

    var body: some View {
        if case .loaded(let data) = state.ui {
            let rows = usd ? MainPage.inUsd(data.rows, rates: data.rates) : data.rows
            let rising = FifoInventory.stableSorted(rows.filter { ($0.dailyChangePercent?.signum ?? 0) > 0 }) { $0.dailyChangePercent!.compare($1.dailyChangePercent!) > 0 }
            let falling = FifoInventory.stableSorted(rows.filter { ($0.dailyChangePercent?.signum ?? 0) < 0 }) { $0.dailyChangePercent!.compare($1.dailyChangePercent!) < 0 }
            let flat = rows.filter { ($0.dailyChangePercent?.signum ?? 0) == 0 }
            let sold = data.performance.filter(\.isSold)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    FocusBack(label: Strings.focusBackToday, onBack: onBack)
                    Text(Strings.focusHoldingsTitle).font(.focusSerif(26))
                    Text(usd ? Strings.focusHoldingsHintUsd : Strings.focusHoldingsHintOwn).font(.system(size: 14)).foregroundStyle(FocusColors.muted)
                    ForEach([(Strings.focusRising, rising), (Strings.focusFalling, falling), (Strings.focusNoChange, flat)].filter { !$0.1.isEmpty }, id: \.0) { title, list in
                        GroupTitle(title: title)
                        ForEach(list, id: \.focusId) { row in
                            let detail = [row.marketValue.map { skinMoney($0, row.displayCurrency) }, Strings.focusShares(Formats.quantity(row.holding.quantity))].compactMap { $0 }
                            HoldingRow(ticker: row.ticker, detail: detail.joined(separator: " · "), onClick: { onOpen(focusRowKey(row)) }) { ChangePill(percent: row.dailyChangePercent) }
                        }
                    }
                    if !sold.isEmpty {
                        GroupTitle(title: Strings.focusSold)
                        ForEach(sold, id: \.focusSoldId) { p in
                            HoldingRow(ticker: p.ticker, detail: Strings.detailsSold(p.currency), onClick: { onOpen(p.focusSoldId) }) {
                                if let total = p.total {
                                    Text(skinMoney(total, p.currency, signed: true)).bold().foregroundStyle(total.signum >= 0 ? FocusColors.gain : FocusColors.loss)
                                }
                            }
                        }
                    }
                    Color.clear.frame(height: 24)
                }
                .padding(.horizontal, 22)
            }
            .foregroundStyle(FocusColors.ink)
        } else {
            FocusBack(label: Strings.focusBackToday, onBack: onBack)
        }
    }
}

private extension PositionRow {
    var focusId: String { focusRowKey(self) }
}

private extension PositionPerformance {
    var focusSoldId: String { "\(ticker)|\(currency)|sold" }
}

private struct GroupTitle: View {
    let title: String

    var body: some View {
        Text(title.uppercased()).font(.system(size: 12, weight: .bold)).tracking(0.7).foregroundStyle(FocusColors.muted).padding(.top, 18).padding(.bottom, 2)
    }
}

private struct HoldingRow<Trailing: View>: View {
    let ticker: String
    let detail: String
    let onClick: () -> Void
    @ViewBuilder let trailing: Trailing

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 12) {
                Mono(ticker: ticker)
                VStack(alignment: .leading, spacing: 2) {
                    Text(ticker).font(.system(size: 15, weight: .bold))
                    Text(detail).font(.system(size: 13)).foregroundStyle(FocusColors.muted)
                }
                Spacer()
                trailing
            }
            .monospacedDigit()
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        Divider().overlay(FocusColors.line)
    }
}

/// A position told in sentences (mobile-skin-focus "Position stories").
struct FocusStory: View {
    @ObservedObject var state: PortfolioState
    @ObservedObject var income: IncomeState
    let key: String
    let onBack: () -> Void
    @State private var history = false

    var body: some View {
        if case .loaded(let data) = state.ui, let p = position(in: data) {
            content(p)
                .sheet(isPresented: $history) {
                    FocusPayerSheet(events: income.data?.events ?? [], ticker: p.ticker, currency: p.currency)
                        .onAppear { income.reload() }
                }
        } else {
            FocusBack(label: "‹ " + Strings.calmBack, onBack: onBack).padding(.horizontal, 22).frame(maxWidth: .infinity, alignment: .leading)
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
        let story = Summaries.story(p)
        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                FocusBack(label: "‹ " + Strings.calmBack, onBack: onBack)
                Text(p.ticker).font(.focusSerif(30)).padding(.top, 6)
                if let row {
                    Text(row.marketValue.map { skinMoney($0, cur) } ?? "—").font(.focusSerif(38, weight: .semibold))
                    if let quote = row.price?.quote {
                        let day = row.dailyChange
                        let text = [day.map { skinMoney(row.dailyPnl ?? $0, cur, signed: true) + " " + Strings.calmToday }, Strings.focusAShare(skinMoney(quote.price, cur))]
                            .compactMap { $0 }.joined(separator: " · ")
                        Text(text).font(.system(size: 13, weight: .bold))
                            .foregroundStyle(day?.signum == 1 ? FocusColors.gain : (day?.signum == -1 ? FocusColors.loss : FocusColors.muted))
                            .padding(.horizontal, 10).padding(.vertical, 5)
                            .background(day?.signum == 1 ? FocusColors.gainSoft : (day?.signum == -1 ? FocusColors.lossSoft : FocusColors.sand), in: Capsule())
                            .padding(.top, 4)
                    }
                } else {
                    Text(Strings.detailsSold(cur)).foregroundStyle(FocusColors.muted)
                }
                StoryCard {
                    Text(story[0].attributed).font(.system(size: 16)).lineSpacing(4)
                    if let value = row?.marketValue, value.signum > 0 {
                        let fraction = min(max((Double(p.invested.description) ?? 0) / (Double(value.description) ?? 1), 0), 1)
                        GeometryReader { geometry in
                            ZStack(alignment: .leading) {
                                RoundedRectangle(cornerRadius: 4).fill(FocusColors.sand)
                                RoundedRectangle(cornerRadius: 4).fill(FocusColors.gain).frame(width: geometry.size.width * fraction)
                            }
                        }
                        .frame(height: 8)
                        .padding(.top, 12)
                    }
                }
                StoryCard {
                    Text(story[1].attributed).font(.system(size: 16)).lineSpacing(4)
                    if p.dividends.gross.signum != 0 || p.dividends.tax.signum != 0 {
                        Button(Strings.payerHistoryTitle + " ›") { history = true }
                            .font(.system(size: 16, weight: .semibold)).foregroundStyle(FocusColors.deep).padding(.top, 8).padding(.vertical, 6)
                    }
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(Strings.focusStoryTotal).font(.system(size: 14)).foregroundStyle(FocusColors.deepMuted)
                    if let total = p.total {
                        Text(skinMoney(total, cur, signed: true) + (p.totalPercent.map { " · " + skinPercent($0) } ?? "")).font(.focusSerif(26, weight: .bold))
                    } else {
                        Text(story[2].text).font(.focusSerif(16, weight: .bold))
                    }
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(18)
                .background(FocusColors.deep, in: RoundedRectangle(cornerRadius: 22))
                .padding(.top, 12)
                Text(([Strings.detailsNote] + [row?.listingExchange].compactMap { $0 }.filter { !$0.isEmpty }).joined(separator: " · "))
                    .font(.system(size: 12)).foregroundStyle(FocusColors.muted).padding(.top, 10).padding(.bottom, 24)
            }
            .padding(.horizontal, 22)
        }
        .foregroundStyle(FocusColors.ink)
    }
}

private struct StoryCard<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
            .background(FocusColors.card, in: RoundedRectangle(cornerRadius: 22))
            .padding(.top, 12)
    }
}
