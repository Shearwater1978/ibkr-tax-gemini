package com.ibkrtax.mobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.appContainer
import com.ibkrtax.mobile.keys.BackupKeyStatus
import com.ibkrtax.mobile.keys.PassphrasePolicy
import com.ibkrtax.mobile.keys.PassphraseProblem
import com.ibkrtax.mobile.keys.PendingKeySetup
import com.ibkrtax.mobile.keys.PendingRecoveryCode
import com.ibkrtax.mobile.keys.RecoveryCode
import com.ibkrtax.mobile.keys.WrongSecretException
import com.ibkrtax.mobile.ui.SensitiveClipboard
import kotlinx.coroutines.launch

private sealed interface EncryptionStep {
    data object Idle : EncryptionStep

    data object CreateForm : EncryptionStep

    data object ChangeForm : EncryptionStep

    data object Working : EncryptionStep

    data object NewCodeForm : EncryptionStep

    /** Shown exactly once; keys are stored only when the user confirms. */
    data class ShowRecoveryCode(val pending: PendingKeySetup, val cloudCopy: Boolean) : EncryptionStep

    /** A replacement code; the old one keeps working until the user confirms. */
    data class ShowNewRecoveryCode(val pending: PendingRecoveryCode) : EncryptionStep
}

/**
 * Settings section for backup encryption: set up a passphrase, show the recovery code once,
 * change the passphrase, or replace a lost recovery code.
 */
@Composable
fun BackupEncryptionSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }
    var step by remember { mutableStateOf<EncryptionStep>(EncryptionStep.Idle) }
    var error by remember { mutableStateOf<String?>(null) }
    val status by produceState<BackupKeyStatus?>(null, reload) {
        value = runCatching { context.appContainer.backupKeys.status() }.getOrNull()
    }

    Text(stringResource(R.string.encryption_title), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.encryption_body), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))

    val tooShort = stringResource(R.string.passphrase_too_short, PassphrasePolicy.MIN_LENGTH)
    val tooSimple = stringResource(R.string.passphrase_too_simple)
    val mismatch = stringResource(R.string.passphrase_mismatch)
    val wrongCurrent = stringResource(R.string.passphrase_wrong_current)
    fun problem(new: String, confirm: String): String? = when {
        new != confirm -> mismatch
        else -> when (PassphrasePolicy.check(new.toCharArray())) {
            PassphraseProblem.TOO_SHORT -> tooShort
            PassphraseProblem.TOO_SIMPLE -> tooSimple
            null -> null
        }
    }

    when (val current = step) {
        EncryptionStep.Working -> Text(stringResource(R.string.encryption_working))
        is EncryptionStep.ShowRecoveryCode -> RecoveryCodeCard(current.pending.recoveryCode, current.cloudCopy) {
            step = EncryptionStep.Working
            scope.launch {
                context.appContainer.backupKeys.confirm(current.pending)
                step = EncryptionStep.Idle
                reload++
            }
        }
        is EncryptionStep.ShowNewRecoveryCode -> RecoveryCodeCard(current.pending.recoveryCode, cloudCopy = null) {
            step = EncryptionStep.Working
            scope.launch {
                context.appContainer.backupKeys.confirmRecoveryCode(current.pending)
                step = EncryptionStep.Idle
                reload++
            }
        }
        EncryptionStep.NewCodeForm -> PassphraseForm(
            askCurrent = true,
            askNew = false,
            error = error,
            submitLabel = stringResource(R.string.recovery_new_submit),
            onCancel = { step = EncryptionStep.Idle; error = null },
        ) { old, _, _ ->
            error = null
            step = EncryptionStep.Working
            scope.launch {
                step = try {
                    EncryptionStep.ShowNewRecoveryCode(context.appContainer.backupKeys.prepareNewRecoveryCode(old.toCharArray()))
                } catch (e: WrongSecretException) {
                    error = wrongCurrent
                    EncryptionStep.NewCodeForm
                }
            }
        }
        EncryptionStep.CreateForm -> PassphraseForm(
            askCurrent = false,
            error = error,
            submitLabel = stringResource(R.string.encryption_create),
            onCancel = { step = EncryptionStep.Idle; error = null },
        ) { _, new, confirm ->
            error = problem(new, confirm)
            if (error == null) {
                step = EncryptionStep.Working
                scope.launch {
                    val keys = context.appContainer.backupKeys
                    step = EncryptionStep.ShowRecoveryCode(keys.prepare(new.toCharArray()), keys.cloudCopyAvailable())
                }
            }
        }
        EncryptionStep.ChangeForm -> PassphraseForm(
            askCurrent = true,
            error = error,
            submitLabel = stringResource(R.string.encryption_change_submit),
            onCancel = { step = EncryptionStep.Idle; error = null },
        ) { old, new, confirm ->
            error = problem(new, confirm)
            if (error == null) {
                step = EncryptionStep.Working
                scope.launch {
                    try {
                        context.appContainer.backupKeys.changePassphrase(old.toCharArray(), new.toCharArray())
                        step = EncryptionStep.Idle
                        reload++
                    } catch (e: WrongSecretException) {
                        error = wrongCurrent
                        step = EncryptionStep.ChangeForm
                    }
                }
            }
        }
        EncryptionStep.Idle -> when (val state = status) {
            null -> Text(stringResource(R.string.loading))
            BackupKeyStatus.NotSetUp -> Button(onClick = { step = EncryptionStep.CreateForm }) {
                Text(stringResource(R.string.encryption_set_up))
            }
            is BackupKeyStatus.Ready -> {
                Text(stringResource(R.string.encryption_ready))
                Text(
                    stringResource(if (state.cloudCopy) R.string.encryption_cloud_on else R.string.encryption_cloud_off),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { step = EncryptionStep.ChangeForm }, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.encryption_change))
                }
                OutlinedButton(onClick = { step = EncryptionStep.NewCodeForm }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.recovery_new))
                }
                Text(stringResource(R.string.recovery_new_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PassphraseForm(
    askCurrent: Boolean,
    error: String?,
    askNew: Boolean = true,
    submitLabel: String,
    onCancel: () -> Unit,
    onSubmit: (current: String, new: String, confirm: String) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    if (askNew) Text(stringResource(R.string.passphrase_rule, PassphrasePolicy.MIN_LENGTH), style = MaterialTheme.typography.bodySmall)
    if (askCurrent) SecretField(current, { current = it }, stringResource(R.string.passphrase_current))
    if (askNew) {
        SecretField(new, { new = it }, stringResource(R.string.passphrase_new))
        SecretField(confirm, { confirm = it }, stringResource(R.string.passphrase_confirm))
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    Row(modifier = Modifier.padding(top = 8.dp)) {
        Button(onClick = { onSubmit(current, new, confirm) }) { Text(submitLabel) }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
private fun SecretField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

/** [cloudCopy] is shown after first setup; null for a replacement code, which warns that the old code stops working. */
@Composable
private fun RecoveryCodeCard(code: String, cloudCopy: Boolean?, onDone: () -> Unit) {
    var saved by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.recovery_title), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.recovery_body), style = MaterialTheme.typography.bodySmall)
            Text(
                RecoveryCode.format(code),
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            val context = LocalContext.current
            val label = stringResource(R.string.recovery_clip_label)
            OutlinedButton(onClick = {
                SensitiveClipboard.copy(context, label, RecoveryCode.format(code))
                copied = true
            }) { Text(stringResource(R.string.recovery_copy)) }
            if (copied) Text(stringResource(R.string.recovery_copied), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                when (cloudCopy) {
                    null -> stringResource(R.string.recovery_new_replaces)
                    true -> stringResource(R.string.encryption_cloud_on)
                    false -> stringResource(R.string.encryption_cloud_off)
                },
                style = MaterialTheme.typography.bodySmall,
            )
            // The whole row toggles, not just the small checkbox.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().toggleable(value = saved, role = Role.Checkbox, onValueChange = { saved = it }),
            ) {
                Checkbox(checked = saved, onCheckedChange = null)
                Text(stringResource(R.string.recovery_saved))
            }
            Button(enabled = saved, onClick = onDone) { Text(stringResource(R.string.recovery_done)) }
        }
    }
}
