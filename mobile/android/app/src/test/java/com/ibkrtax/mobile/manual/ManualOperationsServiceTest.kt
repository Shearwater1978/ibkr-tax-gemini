package com.ibkrtax.mobile.manual

import com.ibkrtax.mobile.importer.StoredTransaction
import com.ibkrtax.mobile.storage.ManualEntry
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The save and delete flow of manual entries over an in-memory store (mobile-manual-operations Decisions 3–5). */
class ManualOperationsServiceTest {
    private class MemoryStore(imported: List<StoredTransaction> = emptyList()) : ManualStore {
        val importedRows = imported.toMutableList()
        val entries = linkedMapOf<String, ManualEntry>()

        override fun allRows() = (importedRows.map { StoredRow(null, it) } + entries.values.flatMap { e -> e.rows.map { StoredRow(e.entryId, it) } })
            .sortedBy { it.row.date }

        override fun get(entryId: String) = entries[entryId]

        override fun save(entry: ManualEntry) {
            entries[entry.entryId] = entry
        }

        override fun delete(entryId: String) = entries.remove(entryId) != null
    }

    private val clock = Clock.fixed(Instant.parse("2026-10-13T08:00:00Z"), ZoneOffset.UTC)
    private var ids = 0

    // Named: a trailing lambda would bind to the last parameter, onChanged (added in #168).
    private fun service(store: ManualStore) = ManualOperations(store, clock, newId = { "e${++ids}" })

    private fun trade(type: ManualType, date: String, quantity: String, ticker: String = "AAPL") =
        mapOf("date" to date, "ticker" to ticker, "currency" to "USD", "quantity" to quantity, "price" to "100.00", "fee" to "1.00")

    private val importedBuy = StoredTransaction(
        sourceKey = "hash", date = "2023-01-10", eventType = "BUY", ticker = "AAPL", quantity = "10", price = "150.00", currency = "USD",
        amount = "1500.00", fee = "-1.00", description = "IBKR Trade", splitRatio = null, isin = "US0378331005", conid = "900001",
        instrumentDescription = "APPLE INC", listingExchange = "NASDAQ",
    )

    @Test
    fun aValidBuyIsSavedWithItsFormAndTheImportedIdentity() {
        val store = MemoryStore(listOf(importedBuy))
        val result = service(store).save(ManualType.BUY, trade(ManualType.BUY, "2024-02-01", "3"))

        assertEquals(ManualSaveResult.Saved("e1"), result)
        val entry = store.entries.getValue("e1")
        assertEquals("BUY", entry.type)
        assertEquals("3", JSONObject(entry.fields).getString("quantity"))
        assertEquals("2026-10-13T08:00:00Z", entry.enteredAt)
        assertEquals("US0378331005", entry.rows.single().isin)
        assertEquals("NASDAQ", entry.rows.single().listingExchange)
    }

    @Test
    fun problemsAreReportedAndNothingIsSaved() {
        val store = MemoryStore()
        val result = service(store).save(ManualType.BUY, trade(ManualType.BUY, "2026-10-14", "0"))

        assertEquals(ManualSaveResult.Problems(listOf(ManualProblem.FutureDate(), ManualProblem.OutOfRange("quantity"))), result)
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun sellingMoreThanHeldIsRefused() {
        val store = MemoryStore(listOf(importedBuy))
        val result = service(store).save(ManualType.SELL, trade(ManualType.SELL, "2024-02-01", "12"))

        assertEquals(ManualSaveResult.Oversold(Oversell("AAPL", "2024-02-01", BigDecimal("10"))), result)
        assertTrue(store.entries.isEmpty())
    }

    @Test
    fun aPossibleDuplicateNeedsConfirmation() {
        val store = MemoryStore(listOf(importedBuy))
        val operations = service(store)
        val fields = trade(ManualType.BUY, "2023-01-10", "10") + ("price" to "150.00")

        val first = operations.save(ManualType.BUY, fields) as ManualSaveResult.PossibleDuplicate
        assertEquals(importedBuy, first.matches.single().second)
        assertTrue(store.entries.isEmpty())

        assertEquals(ManualSaveResult.Saved("e2"), operations.save(ManualType.BUY, fields, confirmDuplicate = true))
        assertEquals(1, operations.duplicatesOfImports().size)
    }

    @Test
    fun editingReplacesTheEntryAndIsCheckedWithoutItsOldRows() {
        val store = MemoryStore()
        val operations = service(store)
        operations.save(ManualType.BUY, trade(ManualType.BUY, "2024-01-02", "5"))

        assertEquals(ManualSaveResult.Saved("e1"), operations.save(ManualType.BUY, trade(ManualType.BUY, "2024-01-02", "8"), entryId = "e1"))
        assertEquals("8", store.entries.getValue("e1").rows.single().quantity)
        operations.save(ManualType.SELL, trade(ManualType.SELL, "2024-03-01", "8"))
        // Checked without the entry's old rows: counting both buys (8 + 5) would hide that 5 cannot cover the sale of 8.
        assertTrue(operations.save(ManualType.BUY, trade(ManualType.BUY, "2024-01-02", "5"), entryId = "e1") is ManualSaveResult.Oversold)
    }

    @Test
    fun deletingABuyThatASaleNeedsIsRefused() {
        val store = MemoryStore()
        val operations = service(store)
        operations.save(ManualType.BUY, trade(ManualType.BUY, "2024-01-02", "5"))
        operations.save(ManualType.SELL, trade(ManualType.SELL, "2024-03-01", "5"))

        assertEquals(ManualDeleteResult.Oversold(Oversell("AAPL", "2024-03-01", BigDecimal.ZERO)), operations.delete("e1"))
        assertEquals(ManualDeleteResult.Deleted, operations.delete("e2"))
        assertEquals(ManualDeleteResult.Deleted, operations.delete("e1"))
        assertEquals(ManualDeleteResult.NotFound, operations.delete("e1"))
    }

    @Test
    fun aSplitUsesTheSharesHeldBeforeItsDate() {
        val store = MemoryStore()
        val operations = service(store)
        val split = mapOf("date" to "2024-06-10", "ticker" to "AAPL", "currency" to "USD", "newShares" to "4", "oldShares" to "1")

        assertEquals(ManualSaveResult.NothingHeld("AAPL", "2024-06-10"), operations.save(ManualType.SPLIT, split))
        operations.save(ManualType.BUY, trade(ManualType.BUY, "2024-01-02", "3"))
        assertEquals(ManualSaveResult.Saved("e2"), operations.save(ManualType.SPLIT, split))
        val row = store.entries.getValue("e2").rows.single()
        assertEquals("9", row.quantity)
        assertEquals("4", row.splitRatio)
        assertEquals("3", JSONObject(store.entries.getValue("e2").fields).getString("heldBefore"))
    }
}
