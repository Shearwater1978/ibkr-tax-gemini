import SwiftUI

/// The Classic skin (Android `ClassicSkin`): five tabs, every number on one screen.
let classicSkin = Skin(
    id: .classic,
    name: Strings.skinClassicName,
    description: Strings.skinClassicDescription,
    features: [
        .portfolioValueAndToday: "Portfolio: summary cards",
        .positionDetails: "Portfolio: tap a row",
        .soldPositions: "Portfolio: Show sold positions",
        .ownCurrencyAndUsd: "Portfolio: Own currency / USD switch",
        .topMovers: "Portfolio: Today's top movers",
        .dividendsByYearAndMonth: "Dividends tab",
        .payerHistory: "Dividends: tap a position; position details: Dividend history",
        .activitySearchAndFilters: "Operations tab",
        .reportImport: "Imports tab",
        .backupKeysAndLocation: "Settings tab",
        .priceKey: "Settings tab",
        .deleteImports: "Imports tab",
        .appVersion: "Settings tab",
        .skinSetting: "Settings tab",
        .manualEntry: "Operations tab: Add operation; tap a Manual row to edit",
    ],
    app: { AnyView(AppTabView(states: $0)) },
    preview: { AnyView(ClassicPreview()) }
)

struct AppTabView: View {
    @ObservedObject var states: AppStates
    @State private var selection: AppDestination = .start

    var body: some View {
        TabView(selection: $selection) {
            ForEach(AppDestination.allCases) { destination in
                screen(for: destination)
                    .tabItem { Label(destination.title, systemImage: destination.systemImage) }
                    .tag(destination)
            }
        }
    }

    @ViewBuilder
    private func screen(for destination: AppDestination) -> some View {
        switch destination {
        case .portfolio:
            PortfolioView(state: states.portfolio, onImportReport: { selection = .imports })
        case .dividends:
            DividendsView(state: states.income, onImportReport: { selection = .imports })
        case .operations:
            OperationsView(state: states.activity, onImportReport: { selection = .imports })
        case .imports:
            ImportsView(data: states.data)
        case .settings:
            SettingsView(data: states.data)
        }
    }
}

/// Two cards, a dense table and five tab dots.
private struct ClassicPreview: View {
    var body: some View {
        VStack(spacing: 3) {
            HStack(spacing: 3) { ForEach(0..<2, id: \.self) { _ in RoundedRectangle(cornerRadius: 3).fill(.gray.opacity(0.3)).frame(height: 14) } }
            ForEach(0..<9, id: \.self) { _ in
                HStack(spacing: 2) { ForEach(0..<5, id: \.self) { _ in Rectangle().fill(.gray.opacity(0.3)).frame(height: 4) } }
            }
            Spacer()
            HStack { ForEach(0..<5, id: \.self) { _ in Circle().fill(.gray.opacity(0.3)).frame(width: 5, height: 5).frame(maxWidth: .infinity) } }
        }
        .padding(5)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(.gray.opacity(0.3)))
    }
}
