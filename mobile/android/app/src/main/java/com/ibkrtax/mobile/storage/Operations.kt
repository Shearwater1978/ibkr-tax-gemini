package com.ibkrtax.mobile.storage

import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.math.BigDecimal

/** Type filters on the Operations tab (mobile-operations spec); [OTHER] is every remaining event type. */
enum class OperationFilter(val eventType: String?) {
    BUYS("BUY"),
    SELLS("SELL"),
    DIVIDENDS("DIVIDEND"),
    TAXES("TAX"),
    OTHER(null),
    ;

    companion object {
        /** Event types that have their own filter; corporate actions and transfers fall under [OTHER]. */
        val NAMED: List<String> = entries.mapNotNull { it.eventType }

        fun of(eventType: String): OperationFilter = entries.firstOrNull { it.eventType == eventType } ?: OTHER
    }
}

/** One imported transaction as listed to the user: no account, report or file reference. */
data class Operation(
    val id: Long,
    val date: String,
    val eventType: String,
    val ticker: String,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val currency: String,
    val amount: BigDecimal,
    val fee: BigDecimal,
)

/** Read-only, paged access to imported transactions, newest first. */
class OperationsRepository(private val database: SupportSQLiteOpenHelper) {
    fun hasAny(): Boolean =
        database.readableDatabase.query("SELECT EXISTS (SELECT 1 FROM transactions)").use { it.moveToFirst() && it.getInt(0) == 1 }

    /** No [filters] means all types; [tickerQuery] matches anywhere in the ticker, ignoring case. */
    fun page(filters: Set<OperationFilter>, tickerQuery: String, limit: Int, offset: Int): List<Operation> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<Any?>()
        if (filters.isNotEmpty()) {
            val alternatives = mutableListOf<String>()
            val named = filters.mapNotNull { it.eventType }
            if (named.isNotEmpty()) {
                alternatives += "event_type IN (${named.joinToString(",") { "?" }})"
                args.addAll(named)
            }
            if (OperationFilter.OTHER in filters) {
                alternatives += "event_type NOT IN (${OperationFilter.NAMED.joinToString(",") { "?" }})"
                args.addAll(OperationFilter.NAMED)
            }
            conditions += alternatives.joinToString(" OR ", "(", ")")
        }
        val query = tickerQuery.trim()
        if (query.isNotEmpty()) {
            // SQLite LIKE ignores case for ASCII; escape the wildcards the user may type.
            conditions += "ticker LIKE ? ESCAPE '\\'"
            args += "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        }
        val where = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")
        args += limit
        args += offset
        return database.readableDatabase.query(
            "SELECT id, date, event_type, ticker, quantity, price, currency, amount, fee FROM transactions " +
                "$where ORDER BY date DESC, id DESC LIMIT ? OFFSET ?",
            args.toTypedArray(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Operation(
                            id = cursor.getLong(0),
                            date = cursor.getString(1),
                            eventType = cursor.getString(2),
                            ticker = cursor.getString(3),
                            quantity = decimal(cursor.getString(4)),
                            price = decimal(cursor.getString(5)),
                            currency = cursor.getString(6),
                            amount = decimal(cursor.getString(7)),
                            fee = decimal(cursor.getString(8)),
                        ),
                    )
                }
            }
        }
    }

    private fun decimal(text: String?): BigDecimal = text?.takeIf { it.isNotBlank() }?.toBigDecimalOrNull() ?: BigDecimal.ZERO
}
