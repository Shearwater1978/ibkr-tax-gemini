import SwiftUI

struct SettingsView: View {
    private var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(Strings.settingsTitle).font(.title2)
            Text(Strings.settingsPrivacy)
            Text(Strings.informationalNotice).font(.footnote).padding(.top, 8)
            Text("Version \(version)").font(.footnote).padding(.top, 8)
            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(24)
    }
}
