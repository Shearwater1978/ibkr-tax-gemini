import SwiftUI

/// What the manual-entry form edits: a new entry, or an existing one by id (mobile-manual-operations).
enum ManualTarget: Identifiable, Hashable {
    case new
    case edit(String)

    var id: String {
        switch self {
        case .new: return "new"
        case .edit(let entryId): return entryId
        }
    }
}

extension CalendarDay {
    /// "2026-10-13".
    var iso: String { String(format: "%04d-%02d-%02d", year, month, day) }
}

extension Notification.Name {
    /// Posted after a manual entry was added, edited or deleted; `AppStates` reloads every screen.
    static let manualOperationsChanged = Notification.Name("ibkrtax.manualOperationsChanged")
}

/// Currencies offered for manual operations, most common first; the same list as Android.
enum ManualCurrencies {
    static let common = ["USD", "EUR", "GBP", "CHF", "PLN", "CAD", "AUD", "JPY", "HKD", "SGD", "CNY", "SEK", "NOK", "DKK", "CZK", "HUF"]

    /// The list, plus `current` when an entry uses a currency outside it.
    static func choices(including current: String?) -> [String] {
        guard let current, !current.isEmpty, !common.contains(current) else { return common }
        return [current] + common
    }
}

/// Turns what the user typed into a plain decimal string: a comma works as the decimal separator (Android `plainDecimal`).
func plainDecimal(_ text: String) -> String {
    let trimmed = text.replacingOccurrences(of: " ", with: "").replacingOccurrences(of: "\u{00a0}", with: "")
    return !trimmed.contains(".") && trimmed.filter { $0 == "," }.count == 1 ? trimmed.replacingOccurrences(of: ",", with: ".") : trimmed
}

/// The shared "Add operation" form, shown as a sheet by every skin (Android `ManualEntryDialog`).
struct ManualEntryView: View {
    let target: ManualTarget
    var container: AppContainer = .shared
    @Environment(\.dismiss) private var dismiss
    @State private var type = ManualType.buy
    @State private var values: [String: String] = ["date": CalendarDay.today().iso, "currency": "USD", "fee": "0", "tax": "0"]
    @State private var problems: [ManualProblem] = []
    @State private var message: String?
    @State private var duplicate: String?
    @State private var confirmDelete = false
    @State private var busy = false

    private static let upperCase: Set<String> = ["ticker", "currency", "newTicker", "fromTicker"]
    private var entryId: String? { if case .edit(let id) = target { return id } else { return nil } }

    var body: some View {
        NavigationStack {
            Form {
                Picker(Strings.manualType, selection: $type) {
                    ForEach(ManualType.allCases, id: \.self) { Text(Strings.manualTypeName($0)).tag($0) }
                }
                .onChange(of: type) { _ in problems = [] }
                Section {
                    ForEach(type.fields.filter { $0 != "heldBefore" }, id: \.self) { name in field(name) }
                } footer: {
                    Text(Strings.manualHint)
                }
                if let message { Text(message).foregroundStyle(.red) }
                // Also in the form itself: some skins hide navigation bars, and the way out must never depend on one.
                Section {
                    Button(Strings.manualSave) { save(confirmDuplicate: false) }.bold().disabled(busy)
                    Button(Strings.cancel) { dismiss() }
                    if entryId != nil {
                        Button(Strings.manualDelete, role: .destructive) { confirmDelete = true }.disabled(busy)
                    }
                }
            }
            .navigationTitle(entryId == nil ? Strings.manualAdd : Strings.manualEdit)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(.visible, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(Strings.cancel) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button(Strings.manualSave) { save(confirmDuplicate: false) }.disabled(busy) }
            }
            .alert(Strings.manualDuplicateTitle, isPresented: Binding(get: { duplicate != nil }, set: { if !$0 { duplicate = nil } })) {
                Button(Strings.manualSaveAnyway) { save(confirmDuplicate: true) }
                Button(Strings.cancel, role: .cancel) {}
            } message: {
                Text(duplicate ?? "")
            }
            .confirmationDialog(Strings.manualDeleteTitle, isPresented: $confirmDelete, titleVisibility: .visible) {
                Button(Strings.manualDelete, role: .destructive) { delete() }
            } message: {
                Text(Strings.manualDeleteBody)
            }
            .task { await load() }
        }
    }

    @ViewBuilder
    private func field(_ name: String) -> some View {
        let problem = problems.first { $0.field == name }
        let binding = Binding(get: { values[name] ?? "" }, set: { values[name] = Self.upperCase.contains(name) ? $0.uppercased() : $0 })
        if name == "currency" {
            Picker(Strings.manualField(type, name), selection: binding) {
                ForEach(ManualCurrencies.choices(including: values[name]), id: \.self) { Text($0).tag($0) }
            }
        } else {
            // The name stays above the field: a placeholder alone disappears once the field has a value.
            VStack(alignment: .leading, spacing: 2) {
                Text(Strings.manualField(type, name)).font(.caption).foregroundStyle(.secondary)
                TextField(Strings.manualField(type, name), text: binding)
                    .keyboardType(Self.upperCase.contains(name) ? .asciiCapable : (name == "date" ? .numbersAndPunctuation : .decimalPad))
                    .textInputAutocapitalization(Self.upperCase.contains(name) ? .characters : .never)
                    .autocorrectionDisabled()
                if let problem {
                    Text(Strings.manualProblem(type, problem, sameTicker: values["newTicker"] == values["ticker"])).font(.footnote).foregroundStyle(.red)
                }
            }
        }
    }

    private func load() async {
        guard let entryId else { return }
        let entry = await Task.detached { [container] in try? container.manualEntries().get(entryId) }.value
        guard let entry, let loaded = ManualType(rawValue: entry.type),
              let fields = try? JSONSerialization.jsonObject(with: Data(entry.fields.utf8)) as? [String: String] else { return dismiss() }
        type = loaded
        values = fields
    }

    private func save(confirmDuplicate: Bool) {
        busy = true
        message = nil
        let fields = Dictionary(uniqueKeysWithValues: type.fields.map { name in
            (name, ["date", "ticker", "currency", "newTicker", "fromTicker"].contains(name) ? (values[name] ?? "") : plainDecimal(values[name] ?? ""))
        })
        let type = self.type, entryId = self.entryId
        Task {
            let result = await Task.detached { [container] in try? container.manualOperations().save(type, fields, entryId: entryId, confirmDuplicate: confirmDuplicate) }.value
            busy = false
            if case .problems(let found)? = result { problems = found } else { problems = [] }
            switch result {
            case .saved?: finish()
            case .problems?: break
            case .nothingHeld(let ticker, let date)?: message = Strings.manualNothingHeld(ticker, Formats.date(date))
            case .oversold(let oversell)?: message = Strings.manualOversold(Formats.quantity(oversell.held), oversell.ticker, Formats.date(oversell.date))
            case .possibleDuplicate(_, let existing)?:
                duplicate = Strings.manualDuplicateBody(Formats.date(existing.date), Strings.manualTypeName(type), existing.ticker)
            case nil: message = Strings.storageUnavailable
            }
        }
    }

    private func delete() {
        guard let entryId else { return }
        busy = true
        Task {
            let result = await Task.detached { [container] in try? container.manualOperations().delete(entryId) }.value
            busy = false
            switch result {
            case .deleted?, .notFound?: finish()
            case .oversold(let oversell)?: message = Strings.manualOversold(Formats.quantity(oversell.held), oversell.ticker, Formats.date(oversell.date))
            case nil: message = Strings.storageUnavailable
            }
        }
    }

    private func finish() {
        NotificationCenter.default.post(name: .manualOperationsChanged, object: nil)
        dismiss()
    }
}

/// "Manual" next to a manual row's type, in every skin.
func manualTypeLabel(_ operation: OperationRecord) -> String {
    OperationDisplay.typeLabel(operation.eventType) + (operation.entryId != nil ? " · " + Strings.manualLabel : "")
}
