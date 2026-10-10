package com.ibkrtax.mobile.storage

import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.ibkrtax.mobile.portfolio.CashEvent
import com.ibkrtax.mobile.portfolio.Dividends
import java.math.BigDecimal

/** Read-only access to imported dividend and withholding-tax rows for the Dividends tab. */
class DividendEvents(private val database: SupportSQLiteOpenHelper) {
    fun all(): List<CashEvent> =
        database.readableDatabase.query(
            "SELECT date, event_type, ticker, currency, amount FROM transactions WHERE event_type IN (?, ?) ORDER BY date",
            arrayOf<Any?>(Dividends.DIVIDEND, Dividends.TAX),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val amount = cursor.getString(4)?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    add(CashEvent(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), amount))
                }
            }
        }
}
