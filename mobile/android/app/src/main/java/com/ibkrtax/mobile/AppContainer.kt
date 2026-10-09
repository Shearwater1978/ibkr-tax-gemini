package com.ibkrtax.mobile

import android.app.Application
import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.ibkrtax.mobile.prices.FinnhubProvider
import com.ibkrtax.mobile.prices.PriceService
import com.ibkrtax.mobile.security.DeviceKeys
import com.ibkrtax.mobile.security.Pseudonymizer
import com.ibkrtax.mobile.storage.EncryptedDatabase
import com.ibkrtax.mobile.storage.ImportHistory
import com.ibkrtax.mobile.storage.PortfolioRepository
import com.ibkrtax.mobile.storage.PriceStore
import com.ibkrtax.mobile.storage.ReportImporter

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
    val prices: PriceService by lazy { PriceService(priceStore, priceStore, { key -> FinnhubProvider(key) }) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as IbkrTaxApplication).container
