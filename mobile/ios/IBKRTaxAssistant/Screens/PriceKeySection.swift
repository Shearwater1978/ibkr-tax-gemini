import SafariServices
import SwiftUI

/**
 * Market prices in Settings (Android `PriceKeySettings`): guides the user to a free Finnhub key,
 * checks it once, and only ever shows it masked. Finnhub opens in an in-app Safari view, so the app
 * never sees the Finnhub password. iOS asks before an app reads the clipboard, so the key comes in
 * through the system Paste button instead of an automatic suggestion.
 */
struct PriceKeySection: View {
    private static let signUp = URL(string: "https://finnhub.io/register")!

    @State private var savedMasked: String?
    @State private var input = ""
    @State private var checking = false
    @State private var message: String?
    @State private var showingFinnhub = false

    var body: some View {
        Section {
            Text(Strings.settingsPricesBody).font(.footnote)
            if let savedMasked {
                Text(Strings.settingsApiKeySaved(savedMasked))
                if let message { Text(message).font(.footnote) }
                Button(Strings.settingsApiKeyRemove, role: .destructive) {
                    Task {
                        await Task.detached { try? AppContainer.shared.priceStore().clear() }.value
                        message = nil
                        await load()
                    }
                }
            } else {
                Text(Strings.keyGuideTitle).bold()
                ForEach([Strings.keyGuideStep1, Strings.keyGuideStep2, Strings.keyGuideStep3], id: \.self) { Text($0).font(.footnote) }
                Button(Strings.keyGuideOpen) { showingFinnhub = true }
                HStack {
                    SecureField(Strings.settingsApiKeyLabel, text: $input).textInputAutocapitalization(.never).autocorrectionDisabled()
                    PasteButton(payloadType: String.self) { strings in
                        if let pasted = strings.first?.trimmingCharacters(in: .whitespacesAndNewlines) { input = pasted }
                    }
                    .labelStyle(.titleOnly)
                    .buttonBorderShape(.capsule)
                }
                Button(checking ? Strings.keyChecking : Strings.settingsApiKeySave) { save(input) }
                    .disabled(input.trimmingCharacters(in: .whitespaces).isEmpty || checking)
                if let message { Text(message).font(.footnote) }
            }
        } header: {
            Text(Strings.settingsPricesTitle)
        }
        .sheet(isPresented: $showingFinnhub) { SafariView(url: Self.signUp).ignoresSafeArea() }
        .task { await load() }
    }

    private func load() async {
        savedMasked = await Task.detached { (try? AppContainer.shared.priceStore().get()).flatMap { $0 }.map(Masking.lastFour) }.value
    }

    /// One test request with only a well-known symbol; a rejected key is not saved (Android `ApiKeyCheck`).
    private func save(_ key: String) {
        let key = key.trimmingCharacters(in: .whitespacesAndNewlines)
        checking = true
        message = nil
        Task {
            let result = await ApiKeyCheck.check(FinnhubProvider(apiKey: key))
            if result != .rejected {
                await Task.detached { try? AppContainer.shared.priceStore().set(key) }.value
                input = ""
                await load()
            }
            switch result {
            case .valid: message = Strings.keyCheckValid
            case .rejected: message = Strings.keyCheckRejected
            case .unverified: message = Strings.keyCheckUnverified
            }
            checking = false
        }
    }
}

/// Finnhub's sign-up page in an in-app Safari view; its cookies and typing stay out of the app.
private struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController { SFSafariViewController(url: url) }

    func updateUIViewController(_ controller: SFSafariViewController, context: Context) {}
}

/// A short version of mobile/PRIVACY.md (task 7.8), as the Android `PrivacySection`.
struct PrivacySection: View {
    var body: some View {
        Section(Strings.privacyTitle) {
            ForEach([Strings.privacyOnDevice, Strings.privacyLeaves, Strings.privacyChoices], id: \.self) { Text($0).font(.footnote) }
        }
    }
}
