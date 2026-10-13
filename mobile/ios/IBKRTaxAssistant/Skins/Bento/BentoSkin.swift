import SwiftUI

/// Bento's dark palette (mobile-skin-bento). Same values as Android.
enum BentoColors {
    static let page = Color(hex: 0x0E1015)
    static let tile = Color(hex: 0x171A21)
    static let tile2 = Color(hex: 0x1F232C)
    static let ink = Color(hex: 0xF3F5F8)
    static let muted = Color(hex: 0x8B93A1)
    static let line = Color(hex: 0x262A33)
    static let gain = Color(hex: 0x3DDC97)
    static let loss = Color(hex: 0xFF6B6B)
    static let violet = Color(hex: 0x8B7BFF)
    static let cyan = Color(hex: 0x3CC8F0)
    static let amber = Color(hex: 0xF6B84B)
    static let hero = LinearGradient(colors: [Color(hex: 0x2A2160), tile], startPoint: .topLeading, endPoint: .bottomTrailing)
}

/// Height of the floating tab bar plus its margins; every screen keeps this much room at the end.
let bentoTabBarRoom: CGFloat = 104

func bentoSign(_ value: ExactDecimal?) -> Color {
    switch value?.signum ?? 0 {
    case 1: return BentoColors.gain
    case -1: return BentoColors.loss
    default: return BentoColors.muted
    }
}

/// Dark tile dashboard with a floating tab bar: Home, Income, Activity and Data.
let bentoSkin = Skin(
    id: .bento,
    name: Strings.skinBentoName,
    description: Strings.skinBentoDescription,
    features: [
        .portfolioValueAndToday: "Home: value tile",
        .positionDetails: "Home: tap a holding or the best/worst tile",
        .soldPositions: "Home: Sold positions list under the holdings",
        .ownCurrencyAndUsd: "Home: USD / Own currency pill",
        .topMovers: "Home: Best and Worst today tiles; sort by today",
        .dividendsByYearAndMonth: "Income tab",
        .payerHistory: "Income: tap a payer; position: dividend tiles",
        .activitySearchAndFilters: "Activity tab",
        .reportImport: "Data tab: Add reports",
        .backupKeysAndLocation: "Data tab: Settings",
        .priceKey: "Data tab: Settings",
        .deleteImports: "Data tab: Reports imported or Delete imported data",
        .appVersion: "Data tab: Settings",
        .skinSetting: "Data tab: Settings",
        .manualEntry: "Activity tab: Add operation; tap a Manual row to edit",
    ],
    app: { AnyView(BentoApp(states: $0)) },
    preview: { AnyView(BentoPreview()) }
)

enum BentoTab: Hashable, CaseIterable {
    case home
    case income
    case activity
    case data

    var label: String {
        switch self {
        case .home: return Strings.calmTabHome
        case .income: return Strings.calmTabIncome
        case .activity: return Strings.calmTabActivity
        case .data: return Strings.bentoTabData
        }
    }
}

/// Screens opened on top of a tab. Position keys are `ticker|currency|isin` for held and `ticker|currency|sold` for sold positions.
enum BentoRoute: Hashable {
    case position(String)
    case payer(ticker: String, currency: String)
    case importReports
    case reports
    case settings
}

private struct BentoApp: View {
    @ObservedObject var states: AppStates
    @State private var tab = BentoTab.home
    @State private var path: [BentoRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            root
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: BentoRoute.self) { route in
                    destination(route).background(BentoColors.page)
                }
        }
        .overlay(alignment: .bottom) { BentoTabBar(current: tab, onTab: open).padding(.bottom, 16) }
        .tint(BentoColors.violet)
        .preferredColorScheme(.dark)
    }

    @ViewBuilder
    private var root: some View {
        Group {
            switch tab {
            case .home:
                BentoHome(state: states.portfolio, income: states.income, onOpen: { path.append(.position($0)) }, onIncome: { open(.income) })
            case .income:
                BentoIncome(state: states.income, onPayer: { path.append(.payer(ticker: $0, currency: $1)) })
            case .activity:
                BentoActivity(state: states.activity, onImport: { path.append(.importReports) })
            case .data:
                BentoData(states: states, onImport: { path.append(.importReports) }, onReports: { path.append(.reports) }, onSettings: { path.append(.settings) })
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(BentoColors.page)
        .foregroundStyle(BentoColors.ink)
    }

    @ViewBuilder
    private func destination(_ route: BentoRoute) -> some View {
        switch route {
        case .position(let key):
            BentoPosition(state: states.portfolio, key: key, onBack: { _ = path.popLast() }, onDividends: { ticker, currency in
                // Switch to Income first, so Back from the history returns to Income.
                tab = .income
                path = [.payer(ticker: ticker, currency: currency)]
            })
            .toolbar(.hidden, for: .navigationBar)
        case .payer(let ticker, let currency):
            BentoPayer(state: states.income, ticker: ticker, currency: currency, onBack: { _ = path.popLast() }).toolbar(.hidden, for: .navigationBar)
        // Shared screens keep room for the tab bar, so their last item scrolls above it.
        case .importReports:
            ImportsView(data: states.data).navigationTitle(Strings.calmAddReports).safeAreaInset(edge: .bottom) { Color.clear.frame(height: bentoTabBarRoom) }
        case .reports:
            ImportsView(data: states.data, reportsOnly: true).navigationTitle(Strings.importsHistoryTitle).safeAreaInset(edge: .bottom) { Color.clear.frame(height: bentoTabBarRoom) }
        case .settings:
            SettingsView(data: states.data).navigationTitle(Strings.settingsTitle).safeAreaInset(edge: .bottom) { Color.clear.frame(height: bentoTabBarRoom) }
        }
    }

    /// A tab tap leaves any opened screen first, so each tab opens on its first screen.
    private func open(_ target: BentoTab) {
        path = []
        tab = target
    }
}

private struct BentoTabBar: View {
    let current: BentoTab
    let onTab: (BentoTab) -> Void

    var body: some View {
        HStack(spacing: 4) {
            ForEach(BentoTab.allCases, id: \.self) { tab in
                let on = tab == current
                Button { onTab(tab) } label: {
                    Text(tab.label).font(.system(size: 13, weight: .bold))
                        .foregroundStyle(on ? BentoColors.page : BentoColors.muted)
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .background(on ? BentoColors.ink : .clear, in: Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(6)
        .background(BentoColors.tile2.opacity(0.95), in: Capsule())
        .overlay(Capsule().stroke(Color(hex: 0x2C313C)))
    }
}

/// A dark rounded tile; with `action` the whole tile is a button.
struct BentoTile<Content: View>: View {
    var gradient = false
    var action: (() -> Void)?
    @ViewBuilder let content: Content

    var body: some View {
        let tile = VStack(alignment: .leading, spacing: 0) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 16).padding(.vertical, 14)
            .background { RoundedRectangle(cornerRadius: 22).fill(gradient ? AnyShapeStyle(BentoColors.hero) : AnyShapeStyle(BentoColors.tile)) }
        if let action {
            Button(action: action) { tile.contentShape(Rectangle()) }.buttonStyle(.plain)
        } else {
            tile
        }
    }
}

struct BentoTileLabel: View {
    let text: String

    var body: some View {
        Text(text.uppercased()).font(.system(size: 11, weight: .semibold)).tracking(0.3).foregroundStyle(BentoColors.muted)
    }
}

struct BentoPill: View {
    let text: String
    let active: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text).font(.system(size: 13, weight: .semibold))
                .foregroundStyle(active ? .white : BentoColors.ink)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(active ? BentoColors.violet : BentoColors.tile2, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

struct BentoHeader<Trailing: View>: View {
    let title: String
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack {
            Text(title).font(.system(size: 22, weight: .bold))
            Spacer()
            trailing
        }
        .padding(.vertical, 12)
    }
}

extension BentoHeader where Trailing == EmptyView {
    init(title: String) {
        self.init(title: title) { EmptyView() }
    }
}

/// Rows inside one dark rounded block, divided by thin lines.
struct BentoList<Item, Row: View>: View {
    let items: [Item]
    @ViewBuilder let row: (Item) -> Row

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                row(item)
                if index < items.count - 1 { Divider().overlay(BentoColors.line) }
            }
        }
        .padding(.horizontal, 14).padding(.vertical, 4)
        .background(BentoColors.tile, in: RoundedRectangle(cornerRadius: 22))
    }
}

func bentoFreshness(_ freshness: PriceFreshness?) -> String? {
    switch freshness {
    case .delayed?: return Strings.priceDelayedShort
    case .latestClose?: return Strings.priceCloseShort
    case .stale?: return Strings.priceStaleShort
    default: return nil
    }
}

/// Dark tiles in a grid and a pill at the bottom.
private struct BentoPreview: View {
    var body: some View {
        VStack(spacing: 3) {
            RoundedRectangle(cornerRadius: 4).fill(BentoColors.hero).frame(height: 18)
            ForEach(0..<2, id: \.self) { _ in
                HStack(spacing: 3) { ForEach(0..<2, id: \.self) { _ in RoundedRectangle(cornerRadius: 4).fill(BentoColors.tile).frame(height: 16) } }
            }
            GeometryReader { geometry in
                HStack(spacing: 0) {
                    BentoColors.violet.frame(width: geometry.size.width * 0.6)
                    BentoColors.cyan.frame(width: geometry.size.width * 0.3)
                    BentoColors.amber
                }
            }
            .frame(height: 4)
            .clipShape(RoundedRectangle(cornerRadius: 2))
            Spacer()
            RoundedRectangle(cornerRadius: 4).fill(BentoColors.tile2).frame(width: 36, height: 8)
        }
        .padding(5)
        .background(BentoColors.page, in: RoundedRectangle(cornerRadius: 10))
    }
}
