package com.ibkrtax.mobile

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.ibkrtax.mobile.backup.BackupService
import com.ibkrtax.mobile.backup.ReportStore
import com.ibkrtax.mobile.backup.RestoreService
import com.ibkrtax.mobile.backup.SafBackupFolder
import com.ibkrtax.mobile.fx.FxService
import com.ibkrtax.mobile.fx.NbpClient
import com.ibkrtax.mobile.keys.BackupKeys
import com.ibkrtax.mobile.keys.BlockStoreKeyCopy
import com.ibkrtax.mobile.keys.LocalKeyFiles
import com.ibkrtax.mobile.manual.ManualJournalBackup
import com.ibkrtax.mobile.manual.ManualOperations
import com.ibkrtax.mobile.prices.FinnhubProvider
import com.ibkrtax.mobile.prices.TwelveDataProvider
import com.ibkrtax.mobile.prices.PriceProviderId
import com.ibkrtax.mobile.prices.MarketDataProvider
import com.ibkrtax.mobile.prices.PriceService
import com.ibkrtax.mobile.prices.RateLimiter
import com.ibkrtax.mobile.prices.YahooProvider
import com.ibkrtax.mobile.security.AesGcmKeyWrapper
import com.ibkrtax.mobile.security.DataEraser
import com.ibkrtax.mobile.security.DeviceKeys
import com.ibkrtax.mobile.security.Pseudonymizer
import com.ibkrtax.mobile.storage.BackupLocationStore
import com.ibkrtax.mobile.storage.EncryptedDatabase
import com.ibkrtax.mobile.storage.FxStore
import com.ibkrtax.mobile.state.AppStates
import com.ibkrtax.mobile.storage.DividendEvents
import com.ibkrtax.mobile.storage.ImportHistory
import com.ibkrtax.mobile.storage.ManualEntries
import com.ibkrtax.mobile.storage.OperationsRepository
import com.ibkrtax.mobile.storage.PortfolioRepository
import com.ibkrtax.mobile.storage.PriceStore
import com.ibkrtax.mobile.storage.ReportImporter
import com.ibkrtax.mobile.ui.common.UiPreferences
import java.io.File
import java.time.Duration

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
    val operations: OperationsRepository by lazy { OperationsRepository(database) }
    val dividendEvents: DividendEvents by lazy { DividendEvents(database) }

    /** Shared by every skin for the whole session (mobile-skins spec). */
    val states: AppStates by lazy { AppStates.create(this) }
    val priceStore: PriceStore by lazy { PriceStore(database) }
    private val finnhubLimiter = RateLimiter.forFinnhubFreeTier()
    private val twelveDataLimiter = RateLimiter.forTwelveDataFreeTier()
    // Temporary internal source for European listings only; never offered as a provider.
    private val yahoo = YahooProvider(limiter = RateLimiter(maxCredits = 60, window = Duration.ofMinutes(1)))
    val prices: PriceService by lazy {
        PriceService(priceStore, priceStore, ::provider, europe = yahoo)
    }

    /** The client for the chosen provider and the user's own [key]; each provider shares one limiter. */
    fun provider(id: PriceProviderId, key: String): MarketDataProvider = when (id) {
        PriceProviderId.FINNHUB -> FinnhubProvider(key, limiter = finnhubLimiter)
        PriceProviderId.TWELVE_DATA -> TwelveDataProvider(key, limiter = twelveDataLimiter)
        PriceProviderId.NONE -> error("None has no provider")
    }
    val fxStore: FxStore by lazy { FxStore(database) }
    val fx: FxService by lazy { FxService(NbpClient(), fxStore) }
    val backupLocation: BackupLocationStore by lazy { BackupLocationStore(database) }
    val backup: BackupService by lazy {
        BackupService(
            dataKey = { backupKeys.dataKey() },
            manifestJson = { backupKeys.manifestJson() },
            folder = { backupLocation.get()?.let { SafBackupFolder(appContext, Uri.parse(it.treeUri), it.subfolder) } },
            reports = reportStore,
            pendingDir = File(appContext.noBackupFilesDir, "pending-backups"),
            journal = manualJournal,
        )
    }

    /** Operations entered by hand (mobile-manual-operations), stored next to imported ones. */
    val manualEntries: ManualEntries by lazy { ManualEntries(database) }
    val manualJournal: ManualJournalBackup by lazy {
        ManualJournalBackup(
            dataKey = { backupKeys.dataKey() },
            folder = { backupLocation.get()?.let { SafBackupFolder(appContext, Uri.parse(it.treeUri), it.subfolder) } },
            store = manualEntries,
            pendingDir = File(appContext.noBackupFilesDir, "pending-backups"),
        )
    }
    val manualOperations: ManualOperations by lazy { ManualOperations(manualEntries, onChanged = { manualJournal.changed() }) }

    /** Erase all data (task 7.6); afterwards the app must restart, since this container is unusable. */
    val eraser: DataEraser by lazy {
        DataEraser(
            eraseFolder = { backup.eraseAll() },
            eraseBackupKeys = { backupKeys.erase() },
            deleteLocalData = {
                database.close()
                EncryptedDatabase.delete(appContext)
                File(appContext.noBackupFilesDir, "pending-backups").deleteRecursively()
                appContext.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                UiPreferences.clear(appContext)
            },
            deleteDeviceKeys = {
                listOf(DeviceKeys.DATABASE_WRAP_ALIAS, DeviceKeys.PSEUDONYM_ALIAS, DeviceKeys.BACKUP_DATA_KEY_WRAP_ALIAS).forEach(DeviceKeys::delete)
            },
        )
    }

    /** Restore on a new device from the backup folder (encrypted-backup "Restore on a new device"). */
    val restore: RestoreService by lazy { RestoreService(reportStore, journal = manualJournal) }

    private val reportStore: ReportStore by lazy {
        object : ReportStore {
            override fun import(bytes: ByteArray, backupFile: String) = importer.import(bytes, backupFile)

            override fun pendingBackups() = importer.pendingBackups()

            override fun markBackedUp(backupFile: String) = importer.markBackedUp(backupFile)

            override fun deleteReport(reportId: Long) = importer.deleteReport(reportId)

            override fun deleteAll() = importer.deleteAll()
        }
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
