import CryptoKit
import Foundation

/**
 * The manual journal payload (mobile-manual-operations Decision 6): every manual entry with its form and rows, as
 * UTF-8 JSON. The format is documented in `mobile/fixtures/manual/README.md`; Android writes and reads the same.
 */
enum ManualJournal {
    static let kind = "manual-journal"
    static let version = 1

    struct Decoded: Equatable {
        var sequence: Int64
        var entries: [ManualEntry]
    }

    static func encode(_ entries: [ManualEntry], sequence: Int64, savedAt: String) -> Data {
        let json: [String: Any] = [
            "kind": kind,
            "version": version,
            "sequence": sequence,
            "savedAt": savedAt,
            "entries": entries.map { entry -> [String: Any] in
                [
                    "entryId": entry.entryId,
                    "type": entry.type,
                    "enteredAt": entry.enteredAt,
                    "fields": (try? JSONSerialization.jsonObject(with: Data(entry.fields.utf8))) ?? [String: String](),
                    "rows": entry.rows.map(rowJson),
                ]
            },
        ]
        return (try? JSONSerialization.data(withJSONObject: json, options: [.sortedKeys])) ?? Data()
    }

    /// Nil when `bytes` are not a journal this version can read (a report backup, or a newer journal).
    static func decode(_ bytes: Data) -> Decoded? {
        guard bytes.first(where: { ![0x20, 0x0A, 0x0D, 0x09].contains($0) }) == UInt8(ascii: "{"),
              let json = try? JSONSerialization.jsonObject(with: bytes) as? [String: Any],
              json["kind"] as? String == kind, (json["version"] as? NSNumber)?.intValue == version,
              let sequence = (json["sequence"] as? NSNumber)?.int64Value, let entries = json["entries"] as? [[String: Any]] else { return nil }
        var decoded: [ManualEntry] = []
        for entry in entries {
            guard let entryId = entry["entryId"] as? String, let type = entry["type"] as? String, let enteredAt = entry["enteredAt"] as? String,
                  let fields = entry["fields"] as? [String: Any], let rows = entry["rows"] as? [[String: Any]],
                  let fieldsData = try? JSONSerialization.data(withJSONObject: fields, options: [.sortedKeys]) else { return nil }
            var stored: [StoredTransaction] = []
            for row in rows {
                guard let transaction = rowFrom(row) else { return nil }
                stored.append(transaction)
            }
            decoded.append(ManualEntry(entryId: entryId, type: type, fields: String(decoding: fieldsData, as: UTF8.self), enteredAt: enteredAt, rows: stored))
        }
        return Decoded(sequence: sequence, entries: decoded)
    }

    private static func rowJson(_ row: StoredTransaction) -> [String: Any] {
        [
            "sourceKey": row.sourceKey, "date": row.date, "eventType": row.eventType, "ticker": row.ticker, "quantity": row.quantity,
            "price": row.price, "currency": row.currency, "amount": row.amount, "fee": row.fee, "description": row.description,
            "splitRatio": row.splitRatio.map { $0 as Any } ?? NSNull(), "isin": row.isin, "conid": row.conid,
            "instrumentDescription": row.instrumentDescription, "listingExchange": row.listingExchange,
        ]
    }

    private static func rowFrom(_ json: [String: Any]) -> StoredTransaction? {
        func text(_ key: String) -> String? { json[key] as? String }
        guard let sourceKey = text("sourceKey"), let date = text("date"), let eventType = text("eventType"), let ticker = text("ticker"),
              let quantity = text("quantity"), let price = text("price"), let currency = text("currency"), let amount = text("amount"),
              let fee = text("fee"), let description = text("description"), let isin = text("isin"), let conid = text("conid"),
              let instrumentDescription = text("instrumentDescription"), let listingExchange = text("listingExchange") else { return nil }
        return StoredTransaction(sourceKey: sourceKey, date: date, eventType: eventType, ticker: ticker, quantity: quantity, price: price,
                                 currency: currency, amount: amount, fee: fee, description: description, splitRatio: text("splitRatio"), isin: isin,
                                 conid: conid, instrumentDescription: instrumentDescription, listingExchange: listingExchange)
    }
}

/**
 * Where the journal stands on this device. `sequence` is the last journal built; `file` is the verified journal in
 * the backup folder; `pending` is an encrypted journal waiting in the protected folder; `stale` means entries changed
 * since the last journal was built (for example while backups were not set up).
 */
struct JournalState: Equatable {
    var sequence: Int64 = 0
    var file: String?
    var pending: String?
    var stale = false
}

protocol JournalStore {
    func entries() throws -> [ManualEntry]
    func journalState() throws -> JournalState
    func setJournalState(_ state: JournalState) throws
    /// Stores restored entries as they are; they were checked when first saved.
    func restoreEntries(_ entries: [ManualEntry]) throws
}

extension ManualEntries: JournalStore {
    private static let keys = (sequence: "manual_journal_sequence", file: "manual_journal_file", pending: "manual_journal_pending", stale: "manual_journal_stale")

    func entries() throws -> [ManualEntry] { try all() }

    func journalState() throws -> JournalState {
        var values: [String: String] = [:]
        for row in try database.query("SELECT key, value FROM app_settings WHERE key LIKE 'manual_journal_%'") {
            if case .text(let key)? = row[0], case .text(let value)? = row[1] { values[key] = value }
        }
        return JournalState(sequence: values[Self.keys.sequence].flatMap { Int64($0) } ?? 0, file: values[Self.keys.file],
                            pending: values[Self.keys.pending], stale: values[Self.keys.stale] == "true")
    }

    func setJournalState(_ state: JournalState) throws {
        try database.transaction {
            try database.execute("DELETE FROM app_settings WHERE key LIKE 'manual_journal_%'")
            let values: [(String, String?)] = [(Self.keys.sequence, String(state.sequence)), (Self.keys.file, state.file),
                                               (Self.keys.pending, state.pending), (Self.keys.stale, state.stale ? "true" : "false")]
            for case let (key, value?) in values {
                try database.execute("INSERT OR REPLACE INTO app_settings (key, value) VALUES (?, ?)", [.text(key), .text(value)])
            }
        }
    }

    func restoreEntries(_ entries: [ManualEntry]) throws {
        for entry in entries { try save(entry) }
    }
}

/**
 * Backs up manual entries as one encrypted journal (Decision 6): after every change a new journal is built, written
 * and verified, and only then is the previous one deleted. Without a folder it stays pending and is retried with the
 * report backups. Port of the Android `ManualJournalBackup`.
 */
struct ManualJournalBackup {
    let dataKey: () throws -> Data?
    let folder: () throws -> BackupFolder?
    let store: JournalStore
    let pendingDirectory: URL
    var now: () -> Date = Date.init

    /// True when a journal still has to reach the folder; false while backups are not set up.
    var isPending: Bool {
        guard ((try? dataKey()) ?? nil) != nil, let state = try? store.journalState() else { return false }
        return state.pending != nil || state.stale
    }

    /// Call after every add, edit or delete of a manual entry.
    @discardableResult
    func changed() throws -> BackupResult { try sync(changed: true) }

    @discardableResult
    func sync(changed: Bool = false) throws -> BackupResult {
        var state = try store.journalState()
        if changed && !state.stale {
            state.stale = true
            try store.setJournalState(state)
        }
        guard let key = try dataKey() else { return .pending }
        if state.stale {
            let sequence = state.sequence + 1
            let payload = ManualJournal.encode(try store.entries(), sequence: sequence, savedAt: ManualOperations.iso(now()))
            let name = BackupService.randomFileName()
            try FileManager.default.createDirectory(at: pendingDirectory, withIntermediateDirectories: true)
            try BackupFileFormat.encrypt(dataKey: key, plaintext: payload).write(to: pendingDirectory.appendingPathComponent(name), options: [.atomic, .completeFileProtection])
            if let old = state.pending { try? FileManager.default.removeItem(at: pendingDirectory.appendingPathComponent(old)) }
            state.sequence = sequence
            state.pending = name
            state.stale = false
            try store.setJournalState(state)
        }
        guard let name = state.pending else { return .backedUp }
        guard let target = try folder() else { return .pending }
        guard let encrypted = try? Data(contentsOf: pendingDirectory.appendingPathComponent(name)) else {
            state.pending = nil
            state.stale = true
            try store.setJournalState(state)
            return .pending
        }
        do {
            try target.write(name, encrypted)
            guard let readBack = try target.read(name), SHA256.hash(data: readBack) == SHA256.hash(data: encrypted) else {
                try? target.delete(name)
                return .pending
            }
        } catch BackupFolderError.accessLost {
            return .accessLost
        } catch is BackupFolderError {
            return .pending
        }
        // The new journal is verified; only now is the previous one removed.
        if let old = state.file, old != name { try? target.delete(old) }
        state.file = name
        state.pending = nil
        try store.setJournalState(state)
        try? FileManager.default.removeItem(at: pendingDirectory.appendingPathComponent(name))
        return .backedUp
    }

    /// Restores the newest of `journals` (file name and decoded journal); returns how many entries came back.
    func restore(_ journals: [(name: String, journal: ManualJournal.Decoded)]) throws -> Int {
        guard let newest = journals.max(by: { $0.journal.sequence < $1.journal.sequence }) else { return 0 }
        try store.restoreEntries(newest.journal.entries)
        try store.setJournalState(JournalState(sequence: newest.journal.sequence, file: newest.name))
        return newest.journal.entries.count
    }
}
