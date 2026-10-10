package com.ibkrtax.mobile.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.ibkrtax.mobile.backup.BackupResult
import com.ibkrtax.mobile.backup.SafBackupFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class FolderInfo(val name: String, val localOnly: Boolean)

/**
 * Settings section for the backup folder (backup-location spec): chosen once with the system
 * folder picker; changing it later keeps the old backups where they are.
 */
@Composable
fun BackupFolderSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    val folder by produceState<FolderInfo?>(null, reload) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.appContainer.backupLocation.get()?.let(Uri::parse)?.let {
                    FolderInfo(SafBackupFolder.displayName(context, it), SafBackupFolder.isLocalOnly(it))
                }
            }.getOrNull()
        }
    }
    val saved = stringResource(R.string.folder_saved)
    val noKeysYet = stringResource(R.string.folder_saved_no_keys)
    val lost = stringResource(R.string.backup_access_lost)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Keep access across restarts; only this folder is granted.
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        scope.launch {
            message = withContext(Dispatchers.IO) {
                val container = context.appContainer
                container.backupLocation.set(uri.toString())
                when (container.backup.syncManifest()) {
                    BackupResult.BACKED_UP -> saved
                    BackupResult.ACCESS_LOST -> lost
                    BackupResult.PENDING -> if (container.backupKeys.dataKey() == null) noKeysYet else saved
                }
            }
            reload++
        }
    }

    Text(stringResource(R.string.folder_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.folder_body), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))

    val current = folder
    if (current == null) {
        Button(onClick = { picker.launch(null) }) { Text(stringResource(R.string.folder_choose)) }
    } else {
        Text(stringResource(R.string.folder_current, current.name))
        if (current.localOnly) {
            Text(stringResource(R.string.folder_local_only), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = { picker.launch(null) }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.folder_change))
        }
        Text(stringResource(R.string.folder_change_hint), style = MaterialTheme.typography.bodySmall)
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
}
