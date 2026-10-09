import SwiftUI

/// Import stays disabled until the mobile-encrypted-drive-backup storage exists (task 1.2).
struct ImportsView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(Strings.importsTitle).font(.title2)
            Text(Strings.importsSupportedFormat)
            Button(Strings.actionImportReport) {}
                .buttonStyle(.borderedProminent)
                .disabled(true)
                .padding(.top, 8)
            Text(Strings.importsUnavailable).font(.footnote)
            Text(Strings.importsEmpty).padding(.top, 16)
            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(24)
    }
}
