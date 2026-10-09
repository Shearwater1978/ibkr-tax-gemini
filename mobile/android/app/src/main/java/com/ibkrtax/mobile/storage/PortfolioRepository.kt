package com.ibkrtax.mobile.storage

import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.ibkrtax.mobile.portfolio.FifoInventory
import com.ibkrtax.mobile.portfolio.FifoResult
import com.ibkrtax.mobile.portfolio.Holding
import com.ibkrtax.mobile.portfolio.Holdings
import com.ibkrtax.mobile.portfolio.PortfolioEvent
import java.math.BigDecimal

sealed interface HoldingsResult {
    data class Success(val holdings: List<Holding>) : HoldingsResult

    /** Imported data sells more than it buys for [ticker]; holdings would be wrong, so none are shown. */
    data class Incomplete(val ticker: String, val date: String) : HoldingsResult
}

/** Reads imported transactions from the encrypted database and derives current holdings. */
class PortfolioRepository(private val database: SupportSQLiteOpenHelper) {
    fun holdings(): HoldingsResult =
        when (val result = FifoInventory.openLots(events())) {
            is FifoResult.Success -> HoldingsResult.Success(Holdings.fromLots(result.lots))
            is FifoResult.UnmatchedSell -> HoldingsResult.Incomplete(result.ticker, result.date)
        }

    private fun events(): List<PortfolioEvent> =
        database.readableDatabase.query(
            "SELECT id, date, event_type, ticker, quantity, price, currency, isin, description, split_ratio " +
                "FROM transactions ORDER BY date, id",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        PortfolioEvent(
                            order = cursor.getLong(0),
                            date = cursor.getString(1),
                            eventType = cursor.getString(2),
                            ticker = cursor.getString(3),
                            quantity = BigDecimal(cursor.getString(4)),
                            price = BigDecimal(cursor.getString(5)),
                            currency = cursor.getString(6),
                            isin = cursor.getString(7),
                            description = cursor.getString(8),
                            splitRatio = if (cursor.isNull(9)) null else BigDecimal(cursor.getString(9)),
                        ),
                    )
                }
            }
        }
}
