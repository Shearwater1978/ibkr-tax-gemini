import SwiftUI

/// Calm's palette (mobile-skin-calm): white, one blue accent, soft green/red chips. Same values as Android.
enum CalmColors {
    static let accent = Color(hex: 0x2F6BFF)
    static let accentSoft = Color(hex: 0xE8EFFF)
    static let ink = Color(hex: 0x0F1419)
    static let muted = Color(hex: 0x6B7280)
    static let line = Color(hex: 0xECEEF1)
    static let card = Color(hex: 0xF6F7F9)
    static let gain = Color(hex: 0x0A8F5A)
    static let gainSoft = Color(hex: 0xE5F6EE)
    static let loss = Color(hex: 0xD6453D)
    static let lossSoft = Color(hex: 0xFDECEA)
}

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

/// Own-currency amounts with a symbol where one is common ("$1,234.50", "€12.00"), else the code (Android `skinMoney`).
func skinMoney(_ value: ExactDecimal, _ currency: String, signed: Bool = false) -> String {
    let symbol = ["USD": "$", "EUR": "€", "GBP": "£"][currency]
    let sign = signed && value.signum > 0 ? "+" : (value.signum < 0 ? "−" : "")
    let number = Formats.number(value.magnitude, 2, 2)
    return symbol.map { "\(sign)\($0)\(number)" } ?? "\(sign)\(number) \(currency)"
}

func skinPercent(_ value: ExactDecimal) -> String { Formats.signedPercent(value).replacingOccurrences(of: "-", with: "−") }

/// A stable colour per ticker for the letter tiles; Java's `String.hashCode`, so both apps pick the same colour.
func tickerColor(_ ticker: String) -> Color {
    let palette: [UInt32] = [0x111111, 0x2B8A3E, 0x0A66C2, 0xE11D48, 0x1D4ED8, 0x0B3D91, 0xB45309, 0x7C3AED, 0x0E7490]
    let hash = ticker.utf16.reduce(Int32(0)) { $0 &* 31 &+ Int32($1) }
    let index = ((Int(hash) % palette.count) + palette.count) % palette.count
    return Color(hex: palette[index])
}

struct TickerTile: View {
    let ticker: String
    var size: CGFloat = 40

    var body: some View {
        Text(String(ticker.prefix(4)))
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(.white)
            .lineLimit(1)
            .frame(width: size, height: size)
            .background(tickerColor(ticker), in: RoundedRectangle(cornerRadius: 12))
    }
}

struct CalmChip: View {
    let text: String
    let positive: Bool

    var body: some View {
        Text(text)
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(positive ? CalmColors.gain : CalmColors.loss)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(positive ? CalmColors.gainSoft : CalmColors.lossSoft, in: Capsule())
    }
}

func calmSignColor(_ value: ExactDecimal?) -> Color {
    switch value?.signum ?? 0 {
    case 1: return CalmColors.gain
    case -1: return CalmColors.loss
    default: return CalmColors.muted
    }
}

/// Light and minimal: Home, Income and Activity; import behind "+", settings behind the avatar.
let calmSkin = Skin(
    id: .calm,
    name: Strings.skinCalmName,
    description: Strings.skinCalmDescription,
    features: [
        .portfolioValueAndToday: "Home: hero value and today chip",
        .positionDetails: "Home: tap a holding (sheet)",
        .soldPositions: "Home: View > Show sold positions",
        .ownCurrencyAndUsd: "Home: View > Amounts in",
        .topMovers: "Home: View > Sort by today",
        .dividendsByYearAndMonth: "Income tab",
        .payerHistory: "Income: tap a payer; position sheet: Dividend history",
        .activitySearchAndFilters: "Activity tab",
        .reportImport: "Home: + button",
        .backupKeysAndLocation: "Home: avatar > Settings",
        .priceKey: "Home: avatar > Settings",
        .deleteImports: "Home: + button (import screen)",
        .appVersion: "Home: avatar > Settings",
        .skinSetting: "Home: avatar > Settings",
        .manualEntry: "Activity tab: Add operation; tap a Manual row to edit",
    ],
    app: { AnyView(CalmApp(states: $0)) },
    preview: { AnyView(CalmPreview()) }
)

private enum CalmTab: Hashable {
    case home
    case income
    case activity
}

enum CalmRoute: Hashable {
    case importReports
    case settings
}

private struct CalmApp: View {
    @ObservedObject var states: AppStates
    @State private var tab = CalmTab.home
    @State private var path: [CalmRoute] = []

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack(path: $path) {
                CalmHome(state: states.portfolio, income: states.income, onImport: { path.append(.importReports) }, onSettings: { path.append(.settings) })
                    .navigationDestination(for: CalmRoute.self) { route in
                        switch route {
                        case .importReports: ImportsView(data: states.data).navigationTitle(Strings.calmAddReports).toolbar(.hidden, for: .tabBar)
                        case .settings: SettingsView(data: states.data).navigationTitle(Strings.settingsTitle).toolbar(.hidden, for: .tabBar)
                        }
                    }
                    .toolbar(.hidden, for: .navigationBar)
            }
            .tabItem { Label(Strings.calmTabHome, systemImage: "house") }
            .tag(CalmTab.home)
            CalmIncome(state: states.income, onImport: { tab = .home; path = [.importReports] })
                .tabItem { Label(Strings.calmTabIncome, systemImage: "calendar") }
                .tag(CalmTab.income)
            CalmActivity(state: states.activity, onImport: { tab = .home; path = [.importReports] })
                .tabItem { Label(Strings.calmTabActivity, systemImage: "list.bullet") }
                .tag(CalmTab.activity)
        }
        .tint(CalmColors.accent)
        .preferredColorScheme(.light)
    }
}

/// A big number, a green chip and a few rows on white.
private struct CalmPreview: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            RoundedRectangle(cornerRadius: 3).fill(CalmColors.ink).frame(width: 40, height: 10)
            RoundedRectangle(cornerRadius: 3).fill(CalmColors.gainSoft).frame(width: 26, height: 6)
            ForEach(0..<5, id: \.self) { _ in
                HStack(spacing: 3) {
                    RoundedRectangle(cornerRadius: 2).fill(CalmColors.accent).frame(width: 7, height: 7)
                    Rectangle().fill(CalmColors.line).frame(height: 4)
                    Rectangle().fill(CalmColors.gain).frame(width: 10, height: 4)
                }
            }
            Spacer()
            HStack { ForEach(0..<3, id: \.self) { _ in Circle().fill(CalmColors.muted).frame(width: 6, height: 6).frame(maxWidth: .infinity) } }
        }
        .padding(6)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(CalmColors.line))
    }
}
