package com.ibkrtax.mobile.importer

/** A parsed CSV record and the 1-based line it starts on. */
internal data class CsvRow(val line: Int, val fields: List<String>)

internal class UnterminatedQuoteException(val line: Int) : Exception()

/**
 * Minimal reader matching Python's default `csv.reader` dialect over a file opened
 * in text mode: universal newlines, comma delimiter, `"` quoting with `""` escapes,
 * quoted fields may span lines, and a blank line yields an empty record.
 */
internal object CsvReader {
    fun read(input: String): List<CsvRow> {
        val text = input.replace("\r\n", "\n").replace('\r', '\n')
        val rows = mutableListOf<CsvRow>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var atFieldStart = true
        var rowStarted = false
        var line = 1
        var rowLine = 1

        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> {
                        if (c == '\n') line++
                        field.append(c)
                    }
                }
            } else {
                when (c) {
                    '"' -> {
                        if (atFieldStart) inQuotes = true else field.append(c)
                        atFieldStart = false
                        rowStarted = true
                    }
                    ',' -> {
                        fields.add(field.toString())
                        field.clear()
                        atFieldStart = true
                        rowStarted = true
                    }
                    '\n' -> {
                        if (rowStarted) fields.add(field.toString())
                        rows.add(CsvRow(rowLine, fields))
                        fields = mutableListOf()
                        field.clear()
                        atFieldStart = true
                        rowStarted = false
                        line++
                        rowLine = line
                    }
                    else -> {
                        field.append(c)
                        atFieldStart = false
                        rowStarted = true
                    }
                }
            }
            i++
        }
        if (inQuotes) throw UnterminatedQuoteException(rowLine)
        if (rowStarted) {
            fields.add(field.toString())
            rows.add(CsvRow(rowLine, fields))
        }
        return rows
    }
}
