import Foundation

enum ImportResult: Equatable {
    case imported(reportId: Int64, inserted: Int, skipped: Int)

    /// The same file was imported before; nothing changed.
    case alreadyImported

    case rejected(ImportRejection)
}

enum ImportRejection: Equatable {
    case unparseable(FlexImportError)
    case invalidRecord(InvalidRecordReason)
}

/// What a deletion removed: records, and backup files still to delete from the folder.
struct DeletedImports: Equatable {
    var removedRecords: Int
    var backupFiles: [String]
}

/**
 * Imports one report atomically (mobile-report-upload spec): every derived record and the report
 * entry commit together, or nothing does. Re-importing the same bytes is detected by a keyed
 * fingerprint; overlapping reports skip records whose source key already exists, like the desktop
 * `save_to_database`. Port of the Android `ReportImporter`.
 */
struct ReportImporter {
    static let backupPending = "pending"
    static let backupDone = "backed_up"

    let database: EncryptedDatabase
    let pseudonymizer: Pseudonymizer
    var now: () -> Date = Date.init
    /// Test hook that runs just before commit; throwing here must roll everything back.
    var beforeCommit: () throws -> Void = {}

    /// `backupFile`: the encrypted backup recorded as pending in the same transaction (nil for debug samples).
    func importReport(_ bytes: Data, backupFile: String? = nil) throws -> ImportResult {
        // The file name may contain the account number, so it is neither parsed into records nor stored.
        let report: FlexReport
        switch FlexQueryParser.parse(bytes, sourceFile: "") {
        case .failure(let error): return .rejected(.unparseable(error))
        case .success(let parsed): report = parsed
        }
        let transactions: [StoredTransaction]
        let duplicatesInReport: Int
        switch ImportPlan.build(report) {
        case .invalid(let reason): return .rejected(.invalidRecord(reason))
        case .ready(let ready, let duplicates): (transactions, duplicatesInReport) = (ready, duplicates)
        }
        let fingerprint = try pseudonymizer.pseudonym(.reportContent, ImportPlan.sha256Hex(bytes))
        let accountPseudonym = try report.accountId.map { try pseudonymizer.pseudonym(.account, $0) }

        return try database.transaction {
            if try !database.query("SELECT 1 FROM reports WHERE fingerprint = ?", [.text(fingerprint)]).isEmpty { return .alreadyImported }

            try database.execute(
                """
                INSERT INTO reports (fingerprint, account_pseudonym, account_masked, imported_at, inserted_count, skipped_count, backup_file, backup_status)
                VALUES (?, ?, ?, ?, 0, 0, ?, ?)
                """,
                [.text(fingerprint), accountPseudonym.map { .text($0) }, report.accountId.map { .text(Masking.lastFour($0)) },
                 .text(StoredTime.text(now())), backupFile.map { .text($0) }, backupFile.map { _ in .text(Self.backupPending) }]
            )
            let reportId = database.lastInsertRowId

            var inserted = 0
            for transaction in transactions {
                try database.execute(
                    """
                    INSERT OR IGNORE INTO transactions (report_id, source_key, date, event_type, ticker, quantity, price, currency, amount, fee,
                        description, split_ratio, isin, conid, instrument_description, listing_exchange)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    [.integer(reportId), .text(transaction.sourceKey), .text(transaction.date), .text(transaction.eventType), .text(transaction.ticker),
                     .text(transaction.quantity), .text(transaction.price), .text(transaction.currency), .text(transaction.amount), .text(transaction.fee),
                     .text(transaction.description), transaction.splitRatio.map { .text($0) }, .text(transaction.isin), .text(transaction.conid),
                     .text(transaction.instrumentDescription), .text(transaction.listingExchange)]
                )
                if database.changes == 0 { try fillMissingIdentity(transaction) } else { inserted += 1 }
                try database.execute("INSERT OR IGNORE INTO report_records (report_id, source_key) VALUES (?, ?)", [.integer(reportId), .text(transaction.sourceKey)])
            }
            let skipped = duplicatesInReport + transactions.count - inserted
            try database.execute("UPDATE reports SET inserted_count = ?, skipped_count = ? WHERE id = ?",
                                 [.integer(Int64(inserted)), .integer(Int64(skipped)), .integer(reportId)])
            try beforeCommit()
            return .imported(reportId: reportId, inserted: inserted, skipped: skipped)
        }
    }

    /// Same as the desktop import: an existing record gains identity fields it lacked.
    private func fillMissingIdentity(_ t: StoredTransaction) throws {
        try database.execute(
            """
            UPDATE transactions SET
                isin = CASE WHEN ?1 <> '' THEN ?1 ELSE isin END,
                conid = CASE WHEN ?2 <> '' THEN ?2 ELSE conid END,
                instrument_description = CASE WHEN ?3 <> '' THEN ?3 ELSE instrument_description END,
                listing_exchange = CASE WHEN ?4 <> '' THEN ?4 ELSE listing_exchange END
            WHERE source_key = ?5
            """,
            [.text(t.isin), .text(t.conid), .text(t.instrumentDescription), .text(t.listingExchange), .text(t.sourceKey)]
        )
    }

    /**
     * Deletes one import and the records only it contains; records that other imports also contain
     * stay and move to one of those imports. Returns the deleted report's backup file and how many
     * records were removed, or nil when the report does not exist.
     */
    func deleteReport(_ reportId: Int64) throws -> DeletedImports? {
        try database.transaction {
            let rows = try database.query("SELECT backup_file FROM reports WHERE id = ?", [.integer(reportId)])
            guard let row = rows.first else { return nil }
            var backupFiles: [String] = []
            if case .text(let file)? = row[0] { backupFiles.append(file) }
            let before = try count("transactions")
            try database.execute("DELETE FROM report_records WHERE report_id = ?", [.integer(reportId)])
            try database.execute("DELETE FROM transactions WHERE source_key NOT IN (SELECT source_key FROM report_records)")
            try database.execute(
                """
                UPDATE transactions SET report_id =
                    (SELECT MIN(rr.report_id) FROM report_records rr WHERE rr.source_key = transactions.source_key)
                WHERE report_id = ?
                """,
                [.integer(reportId)]
            )
            try database.execute("DELETE FROM reports WHERE id = ?", [.integer(reportId)])
            return DeletedImports(removedRecords: before - (try count("transactions")), backupFiles: backupFiles)
        }
    }

    /// Deletes every import and record; returns the backup files to remove from the folder.
    func deleteAll() throws -> DeletedImports {
        try database.transaction {
            let files = try texts("SELECT backup_file FROM reports WHERE backup_file IS NOT NULL")
            let removed = try count("transactions")
            try database.execute("DELETE FROM report_records")
            try database.execute("DELETE FROM transactions")
            try database.execute("DELETE FROM reports")
            return DeletedImports(removedRecords: removed, backupFiles: files)
        }
    }

    func pendingBackups() throws -> [String] {
        try texts("SELECT backup_file FROM reports WHERE backup_status = ?", [.text(Self.backupPending)])
    }

    func markBackedUp(_ backupFile: String) throws {
        try database.execute("UPDATE reports SET backup_status = ? WHERE backup_file = ?", [.text(Self.backupDone), .text(backupFile)])
    }

    private func count(_ table: String) throws -> Int {
        guard case .integer(let value)? = try database.query("SELECT count(*) FROM \(table)").first?.first else { return 0 }
        return Int(value)
    }

    private func texts(_ sql: String, _ arguments: [SQLValue?] = []) throws -> [String] {
        try database.query(sql, arguments).compactMap { row in
            if case .text(let value)? = row.first { return value }
            return nil
        }
    }
}

/// An imported report as shown to the user: no file name, only a masked account.
struct ImportedReport: Equatable {
    var id: Int64
    var importedAt: String
    var accountMasked: String?
    var inserted: Int
    var skipped: Int
    /// "pending", "backed_up", or nil for reports imported without backup (debug samples).
    var backupStatus: String?
}

struct ImportHistory {
    let database: EncryptedDatabase

    func list() throws -> [ImportedReport] {
        try database.query("SELECT id, imported_at, account_masked, inserted_count, skipped_count, backup_status FROM reports ORDER BY id DESC")
            .compactMap { row in
                guard case .integer(let id)? = row[0], case .text(let importedAt)? = row[1],
                      case .integer(let inserted)? = row[3], case .integer(let skipped)? = row[4] else { return nil }
                var masked: String?
                if case .text(let value)? = row[2] { masked = value }
                var status: String?
                if case .text(let value)? = row[5] { status = value }
                return ImportedReport(id: id, importedAt: importedAt, accountMasked: masked, inserted: Int(inserted), skipped: Int(skipped), backupStatus: status)
            }
    }
}
