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
    private const val SCHEMA_VERSION = 1

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
            // Import tables arrive with mobile-app-core task 2.2; this records the schema origin.
            db.execSQL("CREATE TABLE schema_info (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
            db.execSQL("INSERT INTO schema_info (key, value) VALUES ('created_version', '$SCHEMA_VERSION')")
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
