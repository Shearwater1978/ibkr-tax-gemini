import SwiftUI

/// Empty-state shell; operations arrive with the import task.
struct OperationsView: View {
    var body: some View {
        Text(Strings.operationsEmpty)
            .multilineTextAlignment(.center)
            .padding(24)
    }
}
