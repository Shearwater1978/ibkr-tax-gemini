package com.ibkrtax.mobile.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.appContainer
import com.ibkrtax.mobile.manual.ManualDeleteResult
import com.ibkrtax.mobile.manual.ManualProblem
import com.ibkrtax.mobile.manual.ManualSaveResult
import com.ibkrtax.mobile.manual.ManualType
import com.ibkrtax.mobile.ui.Formats
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** What the manual-entry form edits: a new entry, or an existing one by id (mobile-manual-operations). */
sealed interface ManualTarget {
    data object New : ManualTarget

    data class Edit(val entryId: String) : ManualTarget
}

/** Shows the form while [target] is set; every skin's activity screen hosts one. */
@Composable
fun ManualEntryHost(target: MutableState<ManualTarget?>) {
    target.value?.let { current -> ManualEntryDialog(current, onDone = { target.value = null }) }
}

/** Turns what the user typed into a plain decimal string: a comma works as the decimal separator. */
internal fun plainDecimal(text: String): String {
    val trimmed = text.trim().replace(" ", "").replace(" ", "")
    return if ('.' !in trimmed && trimmed.count { it == ',' } == 1) trimmed.replace(',', '.') else trimmed
}

private val UPPERCASE_FIELDS = setOf("ticker", "currency", "newTicker", "fromTicker")
private val TEXT_FIELDS = UPPERCASE_FIELDS + "date"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryDialog(target: ManualTarget, onDone: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(ManualType.BUY) }
    val values = remember { mutableStateMapOf("date" to LocalDate.now().toString(), "currency" to "USD", "fee" to "0", "tax" to "0") }
    var problems by remember { mutableStateOf<List<ManualProblem>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var duplicate by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val entryId = (target as? ManualTarget.Edit)?.entryId

    LaunchedEffect(entryId) {
        if (entryId == null) return@LaunchedEffect
        val entry = withContext(Dispatchers.IO) { container.manualEntries.get(entryId) } ?: return@LaunchedEffect onDone()
        type = ManualType.valueOf(entry.type)
        val fields = JSONObject(entry.fields)
        values.clear()
        fields.keys().forEach { values[it] = fields.getString(it) }
    }

    val oversoldText = stringResource(R.string.manual_oversold, "%1\$s", "%2\$s", "%3\$s")
    val nothingHeldText = stringResource(R.string.manual_nothing_held, "%1\$s", "%2\$s")
    val duplicateText = stringResource(R.string.manual_duplicate_body, "%1\$s", "%2\$s", "%3\$s")
    val typeNames = ManualType.entries.associateWith { stringResource(it.labelRes()) }

    fun changed() {
        container.states.manualChanged()
        onDone()
    }

    fun save(confirmDuplicate: Boolean) {
        busy = true
        message = null
        val fields = type.fields.associateWith { name ->
            val raw = values[name].orEmpty()
            if (name in TEXT_FIELDS) raw.trim() else plainDecimal(raw)
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) { container.manualOperations.save(type, fields, entryId, confirmDuplicate) }
            busy = false
            problems = (result as? ManualSaveResult.Problems)?.problems.orEmpty()
            when (result) {
                is ManualSaveResult.Saved -> changed()
                is ManualSaveResult.Problems -> Unit
                is ManualSaveResult.NothingHeld -> message = nothingHeldText.format(result.ticker, displayDate(result.date))
                is ManualSaveResult.Oversold -> message = oversoldText.format(
                    Formats.quantity(result.oversell.held), result.oversell.ticker, displayDate(result.oversell.date),
                )
                is ManualSaveResult.PossibleDuplicate -> result.matches.first().second.let { existing ->
                    duplicate = duplicateText.format(displayDate(existing.date), typeNames[type].orEmpty(), existing.ticker)
                }
            }
        }
    }

    fun delete() {
        val id = entryId ?: return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { container.manualOperations.delete(id) }
            busy = false
            when (result) {
                ManualDeleteResult.Deleted, ManualDeleteResult.NotFound -> changed()
                is ManualDeleteResult.Oversold -> message = oversoldText.format(
                    Formats.quantity(result.oversell.held), result.oversell.ticker, displayDate(result.oversell.date),
                )
            }
        }
    }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(if (entryId == null) R.string.manual_add else R.string.manual_edit), style = MaterialTheme.typography.titleLarge)
                var open by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                    OutlinedTextField(
                        value = typeNames.getValue(type),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.manual_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        ManualType.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(typeNames.getValue(option)) }, onClick = {
                                type = option
                                problems = emptyList()
                                open = false
                            })
                        }
                    }
                }
                type.fields.filter { it != "heldBefore" }.forEach { name ->
                    val problem = problems.firstOrNull { it.field == name }
                    OutlinedTextField(
                        value = values[name].orEmpty(),
                        onValueChange = { values[name] = if (name in UPPERCASE_FIELDS) it.uppercase() else it },
                        label = { Text(stringResource(fieldLabel(type, name))) },
                        isError = problem != null,
                        supportingText = if (problem != null) {
                            { Text(stringResource(problemText(type, problem, sameTicker = values["newTicker"] == values["ticker"]))) }
                        } else {
                            null
                        },
                        singleLine = true,
                        keyboardOptions = when (name) {
                            in UPPERCASE_FIELDS -> KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false)
                            "date" -> KeyboardOptions(keyboardType = KeyboardType.Number)
                            else -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(stringResource(R.string.manual_hint), style = MaterialTheme.typography.bodySmall)
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { save(confirmDuplicate = false) }, enabled = !busy) { Text(stringResource(R.string.manual_save)) }
                    OutlinedButton(onClick = onDone) { Text(stringResource(R.string.manual_cancel)) }
                    if (entryId != null) {
                        TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                            Text(stringResource(R.string.manual_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    duplicate?.let { text ->
        AlertDialog(
            onDismissRequest = { duplicate = null },
            title = { Text(stringResource(R.string.manual_duplicate_title)) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    duplicate = null
                    save(confirmDuplicate = true)
                }) { Text(stringResource(R.string.manual_save_anyway)) }
            },
            dismissButton = { TextButton(onClick = { duplicate = null }) { Text(stringResource(R.string.manual_cancel)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.manual_delete_title)) },
            text = { Text(stringResource(R.string.manual_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    delete()
                }) { Text(stringResource(R.string.manual_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.manual_cancel)) } },
        )
    }
}

private fun ManualType.labelRes(): Int = when (this) {
    ManualType.BUY -> R.string.manual_type_buy
    ManualType.SELL -> R.string.manual_type_sell
    ManualType.TRANSFER_IN -> R.string.manual_type_transfer_in
    ManualType.TRANSFER_OUT -> R.string.manual_type_transfer_out
    ManualType.DIVIDEND -> R.string.manual_type_dividend
    ManualType.TAX -> R.string.manual_type_tax
    ManualType.SPLIT -> R.string.manual_type_split
    ManualType.STOCK_DIV -> R.string.manual_type_stock_div
    ManualType.MERGER -> R.string.manual_type_merger
    ManualType.SPINOFF -> R.string.manual_type_spinoff
}

private fun fieldLabel(type: ManualType, name: String): Int = when (name) {
    "date" -> R.string.manual_field_date
    "ticker" -> R.string.manual_field_ticker
    "currency" -> R.string.manual_field_currency
    "quantity" -> when (type) {
        ManualType.MERGER -> R.string.manual_field_old_quantity
        ManualType.STOCK_DIV, ManualType.SPINOFF -> R.string.manual_field_received
        else -> R.string.manual_field_quantity
    }
    "price" -> if (type == ManualType.TRANSFER_IN) R.string.manual_field_cost else R.string.manual_field_price
    "fee" -> R.string.manual_field_fee
    "amount" -> if (type == ManualType.TAX) R.string.manual_field_tax_amount else R.string.manual_field_gross
    "tax" -> R.string.manual_field_tax
    "newShares" -> R.string.manual_field_new_shares
    "oldShares" -> R.string.manual_field_old_shares
    "newTicker" -> R.string.manual_field_new_ticker
    "newQuantity" -> R.string.manual_field_new_quantity
    else -> R.string.manual_field_from_ticker
}

private fun problemText(type: ManualType, problem: ManualProblem, sameTicker: Boolean): Int = when (problem) {
    is ManualProblem.Missing -> R.string.manual_problem_missing
    is ManualProblem.FutureDate -> R.string.manual_problem_future
    is ManualProblem.Invalid -> if (problem.field == "newTicker" && sameTicker) R.string.manual_problem_same_ticker else R.string.manual_problem_invalid
    is ManualProblem.OutOfRange -> when {
        type == ManualType.DIVIDEND && problem.field == "tax" -> R.string.manual_problem_tax
        problem.field == "fee" -> R.string.manual_problem_negative
        type == ManualType.TAX -> R.string.manual_problem_zero
        problem.field == "newShares" || problem.field == "oldShares" -> R.string.manual_problem_whole
        else -> R.string.manual_problem_positive
    }
}
