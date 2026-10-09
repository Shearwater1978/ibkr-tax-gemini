package com.ibkrtax.mobile.importer

import com.ibkrtax.mobile.testing.Fixtures
import java.math.BigDecimal
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportPlanTest {
    private fun parse(name: String): FlexReport =
        (FlexQueryParser.parse(Fixtures.flexQuery(name), name) as FlexParseResult.Success).report

    private fun ready(report: FlexReport): ImportPlanResult.Ready = ImportPlan.build(report) as ImportPlanResult.Ready

    @Test
    fun sourceKeysMatchPythonReference() {
        listOf(Fixtures.VALID_BASIC, Fixtures.VALID_FOLLOWUP).forEach { name ->
            val expected = JSONArray(Fixtures.expectedSourceKeysJson(name)).let { array ->
                List(array.length()) { array.getString(it) }
            }
            assertEquals(name, expected, ready(parse(name)).transactions.map { it.sourceKey })
        }
    }

    @Test
    fun storedValuesMirrorDesktopTransactions() {
        val transactions = ready(parse(Fixtures.VALID_BASIC)).transactions
        val sell = transactions.first { it.eventType == "SELL" && it.ticker == "AAPL" }
        assertEquals("-1400.00", sell.amount)
        assertEquals("-1.00", sell.fee)

        val split = transactions.single { it.eventType == "SPLIT" }
        assertEquals("2", split.splitRatio)

        val dividend = transactions.single { it.eventType == "DIVIDEND" }
        assertEquals("Dividend", dividend.description)
        assertEquals("3.60", dividend.amount)
        assertEquals("Tax", transactions.single { it.eventType == "TAX" }.description)
    }

    @Test
    fun accountIdIsReadForPseudonymization() {
        assertEquals("U00000001", parse(Fixtures.VALID_BASIC).accountId)
    }

    @Test
    fun identicalRecordsInOneReportAreSkipped() {
        val report = parse(Fixtures.VALID_BASIC)
        val doubled = report.copy(trades = report.trades + report.trades.first())

        val plan = ready(doubled)
        assertEquals(1, plan.duplicatesInReport)
        assertEquals(ready(report).transactions, plan.transactions)
    }

    @Test
    fun invalidRecordRejectsTheBatch() {
        val report = parse(Fixtures.VALID_BASIC)
        val noCurrency = report.copy(trades = report.trades + report.trades.first().copy(currency = "", date = "2023-12-01"))
        assertEquals(
            ImportPlanResult.Invalid(InvalidRecordReason.MISSING_DATE_TICKER_OR_CURRENCY),
            ImportPlan.build(noCurrency),
        )

        val unknown = report.copy(trades = report.trades + report.trades.first().copy(type = "UNKNOWN", quantity = BigDecimal.ONE))
        assertEquals(ImportPlanResult.Invalid(InvalidRecordReason.UNKNOWN_EVENT_TYPE), ImportPlan.build(unknown))
    }
}
