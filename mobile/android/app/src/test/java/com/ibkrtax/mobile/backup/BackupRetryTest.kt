package com.ibkrtax.mobile.backup

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRetryTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    /** A folder that comes back after [failures] rounds; records when each round ran. */
    private class Folder(var pending: Int, var failures: Int, val accessLostAt: Int? = null) {
        val rounds = mutableListOf<Long>()
        var changes = 0
    }

    private fun retry(folder: Folder, now: () -> Long) = BackupRetry(
        scope,
        pending = { folder.pending },
        retry = {
            folder.rounds += now()
            when {
                folder.accessLostAt == folder.rounds.size -> RetryResult(0, folder.pending, accessLost = true)
                folder.failures-- > 0 -> RetryResult(0, folder.pending, accessLost = false)
                else -> RetryResult(folder.pending, 0, accessLost = false).also { folder.pending = 0 }
            }
        },
        onChange = { folder.changes++ },
        io = dispatcher,
    )

    @Test
    fun retriesWithDoublingDelaysUntilSaved() = scope.runTest {
        val folder = Folder(pending = 2, failures = 3)
        retry(folder) { currentTime }.kick()
        advanceUntilIdle()
        // 30 s, +1 min, +2 min, +4 min: the fourth round saves both.
        assertEquals(listOf(30_000L, 90_000L, 210_000L, 450_000L), folder.rounds)
        assertEquals(0, folder.pending)
        assertEquals(1, folder.changes)
    }

    @Test
    fun theDelayIsCappedAtFifteenMinutes() = scope.runTest {
        val folder = Folder(pending = 1, failures = 7)
        retry(folder) { currentTime }.kick()
        advanceUntilIdle()
        val gaps = folder.rounds.zipWithNext { a, b -> b - a }
        assertEquals(listOf(60_000L, 120_000L, 240_000L, 480_000L, 900_000L, 900_000L, 900_000L), gaps)
    }

    @Test
    fun stopsWhenFolderAccessIsLost() = scope.runTest {
        val folder = Folder(pending = 1, failures = 99, accessLostAt = 2)
        retry(folder) { currentTime }.kick()
        advanceUntilIdle()
        assertEquals(2, folder.rounds.size)
        assertEquals(1, folder.changes)
    }

    @Test
    fun nothingPendingMeansNoRoundAndKickDoesNotStartASecondLoop() = scope.runTest {
        val idle = Folder(pending = 0, failures = 0)
        retry(idle) { currentTime }.kick()
        advanceUntilIdle()
        assertEquals(emptyList<Long>(), idle.rounds)

        val folder = Folder(pending = 1, failures = 1)
        val retrier = retry(folder) { currentTime }
        retrier.kick()
        retrier.kick()
        advanceUntilIdle()
        assertEquals(listOf(30_000L, 90_000L), folder.rounds)
    }
}
