package com.ibkrtax.mobile.portfolio

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** A stored transaction as the portfolio engine sees it; [order] is the database id. */
data class PortfolioEvent(
    val order: Long,
    val date: String,
    val eventType: String,
    val ticker: String,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val currency: String,
    val isin: String,
    val description: String = "",
    val splitRatio: BigDecimal? = null,
)

/** A remaining FIFO lot, in the currency it was bought in. */
data class OpenLot(
    val ticker: String,
    val isin: String,
    val date: String,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val currency: String,
)

sealed interface FifoResult {
    data class Success(val lots: List<OpenLot>) : FifoResult

    /** A removal exceeded the held quantity (desktop `UNMATCHED_SELL`): the data is incomplete. */
    data class UnmatchedSell(val ticker: String, val date: String) : FifoResult
}

/**
 * Open-lot part of the desktop pipeline: `resolve_instrument_identities`, ticker aliases,
 * and `TradeMatcher` from src/fifo.py, without PLN conversion. Shared FIFO scenarios
 * (mobile/fixtures/fifo) pin the parity.
 */
object FifoInventory {
    /** Python's default Decimal context, so lot arithmetic rounds identically. */
    private val PY = MathContext(28, RoundingMode.HALF_EVEN)
    private val EPSILON = BigDecimal("0.00000001")

    private val TYPE_PRIORITY = mapOf(
        "SPLIT" to 0,
        "STOCK_DIV" to 1,
        "MERGER" to 1,
        "SPLIT_ADD" to 1,
        "BUY" to 2,
        "TRANSFER" to 2,
        "SELL" to 3,
    )
    private val NON_FIFO = setOf("DIVIDEND", "TAX")
    private val TRAILING_PARENTHESES = Regex("""\s*\([^()]*\)\s*$""")

    fun openLots(events: List<PortfolioEvent>): FifoResult {
        val trades = InstrumentIdentities.resolve(events.sortedWith(compareBy({ it.date }, { it.order })))
            .filter { it.eventType !in NON_FIFO }
        return try {
            FifoResult.Success(Matcher(trades).run())
        } catch (e: UnmatchedSellException) {
            FifoResult.UnmatchedSell(e.ticker, e.date)
        }
    }

    private class UnmatchedSellException(val ticker: String, val date: String) : Exception()

    private class Lot(var quantity: BigDecimal, var price: BigDecimal, val ticker: String, val isin: String, val date: String, val currency: String) {
        fun copy() = Lot(quantity, price, ticker, isin, date, currency)
    }

    /** Port of TradeMatcher.process_trades; inventory keys are a ticker or a (ticker, ISIN) pair. */
    private class Matcher(private val trades: List<PortfolioEvent>) {
        private val inventory = LinkedHashMap<Pair<String, String?>, ArrayDeque<Lot>>()
        private val sensitive: Set<String>
        private val carry = mutableMapOf<Pair<String, String>, MutableList<Lot>>()

        init {
            val observed = mutableMapOf<String, MutableSet<String>>()
            trades.filter { it.isin.isNotEmpty() }.forEach { observed.getOrPut(it.ticker) { mutableSetOf() }.add(it.isin) }
            sensitive = observed.filterValues { it.size > 1 }.keys
        }

        fun run(): List<OpenLot> {
            val sorted = trades.sortedWith(compareBy({ it.date }, { TYPE_PRIORITY[it.eventType] ?: 99 }))

            val addedQty = mutableMapOf<Pair<String, String>, BigDecimal>()
            val removals = mutableMapOf<Pair<String, String>, Int>()
            for (trade in sorted) {
                val group = actionGroup(trade) ?: continue
                when {
                    trade.quantity.signum() > 0 -> addedQty[group] = (addedQty[group] ?: BigDecimal.ZERO).add(trade.quantity, PY)
                    trade.quantity.signum() < 0 -> removals[group] = (removals[group] ?: 0) + 1
                }
            }
            val deferred = linkedMapOf<Pair<String, String>, MutableList<PortfolioEvent>>()

            for (trade in sorted) {
                inventory.getOrPut(key(trade)) { ArrayDeque() }
                if (trade.eventType == "SPLIT") {
                    split(trade)
                    continue
                }
                val quantity = trade.quantity
                if (quantity.signum() > 0) {
                    if (trade.eventType == "BUY" || trade.eventType == "TRANSFER") {
                        buy(trade, trade.price)
                    } else {
                        // Corporate-action additions arrive at zero cost unless cost carries over.
                        val group = actionGroup(trade)
                        when {
                            group != null && group in carry && removals[group] == 0 -> carriedAdd(trade, group, addedQty.getValue(group))
                            group != null && (removals[group] ?: 0) > 0 -> deferred.getOrPut(group) { mutableListOf() }.add(trade)
                            else -> buy(trade, BigDecimal.ZERO)
                        }
                    }
                } else if (quantity.signum() < 0) {
                    val consumed = consume(trade)
                    if (trade.eventType == "SELL") continue
                    val group = actionGroup(trade) ?: continue
                    removals[group] = removals.getValue(group) - 1
                    if (consumed.isNotEmpty()) carry.getOrPut(group) { mutableListOf() }.addAll(consumed)
                    if (removals[group] == 0) {
                        deferred.remove(group)?.forEach { pending ->
                            if (group in carry) carriedAdd(pending, group, addedQty.getValue(group)) else buy(pending, BigDecimal.ZERO)
                        }
                    }
                }
            }
            deferred.values.flatten().forEach { buy(it, BigDecimal.ZERO) }

            return inventory.values.flatten().map { OpenLot(it.ticker, it.isin, it.date, it.quantity, it.price, it.currency) }
        }

        private fun key(trade: PortfolioEvent): Pair<String, String?> =
            if (trade.ticker in sensitive) trade.ticker to trade.isin else trade.ticker to null

        /** Shared by the removal and addition rows of one merger or stock dividend. */
        private fun actionGroup(trade: PortfolioEvent): Pair<String, String>? {
            if (trade.eventType != "MERGER" && trade.eventType != "STOCK_DIV" || trade.description.isEmpty()) return null
            val stem = TRAILING_PARENTHESES.replace(trade.description, "")
            return if (stem != trade.description) trade.date to stem else null
        }

        private fun buy(trade: PortfolioEvent, price: BigDecimal) {
            inventory.getValue(key(trade)).addLast(Lot(trade.quantity, price, trade.ticker, trade.isin, trade.date, trade.currency))
        }

        /** The old shares' purchase dates and cost move to the new shares. */
        private fun carriedAdd(trade: PortfolioEvent, group: Pair<String, String>, groupAddedQty: BigDecimal) {
            val lots = carry.getValue(group)
            val removedQty = lots.fold(BigDecimal.ZERO) { sum, lot -> sum.add(lot.quantity, PY) }
            val share = trade.quantity.divide(groupAddedQty, PY)
            val scale = groupAddedQty.divide(removedQty, PY)
            for (lot in lots) {
                inventory.getValue(key(trade)).addLast(
                    Lot(
                        quantity = lot.quantity.multiply(scale, PY).multiply(share, PY),
                        price = lot.price.divide(scale, PY),
                        ticker = trade.ticker,
                        isin = trade.isin,
                        date = lot.date,
                        currency = lot.currency,
                    ),
                )
            }
        }

        private fun split(trade: PortfolioEvent) {
            // IBKR books a reverse split as two rows; the negative one is ignored.
            if (trade.quantity.signum() < 0) return
            val ratio = trade.splitRatio ?: BigDecimal.ONE
            val own = key(trade)
            val keys = when {
                inventory[own].orEmpty().isNotEmpty() -> listOf(own)
                // A split that changes the ISIN is booked under the new ISIN while shares sit under the old one.
                own.second != null -> inventory.filter { (k, lots) -> k.second != null && k.first == trade.ticker && lots.isNotEmpty() }.keys.toList()
                else -> emptyList()
            }
            for (k in keys) {
                inventory.getValue(k).forEach { lot ->
                    lot.quantity = lot.quantity.multiply(ratio, PY)
                    if (ratio.signum() != 0) lot.price = lot.price.divide(ratio, PY)
                }
            }
        }

        /** Port of _consume_inventory; returns the consumed (possibly partial) lots. */
        private fun consume(trade: PortfolioEvent): List<Lot> {
            val lots = inventory.getValue(key(trade))
            var remaining = trade.quantity.abs()
            val consumed = mutableListOf<Lot>()
            while (remaining.signum() > 0 && lots.isNotEmpty()) {
                val lot = lots.first()
                if (lot.quantity <= remaining.add(EPSILON, PY)) {
                    consumed += lot.copy()
                    lots.removeFirst()
                    remaining = remaining.subtract(lot.quantity, PY)
                } else {
                    consumed += lot.copy().also { it.quantity = remaining }
                    lot.quantity = lot.quantity.subtract(remaining, PY)
                    remaining = BigDecimal.ZERO
                }
            }
            if (remaining > EPSILON) throw UnmatchedSellException(trade.ticker, trade.date)
            return consumed
        }
    }
}

/** Port of src/instrument_identity.py plus the alias step in process_yearly_data. */
internal object InstrumentIdentities {
    private val TICKER_ALIASES = mapOf("TOT" to "TTE", "FB" to "META")
    private val PLACEHOLDER_TICKER = Regex("""^\d+[A-Z]?$""")
    private val ACTION_SYMBOL = Regex("""^([A-Z][A-Z0-9.]*)\(""")

    fun resolve(events: List<PortfolioEvent>): List<PortfolioEvent> {
        val withTickers = events.map { event ->
            val symbol = ACTION_SYMBOL.find(event.description)?.groupValues?.get(1)
            if (PLACEHOLDER_TICKER.matches(event.ticker.trim()) && symbol != null) event.copy(ticker = symbol) else event
        }

        val firstSeen = mutableMapOf<String, MutableMap<String, String>>()
        for (event in withTickers) {
            if (event.ticker.isEmpty() || event.isin.isEmpty()) continue
            val seen = firstSeen.getOrPut(normalized(event.ticker)) { mutableMapOf() }
            val date = seen[event.isin]
            if (date == null || event.date < date) seen[event.isin] = event.date
        }
        val earliestIsin = firstSeen.mapValues { (_, isins) ->
            isins.entries.minWith(compareBy({ it.value }, { it.key })).key
        }

        return withTickers.map { event ->
            val isin = event.isin.ifEmpty { earliestIsin[normalized(event.ticker)].orEmpty() }
            event.copy(ticker = TICKER_ALIASES[event.ticker] ?: event.ticker, isin = isin)
        }
    }

    private fun normalized(ticker: String): String {
        val upper = ticker.trim().uppercase()
        return TICKER_ALIASES[upper] ?: upper
    }
}
