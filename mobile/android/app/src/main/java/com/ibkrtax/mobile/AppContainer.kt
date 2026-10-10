package com.ibkrtax.mobile

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.ibkrtax.mobile.backup.BackupService
import com.ibkrtax.mobile.backup.ReportStore
import com.ibkrtax.mobile.backup.SafBackupFolder
import com.ibkrtax.mobile.fx.FxService
import com.ibkrtax.mobile.fx.NbpClient
import com.ibkrtax.mobile.keys.BackupKeys
import com.ibkrtax.mobile.keys.BlockStoreKeyCopy
import com.ibkrtax.mobile.keys.LocalKeyFiles
import com.ibkrtax.mobile.prices.FinnhubProvider
import com.ibkrtax.mobile.prices.PriceService
import com.ibkrtax.mobile.prices.RateLimiter
import com.ibkrtax.mobile.security.AesGcmKeyWrapper
import com.ibkrtax.mobile.security.DeviceKeys
import com.ibkrtax.mobile.security.Pseudonymizer
import com.ibkrtax.mobile.storage.BackupLocationStore
import com.ibkrtax.mobile.storage.EncryptedDatabase
import com.ibkrtax.mobile.storage.FxStore
import com.ibkrtax.mobile.storage.ImportHistory
import com.ibkrtax.mobile.storage.PortfolioRepository
import com.ibkrtax.mobile.storage.PriceStore
import com.ibkrtax.mobile.storage.ReportImporter
import java.io.File

class IbkrTaxApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/**
 * App-wide dependencies. Everything is lazy: the first access opens the Keystore and
 * the SQLCipher database, so call it off the main thread.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: SupportSQLiteOpenHelper by lazy { EncryptedDatabase.open(appContext) }
    val importer: ReportImporter by lazy { ReportImporter(database, Pseudonymizer { DeviceKeys.hmacKey() }) }
    val portfolio: PortfolioRepository by lazy { PortfolioRepository(database) }
    val importHistory: ImportHistory by lazy { ImportHistory(database) }
    val priceStore: PriceStore by lazy { PriceStore(database) }
    private val finnhubLimiter = RateLimiter.forFinnhubFreeTier()
    val prices: PriceService by lazy { PriceService(priceStore, priceStore, ::finnhub) }

    /** Finnhub client for [key]; all clients share one rate limiter. */
    fun finnhub(key: String): FinnhubProvider = FinnhubProvider(key, limiter = finnhubLimiter)
    val fxStore: FxStore by lazy { FxStore(database) }
    val fx: FxService by lazy { FxService(NbpClient(), fxStore) }
    val backupLocation: BackupLocationStore by lazy { BackupLocationStore(database) }
    val backup: BackupService by lazy {
        BackupService(
            dataKey = { backupKeys.dataKey() },
            manifestJson = { backupKeys.manifestJson() },
            folder = { backupLocation.get()?.let { SafBackupFolder(appContext, Uri.parse(it.treeUri), it.subfolder) } },
            reports = object : ReportStore {
                override fun import(bytes: ByteArray, backupFile: String) = importer.import(bytes, backupFile)

                override fun pendingBackups() = importer.pendingBackups()

                override fun markBackedUp(backupFile: String) = importer.markBackedUp(backupFile)

                override fun deleteReport(reportId: Long) = importer.deleteReport(reportId)

                override fun deleteAll() = importer.deleteAll()
            },
            pendingDir = File(appContext.noBackupFilesDir, "pending-backups"),
        )
    }
    val backupKeys: BackupKeys by lazy {
        BackupKeys(
            LocalKeyFiles(File(appContext.noBackupFilesDir, "backup-keys"), AesGcmKeyWrapper { DeviceKeys.aesWrappingKey(DeviceKeys.BACKUP_DATA_KEY_WRAP_ALIAS) }),
            BlockStoreKeyCopy(appContext),
        )
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as IbkrTaxApplication).container
