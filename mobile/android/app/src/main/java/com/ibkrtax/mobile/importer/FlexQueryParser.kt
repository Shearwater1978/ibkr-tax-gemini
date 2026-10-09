package com.ibkrtax.mobile.importer

import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * On-device parser for IBKR Activity Flex Query CSV reports.
 *
 * Valid reports produce the same records as the Python reference `parse_csv`.
 * Unlike the reference, which skips or partially imports broken input, any
 * malformed row in a parsed section rejects the whole report so an import is
 * all-or-nothing (mobile-report-upload spec).
 */
object FlexQueryParser {
    private const val TRADES = "Trades"
    private const val CORPORATE_ACTIONS = "Corporate Actions"
    private const val DIVIDENDS = "Dividends"
    private const val WITHHOLDING_TAX = "Withholding Tax"
    private const val INSTRUMENTS = "Financial Instrument Information"
    private const val ACCOUNT_INFORMATION = "Account Information"

    /** A Header row for one of these sections identifies an IBKR statement. */
    private val RECOGNIZED_SECTIONS = setOf("Statement", ACCOUNT_INFORMATION, TRADES, CORPORATE_ACTIONS, DIVIDENDS, WITHHOLDING_TAX, INSTRUMENTS)

    private val STOCK_ASSETS = setOf("Stocks", "Equity")
    private val CORPORATE_ACTION_TYPES = setOf("SPLIT", "STOCK_DIV", "MERGER", "SPINOFF")

    fun parse(bytes: ByteArray, sourceFile: String): FlexParseResult =
        try {
            FlexParseResult.Success(parseOrThrow(bytes, sourceFile))
        } catch (e: ImportException) {
            FlexParseResult.Failure(e.error)
        }

    private class ImportException(val error: FlexImportError) : Exception()

    private class InstrumentInfo(val isin: String, val conid: String, val description: String)

    private class Section(val name: String, val line: Int, private val columns: Map<String, Int>) {
        fun column(vararg names: String): Int? = names.firstNotNullOfOrNull { columns[it] }

        fun required(vararg names: String): Int =
            column(*names) ?: throw malformed(MalformedReason.MISSING_COLUMNS, line, name)
    }

    private fun malformed(reason: MalformedReason, line: Int, section: String? = null) =
        ImportException(FlexImportError.Malformed(reason, line, section))

    private fun parseOrThrow(bytes: ByteArray, sourceFile: String): FlexReport {
        if (bytes.isEmpty()) throw ImportException(FlexImportError.EmptyFile)
        val rows = try {
            CsvReader.read(decodeUtf8(bytes).removePrefix("﻿"))
        } catch (e: UnterminatedQuoteException) {
            throw malformed(MalformedReason.UNTERMINATED_QUOTE, e.line)
        }
        if (rows.none { it.fields.size >= 2 && it.fields[1] == "Header" && it.fields[0] in RECOGNIZED_SECTIONS }) {
            throw ImportException(FlexImportError.UnsupportedFormat)
        }

        val trades = mutableListOf<TradeRecord>()
        val dividends = mutableListOf<CashRecord>()
        val taxes = mutableListOf<CashRecord>()
        val corporateActions = mutableListOf<CorporateActionRecord>()
        val instruments = mutableMapOf<String, MutableList<InstrumentInfo>>()
        val sections = mutableMapOf<String, Section>()
        var accountId: String? = null

        for (row in rows) {
            val fields = row.fields
            if (fields.size < 2) continue
            val name = fields[0]
            if (fields[1] == "Header") {
                sections[name] = Section(name, row.line, fields.withIndex().associate { (i, v) -> v.trim() to i })
                continue
            }
            if (fields[1] != "Data") continue
            val section = sections[name] ?: continue
            val reader = RowReader(section, row)
            when (name) {
                TRADES -> parseTrade(reader, sourceFile)?.let(trades::add)
                CORPORATE_ACTIONS -> parseCorporateAction(reader, sourceFile)?.let(corporateActions::add)
                DIVIDENDS -> parseCash(reader, sourceFile, amountColumns = arrayOf("Amount", "Gross Rate", "Gross Amount"),
                    dateColumns = arrayOf("Date", "PayDate"), descriptionRequired = true)?.let(dividends::add)
                WITHHOLDING_TAX -> parseCash(reader, sourceFile, amountColumns = arrayOf("Amount"),
                    dateColumns = arrayOf("Date"), descriptionRequired = false)?.let(taxes::add)
                INSTRUMENTS -> parseInstrument(reader)?.let { (symbol, info) ->
                    instruments.getOrPut(symbol) { mutableListOf() }.add(info)
                }
                ACCOUNT_INFORMATION -> parseAccountId(reader)?.let { accountId = it }
            }
        }

        return resolveIdentities(FlexReport(trades, dividends, taxes, corporateActions, accountId), instruments)
    }

    private fun decodeUtf8(bytes: ByteArray): String =
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            throw malformed(MalformedReason.NOT_UTF8, 1)
        }

    /** Reads fields of one Data row, failing the import on short rows or bad values. */
    private class RowReader(val section: Section, val row: CsvRow) {
        fun text(index: Int): String =
            row.fields.getOrNull(index) ?: throw malformed(MalformedReason.SHORT_ROW, row.line, section.name)

        fun optionalText(index: Int?): String = index?.let(::text).orEmpty()

        fun date(index: Int): String =
            IbkrText.normalizeDate(text(index)) ?: throw malformed(MalformedReason.INVALID_DATE, row.line, section.name)

        fun decimal(index: Int): BigDecimal =
            IbkrText.parseDecimalOrNull(text(index))
                ?: throw malformed(MalformedReason.INVALID_NUMBER, row.line, section.name)
    }

    private fun isStock(reader: RowReader, vararg assetColumns: String): Boolean {
        val column = reader.section.column(*assetColumns) ?: return true
        return reader.text(column) in STOCK_ASSETS
    }

    private fun parseTrade(reader: RowReader, sourceFile: String): TradeRecord? {
        val section = reader.section
        val date = section.required("Date/Time", "Date", "TradeDate")
        val quantity = section.required("Quantity")
        val price = section.required("T. Price", "TradePrice", "Price")
        val currency = section.required("Currency")
        val symbol = section.column("Symbol", "Ticker")
        val commission = section.column("Comm/Fee", "IBCommission", "Commission")
        val description = section.column("Description")

        if (!isStock(reader, "Asset Category", "Asset Class")) return null
        val descriptionText = reader.optionalText(description)
        if ("Total" in descriptionText) return null

        val tradeDate = reader.date(date)
        val qty = reader.decimal(quantity)
        if (qty.signum() == 0) return null

        val ticker = IbkrText.extractTicker(descriptionText, reader.optionalText(symbol), qty)
        return TradeRecord(
            identity = InstrumentIdentity(ticker, IbkrText.extractIsin(descriptionText, ticker)),
            currency = reader.text(currency),
            date = tradeDate,
            quantity = qty,
            price = reader.decimal(price),
            commission = commission?.let(reader::decimal) ?: BigDecimal.ZERO,
            type = IbkrText.classifyTradeType(descriptionText, qty),
            source = descriptionText.ifEmpty { "IBKR Trade" },
            sourceFile = sourceFile,
        )
    }

    private fun parseCorporateAction(reader: RowReader, sourceFile: String): CorporateActionRecord? {
        val section = reader.section
        val date = section.required("Date/Time", "Report Date")
        val description = section.required("Description")
        val quantity = section.required("Quantity")
        val currency = section.column("Currency")
        val symbol = section.column("Symbol", "Ticker")

        if (!isStock(reader, "Asset Category")) return null
        val descriptionText = reader.text(description)
        if ("Total" in descriptionText) return null

        val actionDate = reader.date(date)
        val qty = reader.decimal(quantity)
        val ratio = IbkrText.extractSplitRatio(descriptionText)
        val type = if (ratio != null) "SPLIT" else IbkrText.classifyCorporateAction(descriptionText, qty)
        if (type !in CORPORATE_ACTION_TYPES) return null

        val ticker = IbkrText.extractTicker(descriptionText, reader.optionalText(symbol), qty)
        return CorporateActionRecord(
            identity = InstrumentIdentity(ticker, IbkrText.extractIsin(descriptionText, ticker)),
            currency = currency?.let(reader::text) ?: "USD",
            date = actionDate,
            quantity = qty,
            type = type,
            ratio = ratio,
            source = descriptionText,
            sourceFile = sourceFile,
        )
    }

    private fun parseCash(
        reader: RowReader,
        sourceFile: String,
        amountColumns: Array<String>,
        dateColumns: Array<String>,
        descriptionRequired: Boolean,
    ): CashRecord? {
        val section = reader.section
        val date = section.required(*dateColumns)
        val amount = section.required(*amountColumns)
        val currency = section.required("Currency")
        val description = if (descriptionRequired) {
            section.required("Description", "Label")
        } else {
            section.column("Description", "Label")
        }

        val descriptionText = reader.optionalText(description)
        val currencyText = reader.text(currency)
        // IBKR summary rows ("Total", "Total in USD", ...) sit in the currency column.
        if ("Total" in descriptionText || currencyText.startsWith("Total")) return null

        val ticker = IbkrText.extractTicker(descriptionText, "", BigDecimal.ZERO)
        return CashRecord(
            identity = InstrumentIdentity(ticker, IbkrText.extractIsin(descriptionText, ticker)),
            currency = currencyText,
            date = reader.date(date),
            amount = reader.decimal(amount),
            sourceFile = sourceFile,
        )
    }

    private fun parseInstrument(reader: RowReader): Pair<String, InstrumentInfo>? {
        val section = reader.section
        val symbolColumn = section.required("Symbol", "Ticker")
        val symbol = reader.text(symbolColumn).trim().uppercase()
        if (symbol.isEmpty()) return null
        val securityId = reader.optionalText(section.column("Security ID")).trim()
        return symbol to InstrumentInfo(
            isin = IbkrText.extractIsin("$symbol($securityId)"),
            conid = reader.optionalText(section.column("Conid", "ConID")).trim(),
            description = reader.optionalText(section.column("Description")).trim(),
        )
    }

    private fun parseAccountId(reader: RowReader): String? {
        val name = reader.section.column("Field Name") ?: return null
        val value = reader.section.column("Field Value") ?: return null
        if (reader.text(name).trim() != "Account") return null
        return reader.text(value).trim().ifEmpty { null }
    }

    /** Port of the identity back-fill at the end of Python `parse_csv`. */
    private fun resolveIdentities(report: FlexReport, instruments: Map<String, List<InstrumentInfo>>): FlexReport {
        val all = report.trades.map { it.identity to it.currency } +
            report.dividends.map { it.identity to it.currency } +
            report.taxes.map { it.identity to it.currency } +
            report.corporateActions.map { it.identity to it.currency }

        val observed = mutableMapOf<Pair<String, String>, MutableSet<String>>()
        for ((identity, currency) in all) {
            val key = identity.ticker.trim().uppercase() to currency.trim().uppercase()
            if (key.first.isNotEmpty() && key.second.isNotEmpty() && identity.isin.isNotEmpty()) {
                observed.getOrPut(key) { mutableSetOf() }.add(identity.isin)
            }
        }

        fun resolve(identity: InstrumentIdentity, currency: String): InstrumentIdentity {
            val ticker = identity.ticker.trim().uppercase()
            val candidates = instruments[ticker].orEmpty()
            var isin = identity.isin
            val instrument: InstrumentInfo?
            if (isin.isNotEmpty()) {
                instrument = candidates.firstOrNull { it.isin == isin }
            } else {
                val seen = observed[ticker to currency.trim().uppercase()].orEmpty()
                if (seen.size == 1) {
                    isin = seen.first()
                    instrument = candidates.firstOrNull { it.isin == isin }
                } else if (seen.isEmpty() && candidates.size == 1) {
                    instrument = candidates[0]
                    isin = instrument.isin
                } else {
                    instrument = null
                }
            }
            return identity.copy(
                isin = isin,
                conid = identity.conid.ifEmpty { instrument?.conid.orEmpty() },
                instrumentDescription = identity.instrumentDescription.ifEmpty { instrument?.description.orEmpty() },
            )
        }

        return report.copy(
            trades = report.trades.map { it.copy(identity = resolve(it.identity, it.currency)) },
            dividends = report.dividends.map { it.copy(identity = resolve(it.identity, it.currency)) },
            taxes = report.taxes.map { it.copy(identity = resolve(it.identity, it.currency)) },
            corporateActions = report.corporateActions.map { it.copy(identity = resolve(it.identity, it.currency)) },
        )
    }
}
