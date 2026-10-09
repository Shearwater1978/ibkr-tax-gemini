package com.ibkrtax.mobile.security

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    private val policy = AppLockPolicy(Duration.ofMinutes(5))
    private val now = Instant.parse("2026-10-09T12:00:00Z")

    @Test
    fun staysUnlockedWhileTheAppHasNotLeftTheForeground() {
        assertFalse(policy.shouldLock(backgroundedAt = null, now = now))
    }

    @Test
    fun shortAppSwitchesDoNotLock() {
        assertFalse(policy.shouldLock(now.minusSeconds(299), now))
    }

    @Test
    fun locksAfterFiveMinutesInTheBackground() {
        assertTrue(policy.shouldLock(now.minusSeconds(300), now))
        assertTrue(policy.shouldLock(now.minus(Duration.ofHours(3)), now))
    }
}
