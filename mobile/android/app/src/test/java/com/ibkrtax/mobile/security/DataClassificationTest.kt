package com.ibkrtax.mobile.security

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every stored column has a classification, and no stored column holds a direct identifier
 * (data-anonymization spec "Data classification", scenario "New field added").
 */
class DataClassificationTest {
    /** Columns read from the schema code, so a new column fails here until it is classified. */
    private fun schemaColumns(): Set<String> {
        val source = File("src/main/java/com/ibkrtax/mobile/storage/EncryptedDatabase.kt").readText()
        val columns = mutableSetOf<String>()
        Regex("""CREATE TABLE (\w+) \((.*?)\)\s*"""", RegexOption.DOT_MATCHES_ALL).findAll(source).forEach { match ->
            val table = match.groupValues[1]
            // Columns are separated by commas, except inside table constraints such as PRIMARY KEY (a, b).
            match.groupValues[2].lines().map { it.trim() }
                .filterNot { it.startsWith("PRIMARY KEY (") || it.startsWith("FOREIGN KEY") || it.startsWith("UNIQUE (") }
                .flatMap { it.split(",") }.map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { columns += "$table.${it.split(Regex("""\s+""")).first()}" }
        }
        Regex("""ALTER TABLE (\w+) ADD COLUMN (\w+)""").findAll(source).forEach { columns += "${it.groupValues[1]}.${it.groupValues[2]}" }
        return columns
    }

    @Test
    fun everyStoredColumnIsClassified() {
        val schema = schemaColumns()
        assertTrue("schema not parsed: $schema", schema.size > 30)
        assertEquals("unclassified columns", emptySet<String>(), schema - DataClassification.columns.keys)
        assertEquals("classified columns that do not exist", emptySet<String>(), DataClassification.columns.keys - schema)
    }

    @Test
    fun directIdentifiersAreNeverStored() {
        val stored = DataClassification.columns.filterValues { it.dataClass == DataClass.DIRECT_IDENTIFIER }
        assertEquals(emptyMap<String, Classified>(), stored)
        assertTrue(DataClassification.reportFields.values.all { it.dataClass == DataClass.DIRECT_IDENTIFIER })
    }
}
