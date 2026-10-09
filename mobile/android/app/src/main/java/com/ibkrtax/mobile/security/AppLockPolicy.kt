package com.ibkrtax.mobile.security

import java.time.Duration
import java.time.Instant

/**
 * When the app must ask for biometrics or the device credential again
 * (device-hardening spec, auto-lock). The app always starts locked; after that it
 * locks once it has been in the background for at least [timeout].
 */
class AppLockPolicy(private val timeout: Duration = DEFAULT_TIMEOUT) {
    /** [backgroundedAt] is null when the app has not left the foreground since it was unlocked. */
    fun shouldLock(backgroundedAt: Instant?, now: Instant): Boolean =
        backgroundedAt != null && Duration.between(backgroundedAt, now) >= timeout

    companion object {
        val DEFAULT_TIMEOUT: Duration = Duration.ofMinutes(5)
    }
}
