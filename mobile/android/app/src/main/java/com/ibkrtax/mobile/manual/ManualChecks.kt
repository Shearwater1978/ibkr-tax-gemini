package com.ibkrtax.mobile.manual

import com.ibkrtax.mobile.importer.StoredTransaction
import com.ibkrtax.mobile.portfolio.FifoInventory
import com.ibkrtax.mobile.portfolio.FifoResult
import com.ibkrtax.mobile.portfolio.PortfolioEvent
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Why a manual entry cannot be saved as entered; [field] names the form field, when one is at fault. */
sealed interface ManualProblem {
    val field: String?

    data class Missing(override val field: String) : ManualProblem

    /** Not a plain decimal, an ISO date, a ticker or a currency code. */
    data class Invalid(override val field: String) : ManualProblem

    /** Must be greater than zero (quantities, prices, share counts) or, for fees and tax, not negative. */
    data class OutOfRange(override val field: String) : ManualProblem

    data class FutureDate(override val field: String = "date") : ManualProblem
}

/** Field checks of Decision 4. Numbers must be plain decimal strings; the form converts what the user typed. */
object ManualValidation {
    private val DECIMAL = Regex("""-?\d+(\.\d+)?""")
    private val WHOLE = Regex("""\d+""")
    private val TICKER = Regex("""[A-Z0-9][A-Z0-9.]{0,11}""")
    private val CURRENCY = Regex("""[A-Z]{3}""")

    private enum class Rule { POSITIVE, NOT_NEGATIVE, NON_ZERO, WHOLE_POSITIVE }

    private val RULES = mapOf(
        ManualType.BUY to mapOf("quantity" to Rule.POSITIVE, "price" to Rule.POSITIVE, "fee" to Rule.NOT_NEGATIVE),
        ManualType.SELL to mapOf("quantity" to Rule.POSITIVE, "price" to Rule.POSITIVE, "fee" to Rule.NOT_NEGATIVE),
        ManualType.TRANSFER_IN to mapOf("quantity" to Rule.POSITIVE, "price" to Rule.POSITIVE),
        ManualType.TRANSFER_OUT to mapOf("quantity" to Rule.POSITIVE),
        ManualType.DIVIDEND to mapOf("amount" to Rule.POSITIVE, "tax" to Rule.NOT_NEGATIVE),
        ManualType.TAX to mapOf("amount" to Rule.NON_ZERO),
        ManualType.SPLIT to mapOf("newShares" to Rule.WHOLE_POSITIVE, "oldShares" to Rule.WHOLE_POSITIVE, "heldBefore" to Rule.NOT_NEGATIVE),
        ManualType.STOCK_DIV to mapOf("quantity" to Rule.POSITIVE),
        ManualType.MERGER to mapOf("quantity" to Rule.POSITIVE, "newQuantity" to Rule.POSITIVE),
        ManualType.SPINOFF to mapOf("quantity" to Rule.POSITIVE),
    )

    fun check(type: ManualType, fields: Map<String, String>, today: LocalDate): List<ManualProblem> {
        val problems = mutableListOf<ManualProblem>()
        for (name in type.fields) {
            val value = fields[name]?.trim().orEmpty()
            if (value.isEmpty()) {
                problems += ManualProblem.Missing(name)
                continue
            }
            when (name) {
                "date" -> try {
                    if (LocalDate.parse(value).isAfter(today)) problems += ManualProblem.FutureDate()
                } catch (e: DateTimeParseException) {
                    problems += ManualProblem.Invalid(name)
                }
                "ticker", "newTicker", "fromTicker" -> if (!TICKER.matches(value)) problems += ManualProblem.Invalid(name)
                "currency" -> if (!CURRENCY.matches(value)) problems += ManualProblem.Invalid(name)
                else -> number(name, value, RULES.getValue(type)[name])?.let { problems += it }
            }
        }
        if (type == ManualType.DIVIDEND && problems.isEmpty() && BigDecimal(fields.getValue("tax")) > BigDecimal(fields.getValue("amount"))) {
            problems += ManualProblem.OutOfRange("tax")
        }
        if (type == ManualType.MERGER && problems.none { it.field == "newTicker" } && fields["newTicker"]?.trim() == fields["ticker"]?.trim()) {
            problems += ManualProblem.Invalid("newTicker")
        }
        return problems
    }

    private fun number(name: String, value: String, rule: Rule?): ManualProblem? {
        if (!DECIMAL.matches(value)) return ManualProblem.Invalid(name)
        val number = BigDecimal(value)
        val ok = when (rule) {
            Rule.POSITIVE -> number.signum() > 0
            Rule.NOT_NEGATIVE -> number.signum() >= 0
            Rule.NON_ZERO -> number.signum() != 0
            Rule.WHOLE_POSITIVE -> WHOLE.matches(value) && number.signum() > 0
            null -> true
        }
        return if (ok) null else ManualProblem.OutOfRange(name)
    }
}

/** A change would sell or move out more [ticker] shares on [date] than the [held] ones (Decision 4). */
data class Oversell(val ticker: String, val date: String, val held: BigDecimal)

/** FIFO-based checks for manual entries; rows are turned into FIFO events the way the portfolio reads them. */
object ManualFifo {
    fun events(rows: List<StoredTransaction>): List<PortfolioEvent> = rows.mapIndexed { index, row ->
        PortfolioEvent(
            order = index.toLong(),
            date = row.date,
            eventType = row.eventType,
            ticker = row.ticker,
            quantity = BigDecimal(row.quantity),
            price = BigDecimal(row.price),
            currency = row.currency,
            isin = row.isin,
            description = row.description,
            splitRatio = row.splitRatio?.let(::BigDecimal),
        )
    }

    /**
     * Null when [after] (all rows with the change applied) matches every removal, or fails exactly as
     * [before] already did, so data that was incomplete before does not block unrelated entries.
     */
    fun oversell(before: List<PortfolioEvent>, after: List<PortfolioEvent>): Oversell? {
        val result = FifoInventory.openLots(after) as? FifoResult.UnmatchedSell ?: return null
        if (FifoInventory.openLots(before) == result) return null
        return Oversell(result.ticker, result.date, heldBefore(after, result.ticker, result.date))
    }

    /** Shares of [ticker] held on [date] before that day's removals: earlier events plus that day's additions. */
    fun heldBefore(events: List<PortfolioEvent>, ticker: String, date: String): BigDecimal {
        val earlier = events.filter { it.date < date || (it.date == date && it.quantity.signum() > 0) }
        val lots = (FifoInventory.openLots(earlier) as? FifoResult.Success)?.lots ?: return BigDecimal.ZERO
        return lots.filter { it.ticker == ticker }.fold(BigDecimal.ZERO) { sum, lot -> sum.add(lot.quantity) }
    }
}

/** Possible duplicates between manual and other operations (Decision 5); rows are never merged. */
object ManualDuplicates {
    private val CASH = setOf("DIVIDEND", "TAX")

    fun matches(a: StoredTransaction, b: StoredTransaction): Boolean {
        if (a.date != b.date || a.eventType != b.eventType || a.ticker != b.ticker || a.currency != b.currency) return false
        val size = if (a.eventType in CASH) StoredTransaction::amount else StoredTransaction::quantity
        return BigDecimal(size(a)).abs().compareTo(BigDecimal(size(b)).abs()) == 0
    }

    /** Each row of [candidates] paired with the first row of [others] it matches. */
    fun find(candidates: List<StoredTransaction>, others: List<StoredTransaction>): List<Pair<StoredTransaction, StoredTransaction>> =
        candidates.mapNotNull { row -> others.firstOrNull { matches(row, it) }?.let { row to it } }
}

/** Instrument identity for manual entries from the imported rows of the same ticker and currency (Decision 3). */
object ManualInstruments {
    fun lookup(rows: List<StoredTransaction>): (String, String) -> InstrumentInfo {
        val known = rows.groupBy { it.ticker to it.currency }.mapValues { (_, group) ->
            InstrumentInfo(
                isin = group.lastOrNull { it.isin.isNotEmpty() }?.isin.orEmpty(),
                conid = group.lastOrNull { it.conid.isNotEmpty() }?.conid.orEmpty(),
                instrumentDescription = group.lastOrNull { it.instrumentDescription.isNotEmpty() }?.instrumentDescription.orEmpty(),
                listingExchange = group.lastOrNull { it.listingExchange.isNotEmpty() }?.listingExchange.orEmpty(),
            )
        }
        return { ticker, currency -> known[ticker to currency] ?: InstrumentInfo.UNKNOWN }
    }
}
