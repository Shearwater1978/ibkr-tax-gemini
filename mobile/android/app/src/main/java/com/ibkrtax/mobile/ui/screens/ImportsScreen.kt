package com.ibkrtax.mobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.appContainer
import com.ibkrtax.mobile.debug.SampleReports
import com.ibkrtax.mobile.storage.ImportResult
import com.ibkrtax.mobile.storage.ImportedReport
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The import button stays disabled until encrypted Drive backup exists (mobile-app-core
 * task 1.2). Debug builds can load the synthetic sample reports instead.
 */
@Composable
fun ImportsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var debugMessages by remember { mutableStateOf(emptyList<String>()) }
    val history by produceState<List<ImportedReport>?>(null, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { context.appContainer.importHistory.list() }.getOrNull() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.imports_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.imports_supported_format))
        Spacer(Modifier.height(16.dp))
        Button(onClick = {}, enabled = false) { Text(stringResource(R.string.action_import_report)) }
        Text(stringResource(R.string.imports_unavailable), style = MaterialTheme.typography.bodySmall)

        if (SampleReports.AVAILABLE) {
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.debug_samples_title), style = MaterialTheme.typography.titleMedium)
            val messages = SampleMessages()
            OutlinedButton(onClick = {
                scope.launch {
                    debugMessages = withContext(Dispatchers.IO) {
                        SampleReports.names.map { name ->
                            messages.describe(name, context.appContainer.importer.import(SampleReports.read(context, name)))
                        }
                    }
                    refresh++
                }
            }) { Text(stringResource(R.string.debug_samples_action)) }
            debugMessages.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.imports_history_title), style = MaterialTheme.typography.titleMedium)
        val reports = history
        when {
            reports == null -> Text(stringResource(R.string.loading))
            reports.isEmpty() -> Text(stringResource(R.string.imports_empty))
            else -> reports.forEach { ReportRow(it) }
        }
    }
}

@Composable
private fun ReportRow(report: ImportedReport) {
    val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(report.importedAt))
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(stringResource(R.string.imports_history_item, time, report.accountMasked ?: stringResource(R.string.imports_account_unknown)))
        Text(stringResource(R.string.imports_history_counts, report.inserted, report.skipped), style = MaterialTheme.typography.bodySmall)
    }
}

/** Resolves result strings while in composition so they can be used from a coroutine. */
private class SampleMessages(
    private val imported: String,
    private val already: String,
    private val rejected: String,
) {
    fun describe(name: String, result: ImportResult): String = when (result) {
        is ImportResult.Imported -> imported.format(name, result.inserted, result.skipped)
        ImportResult.AlreadyImported -> already.format(name)
        is ImportResult.Rejected -> rejected.format(name)
    }
}

@Composable
private fun SampleMessages() = SampleMessages(
    imported = stringResource(R.string.debug_samples_imported),
    already = stringResource(R.string.debug_samples_already),
    rejected = stringResource(R.string.debug_samples_rejected),
)
