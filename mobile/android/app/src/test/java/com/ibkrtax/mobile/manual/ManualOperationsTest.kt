package com.ibkrtax.mobile.manual

import com.ibkrtax.mobile.importer.StoredTransaction
import com.ibkrtax.mobile.portfolio.FifoInventory
import com.ibkrtax.mobile.portfolio.FifoResult
import com.ibkrtax.mobile.testing.Fixtures
import java.math.BigDecimal
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Row building, validation and FIFO checks for manual entries (mobile-manual-operations tasks 3.1–3.4). */
class ManualOperationsTest {
    private val journal = JSONObject(Fixtures.manual("journal_v1.json").decodeToString())
    private val entries = (0 until journal.getJSONArray("entries").length()).map { journal.getJSONArray("entries").getJSONObject(it) }
    private val today = LocalDate.parse("2026-10-13")

    private fun fields(entry: JSONObject): Map<String, String> = entry.getJSONObject("fields").let { f -> f.keys().asSequence().associateWith(f::getString) }

    private fun row(json: JSONObject) = StoredTransaction(
        sourceKey = json.getString("sourceKey"),
        date = json.getString("date"),
        eventType = json.getString("eventType"),
        ticker = json.getString("ticker"),
        quantity = json.getString("quantity"),
        price = json.getString("price"),
        currency = json.getString("currency"),
        amount = json.getString("amount"),
        fee = json.getString("fee"),
        description = json.getString("description"),
        splitRatio = if (json.isNull("splitRatio")) null else json.getString("splitRatio"),
        isin = json.getString("isin"),
        conid = json.getString("conid"),
        instrumentDescription = json.getString("instrumentDescription"),
        listingExchange = json.getString("listingExchange"),
    )

    private fun fixtureRows(entry: JSONObject) = entry.getJSONArray("rows").let { rows -> (0 until rows.length()).map { row(rows.getJSONObject(it)) } }

    private fun buy(date: String, quantity: String, ticker: String = "AAPL") =
        ManualRows.build("b-$date-$quantity", ManualType.BUY, mapOf("date" to date, "ticker" to ticker, "currency" to "USD", "quantity" to quantity, "price" to "10", "fee" to "0"))

    private fun sell(date: String, quantity: String) =
        ManualRows.build("s-$date-$quantity", ManualType.SELL, mapOf("date" to date, "ticker" to "AAPL", "currency" to "USD", "quantity" to quantity, "price" to "12", "fee" to "0"))

    @Test
    fun everyFixtureEntryBuildsTheRowsOfTheJournal() {
        for (entry in entries) {
            val type = ManualType.valueOf(entry.getString("type"))
            assertEquals(type.name, emptyList<ManualProblem>(), ManualValidation.check(type, fields(entry), today))
            assertEquals(type.name, fixtureRows(entry), ManualRows.build(entry.getString("entryId"), type, fields(entry)))
        }
    }

    @Test
    fun theWholeJournalGivesTheExpectedLots() {
        val rows = entries.flatMap(::fixtureRows).sortedBy { it.date }
        val lots = (FifoInventory.openLots(ManualFifo.events(rows)) as FifoResult.Success).lots.groupBy { it.ticker }
        fun quantity(ticker: String) = lots.getValue(ticker).fold(BigDecimal.ZERO) { sum, lot -> sum.add(lot.quantity) }

        assertEquals(0, quantity("AAPL").compareTo(BigDecimal("6")))
        assertEquals(0, quantity("MSFT").compareTo(BigDecimal("3")))
        assertEquals(0, quantity("SAP").compareTo(BigDecimal("8")))
        assertEquals(0, lots.getValue("SAP").single().price.compareTo(BigDecimal("60")))
        assertEquals(0, quantity("GE").compareTo(BigDecimal("10")))
        assertEquals(0, lots.getValue("GE").single().price.compareTo(BigDecimal("100")))
        assertEquals(0, lots.getValue("KO").single().price.signum())
        assertEquals(0, lots.getValue("OGN").single().price.signum())
        assertFalse("OLDCO" in lots)
        // The merger carries cost and the purchase date over to the new shares.
        val newco = lots.getValue("NEWCO").single()
        assertEquals(0, newco.quantity.compareTo(BigDecimal("5")))
        assertEquals(0, newco.price.compareTo(BigDecimal("100")))
        assertEquals("2021-05-10", newco.date)
    }

    @Test
    fun knownInstrumentsLendTheirIdentity() {
        val imported = StoredTransaction(
            sourceKey = "k", date = "2023-01-10", eventType = "BUY", ticker = "SAP", quantity = "1", price = "1", currency = "EUR",
            amount = "1", fee = "0", description = "IBKR Trade", splitRatio = null, isin = "DE0007164600", conid = "14204",
            instrumentDescription = "SAP SE", listingExchange = "IBIS",
        )
        val lookup = ManualInstruments.lookup(listOf(imported))
        val fields = mapOf("date" to "2024-01-02", "ticker" to "SAP", "currency" to "EUR", "quantity" to "2", "price" to "150", "fee" to "1")

        val known = ManualRows.build("e", ManualType.BUY, fields, lookup).single()
        assertEquals(listOf("DE0007164600", "14204", "SAP SE", "IBIS"), listOf(known.isin, known.conid, known.instrumentDescription, known.listingExchange))
        val unknown = ManualRows.build("e", ManualType.BUY, fields + ("currency" to "USD"), lookup).single()
        assertEquals("", unknown.isin)
        assertEquals("", unknown.listingExchange)
    }

    @Test
    fun invalidEntriesAreRefusedFieldByField() {
        val bad = mapOf("date" to "2026-10-14", "ticker" to "aapl", "currency" to "US", "quantity" to "0", "price" to "1,5", "fee" to "-1")
        assertEquals(
            listOf(
                ManualProblem.FutureDate(),
                ManualProblem.Invalid("ticker"),
                ManualProblem.Invalid("currency"),
                ManualProblem.OutOfRange("quantity"),
                ManualProblem.Invalid("price"),
                ManualProblem.OutOfRange("fee"),
            ),
            ManualValidation.check(ManualType.BUY, bad, today),
        )
        assertEquals(listOf(ManualProblem.Missing("tax")), ManualValidation.check(ManualType.DIVIDEND, mapOf("date" to "2024-01-02", "ticker" to "KO", "currency" to "USD", "amount" to "1"), today))
        val dividend = mapOf("date" to "2024-01-02", "ticker" to "KO", "currency" to "USD", "amount" to "1.00", "tax" to "1.50")
        assertEquals(listOf(ManualProblem.OutOfRange("tax")), ManualValidation.check(ManualType.DIVIDEND, dividend, today))
        val split = mapOf("date" to "2024-01-02", "ticker" to "KO", "currency" to "USD", "newShares" to "1.5", "oldShares" to "1", "heldBefore" to "4")
        assertEquals(listOf(ManualProblem.OutOfRange("newShares")), ManualValidation.check(ManualType.SPLIT, split, today))
        val merger = mapOf("date" to "2024-01-02", "ticker" to "KO", "currency" to "USD", "quantity" to "1", "newTicker" to "KO", "newQuantity" to "1")
        assertEquals(listOf(ManualProblem.Invalid("newTicker")), ManualValidation.check(ManualType.MERGER, merger, today))
    }

    @Test
    fun sellingMoreThanHeldIsRefusedWithTheHeldQuantity() {
        val before = buy("2024-01-02", "10")
        val after = before + sell("2024-03-01", "12")

        assertEquals(
            Oversell("AAPL", "2024-03-01", BigDecimal("10")),
            ManualFifo.oversell(ManualFifo.events(before), ManualFifo.events(after)),
        )
        assertNull(ManualFifo.oversell(ManualFifo.events(before), ManualFifo.events(before + sell("2024-03-01", "10"))))
    }

    @Test
    fun deletingABuyThatALaterSaleNeedsIsRefused() {
        val first = buy("2024-01-02", "5")
        val second = buy("2024-02-01", "5")
        val sale = sell("2024-03-01", "8")

        val oversell = ManualFifo.oversell(ManualFifo.events(first + second + sale), ManualFifo.events(first + sale))
        assertEquals(Oversell("AAPL", "2024-03-01", BigDecimal("5")), oversell)
    }

    @Test
    fun dataThatWasIncompleteBeforeDoesNotBlockUnrelatedEntries() {
        val broken = sell("2024-03-01", "1")
        assertNull(ManualFifo.oversell(ManualFifo.events(broken), ManualFifo.events(broken + buy("2024-04-01", "3", ticker = "KO"))))
    }

    @Test
    fun possibleDuplicatesMatchOnDateTypeTickerCurrencyAndSize() {
        val manual = buy("2023-01-10", "10").single()
        val imported = manual.copy(sourceKey = "hash", quantity = "10.0", price = "150.00", description = "IBKR Trade")

        assertTrue(ManualDuplicates.matches(manual, imported))
        assertFalse(ManualDuplicates.matches(manual, imported.copy(quantity = "11")))
        assertFalse(ManualDuplicates.matches(manual, imported.copy(date = "2023-01-11")))

        val dividend = StoredTransaction("manual:d", "2023-05-18", "DIVIDEND", "AAPL", "0", "0", "USD", "2.40", "0", "Manual", null, "", "", "")
        assertEquals(listOf(dividend to dividend.copy(sourceKey = "x", amount = "2.4")), ManualDuplicates.find(listOf(dividend), listOf(dividend.copy(sourceKey = "x", amount = "2.4"))))
    }
}
