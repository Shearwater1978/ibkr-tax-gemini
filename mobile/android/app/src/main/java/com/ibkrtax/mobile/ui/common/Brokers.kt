package com.ibkrtax.mobile.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibkrtax.mobile.R

/** Supported brokers (mobile-broker-choice spec). Stored by name; never rename an entry. */
enum class BrokerId(val nameRes: Int, val formatRes: Int) {
    INTERACTIVE_BROKERS(R.string.broker_ibkr_name, R.string.broker_ibkr_format),
    ;

    companion object {
        /** The broker for a stored name; null when none or an unknown one is stored. */
        fun resolve(stored: String?): BrokerId? = entries.firstOrNull { it.name == stored }
    }
}

/** The chosen broker and a way to change it, for import and every skin's Settings. */
class BrokerChooser(val current: BrokerId?, val choose: (BrokerId) -> Unit)

val LocalBrokerChooser = staticCompositionLocalOf<BrokerChooser?> { null }

@Composable
fun BrokerList(selected: BrokerId?, onSelect: (BrokerId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BrokerId.entries.forEach { broker ->
            val isSelected = broker == selected
            Card(
                modifier = Modifier.fillMaxWidth().selectable(isSelected, role = Role.RadioButton) { onSelect(broker) },
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(broker.nameRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(broker.formatRes), style = MaterialTheme.typography.bodySmall)
                    }
                    RadioButton(selected = isSelected, onClick = null)
                }
            }
        }
        Text(stringResource(R.string.broker_more_later), style = MaterialTheme.typography.bodySmall)
    }
}

/** The first-launch question after the skin; "Choose later" leaves the broker unset. */
@Composable
fun FirstLaunchBrokerStep(onChoose: (BrokerId) -> Unit, onLater: () -> Unit) {
    var selected by rememberSaveable { mutableStateOf<BrokerId?>(null) }
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.broker_question), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.broker_question_body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
        BrokerList(selected) { selected = it }
        Button(onClick = { selected?.let(onChoose) }, enabled = selected != null, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text(stringResource(R.string.skin_picker_continue))
        }
        TextButton(onClick = onLater, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.broker_later)) }
    }
}

/** Shown by the import screen while no broker is chosen (mobile-broker-choice "Ask for the broker before importing"). */
@Composable
fun BrokerGate() {
    val chooser = LocalBrokerChooser.current ?: return
    var selected by remember { mutableStateOf<BrokerId?>(null) }
    Column {
        Text(stringResource(R.string.broker_question), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.broker_question_body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
        BrokerList(selected) { selected = it }
        Button(onClick = { selected?.let(chooser.choose) }, enabled = selected != null, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(R.string.skin_picker_continue))
        }
    }
}

/** The "Broker" entry every skin's Settings shows. */
@Composable
fun BrokerSettingsSection() {
    val chooser = LocalBrokerChooser.current ?: return
    var open by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.broker_settings_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.skin_settings_current, chooser.current?.let { stringResource(it.nameRes) } ?: stringResource(R.string.broker_not_chosen)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.broker_change)) }
    }
    if (open) {
        var selected by remember { mutableStateOf(chooser.current) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.broker_settings_title)) },
            text = { BrokerList(selected) { selected = it } },
            confirmButton = { TextButton(enabled = selected != null, onClick = { selected?.let(chooser.choose); open = false }) { Text(stringResource(R.string.broker_use)) } },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
