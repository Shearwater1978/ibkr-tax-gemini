import Foundation

/**
 * One operation entered by hand (mobile-manual-operations): the form as entered and the `transactions` rows
 * built from it. `fields` is the form as a JSON object of decimal strings, like the journal's `fields`.
 */
struct ManualEntry: Equatable {
    var entryId: String
    var type: String
    var fields: String
    var enteredAt: String
    var rows: [StoredTransaction]
}

/// A stored operation; `entryId` is nil for imported rows.
struct StoredRow: Equatable {
    var entryId: String?
    var row: StoredTransaction
}

enum ManualEntryError: Error, Equatable {
    case noRows
    case foreignSourceKey(String)
}

/// Stores manual entries next to imported operations; imported rows are never changed here (Android `ManualEntries`).
struct ManualEntries {
    static let originManual = "manual"
    static let sourceKeyPrefix = "manual:"
    private static let rowColumns = "entry_id, source_key, date, event_type, ticker, quantity, price, currency, amount, fee, "
        + "description, split_ratio, isin, conid, instrument_description, listing_exchange"

    let database: EncryptedDatabase

    /// Adds the entry, or replaces it and all its rows when `entry.entryId` exists.
    func save(_ entry: ManualEntry) throws {
        guard !entry.rows.isEmpty else { throw ManualEntryError.noRows }
        if let foreign = entry.rows.first(where: { !$0.sourceKey.hasPrefix(Self.sourceKeyPrefix + entry.entryId) }) {
            throw ManualEntryError.foreignSourceKey(foreign.sourceKey)
        }
        try database.transaction {
            // Deleting the entry removes its old rows through ON DELETE CASCADE.
            try database.execute("DELETE FROM manual_entries WHERE entry_id = ?", [.text(entry.entryId)])
            try database.execute(
                "INSERT INTO manual_entries (entry_id, type, fields, entered_at) VALUES (?, ?, ?, ?)",
                [.text(entry.entryId), .text(entry.type), .text(entry.fields), .text(entry.enteredAt)]
            )
            for row in entry.rows {
                try database.execute(
                    """
                    INSERT INTO transactions (origin, report_id, entry_id, source_key, date, event_type, ticker, quantity, price, currency,
                        amount, fee, description, split_ratio, isin, conid, instrument_description, listing_exchange)
                    VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    [.text(Self.originManual), .text(entry.entryId), .text(row.sourceKey), .text(row.date), .text(row.eventType), .text(row.ticker),
                     .text(row.quantity), .text(row.price), .text(row.currency), .text(row.amount), .text(row.fee), .text(row.description),
                     row.splitRatio.map { .text($0) }, .text(row.isin), .text(row.conid), .text(row.instrumentDescription), .text(row.listingExchange)]
                )
            }
        }
    }

    /// False when no such entry exists.
    @discardableResult
    func delete(_ entryId: String) throws -> Bool {
        try database.transaction {
            let exists = try !database.query("SELECT 1 FROM manual_entries WHERE entry_id = ?", [.text(entryId)]).isEmpty
            try database.execute("DELETE FROM manual_entries WHERE entry_id = ?", [.text(entryId)])
            return exists
        }
    }

    func get(_ entryId: String) throws -> ManualEntry? { try load("WHERE entry_id = ?", [.text(entryId)]).first }

    /// Every manual entry, oldest entered first.
    func all() throws -> [ManualEntry] { try load("", []) }

    /// Every operation, imported and manual, by date and then insertion order.
    func allRows() throws -> [StoredRow] {
        try database.query("SELECT \(Self.rowColumns) FROM transactions ORDER BY date, id").map { StoredRow(entryId: text($0[0]), row: transaction($0)) }
    }

    private func load(_ condition: String, _ arguments: [SQLValue?]) throws -> [ManualEntry] {
        let rows = Dictionary(grouping: try database.query("SELECT \(Self.rowColumns) FROM transactions WHERE origin = 'manual' ORDER BY id")) { text($0[0]) ?? "" }
        return try database.query("SELECT entry_id, type, fields, entered_at FROM manual_entries \(condition) ORDER BY entered_at, entry_id", arguments).map { row in
            let id = text(row[0]) ?? ""
            return ManualEntry(entryId: id, type: text(row[1]) ?? "", fields: text(row[2]) ?? "{}", enteredAt: text(row[3]) ?? "", rows: (rows[id] ?? []).map(transaction))
        }
    }

    private func text(_ value: SQLValue?) -> String? {
        if case .text(let text)? = value { return text }
        return nil
    }

    private func transaction(_ row: [SQLValue?]) -> StoredTransaction {
        StoredTransaction(
            sourceKey: text(row[1]) ?? "", date: text(row[2]) ?? "", eventType: text(row[3]) ?? "", ticker: text(row[4]) ?? "",
            quantity: text(row[5]) ?? "", price: text(row[6]) ?? "", currency: text(row[7]) ?? "", amount: text(row[8]) ?? "",
            fee: text(row[9]) ?? "", description: text(row[10]) ?? "", splitRatio: text(row[11]), isin: text(row[12]) ?? "",
            conid: text(row[13]) ?? "", instrumentDescription: text(row[14]) ?? "", listingExchange: text(row[15]) ?? ""
        )
    }
}
