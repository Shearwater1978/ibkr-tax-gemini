/// Top-level destinations. Routes and order must match the Android `AppDestination`
/// enum (see mobile/README.md) so both clients expose the same navigation.
enum AppDestination: String, CaseIterable, Identifiable {
    case portfolio
    case imports
    case settings

    static let start: AppDestination = .portfolio

    var id: String { rawValue }

    var title: String {
        switch self {
        case .portfolio: return "Portfolio"
        case .imports: return "Imports"
        case .settings: return "Settings"
        }
    }

    var systemImage: String {
        switch self {
        case .portfolio: return "house"
        case .imports: return "list.bullet"
        case .settings: return "gearshape"
        }
    }
}
