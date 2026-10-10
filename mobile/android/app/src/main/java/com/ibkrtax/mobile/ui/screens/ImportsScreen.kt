package com.ibkrtax.mobile.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
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
import com.ibkrtax.mobile.backup.BackupResult
import com.ibkrtax.mobile.backup.ImportOutcome
import com.ibkrtax.mobile.debug.SampleReports
import com.ibkrtax.mobile.importer.FlexImportError
import com.ibkrtax.mobile.security.FileNameMasking
import com.ibkrtax.mobile.keys.BackupKeyStatus
import com.ibkrtax.mobile.storage.ImportHistory
import com.ibkrtax.mobile.storage.ImportRejection
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
 * Report import with encrypted backup. The button stays off until backup encryption and a
 * backup folder are set up (mobile-app-core task 1.2). Debug builds can also load the
 * synthetic sample reports, which are imported without a backup.
 */
@Composable
fun ImportsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var debugMessages by remember { mutableStateOf(emptyList<String>()) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<String?>(null) }
    var fileResults by remember { mutableStateOf(emptyList<String>()) }
    val keyStatus by produceState<BackupKeyStatus?>(null) {
        value = runCatching { context.appContainer.backupKeys.status() }.getOrNull()
    }
    val ready by produceState(false, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { context.appContainer.backup.isReady }.getOrDefault(false) }
    }
    val history by produceState<List<ImportedReport>?>(null, refresh) {
        value = withContext(Dispatchers.IO) { runCatching { context.appContainer.importHistory.list() }.getOrNull() }
    }
    val texts = ImportTexts()

    // Backups that could not be written earlier are retried whenever this screen opens.
    LaunchedEffect(Unit) {
        val retry = withContext(Dispatchers.IO) { runCatching { context.appContainer.backup.retryPending() }.getOrNull() }
        if (retry?.accessLost == true) importMessage = texts.accessLost
        refresh++
    }

    // Several files at once; each is imported on its own, so one bad file does not stop the rest.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = true
        importMessage = null
        fileResults = emptyList()
        scope.launch {
            for ((index, uri) in uris.withIndex()) {
                progress = texts.progress.format(index + 1, uris.size)
                val line = withContext(Dispatchers.IO) {
                    val name = FileNameMasking.mask(displayName(context, uri))
                    val bytes = readLimited(context, uri)
                    val message = if (bytes == null) texts.tooLarge else texts.describe(context.appContainer.backup.importReport(bytes))
                    "$name: $message"
                }
                fileResults = fileResults + line
                refresh++
            }
            progress = null
            importing = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.imports_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.imports_supported_format))
        Spacer(Modifier.height(16.dp))
        Button(onClick = { picker.launch(CSV_TYPES) }, enabled = ready && !importing) {
            Text(stringResource(if (importing) R.string.importing else R.string.action_import_report))
        }
        if (!ready) {
            // Import stays off until backups work (mobile-app-core task 1.2); say which step is next.
            Text(
                stringResource(
                    when (keyStatus) {
                        BackupKeyStatus.NotSetUp -> R.string.imports_unavailable_keys
                        else -> R.string.imports_unavailable_folder
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(stringResource(R.string.import_multiple_hint), style = MaterialTheme.typography.bodySmall)
        progress?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
        fileResults.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
        importMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
        val pending = history.orEmpty().count { it.backupStatus == ImportHistory.PENDING }
        if (pending > 0) {
            Text(stringResource(R.string.backup_pending_count, pending), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    val retry = withContext(Dispatchers.IO) { context.appContainer.backup.retryPending() }
                    importMessage = if (retry.accessLost) texts.accessLost else texts.retried.format(retry.backedUp, retry.stillPending)
                    refresh++
                }
            }) { Text(stringResource(R.string.backup_retry)) }
        }

        if (SampleReports.AVAILABLE) {
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.debug_samples_title), style = MaterialTheme.typography.titleMedium)
            val messages = SampleMessages()
            OutlinedButton(onClick = {
                scope.launch {
                    debugMessages = withContext(Dispatchers.IO) {
                        val container = context.appContainer
                        // Synthetic, past-dated prices and rates so the table and USD total can be checked offline.
                        container.priceStore.putAll(SampleReports.samplePrices())
                        SampleReports.sampleRates()?.let(container.fxStore::put)
                        SampleReports.names.map { name ->
                            messages.describe(name, container.importer.import(SampleReports.read(context, name)))
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
        Text(
            stringResource(
                when (report.backupStatus) {
                    ImportHistory.BACKED_UP -> R.string.backup_status_done
                    ImportHistory.PENDING -> R.string.backup_status_pending
                    else -> R.string.backup_status_none
                },
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** IBKR exports may be labelled as CSV, plain text, Excel CSV or generic binary by different apps. */
private val CSV_TYPES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "application/octet-stream")

private const val MAX_REPORT_BYTES = 20 * 1024 * 1024

/** The picked file's name as the storage app reports it. */
private fun displayName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment.orEmpty()

/** Reads the picked file into memory; null when it is larger than any plausible report. */
private fun readLimited(context: Context, uri: Uri): ByteArray? =
    context.contentResolver.openInputStream(uri)?.use { input ->
        // InputStream.readNBytes needs Android 13; read in chunks for Android 10+.
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > MAX_REPORT_BYTES) return@use null
        }
        out.toByteArray()
    }

/** Result texts, resolved in composition so a coroutine can use them. */
private class ImportTexts(
    private val importedDone: String,
    private val importedPending: String,
    private val importedAccessLost: String,
    private val already: String,
    private val notReady: String,
    private val unsupported: String,
    private val empty: String,
    private val malformed: String,
    private val invalid: String,
    val tooLarge: String,
    val accessLost: String,
    val retried: String,
    val progress: String,
) {
    fun describe(outcome: ImportOutcome): String = when (outcome) {
        is ImportOutcome.Imported -> when (outcome.backup) {
            BackupResult.BACKED_UP -> importedDone
            BackupResult.PENDING -> importedPending
            BackupResult.ACCESS_LOST -> importedAccessLost
        }.format(outcome.inserted, outcome.skipped)
        ImportOutcome.AlreadyImported -> already
        ImportOutcome.NotReady -> notReady
        is ImportOutcome.Rejected -> when (val reason = outcome.reason) {
            is ImportRejection.InvalidRecord -> invalid
            is ImportRejection.Unparseable -> when (val error = reason.error) {
                FlexImportError.EmptyFile -> empty
                FlexImportError.UnsupportedFormat -> unsupported
                is FlexImportError.Malformed -> malformed.format(error.line)
            }
        }
    }
}

@Composable
private fun ImportTexts() = ImportTexts(
    importedDone = stringResource(R.string.import_done),
    importedPending = stringResource(R.string.import_done_pending),
    importedAccessLost = stringResource(R.string.import_done_access_lost),
    already = stringResource(R.string.import_already),
    notReady = stringResource(R.string.import_not_ready),
    unsupported = stringResource(R.string.import_unsupported),
    empty = stringResource(R.string.import_empty),
    malformed = stringResource(R.string.import_malformed),
    invalid = stringResource(R.string.import_invalid_record),
    tooLarge = stringResource(R.string.import_too_large),
    accessLost = stringResource(R.string.backup_access_lost),
    retried = stringResource(R.string.backup_retried),
    progress = stringResource(R.string.import_progress),
)

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
