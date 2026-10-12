import Foundation
import SQLCipher

enum SQLValue: Equatable {
    case integer(Int64)
    case text(String)
}

enum DatabaseError: Error, Equatable {
    /// SQLite result code and message; messages hold SQL errors only, never stored values.
    case sqlite(code: Int32, message: String)
    /// The linked SQLite has no SQLCipher; the database would be plaintext, so it is refused.
    case notEncrypted
    /// The file was created by a newer app version.
    case newerSchema(Int)
}

/**
 * SQLCipher database for derived records (local-storage spec). The key comes from a
 * `WrappedKeyProvider` wrapped by a non-exportable device key; there is no plaintext SQLite
 * fallback. The schema matches the Android `EncryptedDatabase` at the same version.
 */
final class EncryptedDatabase {
    static let name = "ibkrtax.db"
    static let schemaVersion = 6

    private var handle: OpaquePointer?

    /// Opens (or creates) the app database in `SecureFiles`, with its device-wrapped key.
    static func open(directory: URL? = nil, name: String = name) throws -> EncryptedDatabase {
        let directory = try directory ?? SecureFiles.directory()
        let keys = WrappedKeyProvider(file: directory.appendingPathComponent(keyFileName(for: name)), wrapper: DeviceKeyWrapper(tag: DeviceKeys.databaseWrapTag))
        return try EncryptedDatabase(file: directory.appendingPathComponent(name), key: keys.getOrCreate())
    }

    /// Deletes the database and its wrapped key (erasure).
    static func delete(directory: URL? = nil, name: String = name) throws {
        let directory = try directory ?? SecureFiles.directory()
        for file in [name, "\(name)-journal", keyFileName(for: name)] {
            let url = directory.appendingPathComponent(file)
            if FileManager.default.fileExists(atPath: url.path) { try FileManager.default.removeItem(at: url) }
        }
    }

    private static func keyFileName(for name: String) -> String { "\(name).key" }

    /// Opens with an explicit raw 256-bit key; exposed for tests that check wrong-key behavior.
    init(file: URL, key: Data) throws {
        let status = sqlite3_open_v2(file.path, &handle, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, nil)
        do {
            guard status == SQLITE_OK else { throw error(status) }
            // SQLCipher logs errors to the system log by default; the app logs nothing.
            try execute("PRAGMA cipher_log_level = NONE")
            // A raw key (x'…') skips SQLCipher's passphrase KDF; the key is already random.
            try execute("PRAGMA key = \"x'\(key.map { String(format: "%02x", $0) }.joined())'\"")
            guard case .text(let version)? = try query("PRAGMA cipher_version").first?.first, !version.isEmpty else {
                throw DatabaseError.notEncrypted
            }
            // Fails with "file is not a database" when the key is wrong.
            _ = try query("SELECT count(*) FROM sqlite_master")
            try execute("PRAGMA foreign_keys = ON")
            try migrate()
            try SecureFiles.protect(file)
        } catch {
            close()
            throw error
        }
    }

    deinit { close() }

    func close() {
        if let handle { sqlite3_close_v2(handle) }
        handle = nil
    }

    func execute(_ sql: String, _ arguments: [SQLValue?] = []) throws {
        _ = try run(sql, arguments)
    }

    func query(_ sql: String, _ arguments: [SQLValue?] = []) throws -> [[SQLValue?]] {
        try run(sql, arguments)
    }

    /// Runs `body` in a transaction; any error rolls everything back.
    func transaction<T>(_ body: () throws -> T) throws -> T {
        try execute("BEGIN IMMEDIATE")
        do {
            let result = try body()
            try execute("COMMIT")
            return result
        } catch {
            try? execute("ROLLBACK")
            throw error
        }
    }

    private func run(_ sql: String, _ arguments: [SQLValue?]) throws -> [[SQLValue?]] {
        var statement: OpaquePointer?
        let prepared = sqlite3_prepare_v2(handle, sql, -1, &statement, nil)
        guard prepared == SQLITE_OK else { throw error(prepared) }
        defer { sqlite3_finalize(statement) }
        for (index, argument) in arguments.enumerated() {
            let position = Int32(index + 1)
            let bound: Int32
            switch argument {
            case nil: bound = sqlite3_bind_null(statement, position)
            case .integer(let value)?: bound = sqlite3_bind_int64(statement, position, value)
            case .text(let value)?: bound = sqlite3_bind_text(statement, position, value, -1, Self.transient)
            }
            guard bound == SQLITE_OK else { throw error(bound) }
        }
        var rows: [[SQLValue?]] = []
        while true {
            let step = sqlite3_step(statement)
            if step == SQLITE_DONE { return rows }
            guard step == SQLITE_ROW else { throw error(step) }
            rows.append((0..<sqlite3_column_count(statement)).map { column in
                switch sqlite3_column_type(statement, column) {
                case SQLITE_NULL: return nil
                case SQLITE_INTEGER: return .integer(sqlite3_column_int64(statement, column))
                default: return .text(String(cString: sqlite3_column_text(statement, column)))
                }
            })
        }
    }

    private func error(_ code: Int32) -> DatabaseError {
        .sqlite(code: code, message: handle.map { String(cString: sqlite3_errmsg($0)) } ?? "cannot open database")
    }

    private static let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

    private func migrate() throws {
        guard case .integer(let version)? = try query("PRAGMA user_version").first?.first else { return }
        if version > Self.schemaVersion { throw DatabaseError.newerSchema(Int(version)) }
        guard version == 0 else { return }
        try transaction {
            for statement in Self.schema { try execute(statement) }
            try execute("PRAGMA user_version = \(Self.schemaVersion)")
        }
    }

    /// The Android schema at version 6, written as final tables (iOS has no older versions to upgrade).
    private static let schema = [
        "CREATE TABLE schema_info (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)",
        "INSERT INTO schema_info (key, value) VALUES ('created_version', '\(schemaVersion)')",
        // No file names or raw account numbers: only keyed pseudonyms and a masked account.
        """
        CREATE TABLE reports (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            fingerprint TEXT NOT NULL UNIQUE,
            account_pseudonym TEXT,
            account_masked TEXT,
            imported_at TEXT NOT NULL,
            inserted_count INTEGER NOT NULL,
            skipped_count INTEGER NOT NULL,
            backup_file TEXT,
            backup_status TEXT
        )
        """,
        // Mirrors the desktop transactions table; decimals are stored as exact text.
        """
        CREATE TABLE transactions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            report_id INTEGER NOT NULL REFERENCES reports(id),
            source_key TEXT NOT NULL UNIQUE,
            date TEXT NOT NULL,
            event_type TEXT NOT NULL,
            ticker TEXT NOT NULL,
            quantity TEXT NOT NULL,
            price TEXT NOT NULL,
            currency TEXT NOT NULL,
            amount TEXT NOT NULL,
            fee TEXT NOT NULL,
            description TEXT NOT NULL,
            split_ratio TEXT,
            isin TEXT NOT NULL DEFAULT '',
            conid TEXT NOT NULL DEFAULT '',
            instrument_description TEXT NOT NULL DEFAULT '',
            listing_exchange TEXT NOT NULL DEFAULT ''
        )
        """,
        "CREATE INDEX transactions_ticker_date ON transactions (ticker, date)",
        // Every report's records, so deleting one import keeps records that others still contain.
        """
        CREATE TABLE report_records (
            report_id INTEGER NOT NULL REFERENCES reports(id),
            source_key TEXT NOT NULL,
            PRIMARY KEY (report_id, source_key)
        )
        """,
        "CREATE INDEX report_records_source_key ON report_records (source_key)",
        "CREATE TABLE app_settings (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)",
        """
        CREATE TABLE price_cache (
            symbol TEXT PRIMARY KEY NOT NULL,
            price TEXT NOT NULL,
            currency TEXT NOT NULL,
            quote_time TEXT NOT NULL,
            retrieved_at TEXT NOT NULL,
            previous_close TEXT
        )
        """,
        """
        CREATE TABLE fx_rates (
            code TEXT PRIMARY KEY NOT NULL,
            mid TEXT NOT NULL,
            effective_date TEXT NOT NULL
        )
        """,
    ]
}
