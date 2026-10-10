package com.ibkrtax.mobile.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.appContainer
import com.ibkrtax.mobile.backup.BackupLocationKind
import com.ibkrtax.mobile.backup.BackupResult
import com.ibkrtax.mobile.backup.SafBackupFolder
import com.ibkrtax.mobile.storage.BackupLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LocationInfo(val kind: BackupLocationKind?, val path: String)

/**
 * Settings section for the backup location (backup-location spec): "This phone" or
 * "Google Drive". The user confirms a parent folder in the system picker and the app
 * stores backups in its own subfolder there; changing location keeps old backups.
 */
@Composable
fun BackupFolderSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    // The option the user tapped, kept across the picker and rotation to check the result.
    var requested by rememberSaveable { mutableStateOf<BackupLocationKind?>(null) }
    val driveInstalled = remember { isInstalled(context, BackupLocationKind.DRIVE_PACKAGE) }
    val current by produceState<LocationInfo?>(null, reload) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.appContainer.backupLocation.get()?.let { location ->
                    val uri = Uri.parse(location.treeUri)
                    LocationInfo(BackupLocationKind.of(uri.authority), SafBackupFolder.displayName(context, uri, location.subfolder))
                }
            }.getOrNull()
        }
    }
    val texts = LocationTexts(
        saved = stringResource(R.string.folder_saved),
        noKeysYet = stringResource(R.string.folder_saved_no_keys),
        lost = stringResource(R.string.backup_access_lost),
        notDrive = stringResource(R.string.location_mismatch_drive),
        notPhone = stringResource(R.string.location_mismatch_phone),
    )

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val kind = requested
        requested = null
        if (uri == null || kind == null) return@rememberLauncherForActivityResult
        if (BackupLocationKind.of(uri.authority) != kind) {
            // Keep the previous location (backup-location spec "Location mismatch").
            message = if (kind == BackupLocationKind.GOOGLE_DRIVE) texts.notDrive else texts.notPhone
            return@rememberLauncherForActivityResult
        }
        // Keep access across restarts; only this folder is granted.
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        scope.launch {
            message = withContext(Dispatchers.IO) { useFolder(context, uri, texts) }
            reload++
        }
    }

    fun choose(kind: BackupLocationKind) {
        message = null
        requested = kind
        // The phone option starts in Documents; Drive cannot be preselected, so the user opens it from the picker menu.
        picker.launch(if (kind == BackupLocationKind.PHONE) DOCUMENTS else null)
    }

    Text(stringResource(R.string.location_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.folder_body), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))

    current?.let { location ->
        Text(stringResource(R.string.location_current, stringResource(labelFor(location.kind)), location.path))
        if (location.kind == BackupLocationKind.PHONE) {
            Text(stringResource(R.string.folder_local_only), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.location_change_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
    }

    Text(stringResource(R.string.location_question), style = MaterialTheme.typography.bodyMedium)
    Button(onClick = { choose(BackupLocationKind.GOOGLE_DRIVE) }, enabled = driveInstalled, modifier = Modifier.padding(top = 4.dp)) {
        Text(stringResource(R.string.location_drive))
    }
    Text(
        stringResource(if (driveInstalled) R.string.location_drive_hint else R.string.location_drive_missing),
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(onClick = { choose(BackupLocationKind.PHONE) }, modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.location_phone))
    }
    Text(stringResource(R.string.location_phone_hint), style = MaterialTheme.typography.bodySmall)
    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
}

/** Uses the confirmed folder (or the app's subfolder in it), then writes the manifest there. */
private fun useFolder(context: Context, uri: Uri, texts: LocationTexts): String {
    val subfolder = if (SafBackupFolder.containsManifest(context, uri)) null else BackupLocationKind.SUBFOLDER
    val container = context.appContainer
    container.backupLocation.set(BackupLocation(uri.toString(), subfolder))
    return when (container.backup.syncManifest()) {
        BackupResult.BACKED_UP -> texts.saved
        BackupResult.ACCESS_LOST -> texts.lost
        BackupResult.PENDING -> if (container.backupKeys.dataKey() == null) texts.noKeysYet else texts.saved
    }
}

private class LocationTexts(val saved: String, val noKeysYet: String, val lost: String, val notDrive: String, val notPhone: String)

private fun labelFor(kind: BackupLocationKind?): Int = when (kind) {
    BackupLocationKind.GOOGLE_DRIVE -> R.string.location_label_drive
    BackupLocationKind.PHONE -> R.string.location_label_phone
    null -> R.string.location_label_other
}

private fun isInstalled(context: Context, packageName: String): Boolean =
    try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

/** Primary storage "Documents", where the phone option's picker opens. */
private val DOCUMENTS: Uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents")
