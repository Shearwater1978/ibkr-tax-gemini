package com.ibkrtax.mobile.manual

import com.ibkrtax.mobile.importer.StoredTransaction
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** What the user can enter by hand (mobile-manual-operations); [fields] are the form fields each type needs. */
enum class ManualType(val fields: List<String>) {
    BUY(listOf("date", "ticker", "currency", "quantity", "price", "fee")),
    SELL(listOf("date", "ticker", "currency", "quantity", "price", "fee")),
    TRANSFER_IN(listOf("date", "ticker", "currency", "quantity", "price")),
    TRANSFER_OUT(listOf("date", "ticker", "currency", "quantity")),
    DIVIDEND(listOf("date", "ticker", "currency", "amount", "tax")),
    TAX(listOf("date", "ticker", "currency", "amount")),

    /** `heldBefore` is not typed: the app fills in the shares held before the split. */
    SPLIT(listOf("date", "ticker", "currency", "newShares", "oldShares", "heldBefore")),
    STOCK_DIV(listOf("date", "ticker", "currency", "quantity")),
    MERGER(listOf("date", "ticker", "currency", "quantity", "newTicker", "newQuantity")),
    SPINOFF(listOf("date", "ticker", "currency", "quantity", "fromTicker")),
}

/** Identity fields copied from imported operations of the same ticker and currency (Decision 3). */
data class InstrumentInfo(val isin: String, val conid: String, val instrumentDescription: String, val listingExchange: String) {
    companion object {
        val UNKNOWN = InstrumentInfo("", "", "", "")
    }
}

/**
 * Builds the `transactions` rows of a manual entry (Decision 2) with the importer's conventions, so FIFO,
 * holdings and dividends treat them like imported rows. [fields] must already pass [ManualValidation].
 * Mirrors the generator of `mobile/fixtures/manual/journal_v1.json`.
 */
object ManualRows {
    /** Python's default Decimal context, like the importer and FIFO. */
    private val PY = MathContext(28, RoundingMode.HALF_EVEN)
    const val DESCRIPTION = "Manual"

    fun build(
        entryId: String,
        type: ManualType,
        fields: Map<String, String>,
        instrument: (ticker: String, currency: String) -> InstrumentInfo = { _, _ -> InstrumentInfo.UNKNOWN },
    ): List<StoredTransaction> {
        val key = "manual:$entryId"
        val date = fields.getValue("date")
        val ticker = fields.getValue("ticker")
        val currency = fields.getValue("currency")
        fun d(name: String) = BigDecimal(fields.getValue(name))
        fun row(
            sourceKey: String,
            eventType: String,
            quantity: BigDecimal = BigDecimal.ZERO,
            price: BigDecimal = BigDecimal.ZERO,
            amount: BigDecimal = BigDecimal.ZERO,
            fee: BigDecimal = BigDecimal.ZERO,
            description: String = DESCRIPTION,
            splitRatio: BigDecimal? = null,
            rowTicker: String = ticker,
        ): StoredTransaction {
            val info = instrument(rowTicker, currency)
            return StoredTransaction(
                sourceKey = sourceKey,
                date = date,
                eventType = eventType,
                ticker = rowTicker,
                quantity = quantity.toString(),
                price = price.toString(),
                currency = currency,
                amount = amount.toString(),
                fee = fee.toString(),
                description = description,
                splitRatio = splitRatio?.toString(),
                isin = info.isin,
                conid = info.conid,
                instrumentDescription = info.instrumentDescription,
                listingExchange = info.listingExchange,
            )
        }

        return when (type) {
            ManualType.BUY, ManualType.SELL -> {
                val quantity = if (type == ManualType.BUY) d("quantity") else d("quantity").negate()
                listOf(row(key, type.name, quantity, d("price"), quantity.multiply(d("price"), PY), d("fee").negate()))
            }
            ManualType.TRANSFER_IN -> listOf(row(key, "TRANSFER", d("quantity"), d("price"), d("quantity").multiply(d("price"), PY)))
            ManualType.TRANSFER_OUT -> listOf(row(key, "TRANSFER", d("quantity").negate()))
            ManualType.DIVIDEND -> listOfNotNull(
                row(key, "DIVIDEND", amount = d("amount")),
                d("tax").takeIf { it.signum() != 0 }?.let { row("$key:tax", "TAX", amount = it.negate()) },
            )
            ManualType.TAX -> listOf(row(key, "TAX", amount = d("amount")))
            ManualType.SPLIT -> {
                val ratio = d("newShares").divide(d("oldShares"), PY)
                val held = d("heldBefore")
                // IBKR's positive split row: shares added (forward) or the new share count (reverse).
                val quantity = if (ratio > BigDecimal.ONE) held.multiply(ratio, PY).subtract(held, PY) else held.multiply(ratio, PY)
                listOf(row(key, "SPLIT", quantity, splitRatio = ratio))
            }
            ManualType.STOCK_DIV -> listOf(row(key, "STOCK_DIV", d("quantity")))
            ManualType.MERGER -> {
                // FIFO links both rows through the date and this description stem, and carries cost over.
                val stem = "Manual corporate action $entryId"
                val newTicker = fields.getValue("newTicker")
                listOf(
                    row(key, "MERGER", d("quantity").negate(), description = "$stem ($ticker)"),
                    row("$key:new", "STOCK_DIV", d("newQuantity"), description = "$stem ($newTicker)", rowTicker = newTicker),
                )
            }
            ManualType.SPINOFF -> listOf(row(key, "SPINOFF", d("quantity"), description = "Manual spin-off from ${fields.getValue("fromTicker")}"))
        }
    }
}
