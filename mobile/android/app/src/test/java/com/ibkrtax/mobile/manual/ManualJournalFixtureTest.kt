package com.ibkrtax.mobile.manual

import com.ibkrtax.mobile.backup.BackupFileFormat
import com.ibkrtax.mobile.importer.FlexParseResult
import com.ibkrtax.mobile.importer.FlexQueryParser
import com.ibkrtax.mobile.importer.ImportPlan
import com.ibkrtax.mobile.importer.ImportPlanResult
import com.ibkrtax.mobile.testing.Fixtures
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** The shared manual-journal fixture (mobile-manual-operations Decisions 2 and 6); the iOS tests read the same files. */
class ManualJournalFixtureTest {
    @Test
    fun encryptedJournalDecryptsToThePlainOne() {
        val plain = BackupFileFormat.decrypt(ByteArray(32) { it.toByte() }, Fixtures.manual("journal_v1.bin"))
        assertArrayEquals(Fixtures.manual("journal_v1.json"), plain)
    }

    @Test
    fun manualBuyRowMatchesTheImportedRowForTheSameTrade() {
        val journal = JSONObject(Fixtures.manual("journal_v1.json").decodeToString())
        val manual = journal.getJSONArray("entries").getJSONObject(0).getJSONArray("rows").getJSONObject(0)
        val report = (FlexQueryParser.parse(Fixtures.flexQuery(Fixtures.VALID_BASIC), Fixtures.VALID_BASIC) as FlexParseResult.Success).report
        val imported = (ImportPlan.build(report) as ImportPlanResult.Ready).transactions
            .first { it.date == "2023-01-10" && it.ticker == "AAPL" && it.eventType == "BUY" }

        assertEquals(imported.date, manual.getString("date"))
        assertEquals(imported.eventType, manual.getString("eventType"))
        assertEquals(imported.ticker, manual.getString("ticker"))
        assertEquals(imported.quantity, manual.getString("quantity"))
        assertEquals(imported.price, manual.getString("price"))
        assertEquals(imported.currency, manual.getString("currency"))
        assertEquals(imported.amount, manual.getString("amount"))
        assertEquals(imported.fee, manual.getString("fee"))
    }
}
