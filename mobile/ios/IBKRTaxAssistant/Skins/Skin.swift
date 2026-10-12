import SwiftUI

/// Stored by name; never rename a case, or users of it fall back to Classic. Same names as Android.
enum SkinId: String, CaseIterable {
    case classic = "CLASSIC"
    case calm = "CALM"
    case bento = "BENTO"
    case focus = "FOCUS"
}

/// Everything every skin must reach, directly or behind taps (mobile-skins "Keep feature parity").
enum Feature: CaseIterable {
    case portfolioValueAndToday
    case positionDetails
    case soldPositions
    case ownCurrencyAndUsd
    case topMovers
    case dividendsByYearAndMonth
    case payerHistory
    case activitySearchAndFilters
    case reportImport
    case backupKeysAndLocation
    case priceKey
    case deleteImports
    case appVersion
    case skinSetting
}

/**
 * A complete user interface after unlock (mobile-skins spec), as the Android `Skin`: it owns its
 * look, navigation and screens, and reads only `AppStates`, so data, numbers and security stay shared.
 */
struct Skin {
    let id: SkinId
    let name: String
    let description: String
    /// Where each `Feature` lives in this skin; reviewed per skin and checked to cover all features.
    let features: [Feature: String]
    let app: @MainActor (AppStates) -> AnyView
    /// A small drawn thumbnail for the picker; never shows the user's data.
    let preview: () -> AnyView
}

enum SkinCatalog {
    /// Bento and Focus join as they are ported from Android.
    static let skins: [Skin] = [classicSkin, calmSkin]

    static let `default` = classicSkin

    /// Preselected in the first-launch picker.
    static var firstLaunch: Skin { skins.first { $0.id == .calm } ?? `default` }

    /// The skin for a stored name; missing or unknown names (e.g. after a downgrade) give Classic.
    static func resolve(_ stored: String?) -> Skin { skins.first { $0.id.rawValue == stored } ?? `default` }
}

/// Supported brokers (mobile-broker-choice spec). Stored by name; never rename a case. Same names as Android.
enum BrokerId: String, CaseIterable {
    case interactiveBrokers = "INTERACTIVE_BROKERS"

    var name: String { Strings.brokerIbkrName }
    var format: String { Strings.brokerIbkrFormat }
    var reportSteps: [String] { Strings.ibkrReportSteps }

    /// The broker for a stored name; nil when none or an unknown one is stored.
    static func resolve(_ stored: String?) -> BrokerId? { allCases.first { $0.rawValue == stored } }
}

/// The non-sensitive UI choices (Android `UiPreferences`); "Erase all data" clears them with the other settings.
enum UiPreferenceKey {
    static let skin = "skin"
    static let skinNoticePending = "skin_notice_pending"
    static let broker = "broker"
    static let brokerStepDone = "broker_step_done"
}
