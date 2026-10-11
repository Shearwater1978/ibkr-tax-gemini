package com.ibkrtax.mobile.security

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reports are parsed, stored, calculated and backed up without network access (report-ingestion
 * "Process reports without network access", task 3.2). Network code lives only in `prices`
 * (market prices and the shared HTTP transport, also used for exchange rates); the packages that
 * handle report data must not use network APIs or that transport.
 */
class OfflineProcessingTest {
    private val reportPackages = listOf("importer", "storage", "portfolio", "backup", "keys", "security")
    private val forbidden = listOf(
        Regex("""\bjava\.net\b"""),
        Regex("""\bjavax\.net\b"""),
        Regex("""\bHttpURLConnection\b"""),
        Regex("""\bokhttp3?\b"""),
        Regex("""\bHttpTransport\b"""),
        Regex("""\bUrlConnectionTransport\b"""),
    )

    @Test
    fun reportHandlingCodeHasNoNetworkAccess() {
        val root = File("src/main/java/com/ibkrtax/mobile")
        assertTrue("sources not found from ${File(".").absolutePath}", root.isDirectory)
        val findings = reportPackages.map { File(root, it) }.filter { it.isDirectory }.flatMap { dir ->
            dir.walk().filter { it.extension == "kt" }.flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val code = line.substringBefore("//")
                    if (forbidden.any { it.containsMatchIn(code) }) "${file.path}:${i + 1}: ${line.trim()}" else null
                }
            }.toList()
        }
        assertEquals("Network use in report-handling code:\n" + findings.joinToString("\n"), emptyList<String>(), findings)
    }
}
