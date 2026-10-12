import SwiftUI

/// Imported transactions, newest first, with ticker search and type filters (Android Classic `OperationsScreen`, mobile-operations spec).
struct OperationsView: View {
    @ObservedObject var state: ActivityState
    let onImportReport: () -> Void

    var body: some View {
        let view = state.view
        Group {
            switch view.hasAny {
            case nil:
                Text(Strings.loading)
            case false?:
                VStack(spacing: 16) {
                    Text(Strings.operationsEmpty).multilineTextAlignment(.center)
                    Button(Strings.actionImportReport, action: onImportReport).buttonStyle(.borderedProminent)
                }
                .padding(24)
            case true?:
                List {
                    Section {
                        TextField(Strings.operationsSearch, text: Binding(get: { view.query }, set: state.setQuery))
                            .textInputAutocapitalization(.characters)
                            .autocorrectionDisabled()
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(OperationFilter.allCases, id: \.self) { filter in
                                    let isOn = view.filters.contains(filter)
                                    Button { state.toggle(filter) } label: {
                                        Text(OperationDisplay.filterLabel(filter))
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 6)
                                            .background(isOn ? Color.accentColor.opacity(0.2) : Color.clear, in: Capsule())
                                            .overlay(Capsule().stroke(isOn ? Color.accentColor : Color.secondary.opacity(0.4)))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    } header: {
                        Text(Strings.operationsTitle)
                    }
                    Section {
                        if view.items.isEmpty {
                            Text(Strings.operationsNoMatch)
                            Button(Strings.operationsClearAll, action: state.clear)
                        }
                        ForEach(view.items, id: \.id) { operation in
                            OperationRow(operation: operation)
                                // Load the next page shortly before the end of the list.
                                .onAppear { if operation.id == view.items.dropLast(20).last?.id || operation.id == view.items.last?.id { state.loadMore() } }
                        }
                    }
                }
                .listStyle(.plain)
            }
        }
        .onAppear { state.reload() }
    }
}

private struct OperationRow: View {
    let operation: OperationRecord

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(operation.ticker).bold()
                Text(OperationDisplay.typeLabel(operation.eventType)).font(.footnote).foregroundStyle(.secondary)
                Spacer()
                Text(OperationDisplay.amount(operation)).monospacedDigit()
            }
            Text(details).font(.footnote).foregroundStyle(.secondary)
        }
    }

    private var details: String {
        var parts = [Formats.date(operation.date)]
        if let trade = OperationDisplay.details(operation) { parts.append(trade) }
        if operation.fee.signum != 0 { parts.append(Strings.operationsCommission(Formats.money(operation.fee.magnitude, operation.currency))) }
        return parts.joined(separator: " · ")
    }
}
