import SwiftUI

struct AppTabView: View {
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
            PortfolioView(onImportReport: { selection = .imports })
        case .imports:
            ImportsView()
        case .settings:
            SettingsView()
        }
    }
}
