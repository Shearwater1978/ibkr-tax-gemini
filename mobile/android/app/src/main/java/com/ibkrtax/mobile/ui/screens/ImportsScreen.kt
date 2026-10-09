package com.ibkrtax.mobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ibkrtax.mobile.R

/** Import stays disabled until the mobile-encrypted-drive-backup storage exists (task 1.2). */
@Composable
fun ImportsScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.imports_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.imports_supported_format))
        Spacer(Modifier.height(16.dp))
        Button(onClick = {}, enabled = false) { Text(stringResource(R.string.action_import_report)) }
        Text(stringResource(R.string.imports_unavailable), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.imports_empty))
    }
}
