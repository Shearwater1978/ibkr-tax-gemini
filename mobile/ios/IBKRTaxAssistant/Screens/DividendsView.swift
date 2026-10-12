import SwiftUI

/// Empty-state shell; dividends arrive with the import task.
struct DividendsView: View {
    var body: some View {
        Text(Strings.dividendsEmpty)
            .multilineTextAlignment(.center)
            .padding(24)
    }
}
