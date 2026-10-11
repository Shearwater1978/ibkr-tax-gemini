package com.ibkrtax.mobile.ui.common

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.appContainer
import com.ibkrtax.mobile.security.ErasureResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

/**
 * "Erase all data" in Settings (data-anonymization "Erasure and minimization", task 7.6).
 * Confirmed by typing a word rather than the passphrase, since a forgotten passphrase is a
 * reason to erase. Afterwards the app restarts as if freshly installed.
 */
@Composable
fun EraseDataSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ErasureResult?>(null) }

    Column {
        Text(stringResource(R.string.erase_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.erase_body), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
        OutlinedButton(onClick = { confirming = true }) { Text(stringResource(R.string.erase_action), color = MaterialTheme.colorScheme.error) }
    }

    if (confirming) {
        val word = stringResource(R.string.erase_confirm_word)
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!running) confirming = false },
            title = { Text(stringResource(R.string.erase_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.erase_confirm_body, word))
                    OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = typed.trim() == word && !running,
                    onClick = {
                        running = true
                        scope.launch {
                            result = withContext(Dispatchers.IO) { context.appContainer.eraser.eraseAll() }
                            running = false
                            confirming = false
                        }
                    },
                ) { Text(stringResource(if (running) R.string.erase_running else R.string.erase_confirm_button), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }, enabled = !running) { Text(stringResource(R.string.cancel)) } },
        )
    }

    result?.let { erased ->
        AlertDialog(
            // The container's database and keys are gone, so the only way on is a fresh start.
            onDismissRequest = {},
            title = { Text(stringResource(R.string.erase_done_title)) },
            text = { Text(erasureSummary(erased)) },
            confirmButton = { TextButton(onClick = { restart(context) }) { Text(stringResource(R.string.erase_restart)) } },
        )
    }
}

@Composable
private fun erasureSummary(result: ErasureResult): String {
    val folder = result.folder
    return listOfNotNull(
        stringResource(R.string.erase_done_body, folder?.removedRecords ?: 0, folder?.filesDeleted ?: 0),
        if (folder != null && !folder.folderReachable) stringResource(R.string.erase_folder_unreachable) else null,
        folder?.left?.takeIf { it > 0 }?.let { stringResource(R.string.erase_files_left, it) },
        result.failedSteps.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.erase_steps_failed, it.joinToString()) },
    ).joinToString("\n\n")
}

/** Starts the app again in a new process, so nothing from before the erasure stays in memory. */
private fun restart(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    exitProcess(0)
}
