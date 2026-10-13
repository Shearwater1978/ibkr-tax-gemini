import SwiftUI

/// Focus's warm palette (mobile-skin-focus); light only, serif headlines. Same values as Android.
enum FocusColors {
    static let page = Color(hex: 0xFBF8F3)
    static let card = Color(hex: 0xFFFFFF)
    static let ink = Color(hex: 0x1D1B16)
    static let muted = Color(hex: 0x6B655A)
    static let line = Color(hex: 0xEBE5DA)
    static let sand = Color(hex: 0xEFE7D9)
    static let deep = Color(hex: 0x24304A)
    static let deepMuted = Color(hex: 0xB9C2D6)
    static let gain = Color(hex: 0x2E7D4F)
    static let gainSoft = Color(hex: 0xE3F1E8)
    static let loss = Color(hex: 0xB93C0A)
    static let lossSoft = Color(hex: 0xFBE9DF)
    static let slices = [deep, Color(hex: 0xD97706), gain, Color(hex: 0x9A6FB0), Color(hex: 0xC9BFAE)]
}

extension Font {
    static func focusSerif(_ size: CGFloat, weight: Font.Weight = .regular) -> Font { .system(size: size, weight: weight, design: .serif) }
}

extension Sentence {
    /// Bold parts, coloured for gains and losses.
    var attributed: AttributedString {
        parts.reduce(into: AttributedString()) { result, part in
            var piece = AttributedString(part.text)
            if part.tone != .plain { piece.inlinePresentationIntent = .stronglyEmphasized }
            if part.tone == .gain { piece.foregroundColor = FocusColors.gain }
            if part.tone == .loss { piece.foregroundColor = FocusColors.loss }
            result += piece
        }
    }
}

/// Words first: Today and Income, with everything about data behind a central "+".
let focusSkin = Skin(
    id: .focus,
    name: Strings.skinFocusName,
    description: Strings.skinFocusDescription,
    features: [
        .portfolioValueAndToday: "Today: value and summary sentence",
        .positionDetails: "Today: What you own > a holding (story)",
        .soldPositions: "Today: What you own > Sold",
        .ownCurrencyAndUsd: "+ menu: Show amounts in",
        .topMovers: "Today: Biggest move card; holdings Rising / Falling today",
        .dividendsByYearAndMonth: "Income: year pills and monthly timeline with tax",
        .payerHistory: "Income: tap a payment; story: Dividend history (sheet)",
        .activitySearchAndFilters: "+ menu: Activity and operations",
        .reportImport: "+ menu: Add reports",
        .backupKeysAndLocation: "+ menu: Security and recovery code",
        .priceKey: "Today: Settings button, or + menu: Settings and skins",
        .deleteImports: "+ menu: Delete imported data",
        .appVersion: "Today: Settings button, or + menu: Settings and skins",
        .skinSetting: "Today: Settings button, or + menu: Settings and skins",
        .manualEntry: "+ menu: Add operation; Activity: tap a Manual row to edit",
    ],
    app: { AnyView(FocusApp(states: $0)) },
    preview: { AnyView(FocusPreview()) }
)

enum FocusTab: Hashable {
    case today
    case income
}

/// Screens opened on top of a tab. Story keys are `ticker|currency|isin` for held and `ticker|currency|sold` for sold positions.
enum FocusRoute: Hashable {
    case holdings
    case story(String)
    case activity
    case importReports
    case reports
    case settings
}

private struct FocusApp: View {
    @ObservedObject var states: AppStates
    @State private var tab = FocusTab.today
    @State private var path: [FocusRoute] = []
    @State private var menu = false
    /// The "+" sheet shows the manual-entry form instead of the menu.
    @State private var adding = false
    @AppStorage("usd_view") private var usd = false

    var body: some View {
        NavigationStack(path: $path) {
            root
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: FocusRoute.self) { route in
                    destination(route).background(FocusColors.page)
                }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            FocusBottomBar(current: tab, menuOpen: menu, onTab: { menu = false; open($0) }, onMenu: { menu.toggle() })
        }
        .sheet(isPresented: $menu, onDismiss: { adding = false }) {
            if adding {
                ManualEntryView(target: .new)
            } else {
                FocusPlusMenu(data: states.data, portfolio: states.portfolio, usd: $usd, onAdd: { adding = true }) { route in
                    menu = false
                    path.append(route)
                }
                .presentationDetents([.medium, .large])
            }
        }
        .tint(FocusColors.ink)
        .preferredColorScheme(.light)
    }

    @ViewBuilder
    private var root: some View {
        Group {
            switch tab {
            case .today:
                FocusToday(state: states.portfolio, income: states.income,
                           onHoldings: { path.append(.holdings) }, onOpen: { path.append(.story($0)) }, onIncome: { open(.income) },
                           onImport: { path.append(.importReports) }, onSettings: { path.append(.settings) })
            case .income:
                FocusIncome(state: states.income, onImport: { path.append(.importReports) })
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(FocusColors.page)
        .foregroundStyle(FocusColors.ink)
    }

    @ViewBuilder
    private func destination(_ route: FocusRoute) -> some View {
        switch route {
        case .holdings:
            FocusHoldings(state: states.portfolio, usd: usd, onOpen: { path.append(.story($0)) }, onBack: { _ = path.popLast() })
                .toolbar(.hidden, for: .navigationBar)
        case .story(let key):
            FocusStory(state: states.portfolio, income: states.income, key: key, onBack: { _ = path.popLast() }).toolbar(.hidden, for: .navigationBar)
        case .activity:
            FocusActivity(state: states.activity, onBack: { _ = path.popLast() }, onImport: { path.append(.importReports) })
                .toolbar(.hidden, for: .navigationBar)
        case .importReports:
            ImportsView(data: states.data).navigationTitle(Strings.calmAddReports)
        case .reports:
            ImportsView(data: states.data, reportsOnly: true).navigationTitle(Strings.importsHistoryTitle)
        case .settings:
            SettingsView(data: states.data).navigationTitle(Strings.settingsTitle)
        }
    }

    /// A tab tap leaves any opened screen first, so each tab opens on its first screen.
    private func open(_ target: FocusTab) {
        path = []
        tab = target
    }
}

private struct FocusBottomBar: View {
    let current: FocusTab
    let menuOpen: Bool
    let onTab: (FocusTab) -> Void
    let onMenu: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Divider().overlay(FocusColors.line)
            HStack {
                label(Strings.focusTabToday, .today)
                Spacer()
                Button(action: onMenu) {
                    Text(menuOpen ? "×" : "+").font(.system(size: 30)).foregroundStyle(.white)
                        .frame(width: 56, height: 56).background(FocusColors.ink, in: RoundedRectangle(cornerRadius: 20))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Strings.focusMenu)
                Spacer()
                label(Strings.calmTabIncome, .income)
            }
            .padding(.horizontal, 24)
            .frame(height: 72)
        }
        .background(FocusColors.page)
    }

    private func label(_ text: String, _ tab: FocusTab) -> some View {
        let on = tab == current
        return Button { onTab(tab) } label: {
            Text((on ? "● " : "") + text).font(.system(size: 14, weight: .semibold))
                .foregroundStyle(on ? FocusColors.ink : FocusColors.muted)
                .frame(width: 100).padding(.vertical, 12).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// Everything about data in one place (mobile-skin-focus "Focus navigation").
private struct FocusPlusMenu: View {
    @ObservedObject var data: DataState
    @ObservedObject var portfolio: PortfolioState
    @Binding var usd: Bool
    let onAdd: () -> Void
    let onGo: (FocusRoute) -> Void

    var body: some View {
        let source: PriceSource? = { if case .loaded(let value) = portfolio.ui { return value.priceSource } else { return nil } }()
        let backup = data.keyStatus == .ready ? (data.importReady ? "✓ " + Strings.bentoOn : Strings.bentoFolderMissing) : Strings.bentoOff
        let us = source.map { $0 == .finnhub ? Strings.bentoFinnhub : Strings.bentoYahooNoKey } ?? "—"
        ScrollView {
            VStack(spacing: 0) {
                row("＋ " + Strings.calmAddReports, "›", bold: true) { onGo(.importReports) }
                row("＋ " + Strings.manualAdd, "›", bold: true, action: onAdd)
                row(Strings.focusMenuActivity, "›") { onGo(.activity) }
                row(Strings.focusMenuAmounts, (usd ? Strings.focusAmountsUsd : Strings.focusAmountsOwn) + " ⇄") { usd.toggle() }
                row(Strings.focusMenuBackup, backup) { onGo(.settings) }
                row(Strings.focusMenuPrices, Strings.focusPricesStatus(us)) { onGo(.settings) }
                row(Strings.focusMenuSecurity, "›") { onGo(.settings) }
                row(Strings.focusMenuSettings, "›") { onGo(.settings) }
                row(Strings.focusMenuDelete, "›", color: FocusColors.loss, last: true) { onGo(.reports) }
            }
            .padding(.horizontal, 22).padding(.top, 20).padding(.bottom, 24)
        }
        .background(FocusColors.page)
        .onAppear { data.reload() }
    }

    private func row(_ label: String, _ value: String, bold: Bool = false, color: Color = FocusColors.ink, last: Bool = false, action: @escaping () -> Void) -> some View {
        VStack(spacing: 0) {
            Button(action: action) {
                HStack {
                    Text(label).font(.system(size: 16, weight: bold ? .bold : .regular)).foregroundStyle(color)
                    Spacer()
                    Text(value).font(.system(size: 13)).foregroundStyle(FocusColors.muted).multilineTextAlignment(.trailing)
                }
                .padding(.vertical, 15).contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if !last { Divider().overlay(FocusColors.line) }
        }
    }
}

/// A serif number, a sentence, a donut and the "+" bar on warm paper.
private struct FocusPreview: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            RoundedRectangle(cornerRadius: 3).fill(FocusColors.ink).frame(width: 38, height: 10)
            ForEach(0..<2, id: \.self) { _ in RoundedRectangle(cornerRadius: 2).fill(FocusColors.muted.opacity(0.5)).frame(height: 4) }
            HStack(spacing: 3) {
                RoundedRectangle(cornerRadius: 4).fill(FocusColors.deep).frame(width: 26, height: 16)
                RoundedRectangle(cornerRadius: 4).fill(FocusColors.card).frame(width: 20, height: 16)
            }
            Circle().stroke(FocusColors.slices[1], lineWidth: 6).frame(width: 18, height: 18).padding(3)
            Spacer()
            HStack {
                Spacer()
                Circle().fill(FocusColors.muted).frame(width: 6, height: 6)
                Spacer()
                RoundedRectangle(cornerRadius: 3).fill(FocusColors.ink).frame(width: 11, height: 11)
                Spacer()
                Circle().fill(FocusColors.muted).frame(width: 6, height: 6)
                Spacer()
            }
        }
        .padding(6)
        .background(FocusColors.page, in: RoundedRectangle(cornerRadius: 10))
    }
}
