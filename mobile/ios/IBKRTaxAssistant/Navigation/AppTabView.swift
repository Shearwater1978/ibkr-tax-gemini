import SwiftUI

struct AppTabView: View {
    @State private var selection: AppDestination = .start
    @StateObject private var portfolio = PortfolioState(container: .shared)
    @StateObject private var data = DataState(container: .shared)
    @StateObject private var income = IncomeState(container: .shared)
    @StateObject private var activity = ActivityState(container: .shared)

    var body: some View {
        TabView(selection: $selection) {
            ForEach(AppDestination.allCases) { destination in
                screen(for: destination)
                    .tabItem { Label(destination.title, systemImage: destination.systemImage) }
                    .tag(destination)
            }
        }
        // Any import change reloads the portfolio for every tab.
        .onAppear {
            data.onChange = { [weak portfolio, weak income, weak activity] in
                portfolio?.reload()
                income?.reload()
                activity?.reload()
            }
        }
    }

    @ViewBuilder
    private func screen(for destination: AppDestination) -> some View {
        switch destination {
        case .portfolio:
            PortfolioView(state: portfolio, onImportReport: { selection = .imports })
        case .dividends:
            DividendsView(state: income, onImportReport: { selection = .imports })
        case .operations:
            OperationsView(state: activity, onImportReport: { selection = .imports })
        case .imports:
            ImportsView(data: data)
        case .settings:
            SettingsView(data: data)
        }
    }
}
