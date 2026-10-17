package com.ibkrtax.mobile.ui.common

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ibkrtax.mobile.BuildConfig
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.appContainer
import com.ibkrtax.mobile.ui.skins.SkinSettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.settings_privacy))
        Spacer(Modifier.height(24.dp))
        SkinSettingsSection()
        Spacer(Modifier.height(24.dp))
        BrokerSettingsSection()
        Spacer(Modifier.height(24.dp))
        PriceProviderSettings()
        Spacer(Modifier.height(24.dp))
        BackupEncryptionSettings()
        Spacer(Modifier.height(24.dp))
        BackupFolderSettings()
        Spacer(Modifier.height(24.dp))
        PrivacySection()
        Spacer(Modifier.height(24.dp))
        EraseDataSection()
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.settings_version_full, version, BuildConfig.BUILD_TIME_UTC, BuildConfig.BUILD_TYPE),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Guides the user to a free Finnhub key, checks it once, and only ever shows it masked.
 * Finnhub has no sign-in for third-party apps, so its site opens in a Chrome tab (the
 * app never sees the password) and, on return, a just-copied key is offered.
 */
/** A short version of mobile/PRIVACY.md (task 7.8). */
@Composable
private fun PrivacySection() {
    Column {
        Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
        listOf(R.string.privacy_on_device, R.string.privacy_leaves, R.string.privacy_choices).forEach {
            Text(stringResource(it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
