package com.ibkrtax.mobile

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ibkrtax.mobile.security.AppLockPolicy
import com.ibkrtax.mobile.ui.AppScaffold
import com.ibkrtax.mobile.ui.screens.LockScreen
import com.ibkrtax.mobile.ui.screens.LockState
import java.time.Instant

/** FragmentActivity because BiometricPrompt needs one. */
class MainActivity : FragmentActivity() {
    private val policy = AppLockPolicy()
    private var lockState by mutableStateOf<LockState>(LockState.Locked)
    private var backgroundedAt: Instant? = null

    /** Prompt automatically once per lock; after a cancel the user taps Unlock, so cancelling cannot loop. */
    private var autoPrompt = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No screenshots, screen recording, or app-switcher preview of financial data.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        if (savedInstanceState?.getBoolean(KEY_UNLOCKED) == true) lockState = LockState.Unlocked
        backgroundedAt = savedInstanceState?.getLong(KEY_BACKGROUNDED_AT, 0L)?.takeIf { it > 0 }?.let(Instant::ofEpochMilli)

        setContent {
            MaterialTheme {
                when (val state = lockState) {
                    LockState.Unlocked -> AppScaffold()
                    else -> LockScreen(state, onUnlock = ::authenticate, onContinueWithoutLock = { unlock() })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (lockState == LockState.Unlocked && policy.shouldLock(backgroundedAt, Instant.now())) {
            lockState = LockState.Locked
            autoPrompt = true
        }
        if (lockState == LockState.Locked && autoPrompt) {
            autoPrompt = false
            authenticate()
        }
    }

    override fun onStop() {
        super.onStop()
        // Rotation recreates the activity without leaving the app; it must not start the timer.
        if (!isChangingConfigurations && lockState == LockState.Unlocked) backgroundedAt = Instant.now()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, lockState == LockState.Unlocked)
        backgroundedAt?.let { outState.putLong(KEY_BACKGROUNDED_AT, it.toEpochMilli()) }
    }

    private fun authenticate() {
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        } else {
            // Android 10 cannot combine strong biometrics with the device credential.
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        }
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            lockState = LockState.NoScreenLock
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = unlock()

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    lockState = LockState.Locked
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.lock_prompt_title))
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    private fun unlock() {
        lockState = LockState.Unlocked
        backgroundedAt = null
    }

    private companion object {
        const val KEY_UNLOCKED = "unlocked"
        const val KEY_BACKGROUNDED_AT = "backgrounded_at"
    }
}
