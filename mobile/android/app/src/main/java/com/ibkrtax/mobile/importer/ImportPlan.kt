package com.ibkrtax.mobile.importer

import java.math.BigDecimal
import java.security.MessageDigest

/** One row of the `transactions` table, mirroring the Python `transactions` columns. Decimals stay exact text. */
data class StoredTransaction(
    val sourceKey: String,
    val date: String,
    val eventType: String,
    val ticker: String,
    val quantity: String,
    val price: String,
    val currency: String,
    val amount: String,
    val fee: String,
    val description: String,
    val splitRatio: String?,
    val isin: String,
    val conid: String,
    val instrumentDescription: String,
    val listingExchange: String = "",
)

enum class InvalidRecordReason {
    MISSING_DATE_TICKER_OR_CURRENCY,
    UNKNOWN_EVENT_TYPE,
}

sealed interface ImportPlanResult {
    data class Ready(val transactions: List<StoredTransaction>, val duplicatesInReport: Int) : ImportPlanResult

    data class Invalid(val reason: InvalidRecordReason) : ImportPlanResult
}

/**
 * Port of the validation and de-duplication in Python `save_to_database`: any invalid
 * record rejects the batch, and records with an already-seen source key are skipped.
 */
object ImportPlan {
    private const val SEPARATOR = "\u001f"

    fun build(report: FlexReport): ImportPlanResult {
        val candidates = report.trades.map(::fromTrade) +
            report.corporateActions.map(::fromCorporateAction) +
            report.dividends.map { fromCash(it, "DIVIDEND", "Dividend") } +
            report.taxes.map { fromCash(it, "TAX", "Tax") }

        val seen = mutableSetOf<String>()
        val unique = mutableListOf<StoredTransaction>()
        for (record in candidates) {
            if (record.date.isEmpty() || record.ticker.isEmpty() || record.currency.isEmpty()) {
                return ImportPlanResult.Invalid(InvalidRecordReason.MISSING_DATE_TICKER_OR_CURRENCY)
            }
            if (record.eventType == "UNKNOWN") return ImportPlanResult.Invalid(InvalidRecordReason.UNKNOWN_EVENT_TYPE)
            if (seen.add(record.sourceKey)) unique += record
        }
        return ImportPlanResult.Ready(unique, duplicatesInReport = candidates.size - unique.size)
    }

    private fun fromTrade(t: TradeRecord) = StoredTransaction(
        sourceKey = sourceKey(t.date, t.type, t.identity.ticker, f(t.quantity), f(t.price), t.currency, "", f(t.commission), t.source, ""),
        date = t.date,
        eventType = t.type,
        ticker = t.identity.ticker,
        quantity = f(t.quantity),
        price = f(t.price),
        currency = t.currency,
        amount = f(t.quantity.multiply(t.price)),
        fee = f(t.commission),
        description = t.source,
        splitRatio = null,
        isin = t.identity.isin,
        conid = t.identity.conid,
        instrumentDescription = t.identity.instrumentDescription,
        listingExchange = t.identity.listingExchange,
    )

    private fun fromCorporateAction(c: CorporateActionRecord): StoredTransaction {
        val ratio = c.ratio?.let(::f)
        return StoredTransaction(
            // Python keys the record's literal ratio, so a missing ratio is the string "None".
            sourceKey = sourceKey(c.date, c.type, c.identity.ticker, f(c.quantity), "0", c.currency, "", "0", c.source, ratio ?: "None"),
            date = c.date,
            eventType = c.type,
            ticker = c.identity.ticker,
            quantity = f(c.quantity),
            price = "0",
            currency = c.currency,
            amount = "0",
            fee = "0",
            description = c.source,
            splitRatio = ratio,
            isin = c.identity.isin,
            conid = c.identity.conid,
            instrumentDescription = c.identity.instrumentDescription,
            listingExchange = c.identity.listingExchange,
        )
    }

    private fun fromCash(r: CashRecord, type: String, description: String) = StoredTransaction(
        sourceKey = sourceKey(r.date, type, r.identity.ticker, "", "", r.currency, f(r.amount), "", "", ""),
        date = r.date,
        eventType = type,
        ticker = r.identity.ticker,
        quantity = "0",
        price = "0",
        currency = r.currency,
        amount = f(r.amount),
        fee = "0",
        description = description,
        splitRatio = null,
        isin = r.identity.isin,
        conid = r.identity.conid,
        instrumentDescription = r.identity.instrumentDescription,
        listingExchange = r.identity.listingExchange,
    )

    /** Same field order and separator as Python `_source_key`. */
    internal fun sourceKey(
        date: String,
        type: String,
        ticker: String,
        quantity: String,
        price: String,
        currency: String,
        amount: String,
        commission: String,
        source: String,
        ratio: String,
    ): String = sha256Hex(listOf(date, type, ticker, quantity, price, currency, amount, commission, source, ratio).joinToString(SEPARATOR))

    internal fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    internal fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun f(value: BigDecimal): String = IbkrText.format(value)
}
