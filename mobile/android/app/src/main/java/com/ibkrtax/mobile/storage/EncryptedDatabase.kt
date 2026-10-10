package com.ibkrtax.mobile.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.ibkrtax.mobile.security.AesGcmKeyWrapper
import com.ibkrtax.mobile.security.DatabaseKeyProvider
import com.ibkrtax.mobile.security.DeviceKeys
import java.io.File
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * SQLCipher database for derived records (local-storage spec). The key comes from
 * [DatabaseKeyProvider], wrapped by a non-exportable Android Keystore key; there is
 * no plaintext SQLite fallback.
 */
object EncryptedDatabase {
    const val NAME = "ibkrtax.db"
    private const val WRAPPED_KEY_FILE = "ibkrtax.db.key"
    private const val SCHEMA_VERSION = 6

    init {
        System.loadLibrary("sqlcipher")
    }

    fun open(context: Context, name: String = NAME): SupportSQLiteOpenHelper {
        val keys = DatabaseKeyProvider(
            wrappedKeyFile = File(context.noBackupFilesDir, wrappedKeyFileFor(name)),
            wrapper = AesGcmKeyWrapper { DeviceKeys.aesWrappingKey() },
        )
        return openWithKey(context, name, keys.getOrCreate())
    }

    /** Opens with an explicit key; exposed for tests that check wrong-key behavior. */
    internal fun openWithKey(context: Context, name: String, key: ByteArray): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(Schema)
            .build()
        return SupportOpenHelperFactory(key).create(configuration)
    }

    /** Deletes the database and its wrapped key (erasure). */
    fun delete(context: Context, name: String = NAME) {
        context.deleteDatabase(name)
        File(context.noBackupFilesDir, wrappedKeyFileFor(name)).delete()
    }

    private fun wrappedKeyFileFor(name: String) = if (name == NAME) WRAPPED_KEY_FILE else "$name.key"

    private object Schema : SupportSQLiteOpenHelper.Callback(SCHEMA_VERSION) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE schema_info (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
            db.execSQL("INSERT INTO schema_info (key, value) VALUES ('created_version', '$SCHEMA_VERSION')")
            createImportTables(db)
            createPriceTables(db)
            addMainPageColumns(db)
            addBackupColumns(db)
            createReportRecords(db)
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) createImportTables(db)
            if (oldVersion < 3) createPriceTables(db)
            if (oldVersion < 4) addMainPageColumns(db)
            if (oldVersion < 5) addBackupColumns(db)
            if (oldVersion < 6) createReportRecords(db)
        }

        override fun onConfigure(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys = ON")
        }

        /** Imported reports and their derived records (mobile-app-core task 2.2). */
        private fun createImportTables(db: SupportSQLiteDatabase) {
            // No file names or raw account numbers: only keyed pseudonyms and a masked account.
            db.execSQL(
                """
                CREATE TABLE reports (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    fingerprint TEXT NOT NULL UNIQUE,
                    account_pseudonym TEXT,
                    account_masked TEXT,
                    imported_at TEXT NOT NULL,
                    inserted_count INTEGER NOT NULL,
                    skipped_count INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            // Mirrors the desktop transactions table; decimals are stored as exact text.
            db.execSQL(
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
                    instrument_description TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX transactions_ticker_date ON transactions (ticker, date)")
        }

        /** Market prices (mobile-market-prices): the user's provider key and the quote cache. */
        private fun createPriceTables(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE app_settings (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
            db.execSQL(
                """
                CREATE TABLE price_cache (
                    symbol TEXT PRIMARY KEY NOT NULL,
                    price TEXT NOT NULL,
                    currency TEXT NOT NULL,
                    quote_time TEXT NOT NULL,
                    retrieved_at TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }

        /** Main page (mobile-app-core section 6): listing exchange, previous close, and NBP rates. */
        private fun addMainPageColumns(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN listing_exchange TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE price_cache ADD COLUMN previous_close TEXT")
            db.execSQL(
                """
                CREATE TABLE fx_rates (
                    code TEXT PRIMARY KEY NOT NULL,
                    mid TEXT NOT NULL,
                    effective_date TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }

        /** Encrypted backup per report (backup-location, encrypted-backup); NULL for debug samples. */
        private fun addBackupColumns(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE reports ADD COLUMN backup_file TEXT")
            db.execSQL("ALTER TABLE reports ADD COLUMN backup_status TEXT")
        }

        /**
         * Every report's records, including ones skipped because another report stored them first,
         * so deleting one import keeps records that others still contain (mobile-report-upload).
         */
        private fun createReportRecords(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE report_records (
                    report_id INTEGER NOT NULL REFERENCES reports(id),
                    source_key TEXT NOT NULL,
                    PRIMARY KEY (report_id, source_key)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX report_records_source_key ON report_records (source_key)")
            // Imports made before this version only know the records they inserted themselves.
            db.execSQL("INSERT OR IGNORE INTO report_records (report_id, source_key) SELECT report_id, source_key FROM transactions")
        }
    }
}
