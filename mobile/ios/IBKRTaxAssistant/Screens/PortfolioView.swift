import SwiftUI

/// Empty-state shell; holdings arrive with the import and FIFO tasks.
struct PortfolioView: View {
    let onImportReport: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Text(Strings.portfolioEmptyTitle).font(.title2)
            Text(Strings.portfolioEmptyBody).multilineTextAlignment(.center)
            Button(Strings.actionImportReport, action: onImportReport)
                .buttonStyle(.borderedProminent)
            Text(Strings.informationalNotice)
                .font(.footnote)
                .multilineTextAlignment(.center)
                .padding(.top, 8)
        }
        .padding(24)
    }
}
