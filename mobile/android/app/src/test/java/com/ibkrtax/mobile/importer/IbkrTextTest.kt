package com.ibkrtax.mobile.importer

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Mirrors the parametrized cases in tests/test_parser.py and test_import_integrity.py. */
class IbkrTextTest {
    @Test
    fun normalizeDate() {
        mapOf(
            "20250102" to "2025-01-02",
            "01/02/2025" to "2025-01-02",
            "2025-01-02, 15:00:00" to "2025-01-02",
            "13/02/2025" to "2025-02-13",
            "02-Jan-25" to "2025-01-02",
            "02-jan-70" to "1970-01-02",
        ).forEach { (input, expected) -> assertEquals(input, expected, IbkrText.normalizeDate(input)) }
        assertNull(IbkrText.normalizeDate(""))
        assertNull(IbkrText.normalizeDate("2025-02-30"))
    }

    @Test
    fun extractTicker() {
        assertEquals("AGR", IbkrText.extractTicker("AGR(US05351W1036) Cash Dividend", "", BigDecimal.ZERO))
        assertEquals("MGA", IbkrText.extractTicker("MGA (CA5592224011) Cash Dividend", "", BigDecimal.ZERO))
        assertEquals("AAPL", IbkrText.extractTicker("Unknown Description", "AAPL", BigDecimal.ZERO))
        assertEquals("TSLA", IbkrText.extractTicker("TSLA Cash Div", "", BigDecimal.ZERO))
        assertEquals("UNKNOWN", IbkrText.extractTicker("lowercase words", "", BigDecimal.ZERO))
        assertEquals(
            "OGN",
            IbkrText.extractTicker(
                "MRK(US58933Y1055) Spinoff 1 for 10 (OGN, ORGANON & CO-W/I, US68622V1061)",
                "MRK",
                BigDecimal.ONE,
            ),
        )
    }

    @Test
    fun extractIsin() {
        assertEquals("US6826801036", IbkrText.extractIsin("OKE(US6826801036) Cash Dividend USD 0.99 per Share"))
        assertEquals("CA5592224011", IbkrText.extractIsin("MGA (CA5592224011) Cash Dividend"))
        assertEquals("", IbkrText.extractIsin("Cash dividend without security identifier"))
        assertEquals(
            "US68622V1061",
            IbkrText.extractIsin("MRK(US58933Y1055) Spinoff 1 for 10 (OGN, ORGANON & CO-W/I, US68622V1061)", "OGN"),
        )
    }

    @Test
    fun parseDecimal() {
        assertEquals(BigDecimal("1000.50"), IbkrText.parseDecimalOrNull("1,000.50"))
        assertEquals(BigDecimal("1234.56"), IbkrText.parseDecimalOrNull("\"1,234.56\""))
        assertEquals(BigDecimal("-500"), IbkrText.parseDecimalOrNull("-500"))
        assertEquals(BigDecimal.ZERO, IbkrText.parseDecimalOrNull(""))
        assertNull(IbkrText.parseDecimalOrNull("abc"))
    }

    @Test
    fun classifyTradeType() {
        assertEquals("TRANSFER", IbkrText.classifyTradeType("ACATS Transfer", BigDecimal.TEN))
        assertEquals("TRANSFER", IbkrText.classifyTradeType("Internal Transfer", BigDecimal.TEN))
        assertEquals("BUY", IbkrText.classifyTradeType("Buy Order", BigDecimal.TEN))
        assertEquals("SELL", IbkrText.classifyTradeType("Sell Order", BigDecimal("-5")))
        assertEquals("UNKNOWN", IbkrText.classifyTradeType("Random Text", BigDecimal.ZERO))
    }

    @Test
    fun splitRatioMatchesPythonDecimalDivision() {
        assertEquals("3", IbkrText.format(IbkrText.extractSplitRatio("WMT Split 3 for 1")!!))
        assertEquals("0.125", IbkrText.format(IbkrText.extractSplitRatio("GE Split 1 for 8")!!))
        assertEquals("0.3333333333333333333333333333", IbkrText.format(IbkrText.extractSplitRatio("X Split 1 for 3")!!))
        assertNull(IbkrText.extractSplitRatio("No split here"))
        assertNull(IbkrText.extractSplitRatio("X Split 1 for 0"))
    }

    @Test
    fun csvReaderMatchesPythonDialect() {
        val rows = CsvReader.read("a,\"b,c\",\"say \"\"hi\"\"\"\n\nx,\"multi\nline\",\nlast")
        assertEquals(listOf("a", "b,c", "say \"hi\""), rows[0].fields)
        assertEquals(emptyList<String>(), rows[1].fields)
        assertEquals(listOf("x", "multi\nline", ""), rows[2].fields)
        assertEquals(3, rows[2].line)
        assertEquals(listOf("last"), rows[3].fields)
        assertEquals(5, rows[3].line)
    }
}
