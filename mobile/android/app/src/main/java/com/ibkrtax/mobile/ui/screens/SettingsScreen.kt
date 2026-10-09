package com.ibkrtax.mobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.appContainer
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
        Text(stringResource(R.string.informational_notice), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.settings_version, version), style = MaterialTheme.typography.bodySmall)
    }
}

/** The key is written to the encrypted database and only ever shown masked. */
@Composable
private fun PriceKeySettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    val savedMasked by produceState<String?>(null, reload) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.appContainer.priceStore.get()?.let(Masking::lastFour) }.getOrNull()
        }
    }

    Text(stringResource(R.string.settings_prices_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.settings_prices_body), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))

    val masked = savedMasked
    if (masked != null) {
        Text(stringResource(R.string.settings_api_key_saved, masked))
        OutlinedButton(onClick = {
            scope.launch {
                withContext(Dispatchers.IO) { context.appContainer.priceStore.clear() }
                reload++
            }
        }) { Text(stringResource(R.string.settings_api_key_remove)) }
    } else {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(stringResource(R.string.settings_api_key_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = input.isNotBlank(),
            onClick = {
                val key = input
                scope.launch {
                    withContext(Dispatchers.IO) { context.appContainer.priceStore.set(key) }
                    input = ""
                    reload++
                }
            },
        ) { Text(stringResource(R.string.settings_api_key_save)) }
    }
}
