package com.ilsecondodasinistra.parakeet.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilsecondodasinistra.parakeet.R
import com.ilsecondodasinistra.parakeet.sync.DeviceInfo
import com.ilsecondodasinistra.parakeet.sync.SyncResult
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private fun whenText(millis: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

/** The folder as a person reads it: the last part of its path. */
private fun folderLabel(uri: String): String = Uri.decode(uri).substringAfterLast(':').substringAfterLast('/').ifEmpty { Uri.decode(uri) }

@Composable
internal fun SyncTab(viewModel: SyncViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val pairing by viewModel.pairing.collectAsStateWithLifecycle()
    var askName by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var pendingName by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.connect(uri, pendingName)
        pendingName = null
    }

    SettingsCard {
        Row(
            Modifier
                .toggleable(
                    value = s.enabled, role = Role.Switch,
                    onValueChange = { on -> if (on) askName = true else viewModel.disable() },
                )
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sync_enable_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.sync_enable_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = s.enabled, onCheckedChange = null)
        }
    }

    if (s.enabled) {
        SettingsCard {
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { pendingName = null; picker.launch(null) }
                        .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sync_folder_title), style = MaterialTheme.typography.titleMedium)
                        Text(s.folderUri?.let(::folderLabel).orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.sync_change_folder), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { rename = true }
                        .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sync_name_title), style = MaterialTheme.typography.titleMedium)
                        Text(s.deviceName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        SettingsCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (line in lastLines(s.last)) {
                    Text(
                        line.text, style = MaterialTheme.typography.bodyMedium,
                        color = if (line.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = viewModel::syncNow, enabled = !running,
                    modifier = Modifier.padding(top = 4.dp).heightIn(min = 48.dp),
                ) { Text(stringResource(if (running) R.string.sync_running else R.string.sync_now)) }
            }
        }
    }

    // When a round runs, and that it does not run with the app closed
    Text(
        stringResource(R.string.sync_when_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    if (askName) {
        NameDialog(
            initial = s.deviceName,
            onConfirm = { name -> askName = false; pendingName = name; picker.launch(null) },
            onDismiss = { askName = false },
        )
    }
    if (rename) {
        NameDialog(initial = s.deviceName, onConfirm = { viewModel.rename(it); rename = false }, onDismiss = { rename = false })
    }
    pairing?.let { devices ->
        PairingDialog(devices, onThis = viewModel::adopt, onNew = viewModel::startNew, onCancel = viewModel::disable)
    }
}

private class Line(val text: String, val error: Boolean = false)

@Composable
private fun lastLines(last: SyncResult?): List<Line> {
    if (last == null) return listOf(Line(stringResource(R.string.sync_never)))
    return buildList {
        if (!last.ok) {
            add(Line(stringResource(R.string.sync_error, last.error.orEmpty()), error = true))
        } else {
            add(Line(stringResource(R.string.sync_last_ok, whenText(last.at))))
            add(Line(if (last.changed > 0) stringResource(R.string.sync_changes, last.changed) else stringResource(R.string.sync_no_changes)))
            if (last.devices.isNotEmpty()) add(Line(stringResource(R.string.sync_devices, last.devices.joinToString(", "))))
            if (last.resolved > 0) add(Line(stringResource(R.string.sync_resolved, last.resolved)))
            if (last.unreadable > 0) add(Line(stringResource(R.string.sync_unreadable, last.unreadable), error = true))
        }
    }
}

@Composable
private fun NameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_name_title)) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                supportingText = { Text(stringResource(R.string.sync_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim().ifEmpty { initial }) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** First connection to a folder that already has other devices: which one is this phone, if any? */
@Composable
private fun PairingDialog(devices: List<DeviceInfo>, onThis: (DeviceInfo) -> Unit, onNew: () -> Unit, onCancel: () -> Unit) {
    var confirm by remember { mutableStateOf<DeviceInfo?>(null) }
    val chosen = confirm
    if (chosen != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.sync_pairing_confirm_title, chosen.deviceName.ifEmpty { chosen.deviceId.take(6) })) },
            text = { Text(stringResource(R.string.sync_pairing_confirm_desc)) },
            confirmButton = { TextButton(onClick = { onThis(chosen) }) { Text(stringResource(R.string.sync_pairing_yes)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.sync_pairing_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sync_pairing_desc), style = MaterialTheme.typography.bodyMedium)
                devices.forEach { d ->
                    OutlinedButton(onClick = { confirm = d }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                            Text(d.deviceName.ifEmpty { d.deviceId.take(6) }, style = MaterialTheme.typography.titleSmall)
                            if (d.writtenAt > 0) Text(stringResource(R.string.sync_pairing_last, whenText(d.writtenAt)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                FilledTonalButton(onClick = onNew, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.sync_pairing_new))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun BackupTab(viewModel: SyncViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val scope = rememberCoroutineScope()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            val ok = viewModel.exportTo(context.contentResolver, uri)
            Toast.makeText(context, if (ok) R.string.backup_saved else R.string.backup_failed, Toast.LENGTH_SHORT).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = viewModel.restoreFrom(context.contentResolver, uri)
            val text = r.fold(
                onSuccess = { resources.getString(R.string.backup_restored, it) },
                onFailure = {
                    resources.getString(
                        when (it.message) { "format" -> R.string.backup_bad_format; "newer" -> R.string.backup_newer; else -> R.string.backup_unreadable },
                    )
                },
            )
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }

    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.backup_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(
                onClick = { exporter.launch("parakeet-${java.time.LocalDate.now()}.parakeet") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.backup_export)) }
            OutlinedButton(
                onClick = { importer.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.backup_restore)) }
        }
    }
}
