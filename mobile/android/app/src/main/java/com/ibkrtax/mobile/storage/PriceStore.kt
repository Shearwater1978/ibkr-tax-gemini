package com.ibkrtax.mobile.storage

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.ibkrtax.mobile.prices.ApiKeyStore
import com.ibkrtax.mobile.prices.PriceProviderId
import com.ibkrtax.mobile.prices.Quote
import com.ibkrtax.mobile.prices.QuoteCache
import java.math.BigDecimal
import java.time.Instant

/**
 * Provider key and quote cache in the SQLCipher database, so both are encrypted at
 * rest under the device key. The key is never logged or returned in diagnostics.
 */
class PriceStore(private val database: SupportSQLiteOpenHelper) : ApiKeyStore, QuoteCache {
    // Keys and the choice are rows of app_settings; the Finnhub row keeps its old name, so
    // existing keys keep working without a migration.
    override fun get(provider: PriceProviderId): String? = provider.keySetting?.let(::read)

    override fun set(provider: PriceProviderId, key: String) {
        val setting = requireNotNull(provider.keySetting) { "$provider takes no key" }
        val trimmed = key.trim()
        require(trimmed.isNotEmpty()) { "API key is empty" }
        write(setting, trimmed)
    }

    override fun clear(provider: PriceProviderId) {
        provider.keySetting?.let { database.writableDatabase.delete("app_settings", "key = ?", arrayOf(it)) }
    }

    override fun storedChoice(): PriceProviderId? = PriceProviderId.resolve(read(CHOICE))

    override fun choose(provider: PriceProviderId) = write(CHOICE, provider.name)

    private fun read(setting: String): String? =
        database.readableDatabase.query("SELECT value FROM app_settings WHERE key = ?", arrayOf(setting)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun write(setting: String, value: String) {
        database.writableDatabase.insert(
            "app_settings",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("key", setting)
                put("value", value)
            },
        )
    }

    override fun get(symbols: Set<String>): Map<String, Quote> {
        if (symbols.isEmpty()) return emptyMap()
        val placeholders = symbols.joinToString(",") { "?" }
        return database.readableDatabase.query(
            "SELECT symbol, price, currency, quote_time, retrieved_at, previous_close FROM price_cache WHERE symbol IN ($placeholders)",
            symbols.toTypedArray(),
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val quote = Quote(
                        symbol = cursor.getString(0),
                        price = BigDecimal(cursor.getString(1)),
                        currency = cursor.getString(2),
                        quoteTime = Instant.parse(cursor.getString(3)),
                        retrievedAt = Instant.parse(cursor.getString(4)),
                        previousClose = if (cursor.isNull(5)) null else BigDecimal(cursor.getString(5)),
                    )
                    put(quote.symbol, quote)
                }
            }
        }
    }

    override fun putAll(quotes: Collection<Quote>) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            for (quote in quotes) {
                db.insert(
                    "price_cache",
                    SQLiteDatabase.CONFLICT_REPLACE,
                    ContentValues().apply {
                        put("symbol", quote.symbol)
                        put("price", quote.price.toString())
                        put("currency", quote.currency)
                        put("quote_time", quote.quoteTime.toString())
                        put("retrieved_at", quote.retrievedAt.toString())
                        put("previous_close", quote.previousClose?.toString())
                    },
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        const val CHOICE = "price_provider"
    }
}
