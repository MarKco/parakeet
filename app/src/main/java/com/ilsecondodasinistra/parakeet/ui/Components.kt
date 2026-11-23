package com.ilsecondodasinistra.parakeet.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.ilsecondodasinistra.parakeet.R
import com.ilsecondodasinistra.parakeet.domain.Task
import com.ilsecondodasinistra.parakeet.ui.theme.LcdFont
import java.time.LocalTime

/** Digital-7 LCD style text, used for the key times only (durations stay in the readable UI font). */
@Composable
fun LcdText(text: String, size: TextUnit, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = TextStyle(fontFamily = LcdFont, fontSize = size),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit, title: String? = null) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title ?: stringResource(R.string.exit_time_title)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private val QuickDurations = listOf(5, 10, 15, 20, 30)

/**
 * Add (task == null) or edit an activity. When adding, [onSaveAndContinue] (if given) adds a second button that saves
 * the activity, empties the name and keeps the dialog open, to add several in a row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDialog(
    task: Task?,
    onSave: (String, Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    onSaveAndContinue: ((String, Int) -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(task?.name.orEmpty()) }
    var minutes by rememberSaveable { mutableStateOf(task?.minutes?.toString() ?: "10") }
    val minutesValue = minutes.toIntOrNull()
    val valid = name.isNotBlank() && minutesValue != null && minutesValue in 1..600
    // What was just added, shown in the dialog since the confirmation snackbar is hidden behind it
    var lastAdded by rememberSaveable { mutableStateOf<String?>(null) }
    val nameFocus = remember { FocusRequester() }
    var refocus by remember { mutableIntStateOf(0) }
    LaunchedEffect(refocus) { if (refocus > 0) nameFocus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (task == null) R.string.add_task else R.string.edit_task)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.task_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                )
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.task_minutes)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickDurations.forEach { d ->
                        FilterChip(selected = minutes == d.toString(), onClick = { minutes = d.toString() }, label = { Text("$d") }, modifier = Modifier.heightIn(min = 40.dp))
                    }
                }
                lastAdded?.let {
                    Text(stringResource(R.string.task_added, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Row {
                if (task == null && onSaveAndContinue != null) {
                    // The two ways of adding stand out (tonal, filled); Cancel stays a plain neutral text button
                    FilledTonalButton(
                        enabled = valid,
                        onClick = {
                            onSaveAndContinue(name.trim(), minutesValue!!)
                            lastAdded = name.trim()
                            name = ""
                            refocus++
                        },
                    ) { Text(stringResource(R.string.task_add_continue)) }
                    Spacer(Modifier.width(8.dp))
                }
                Button(enabled = valid, onClick = { onSave(name.trim(), minutesValue!!) }) {
                    Text(stringResource(R.string.save))
                }
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

/** New in 5.0: offers the in-app alarm to people updating from earlier versions. */
@Composable
fun AlarmIntroDialog(onAnswer: (useParakeetAlarm: Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        title = { Text(stringResource(R.string.whatsnew_title)) },
        text = { Text(stringResource(R.string.whatsnew_body)) },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text(stringResource(R.string.whatsnew_try)) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.whatsnew_keep)) } },
    )
}

/** Selectable card with a radio button: the whole card is the tap target. */
@Composable
fun ChoiceCard(selected: Boolean, title: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .heightIn(min = 72.dp)
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
