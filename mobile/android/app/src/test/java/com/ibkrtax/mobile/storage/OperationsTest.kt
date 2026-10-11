package com.ibkrtax.mobile.storage

import com.ibkrtax.mobile.ui.common.OperationDisplay
import java.math.BigDecimal
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class OperationsTest {
    private lateinit var savedLocale: Locale

    @Before
    fun useUsLocale() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private fun operation(type: String, quantity: String, price: String, amount: String) =
        Operation(1, "2024-03-15", type, "AAPL", BigDecimal(quantity), BigDecimal(price), "USD", BigDecimal(amount), BigDecimal.ZERO)

    @Test
    fun everyEventTypeHasAFilter() {
        assertEquals(OperationFilter.BUYS, OperationFilter.of("BUY"))
        assertEquals(OperationFilter.SELLS, OperationFilter.of("SELL"))
        assertEquals(OperationFilter.DIVIDENDS, OperationFilter.of("DIVIDEND"))
        assertEquals(OperationFilter.TAXES, OperationFilter.of("TAX"))
        listOf("SPLIT", "STOCK_DIV", "MERGER", "SPINOFF", "TRANSFER").forEach { assertEquals(OperationFilter.OTHER, OperationFilter.of(it)) }
    }

    @Test
    fun tradesShowAbsoluteAmountsAndQuantityTimesPrice() {
        val sell = operation("SELL", "-15", "130", "-1950")
        assertEquals("1,950.00 USD", OperationDisplay.amount(sell))
        assertEquals("15 × 130.00 USD", OperationDisplay.details(sell))
    }

    @Test
    fun cashKeepsItsSign() {
        assertEquals("+12.50 USD", OperationDisplay.amount(operation("DIVIDEND", "0", "0", "12.5")))
        assertEquals("-1.88 USD", OperationDisplay.amount(operation("TAX", "0", "0", "-1.875")))
        assertNull(OperationDisplay.details(operation("TAX", "0", "0", "-1.875")))
    }

    @Test
    fun corporateActionsShowTheChangeInShares() {
        assertEquals("+30", OperationDisplay.amount(operation("SPLIT", "30", "0", "0")))
        assertEquals("-5", OperationDisplay.amount(operation("MERGER", "-5", "0", "0")))
    }
}
