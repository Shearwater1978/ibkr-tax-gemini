package com.ibkrtax.mobile.testing

import com.ibkrtax.mobile.backup.DriveFailure
import com.ibkrtax.mobile.backup.DriveResult
import com.ibkrtax.mobile.prices.PriceFailure
import com.ibkrtax.mobile.prices.PriceResult
import com.ibkrtax.mobile.prices.Quote
import java.io.IOException
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TestInfrastructureTest {
    @Test
    fun allSharedFixturesAreAvailable() {
        Fixtures.ALL.forEach { Fixtures.flexQuery(it) }
        assertTrue(Fixtures.expectedJson(Fixtures.VALID_BASIC).contains("\"trades\""))
        assertTrue(Fixtures.expectedJson(Fixtures.VALID_FOLLOWUP).contains("\"trades\""))
        assertEquals(0, Fixtures.flexQuery(Fixtures.EMPTY).size)
    }

    @Test
    fun fixturesContainOnlySyntheticAccountIdentifiers() {
        val accountLike = Regex("""\b[UF]\d{7,8}\b""")
        Fixtures.ALL.forEach { name ->
            val text = Fixtures.flexQuery(name).decodeToString()
            accountLike.findAll(text).forEach { assertTrue(name, it.value.matches(Regex("U0000000\\d"))) }
        }
    }

    @Test
    fun unitTestsCannotOpenNetworkConnections() {
        // Gradle routes test sockets through a closed local SOCKS port (see app/build.gradle.kts).
        assertThrows(IOException::class.java) {
            Socket().use { it.connect(InetSocketAddress("1.1.1.1", 443), 2_000) }
        }
    }

    @Test
    fun fakeDriveReportsSizeAndChecksumOfStoredBytes() = runBlocking {
        val drive = FakeDriveBackupService()
        val payload = byteArrayOf(1, 2, 3, 4)

        val uploaded = (drive.upload("report.enc", payload) as DriveResult.Success).value

        assertEquals(4L, uploaded.sizeBytes)
        assertEquals(FakeDriveBackupService.sha256Hex(payload), uploaded.sha256Hex)
        assertEquals(uploaded, (drive.metadata(uploaded.id) as DriveResult.Success).value)
    }

    @Test
    fun fakeDriveCanSimulateCorruptionAndFailures() = runBlocking {
        val drive = FakeDriveBackupService().apply { corruptUploads = true }
        val payload = byteArrayOf(1, 2, 3, 4)
        val uploaded = (drive.upload("report.enc", payload) as DriveResult.Success).value
        assertNotEquals(FakeDriveBackupService.sha256Hex(payload), uploaded.sha256Hex)

        drive.failure = DriveFailure.QUOTA_EXCEEDED
        assertEquals(DriveResult.Failure(DriveFailure.QUOTA_EXCEEDED), drive.upload("next.enc", payload))
        assertEquals(1, drive.storedFileCount)
    }

    @Test
    fun fakeMarketDataRecordsRequestedIsinsAndFailures() = runBlocking {
        val quote = Quote("US0378331005", BigDecimal("190.10"), "USD", Instant.parse("2024-02-12T15:00:00Z"), false)
        val prices = FakeMarketDataProvider().apply { setQuote(quote) }

        val result = prices.latestQuotes(setOf("US0378331005", "DE0007164600"))
        assertEquals(PriceResult.Success(mapOf("US0378331005" to quote)), result)
        assertEquals(listOf(setOf("US0378331005", "DE0007164600")), prices.requests)

        prices.failure = PriceFailure.OFFLINE
        assertEquals(PriceResult.Failure(PriceFailure.OFFLINE), prices.latestQuotes(setOf("US0378331005")))
    }
}
