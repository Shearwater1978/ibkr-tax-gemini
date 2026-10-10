package com.ibkrtax.mobile.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ibkrtax.mobile.BuildConfig
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.appContainer
import com.ibkrtax.mobile.prices.ApiKeyCheck
import com.ibkrtax.mobile.prices.FinnhubKeyFormat
import com.ibkrtax.mobile.prices.KeyCheckResult
import com.ibkrtax.mobile.security.Masking
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
        PriceKeySettings()
        Spacer(Modifier.height(24.dp))
        BackupEncryptionSettings()
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.informational_notice), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
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
@Composable
private fun PriceKeySettings() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // Set when the user opens Finnhub; only then is the clipboard looked at.
    var awaitingKey by rememberSaveable { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf<String?>(null) }
    val savedMasked by produceState<String?>(null, reload) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.appContainer.priceStore.get()?.let(Masking::lastFour) }.getOrNull()
        }
    }
    val messages = KeyMessages(
        valid = stringResource(R.string.key_check_valid),
        rejected = stringResource(R.string.key_check_rejected),
        unverified = stringResource(R.string.key_check_unverified),
    )

    // Android only lets a focused window read the clipboard, so check on focus, not on resume.
    LaunchedEffect(windowFocused, awaitingKey) {
        if (windowFocused && awaitingKey) suggestion = FinnhubKeyFormat.candidate(clipboard.getText()?.text)
    }

    fun saveKey(key: String) {
        scope.launch {
            checking = true
            message = null
            val container = context.appContainer
            val result = ApiKeyCheck.check(container.finnhub(key))
            if (result != KeyCheckResult.Rejected) {
                withContext(Dispatchers.IO) { container.priceStore.set(key) }
                input = ""
                suggestion = null
                awaitingKey = false
                reload++
            }
            message = messages.describe(result)
            checking = false
        }
    }

    Text(stringResource(R.string.settings_prices_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.settings_prices_body), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))

    val masked = savedMasked
    if (masked != null) {
        Text(stringResource(R.string.settings_api_key_saved, masked))
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = {
            scope.launch {
                withContext(Dispatchers.IO) { context.appContainer.priceStore.clear() }
                message = null
                reload++
            }
        }) { Text(stringResource(R.string.settings_api_key_remove)) }
        return
    }

    Text(stringResource(R.string.key_guide_title), fontWeight = FontWeight.Bold)
    listOf(R.string.key_guide_step1, R.string.key_guide_step2, R.string.key_guide_step3).forEach {
        Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
    Button(
        onClick = {
            awaitingKey = true
            suggestion = null
            if (!openInChromeTab(context, FINNHUB_SIGN_UP)) message = context.getString(R.string.key_guide_no_browser, FINNHUB_SIGN_UP)
        },
        modifier = Modifier.padding(vertical = 8.dp),
    ) { Text(stringResource(R.string.key_guide_open)) }

    suggestion?.let { key ->
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.key_suggestion, Masking.lastFour(key)))
                Row {
                    Button(enabled = !checking, onClick = { saveKey(key) }) { Text(stringResource(R.string.key_suggestion_use)) }
                    TextButton(onClick = { suggestion = null }) { Text(stringResource(R.string.key_suggestion_dismiss)) }
                }
            }
        }
    }

    OutlinedTextField(
        value = input,
        onValueChange = { input = it.trim() },
        label = { Text(stringResource(R.string.settings_api_key_label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            TextButton(onClick = { clipboard.getText()?.text?.trim()?.let { input = it } }) {
                Text(stringResource(R.string.key_paste))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        enabled = input.isNotBlank() && !checking,
        onClick = { saveKey(input) },
        modifier = Modifier.padding(top = 8.dp),
    ) { Text(stringResource(if (checking) R.string.key_checking else R.string.settings_api_key_save)) }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

/** Opens [url] in a Chrome Custom Tab, falling back to the default browser. Returns false if neither exists. */
private fun openInChromeTab(context: Context, url: String): Boolean {
    val uri = Uri.parse(url)
    return try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
        true
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}

private const val FINNHUB_SIGN_UP = "https://finnhub.io/register"

/** Resolved in composition so the result can be described from a coroutine. */
private class KeyMessages(private val valid: String, private val rejected: String, private val unverified: String) {
    fun describe(result: KeyCheckResult): String = when (result) {
        KeyCheckResult.Valid -> valid
        KeyCheckResult.Rejected -> rejected
        is KeyCheckResult.Unverified -> unverified
    }
}
