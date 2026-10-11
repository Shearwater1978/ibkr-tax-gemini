package com.ibkrtax.mobile.ui.common

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.ibkrtax.mobile.backup.FoundBackup
import com.ibkrtax.mobile.backup.RestoreOutcome
import com.ibkrtax.mobile.backup.RestoreSecret
import com.ibkrtax.mobile.backup.SafBackupFolder
import com.ibkrtax.mobile.storage.BackupLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Restore on a new phone (encrypted-backup "Restore on a new device"): pick the backup folder;
 * the Block Store key is used when it matches, otherwise the passphrase or recovery code is asked for.
 * After a restore the folder becomes this phone's backup folder; [onRestored] gets the result to show,
 * because this section disappears once the keys exist.
 */
@Composable
fun RestoreSection(onRestored: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var askFor by remember { mutableStateOf<Pair<Uri, FoundBackup>?>(null) }
    var wrong by remember { mutableStateOf(false) }
    val texts = RestoreTexts()

    fun run(uri: Uri, found: FoundBackup, secret: RestoreSecret?) {
        running = true
        scope.launch {
            val container = context.appContainer
            val outcome = withContext(Dispatchers.IO) {
                val cloudKey = if (secret == null) container.backupKeys.cloudKey() else null
                container.restore.restore(SafBackupFolder(context, uri, found.subfolder), secret, cloudKey) { manifest, key ->
                    container.backupKeys.adopt(manifest, key)
                }.also { if (it is RestoreOutcome.Restored) container.backupLocation.set(BackupLocation(uri.toString(), found.subfolder)) }
            }
            running = false
            wrong = outcome == RestoreOutcome.WrongSecret
            when (outcome) {
                RestoreOutcome.NeedsSecret, RestoreOutcome.WrongSecret -> askFor = uri to found
                is RestoreOutcome.Restored -> {
                    askFor = null
                    message = null
                    onRestored(texts.done(outcome))
                }
                else -> {
                    askFor = null
                    message = texts.problem(outcome)
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        scope.launch {
            val found = withContext(Dispatchers.IO) { SafBackupFolder.locateBackup(context, uri) }
            if (found == null) message = texts.noBackup else run(uri, found, secret = null)
        }
    }

    Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.restore_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.restore_body), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            // Starts in Documents, where "This phone" backups live; Google Drive is one tap away in the picker menu.
            OutlinedButton(onClick = { message = null; picker.launch(DOCUMENTS) }, enabled = !running) {
                Text(stringResource(if (running) R.string.restore_running else R.string.restore_choose))
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
        }
    }

    askFor?.let { (uri, found) ->
        UnlockDialog(
            wrong = wrong,
            running = running,
            onRestore = { secret -> run(uri, found, secret) },
            onDismiss = { askFor = null; wrong = false },
        )
    }
}

@Composable
private fun UnlockDialog(wrong: Boolean, running: Boolean, onRestore: (RestoreSecret) -> Unit, onDismiss: () -> Unit) {
    var useCode by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_unlock_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.restore_unlock_body))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !useCode, onClick = { useCode = false; value = "" }, label = { Text(stringResource(R.string.restore_use_passphrase)) })
                    FilterChip(selected = useCode, onClick = { useCode = true; value = "" }, label = { Text(stringResource(R.string.restore_use_code)) })
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(if (useCode) R.string.restore_use_code else R.string.restore_use_passphrase)) },
                    singleLine = true,
                    visualTransformation = if (useCode) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = if (useCode) KeyboardType.Ascii else KeyboardType.Password),
                    isError = wrong,
                    supportingText = if (wrong) ({ Text(stringResource(R.string.restore_wrong)) }) else null,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRestore(if (useCode) RestoreSecret.RecoveryCode(value) else RestoreSecret.Passphrase(value.toCharArray())) },
                enabled = value.isNotBlank() && !running,
            ) { Text(stringResource(if (running) R.string.restore_running else R.string.restore_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private class RestoreTexts(
    val noBackup: String,
    val accessLost: String,
    val newer: String,
    val done: (RestoreOutcome.Restored) -> String,
) {
    fun problem(outcome: RestoreOutcome): String = when (outcome) {
        RestoreOutcome.AccessLost -> accessLost
        RestoreOutcome.NewerVersion -> newer
        else -> noBackup
    }
}

@Composable
private fun RestoreTexts(): RestoreTexts {
    val resources = LocalContext.current.resources
    return RestoreTexts(
        noBackup = stringResource(R.string.restore_no_backup),
        accessLost = stringResource(R.string.restore_access_lost),
        newer = stringResource(R.string.restore_newer),
        done = { r ->
            listOfNotNull(
                resources.getQuantityString(R.plurals.restore_done, r.reports, r.reports, r.inserted),
                r.alreadyImported.takeIf { it > 0 }?.let { resources.getString(R.string.restore_already, it) },
                r.unreadable.takeIf { it > 0 }?.let { resources.getString(R.string.restore_unreadable, it) },
            ).joinToString(" ")
        },
    )
}
