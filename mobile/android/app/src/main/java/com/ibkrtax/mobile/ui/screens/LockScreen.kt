package com.ibkrtax.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ibkrtax.mobile.R

sealed interface LockState {
    data object Locked : LockState

    /** The device has no screen lock, so the app cannot verify the user. */
    data object NoScreenLock : LockState

    data object Unlocked : LockState
}

/** Shown instead of the app until the user unlocks; it reveals no portfolio data. */
@Composable
fun LockScreen(state: LockState, onUnlock: () -> Unit, onContinueWithoutLock: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (state == LockState.NoScreenLock) {
            Text(stringResource(R.string.lock_no_screen_lock), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onContinueWithoutLock) { Text(stringResource(R.string.lock_continue)) }
        } else {
            Text(stringResource(R.string.lock_locked), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onUnlock) { Text(stringResource(R.string.lock_unlock)) }
        }
    }
}
