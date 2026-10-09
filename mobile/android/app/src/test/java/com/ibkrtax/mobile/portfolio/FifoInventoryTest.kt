package com.ibkrtax.mobile.portfolio

import java.math.BigDecimal
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FifoInventoryTest {
    private fun resource(path: String): String =
        FifoInventoryTest::class.java.classLoader!!.getResourceAsStream(path)!!.use { it.readBytes().decodeToString() }

    private val scenarios: Map<String, List<PortfolioEvent>> by lazy {
        val array = JSONArray(resource("fifo/scenarios.json"))
        (0 until array.length()).associate { i ->
            val scenario = array.getJSONObject(i)
            val rows = scenario.getJSONArray("transactions")
            scenario.getString("name") to (0 until rows.length()).map { j -> rows.getJSONObject(j).toEvent(j.toLong()) }
        }
    }

    private fun JSONObject.toEvent(order: Long) = PortfolioEvent(
        order = order,
        date = getString("date"),
        eventType = getString("event_type"),
        ticker = getString("ticker"),
        quantity = BigDecimal(getString("quantity")),
        price = BigDecimal(getString("price")),
        currency = getString("currency"),
        isin = optString("isin"),
        description = optString("description"),
        splitRatio = optString("split_ratio").takeIf { it.isNotEmpty() }?.let(::BigDecimal),
    )

    private fun lots(name: String): List<OpenLot> =
        (FifoInventory.openLots(scenarios.getValue(name)) as FifoResult.Success).lots

    /** Compares numerically: Python and Java may format equal decimals differently (1.0E+2 vs 100). */
    private fun List<OpenLot>.canonical() = map { listOf(it.ticker, it.isin, it.date, it.quantity.stripTrailingZeros().toPlainString(), it.price.stripTrailingZeros().toPlainString(), it.currency) }

    @Test
    fun openLotsMatchPythonReferenceForEveryScenario() {
        val expected = JSONObject(resource("fifo/expected_open_lots.json"))
        assertEquals(expected.keySet(), scenarios.keys)

        for ((name, events) in scenarios) {
            val reference = expected.getJSONObject(name)
            val result = FifoInventory.openLots(events)
            if (reference.has("error")) {
                assertEquals(name, FifoResult.UnmatchedSell(reference.getString("ticker"), reference.getString("date")), result)
                continue
            }
            val rows = reference.getJSONArray("lots")
            val expectedLots = (0 until rows.length()).map { i ->
                val lot = rows.getJSONObject(i)
                OpenLot(
                    lot.getString("ticker"),
                    lot.getString("isin"),
                    lot.getString("date"),
                    BigDecimal(lot.getString("quantity")),
                    BigDecimal(lot.getString("price")),
                    lot.getString("currency"),
                )
            }
            assertEquals(name, expectedLots.canonical(), (result as FifoResult.Success).lots.canonical())
        }
    }

    @Test
    fun partialSellLeavesFifoAverage() {
        val holding = Holdings.fromLots(lots("partial_sell_fifo")).single()
        assertEquals(0, BigDecimal("7").compareTo(holding.quantity))
        // (2 x 150 + 5 x 160) / 7
        assertEquals("157.1428571428571428571428571", holding.averagePrice.toPlainString())
    }

    @Test
    fun fullySoldPositionIsNotAHolding() {
        assertTrue(Holdings.fromLots(lots("fully_sold")).isEmpty())
    }

    @Test
    fun currenciesBecomeSeparateSubpositions() {
        val holdings = Holdings.fromLots(lots("lots_in_two_currencies"))

        assertEquals(listOf("EUR", "GBP"), holdings.map { it.currency })
        val gbp = holdings.single { it.currency == "GBP" }
        assertEquals(0, BigDecimal("14").compareTo(gbp.quantity))
        // (10 x 24 + 4 x 25) / 14, never mixed with the EUR lot
        assertEquals(0, BigDecimal("340").divide(BigDecimal("14"), java.math.MathContext(28)).compareTo(gbp.averagePrice))
    }

    @Test
    fun corporateActionsKeepInstrumentIdentity() {
        assertEquals(listOf("NEWCO"), Holdings.fromLots(lots("merger_carries_cost_and_dates")).map { it.ticker })
        assertEquals(listOf("MRK", "OGN"), Holdings.fromLots(lots("spinoff_adds_zero_cost_lot")).map { it.ticker })
        assertEquals(
            listOf("RU0009029540", "US80585Y3080"),
            Holdings.fromLots(lots("isin_change_keeps_identities_apart")).map { it.isin },
        )
        val meta = Holdings.fromLots(lots("ticker_alias_and_blank_isin")).single()
        assertEquals("META", meta.ticker)
        assertEquals(0, BigDecimal("3").compareTo(meta.quantity))
    }

    @Test
    fun dividendsAndTaxesDoNotAffectLots() {
        val events = scenarios.getValue("partial_sell_fifo") + listOf(
            PortfolioEvent(99, "2023-05-18", "DIVIDEND", "AAPL", BigDecimal.ZERO, BigDecimal.ZERO, "USD", "US0378331005"),
            PortfolioEvent(100, "2023-05-18", "TAX", "AAPL", BigDecimal.ZERO, BigDecimal.ZERO, "USD", "US0378331005"),
        )
        assertEquals(lots("partial_sell_fifo"), (FifoInventory.openLots(events) as FifoResult.Success).lots)
    }
}
