package com.ibkrtax.mobile.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.appContainer
import com.ibkrtax.mobile.prices.ApiKeyCheck
import com.ibkrtax.mobile.prices.FinnhubKeyFormat
import com.ibkrtax.mobile.prices.KeyCheckResult
import com.ibkrtax.mobile.prices.PriceProviderId
import com.ibkrtax.mobile.prices.selected
import com.ibkrtax.mobile.security.Masking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What differs between the providers' key forms. */
private class ProviderTexts(
    val keyLabel: Int,
    val guide: List<Int>,
    val open: Int,
    val signUp: String,
    val rejected: Int,
    /** Finnhub only: offer the key the user just copied on the provider's page. */
    val suggestsCopiedKey: Boolean,
    val note: Int? = null,
)

private fun textsFor(provider: PriceProviderId): ProviderTexts? = when (provider) {
    PriceProviderId.NONE -> null
    PriceProviderId.FINNHUB -> ProviderTexts(
        keyLabel = R.string.settings_api_key_label,
        guide = listOf(R.string.key_guide_step1, R.string.key_guide_step2, R.string.key_guide_step3),
        open = R.string.key_guide_open,
        signUp = FINNHUB_SIGN_UP,
        rejected = R.string.key_check_rejected,
        suggestsCopiedKey = true,
    )
    PriceProviderId.TWELVE_DATA -> ProviderTexts(
        keyLabel = R.string.td_key_label,
        guide = listOf(R.string.td_guide_step1, R.string.td_guide_step2, R.string.td_guide_step3),
        open = R.string.td_guide_open,
        signUp = TWELVE_DATA_SIGN_UP,
        rejected = R.string.td_key_rejected,
        suggestsCopiedKey = false,
        note = R.string.td_free_plan,
    )
}

/**
 * "Market prices" in Settings (mobile-market-prices "Choose a price provider and use the user's
 * own key"): None, Finnhub or Twelve Data, and the chosen provider's key. Yahoo Finance is not a choice.
 */
@Composable
fun PriceProviderSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    val chosen by produceState<PriceProviderId?>(null, reload) {
        value = withContext(Dispatchers.IO) { runCatching { context.appContainer.priceStore.selected() }.getOrNull() }
    }

    Text(stringResource(R.string.settings_prices_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.prices_intro), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.provider_choice_title), fontWeight = FontWeight.Bold)
    Column(Modifier.selectableGroup()) {
        listOf(
            Triple(PriceProviderId.NONE, R.string.provider_none, R.string.provider_none_body),
            Triple(PriceProviderId.FINNHUB, R.string.provider_finnhub, R.string.provider_finnhub_body),
            Triple(PriceProviderId.TWELVE_DATA, R.string.provider_twelvedata, R.string.provider_twelvedata_body),
        ).forEach { (id, name, body) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = chosen == id, role = Role.RadioButton) {
                    scope.launch {
                        withContext(Dispatchers.IO) { context.appContainer.priceStore.choose(id) }
                        reload++
                    }
                }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = chosen == id, onClick = null)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(stringResource(name))
                    Text(stringResource(body), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    val current = chosen ?: return
    textsFor(current)?.let { texts ->
        Spacer(Modifier.height(8.dp))
        // A fresh form per provider, so typed text and messages never carry over.
        key(current) { ProviderKeyForm(current, texts) }
    }
}

@Composable
private fun ProviderKeyForm(provider: PriceProviderId, texts: ProviderTexts) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    // Set when the user opens the provider's page; only then is the clipboard looked at (Finnhub).
    var awaitingKey by rememberSaveable { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf<String?>(null) }
    val savedMasked by produceState<String?>(null, reload) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.appContainer.priceStore.get(provider)?.let(Masking::lastFour) }.getOrNull()
        }
    }
    val messages = KeyMessages(
        valid = stringResource(R.string.key_check_valid),
        rejected = stringResource(texts.rejected),
        unverified = stringResource(R.string.key_check_unverified),
    )

    // Android only lets a focused window read the clipboard, so check on focus, not on resume.
    LaunchedEffect(windowFocused, awaitingKey) {
        if (texts.suggestsCopiedKey && windowFocused && awaitingKey) suggestion = FinnhubKeyFormat.candidate(clipboard.getText()?.text)
    }

    fun saveKey(key: String) {
        scope.launch {
            checking = true
            message = null
            val container = context.appContainer
            val result = ApiKeyCheck.check(container.provider(provider, key))
            if (result != KeyCheckResult.Rejected) {
                withContext(Dispatchers.IO) { container.priceStore.set(provider, key) }
                input = ""
                suggestion = null
                awaitingKey = false
                reload++
            }
            message = messages.describe(result)
            checking = false
        }
    }

    texts.note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp)) }

    val masked = savedMasked
    if (masked != null) {
        Text(stringResource(R.string.settings_api_key_saved, masked))
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = {
            scope.launch {
                withContext(Dispatchers.IO) { context.appContainer.priceStore.clear(provider) }
                message = null
                reload++
            }
        }) { Text(stringResource(R.string.settings_api_key_remove)) }
        return
    }

    Text(stringResource(R.string.key_guide_title), fontWeight = FontWeight.Bold)
    texts.guide.forEach {
        Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
    }
    Button(
        onClick = {
            awaitingKey = true
            suggestion = null
            if (!openInChromeTab(context, texts.signUp)) message = context.getString(R.string.key_guide_no_browser, texts.signUp)
        },
        modifier = Modifier.padding(vertical = 8.dp),
    ) { Text(stringResource(texts.open)) }

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
        label = { Text(stringResource(texts.keyLabel)) },
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
internal fun openInChromeTab(context: Context, url: String): Boolean {
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

/** Where Twelve Data's own error messages send users for a free key. */
private const val TWELVE_DATA_SIGN_UP = "https://twelvedata.com/pricing"

/** Twelve Data attribution target (mobile-market-prices "Attribution"). */
internal const val TWELVE_DATA_HOME = "https://twelvedata.com"

/** Resolved in composition so the result can be described from a coroutine. */
private class KeyMessages(private val valid: String, private val rejected: String, private val unverified: String) {
    fun describe(result: KeyCheckResult): String = when (result) {
        KeyCheckResult.Valid -> valid
        KeyCheckResult.Rejected -> rejected
        is KeyCheckResult.Unverified -> unverified
    }
}
