package com.ibkrtax.mobile.importer

import com.ibkrtax.mobile.testing.Fixtures
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlexQueryParserTest {
    @Test
    fun validFixturesMatchPythonReferenceRecords() {
        listOf(Fixtures.VALID_BASIC, Fixtures.VALID_FOLLOWUP).forEach { name ->
            val expected = JSONObject(Fixtures.expectedJson(name)).toMap()
            val report = parseSuccess(Fixtures.flexQuery(name), name)
            assertEquals(name, expected, report.toReferenceJson())
        }
    }

    @Test
    fun emptyFileIsRejected() {
        assertEquals(FlexImportError.EmptyFile, parseFailure(Fixtures.flexQuery(Fixtures.EMPTY)))
    }

    @Test
    fun nonIbkrCsvIsUnsupported() {
        assertEquals(FlexImportError.UnsupportedFormat, parseFailure(Fixtures.flexQuery(Fixtures.UNSUPPORTED_FORMAT)))
    }

    @Test
    fun malformedRowRejectsTheWholeReport() {
        val error = parseFailure(Fixtures.flexQuery(Fixtures.MALFORMED_TRADES))
        assertEquals(FlexImportError.Malformed(MalformedReason.INVALID_DATE, line = 5, section = "Trades"), error)
    }

    @Test
    fun importErrorsNeverContainReportContents() {
        val description = parseFailure(Fixtures.flexQuery(Fixtures.MALFORMED_TRADES)).toString()
        listOf("MSFT", "AAPL", "not-a-date", "150.00", "U00000001").forEach {
            assertFalse(description, description.contains(it))
        }
    }

    @Test
    fun invalidNumberIsRejected() {
        val error = parseFailure(csv(TRADES_HEADER, "Trades,Data,Stocks,USD,XYZ,2024-01-02,ten,10,0,"))
        assertEquals(FlexImportError.Malformed(MalformedReason.INVALID_NUMBER, line = 2, section = "Trades"), error)
    }

    @Test
    fun shortRowIsRejected() {
        val error = parseFailure(csv(TRADES_HEADER, "Trades,Data,Stocks,USD,XYZ,2024-01-02,1"))
        assertEquals(FlexImportError.Malformed(MalformedReason.SHORT_ROW, line = 2, section = "Trades"), error)
    }

    @Test
    fun headerWithoutRequiredColumnIsRejected() {
        val error = parseFailure(
            csv("Trades,Header,Asset Category,Currency,Symbol,Date/Time", "Trades,Data,Stocks,USD,XYZ,2024-01-02"),
        )
        assertEquals(FlexImportError.Malformed(MalformedReason.MISSING_COLUMNS, line = 1, section = "Trades"), error)
    }

    @Test
    fun invalidUtf8IsRejected() {
        val bytes = csv(TRADES_HEADER).plus(byteArrayOf(0xC3.toByte(), 0x28))
        assertEquals(FlexImportError.Malformed(MalformedReason.NOT_UTF8, line = 1), parseFailure(bytes))
    }

    @Test
    fun unterminatedQuoteIsRejected() {
        val error = parseFailure(csv(TRADES_HEADER, "Trades,Data,Stocks,USD,XYZ,\"2024-01-02,1,10,0,"))
        assertEquals(FlexImportError.Malformed(MalformedReason.UNTERMINATED_QUOTE, line = 2), error)
    }

    @Test
    fun byteOrderMarkAndCrLfLineEndingsAreAccepted() {
        val text = "﻿$TRADES_HEADER\r\nTrades,Data,Stocks,USD,XYZ,2024-01-02,1,10,0,\r\n"
        val report = parseSuccess(text.toByteArray())
        assertEquals("XYZ", report.trades.single().identity.ticker)
    }

    @Test
    fun narrowInstrumentSectionSuppliesMissingIdentity() {
        // Port of test_narrow_financial_instrument_section_supplies_missing_identity.
        val report = parseSuccess(
            csv(
                TRADES_HEADER,
                "Trades,Data,Stocks,USD,XYZ,2024-01-02,1,10,0,",
                "Financial Instrument Information,Header,Symbol,Security ID,Conid,Description",
                "Financial Instrument Information,Data,XYZ,US0378331005,12345,TEST CORPORATION",
            ),
        )
        assertEquals(
            InstrumentIdentity("XYZ", "US0378331005", "12345", "TEST CORPORATION"),
            report.trades.single().identity,
        )
    }

    @Test
    fun duplicateSymbolIdentityUsesCurrencyContext() {
        // Port of test_duplicate_symbol_identity_uses_currency_context_from_statement.
        val report = parseSuccess(
            csv(
                TRADES_HEADER,
                "Trades,Data,Stocks,USD,SBER,2022-01-14,5,13.335,-0.525,",
                "Corporate Actions,Header,Asset Category,Currency,Report Date,Description,Quantity",
                "Corporate Actions,Data,Stocks,USD,2022-05-24," +
                    "\"SBER(US80585Y3080) Tendered to US80585Y3CNV (SBER, SBERBANK ADR, US80585Y3080)\",-5",
                "Corporate Actions,Data,Stocks,RUB,2022-05-24," +
                    "\"SBER.CNV4(563839405) Merged WITH SBER (SBER, SBERBANK COMMON, RU0009029540)\",20",
                "Financial Instrument Information,Header,Symbol,Security ID,Conid,Description",
                "Financial Instrument Information,Data,SBER,US80585Y3080,90581067,SBERBANK ADR",
                "Financial Instrument Information,Data,SBER,RU0009029540,360308912,SBERBANK COMMON",
            ),
        )
        val buy = report.trades.single()
        val tender = report.corporateActions.single { it.quantity.signum() < 0 }
        val addition = report.corporateActions.single { it.quantity.signum() > 0 }

        assertEquals("US80585Y3080", buy.identity.isin)
        assertEquals("US80585Y3080", tender.identity.isin)
        assertEquals("90581067", buy.identity.conid)
        assertEquals("RU0009029540", addition.identity.isin)
        assertEquals("RUB", addition.currency)
    }

    @Test
    fun parsingNeedsNoNetworkOrPlatformServices() {
        // The parser is plain JVM code over bytes: this test runs with sockets blocked.
        assertTrue(FlexQueryParser.parse(Fixtures.flexQuery(Fixtures.VALID_BASIC), "x.csv") is FlexParseResult.Success)
    }

    private fun parseSuccess(bytes: ByteArray, sourceFile: String = "test.csv"): FlexReport =
        when (val result = FlexQueryParser.parse(bytes, sourceFile)) {
            is FlexParseResult.Success -> result.report
            is FlexParseResult.Failure -> throw AssertionError("Expected success, got ${result.error}")
        }

    private fun parseFailure(bytes: ByteArray): FlexImportError =
        when (val result = FlexQueryParser.parse(bytes, "test.csv")) {
            is FlexParseResult.Failure -> result.error
            is FlexParseResult.Success -> throw AssertionError("Expected failure, got $result")
        }

    private fun csv(vararg lines: String): ByteArray = lines.joinToString("\n", postfix = "\n").toByteArray()

    private fun FlexReport.toReferenceJson(): Map<String, Any?> = mapOf(
        "trades" to trades.map {
            it.identity.toJson() + mapOf(
                "currency" to it.currency,
                "date" to it.date,
                "qty" to IbkrText.format(it.quantity),
                "price" to IbkrText.format(it.price),
                "commission" to IbkrText.format(it.commission),
                "type" to it.type,
                "source" to it.source,
                "source_file" to it.sourceFile,
            )
        },
        "dividends" to dividends.map { it.toJson() },
        "taxes" to taxes.map { it.toJson() },
        "corp_actions" to corporateActions.map {
            it.identity.toJson() + mapOf(
                "currency" to it.currency,
                "date" to it.date,
                "qty" to IbkrText.format(it.quantity),
                "price" to "0",
                "commission" to "0",
                "type" to it.type,
                "ratio" to it.ratio?.let(IbkrText::format),
                "source" to it.source,
                "source_file" to it.sourceFile,
            )
        },
    )

    private fun InstrumentIdentity.toJson() = mapOf(
        "ticker" to ticker,
        "isin" to isin,
        "conid" to conid,
        "instrument_description" to instrumentDescription,
    )

    private fun CashRecord.toJson() = identity.toJson() + mapOf(
        "currency" to currency,
        "date" to date,
        "amount" to IbkrText.format(amount),
        "source_file" to sourceFile,
    )

    private companion object {
        const val TRADES_HEADER =
            "Trades,Header,Asset Category,Currency,Symbol,Date/Time,Quantity,T. Price,Comm/Fee,Description"
    }
}
