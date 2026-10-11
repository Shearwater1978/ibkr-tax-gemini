package com.ibkrtax.mobile.security

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TempFilesTest {
    @Test
    fun clearEmptiesTheCacheFolderButKeepsItAndItsSiblings() {
        val app = Files.createTempDirectory("app").toFile()
        val cache = File(app, "cache").apply { mkdirs() }
        File(cache, "report.csv").writeText("synthetic")
        File(cache, "nested/deeper").mkdirs()
        File(cache, "nested/deeper/part.tmp").writeText("synthetic")
        val database = File(app, "databases/app.db").apply { parentFile!!.mkdirs(); writeText("encrypted") }

        TempFiles.clear(cache)

        assertTrue(cache.isDirectory)
        assertEquals(emptyList<String>(), cache.list()!!.toList())
        assertTrue(database.exists())
    }

    @Test
    fun aMissingCacheFolderIsFine() {
        TempFiles.clear(File(Files.createTempDirectory("app").toFile(), "absent"))
    }
}
