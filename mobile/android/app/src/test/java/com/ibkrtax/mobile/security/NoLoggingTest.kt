package com.ibkrtax.mobile.security

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app writes nothing to the system log, so reports, identifiers, amounts and keys cannot end
 * up there (mobile-encrypted-drive-backup task 6.3; device-hardening "no sensitive data in logs").
 * Checks every source set that ships: main, release and debug.
 */
class NoLoggingTest {
    private val forbidden = listOf(
        Regex("""\bandroid\.util\.Log\b"""),
        Regex("""\bLog\.(v|d|i|w|e|wtf)\("""),
        Regex("""\bprintln\("""),
        Regex("""\bprint\("""),
        Regex("""\.printStackTrace\("""),
        Regex("""\bSystem\.(out|err)\b"""),
    )

    @Test
    fun appCodeDoesNotWriteToTheSystemLog() {
        val sources = listOf("src/main/java", "src/release/java", "src/debug/java").map(::File).filter { it.isDirectory }
        assertTrue("source folders not found from ${File(".").absolutePath}", sources.any { it.path.contains("main") })
        val findings = sources.flatMap { root ->
            root.walk().filter { it.extension == "kt" }.flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val code = line.substringBefore("//")
                    if (forbidden.any { it.containsMatchIn(code) }) "${file.path}:${i + 1}: ${line.trim()}" else null
                }
            }.toList()
        }
        assertEquals("Logging calls found:\n" + findings.joinToString("\n"), emptyList<String>(), findings)
    }
}
