import SwiftUI

/**
 * Everything a skin reads and acts on (Android `AppStates`). Created once per app session, so a skin
 * switch keeps the loaded data and every skin shows identical numbers.
 */
@MainActor
final class AppStates: ObservableObject {
    let portfolio: PortfolioState
    let data: DataState
    let income: IncomeState
    let activity: ActivityState

    init(container: AppContainer = .shared) {
        portfolio = PortfolioState(container: container)
        data = DataState(container: container)
        income = IncomeState(container: container)
        activity = ActivityState(container: container)
        // Any import, deletion, restore or erase reloads every screen.
        data.onChange = { [weak portfolio, weak income, weak activity] in
            portfolio?.reload()
            income?.reload()
            activity?.reload()
        }
    }
}

/**
 * Draws the chosen skin after unlock (Android `SkinRoot`). On the very first start it asks for a skin
 * (once there is more than one) and the broker; an app that already holds data keeps Classic and
 * Interactive Brokers and shows a notice once.
 */
struct SkinRoot: View {
    @StateObject private var states = AppStates()
    @AppStorage(UiPreferenceKey.skin) private var stored: String?
    @AppStorage(UiPreferenceKey.skinNoticePending) private var noticePending = false
    @AppStorage(UiPreferenceKey.broker) private var broker: String?
    @AppStorage(UiPreferenceKey.brokerStepDone) private var brokerStepDone = false
    @State private var hasData: Bool?
    @State private var pickerOpen = false

    var body: some View {
        Group {
            if stored == nil || (BrokerId.resolve(broker) == nil && !brokerStepDone) {
                firstLaunch
            } else {
                SkinCatalog.resolve(stored).app(states)
                    .alert(Strings.skinNoticeTitle, isPresented: $noticePending) {
                        Button(Strings.skinNoticeShow) { pickerOpen = true }
                        Button(Strings.skinNoticeLater, role: .cancel) {}
                    } message: {
                        Text(Strings.skinNoticeBody)
                    }
                    .sheet(isPresented: $pickerOpen) { SkinPickerSheet() }
            }
        }
    }

    @ViewBuilder
    private var firstLaunch: some View {
        switch hasData {
        case nil:
            Color.clear.task {
                hasData = await Task.detached { ((try? AppContainer.shared.importHistory().list().isEmpty) ?? true) == false }.value
            }
        case true?:
            // Data on the device means an earlier version: keep Classic and Interactive Brokers.
            Color.clear.onAppear {
                if stored == nil {
                    stored = SkinId.classic.rawValue
                    noticePending = SkinCatalog.skins.count > 1
                }
                if BrokerId.resolve(broker) == nil { chooseBroker(.interactiveBrokers) }
            }
        case false?:
            if stored == nil {
                if SkinCatalog.skins.count > 1 {
                    FirstLaunchPicker { stored = $0.rawValue }
                } else {
                    Color.clear.onAppear { stored = SkinCatalog.default.id.rawValue }
                }
            } else {
                FirstLaunchBrokerStep(onChoose: chooseBroker, onLater: { brokerStepDone = true })
            }
        }
    }

    private func chooseBroker(_ id: BrokerId) {
        broker = id.rawValue
        brokerStepDone = true
    }
}

/// The catalog with previews; tapping selects.
struct SkinList: View {
    @Binding var selected: SkinId

    var body: some View {
        ForEach(SkinCatalog.skins, id: \.id) { skin in
            Button { selected = skin.id } label: {
                HStack(spacing: 12) {
                    skin.preview().frame(width: 64, height: 104)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(skin.name).font(.headline)
                        Text(skin.description).font(.footnote).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: skin.id == selected ? "largecircle.fill.circle" : "circle").foregroundStyle(Color.accentColor)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }
}

private struct FirstLaunchPicker: View {
    let onContinue: (SkinId) -> Void
    @State private var selected = SkinCatalog.firstLaunch.id

    var body: some View {
        List {
            Section {
                SkinList(selected: $selected)
            } header: {
                VStack(alignment: .leading, spacing: 6) {
                    Text(Strings.skinPickerTitle).font(.title2.bold()).foregroundStyle(.primary)
                    Text(Strings.skinPickerBody).textCase(nil)
                }
            } footer: {
                Text(Strings.skinPickerLater)
            }
            Button(Strings.continueAction) { onContinue(selected) }.buttonStyle(.borderedProminent)
        }
    }
}

/// Changing the skin from a notice or Settings.
struct SkinPickerSheet: View {
    @Environment(\.dismiss) private var dismiss
    @AppStorage(UiPreferenceKey.skin) private var stored: String?
    @State private var selected = SkinCatalog.resolve(UserDefaults.standard.string(forKey: UiPreferenceKey.skin)).id

    var body: some View {
        NavigationStack {
            List { SkinList(selected: $selected) }
                .navigationTitle(Strings.skinSettingsTitle)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button(Strings.cancel) { dismiss() } }
                    ToolbarItem(placement: .confirmationAction) { Button(Strings.skinApply) { stored = selected.rawValue; dismiss() } }
                }
        }
    }
}

/// The broker list with the formats it reads.
struct BrokerList: View {
    @Binding var selected: BrokerId?

    var body: some View {
        ForEach(BrokerId.allCases, id: \.self) { broker in
            Button { selected = broker } label: {
                HStack {
                    VStack(alignment: .leading) {
                        Text(broker.name).font(.headline)
                        Text(broker.format).font(.footnote).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Image(systemName: broker == selected ? "largecircle.fill.circle" : "circle").foregroundStyle(Color.accentColor)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        Text(Strings.brokerMoreLater).font(.footnote).foregroundStyle(.secondary)
    }
}

/// The first-launch question after the skin; "Choose later" leaves the broker unset.
private struct FirstLaunchBrokerStep: View {
    let onChoose: (BrokerId) -> Void
    let onLater: () -> Void
    @State private var selected: BrokerId?

    var body: some View {
        List {
            Section {
                BrokerList(selected: $selected)
            } header: {
                VStack(alignment: .leading, spacing: 6) {
                    Text(Strings.brokerQuestion).font(.title2.bold()).foregroundStyle(.primary)
                    Text(Strings.brokerQuestionBody).textCase(nil)
                }
            }
            Button(Strings.continueAction) { selected.map(onChoose) }.buttonStyle(.borderedProminent).disabled(selected == nil)
            Button(Strings.brokerLater, action: onLater)
        }
    }
}

/// Shown on the import screen while no broker is chosen (mobile-broker-choice "Ask for the broker before importing").
struct BrokerGate: View {
    @AppStorage(UiPreferenceKey.broker) private var broker: String?
    @State private var selected: BrokerId?

    var body: some View {
        Section {
            BrokerList(selected: $selected)
            Button(Strings.continueAction) { broker = selected?.rawValue }.disabled(selected == nil)
        } header: {
            Text(Strings.brokerQuestion)
        } footer: {
            Text(Strings.brokerQuestionBody)
        }
    }
}

/// The "Skin" and "Broker" entries every skin's Settings shows.
struct SkinAndBrokerSettingsSection: View {
    @AppStorage(UiPreferenceKey.skin) private var stored: String?
    @AppStorage(UiPreferenceKey.broker) private var broker: String?
    @State private var choosingSkin = false
    @State private var choosingBroker = false
    @State private var selectedBroker: BrokerId?

    var body: some View {
        Section(Strings.skinSettingsTitle) {
            Text(Strings.settingsCurrent(SkinCatalog.resolve(stored).name))
            Button(Strings.skinSettingsChange) { choosingSkin = true }
        }
        .sheet(isPresented: $choosingSkin) { SkinPickerSheet() }
        Section(Strings.brokerSettingsTitle) {
            Text(Strings.settingsCurrent(BrokerId.resolve(broker)?.name ?? Strings.brokerNotChosen))
            if choosingBroker {
                BrokerList(selected: $selectedBroker)
                Button(Strings.brokerUse) { broker = selectedBroker?.rawValue; choosingBroker = false }.disabled(selectedBroker == nil)
            } else {
                Button(Strings.brokerChange) { selectedBroker = BrokerId.resolve(broker); choosingBroker = true }
            }
        }
    }
}
