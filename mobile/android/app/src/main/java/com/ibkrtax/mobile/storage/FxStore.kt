package com.ibkrtax.mobile.storage

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.ibkrtax.mobile.fx.FxRateCache
import com.ibkrtax.mobile.fx.FxRates
import java.math.BigDecimal

/** NBP rates in the encrypted database; a new table replaces the previous one as a whole. */
class FxStore(private val database: SupportSQLiteOpenHelper) : FxRateCache {
    override fun get(): FxRates? =
        database.readableDatabase.query("SELECT code, mid, effective_date FROM fx_rates").use { cursor ->
            var date: String? = null
            val mids = buildMap {
                while (cursor.moveToNext()) {
                    put(cursor.getString(0), BigDecimal(cursor.getString(1)))
                    date = cursor.getString(2)
                }
            }
            date?.let { FxRates(it, mids) }
        }

    override fun put(rates: FxRates) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            // SQLCipher's delete() rejects null whereArgs, so clear the table with plain SQL.
            db.execSQL("DELETE FROM fx_rates")
            for ((code, mid) in rates.plnPerUnit) {
                db.insert(
                    "fx_rates",
                    SQLiteDatabase.CONFLICT_REPLACE,
                    ContentValues().apply {
                        put("code", code)
                        put("mid", mid.toString())
                        put("effective_date", rates.effectiveDate)
                    },
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
