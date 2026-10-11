package com.ibkrtax.mobile.ui.skins

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ibkrtax.mobile.R
import com.ibkrtax.mobile.appContainer
import com.ibkrtax.mobile.state.AppStates
import com.ibkrtax.mobile.ui.common.UiPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The current skin and a way to switch it, for any skin's Settings (mobile-skins spec). */
class SkinChooser(val current: SkinId, val choose: (SkinId) -> Unit)

val LocalSkinChooser = staticCompositionLocalOf<SkinChooser?> { null }

/**
 * Draws the chosen skin after unlock. On the very first start it asks for a skin; an app that
 * already holds data (updated from a version without skins) keeps Classic and shows a notice once.
 */
@Composable
fun SkinRoot(states: AppStates) {
    val context = LocalContext.current
    var stored by remember { mutableStateOf(UiPreferences.skin(context)) }
    var noticePending by remember { mutableStateOf(UiPreferences.skinNoticePending(context)) }
    var pickerOpen by remember { mutableStateOf(false) }
    val choose: (SkinId) -> Unit = { id ->
        UiPreferences.setSkin(context, id.name)
        stored = id.name
        pickerOpen = false
    }

    if (stored == null) {
        val hasData by produceState<Boolean?>(null) {
            value = withContext(Dispatchers.IO) { runCatching { context.appContainer.importHistory.list().isNotEmpty() }.getOrDefault(false) }
        }
        when (hasData) {
            null -> return
            true -> {
                UiPreferences.setSkin(context, SkinId.CLASSIC.name)
                UiPreferences.setSkinNoticePending(context, true)
                stored = SkinId.CLASSIC.name
                noticePending = true
            }
            false -> {
                MaterialTheme { FirstLaunchPicker(onContinue = choose) }
                return
            }
        }
    }

    val skin = SkinCatalog.resolve(stored)
    CompositionLocalProvider(LocalSkinChooser provides SkinChooser(skin.id, choose)) {
        skin.App(states)
        MaterialTheme {
            if (noticePending) {
                NewSkinsNotice(
                    onShow = { pickerOpen = true },
                    onDismiss = {},
                    onSeen = {
                        UiPreferences.setSkinNoticePending(context, false)
                        noticePending = false
                    },
                )
            }
            if (pickerOpen) SkinPickerDialog(skin.id, onChoose = choose, onDismiss = { pickerOpen = false })
        }
    }
}

/** The catalog with previews; [onSelect] fires on tap. */
@Composable
fun SkinList(selected: SkinId, onSelect: (SkinId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SkinCatalog.skins.forEach { skin ->
            val isSelected = skin.id == selected
            Card(
                modifier = Modifier.fillMaxWidth().selectable(isSelected, role = Role.RadioButton) { onSelect(skin.id) },
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(width = 64.dp, height = 104.dp)) { skin.Preview(Modifier.fillMaxSize()) }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(skin.nameRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(skin.descriptionRes), style = MaterialTheme.typography.bodySmall)
                    }
                    RadioButton(selected = isSelected, onClick = null)
                }
            }
        }
    }
}

@Composable
private fun FirstLaunchPicker(onContinue: (SkinId) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(SkinCatalog.firstLaunch.id) }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.skin_picker_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.skin_picker_body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
        SkinList(selected) { selected = it }
        Text(stringResource(R.string.skin_picker_later), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        Button(onClick = { onContinue(selected) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text(stringResource(R.string.skin_picker_continue))
        }
    }
}

@Composable
private fun SkinPickerDialog(current: SkinId, onChoose: (SkinId) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.skin_settings_title)) },
        text = { Column(modifier = Modifier.verticalScroll(rememberScrollState())) { SkinList(selected) { selected = it } } },
        confirmButton = { TextButton(onClick = { onChoose(selected) }) { Text(stringResource(R.string.skin_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        shape = RoundedCornerShape(24.dp),
    )
}

@Composable
private fun NewSkinsNotice(onShow: () -> Unit, onDismiss: () -> Unit, onSeen: () -> Unit) {
    AlertDialog(
        onDismissRequest = { onSeen(); onDismiss() },
        title = { Text(stringResource(R.string.skin_notice_title)) },
        text = { Text(stringResource(R.string.skin_notice_body)) },
        confirmButton = { TextButton(onClick = { onSeen(); onShow() }) { Text(stringResource(R.string.skin_notice_show)) } },
        dismissButton = { TextButton(onClick = { onSeen(); onDismiss() }) { Text(stringResource(R.string.skin_notice_later)) } },
    )
}

/** The "Skin" entry every skin puts into its Settings. */
@Composable
fun SkinSettingsSection() {
    val chooser = LocalSkinChooser.current ?: return
    var open by remember { mutableStateOf(false) }
    val current = SkinCatalog.resolve(chooser.current.name)
    Column {
        Text(stringResource(R.string.skin_settings_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.skin_settings_current, stringResource(current.nameRes)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        OutlinedButton(onClick = { open = true }) { Text(stringResource(R.string.skin_settings_change)) }
    }
    if (open) SkinPickerDialog(chooser.current, onChoose = { open = false; chooser.choose(it) }, onDismiss = { open = false })
}
