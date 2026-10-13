import Foundation

/// Where manual entries live; implemented by the encrypted database (`ManualEntries`).
protocol ManualStore {
    /// Every operation, imported and manual, by date and then insertion order.
    func allRows() throws -> [StoredRow]
    func save(_ entry: ManualEntry) throws
    @discardableResult func delete(_ entryId: String) throws -> Bool
}

extension ManualEntries: ManualStore {}

enum ManualSaveResult: Equatable {
    case saved(entryId: String)
    case problems([ManualProblem])
    /// A split of shares that were not held before its date.
    case nothingHeld(ticker: String, date: String)
    case oversold(Oversell)
    /// Not saved: the rows look like existing operations; save again with `confirmDuplicate` to keep both.
    case possibleDuplicate(manual: StoredTransaction, existing: StoredTransaction)
}

enum ManualDeleteResult: Equatable {
    case deleted
    case notFound
    case oversold(Oversell)
}

/// Adds, edits and deletes manual entries with the checks of Decisions 3–5. Port of the Android `ManualOperations`.
struct ManualOperations {
    let store: ManualStore
    var now: () -> Date = Date.init
    var today: () -> CalendarDay = { CalendarDay.today() }
    var newId: () -> String = { UUID().uuidString.lowercased() }
    /// Runs after every saved or deleted entry; the app backs up the manual journal here.
    var onChanged: () -> Void = {}

    /// Adds an entry, or replaces `entryId`'s entry. Numbers in `fields` are plain decimal strings.
    func save(_ type: ManualType, _ fields: [String: String], entryId: String? = nil, confirmDuplicate: Bool = false) throws -> ManualSaveResult {
        let typed = fields.filter { type.fields.contains($0.key) && $0.key != "heldBefore" }.mapValues { $0.trimmingCharacters(in: .whitespaces) }
        let all = try store.allRows()
        let others = all.filter { entryId == nil || $0.entryId != entryId }.map(\.row)
        // heldBefore is the app's, not the user's; it is checked once the typed fields are valid.
        let problems = ManualValidation.check(type, type == .split ? typed.merging(["heldBefore": "0"]) { a, _ in a } : typed, today: today())
        if !problems.isEmpty { return .problems(problems) }
        var complete = typed
        if type == .split {
            let ticker = typed["ticker"] ?? "", date = typed["date"] ?? ""
            // Additions on the split date count after it (FIFO applies splits first), so only earlier days.
            let held = ManualFifo.heldBefore(ManualFifo.events(others.filter { $0.date < date }), ticker: ticker, date: date)
            guard held.signum > 0 else { return .nothingHeld(ticker: ticker, date: date) }
            complete["heldBefore"] = held.description
        }

        let id = entryId ?? newId()
        let imported = all.filter { $0.entryId == nil }.map(\.row)
        let rows = ManualRows.build(entryId: id, type: type, fields: complete, instrument: ManualInstruments.lookup(imported))
        if let oversell = ManualFifo.oversell(before: ManualFifo.events(all.map(\.row)), after: ManualFifo.events(others + rows)) {
            return .oversold(oversell)
        }
        if !confirmDuplicate, let match = ManualDuplicates.find(rows, in: others).first {
            return .possibleDuplicate(manual: match.0, existing: match.1)
        }
        try store.save(ManualEntry(entryId: id, type: type.rawValue, fields: Self.json(complete), enteredAt: Self.iso(now()), rows: rows))
        onChanged()
        return .saved(entryId: id)
    }

    func delete(_ entryId: String) throws -> ManualDeleteResult {
        let all = try store.allRows()
        guard all.contains(where: { $0.entryId == entryId }) else { return .notFound }
        let remaining = all.filter { $0.entryId != entryId }.map(\.row)
        if let oversell = ManualFifo.oversell(before: ManualFifo.events(all.map(\.row)), after: ManualFifo.events(remaining)) {
            return .oversold(oversell)
        }
        guard try store.delete(entryId) else { return .notFound }
        onChanged()
        return .deleted
    }

    /// Manual rows that match imported ones, each with the imported row; shown after an import.
    func duplicatesOfImports() throws -> [(StoredTransaction, StoredTransaction)] {
        let all = try store.allRows()
        return ManualDuplicates.find(all.filter { $0.entryId != nil }.map(\.row), in: all.filter { $0.entryId == nil }.map(\.row))
    }

    /// The form as a JSON object of strings, keys sorted, like the journal's `fields`.
    static func json(_ fields: [String: String]) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: fields, options: [.sortedKeys]) else { return "{}" }
        return String(decoding: data, as: UTF8.self)
    }

    /// `Instant.toString()` at second precision, e.g. "2026-10-13T08:00:00Z".
    static func iso(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.string(from: date)
    }
}
