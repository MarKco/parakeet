package com.ilsecondodasinistra.parakeet.ui.routines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilsecondodasinistra.parakeet.R
import com.ilsecondodasinistra.parakeet.domain.AlarmPlanner
import com.ilsecondodasinistra.parakeet.domain.DayConflict
import com.ilsecondodasinistra.parakeet.domain.Routine
import com.ilsecondodasinistra.parakeet.domain.RoutineColors
import com.ilsecondodasinistra.parakeet.domain.RoutineRules
import com.ilsecondodasinistra.parakeet.domain.Task
import com.ilsecondodasinistra.parakeet.domain.TaskLabels
import com.ilsecondodasinistra.parakeet.ui.LcdText
import com.ilsecondodasinistra.parakeet.ui.TaskDialog
import com.ilsecondodasinistra.parakeet.ui.TimePickerDialog
import com.ilsecondodasinistra.parakeet.ui.formatDuration
import com.ilsecondodasinistra.parakeet.ui.lcd
import com.ilsecondodasinistra.parakeet.ui.main.MainUiState
import com.ilsecondodasinistra.parakeet.ui.main.BufferHint
import com.ilsecondodasinistra.parakeet.ui.main.MainViewModel
import com.ilsecondodasinistra.parakeet.ui.main.MainViewModel.TaskEdit
import com.ilsecondodasinistra.parakeet.ui.main.WeekdayChips
import com.ilsecondodasinistra.parakeet.ui.main.fullList
import com.ilsecondodasinistra.parakeet.ui.main.fullName
import com.ilsecondodasinistra.parakeet.ui.main.letter
import com.ilsecondodasinistra.parakeet.ui.main.shortList
import com.ilsecondodasinistra.parakeet.ui.main.uiColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

// ---- list --------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(state: MainUiState, viewModel: MainViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var conflictId by rememberSaveable { mutableStateOf<Long?>(null) }
    state.routines.firstOrNull { it.id == conflictId }?.let { r ->
        val conflicts = RoutineRules.conflicts(r.id, r.days, state.routines)
        if (conflicts.isNotEmpty()) {
            RoutineConflictDialog(
                name = r.name,
                conflicts = conflicts,
                onConfirm = { conflictId = null; viewModel.setRoutineEnabled(r, true, alsoDisable = conflicts.map { it.routine }) },
                onDismiss = { conflictId = null },
            )
        }
    }
    state.routines.firstOrNull { it.id == deleteId }?.let { r ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            text = { Text(stringResource(R.string.routine_delete_confirm, r.name)) },
            confirmButton = {
                TextButton(onClick = { deleteId = null; viewModel.deleteRoutine(r) }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routines_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(0L) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.routine_new)) },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 640.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.routines.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.routines_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                items(state.routines, key = { it.id }) { r ->
                    RoutineCard(
                        routine = r,
                        totalMinutes = state.tasks.filter { it.id in r.taskIds }.sumOf { it.minutes },
                        onClick = { onEdit(r.id) },
                        onToggle = { on ->
                            // Switching on over days another routine that is on covers: explain instead of doing it
                            if (on && RoutineRules.conflicts(r.id, r.days, state.routines).isNotEmpty()) conflictId = r.id
                            else viewModel.setRoutineEnabled(r, on)
                        },
                        onDuplicate = { viewModel.duplicateRoutine(r) { id -> onEdit(id) } },
                        onDelete = { deleteId = r.id },
                    )
                }
            }
        }
    }
}

/**
 * A routine in the list. Long names get the whole width (two lines) with the switch beside them; the actions sit
 * on their own row below, with labels, so nothing is squeezed.
 */
@Composable
private fun RoutineCard(
    routine: Routine,
    totalMinutes: Int,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val wake = routine.exitTime.minusMinutes(totalMinutes.toLong())
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Colour spine, as tall as the card
            Box(
                Modifier.padding(start = 12.dp, top = 16.dp, bottom = 16.dp).width(6.dp).fillMaxHeight()
                    .clip(CircleShape).background(routine.uiColor()),
            )
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        routine.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(top = 4.dp, end = 12.dp),
                    )
                    Switch(
                        checked = routine.enabled,
                        onCheckedChange = onToggle,
                        modifier = Modifier.padding(end = 8.dp).semantics { contentDescription = routine.name },
                    )
                }
                // What the two times are: the alarm, then the time you leave
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                    Column {
                        Text(stringResource(R.string.routine_card_wake), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LcdText(wake.lcd(), 30.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        "→",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
                    )
                    Column {
                        Text(stringResource(R.string.routine_card_exit), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LcdText(routine.exitTime.lcd(), 30.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Week at a glance: its days in a circle of its colour; the other days almost disappear
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        val on = d in routine.days
                        val fill = when {
                            !on -> Color.Transparent
                            routine.enabled -> routine.uiColor()
                            else -> routine.uiColor().copy(alpha = 0.3f)
                        }
                        Box(Modifier.size(28.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
                            Text(
                                d.letter(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (on) FontWeight.Black else FontWeight.Normal,
                                color = when {
                                    on && routine.enabled -> Color(0xFF1A2205)
                                    on -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                },
                            )
                        }
                    }
                }
                if (routine.days.isEmpty()) {
                    Text(
                        stringResource(R.string.routine_days_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDuplicate, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.routine_card_duplicate))
                    }
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

// ---- editor -------------------------------------------------------------------------------------

/**
 * Create (routineId 0) or edit a routine. [forDate]: created from a day of the ribbon, used on that date.
 * Weekdays are exclusive: choosing a day of another routine moves it here (its colour dot shows the owner).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    routineId: Long,
    forDate: LocalDate?,
    state: MainUiState,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onDuplicated: (Long) -> Unit,
) {
    val existing = state.routines.firstOrNull { it.id == routineId }
    val template = existing ?: state.routines.firstOrNull()
    val weekdaysName = stringResource(R.string.routine_name_weekdays)
    val weekendName = stringResource(R.string.routine_name_weekend)
    val fallbackName = existing?.name ?: if (state.routines.isEmpty()) stringResource(R.string.routine_default_name)
    else stringResource(R.string.routine_name_n, state.routines.size + 1)

    var name by rememberSaveable { mutableStateOf(existing?.name ?: fallbackName) }
    // The generic name of a migrated/first routine still follows the days ("Feriali", "Weekend")
    val genericName = stringResource(R.string.routine_default_name)
    var nameEdited by rememberSaveable { mutableStateOf(existing != null && existing.name != genericName) }
    var color by rememberSaveable { mutableIntStateOf(existing?.color ?: RoutineColors.first()) }
    var days by rememberSaveable { mutableStateOf(existing?.days?.map { it.value } ?: emptyList()) }
    var exit by rememberSaveable { mutableStateOf((existing?.exitTime ?: template?.exitTime ?: LocalTime.of(8, 0)).toSecondOfDay()) }
    var taskIds by rememberSaveable { mutableStateOf((existing?.taskIds ?: template?.taskIds ?: emptySet()).toList()) }
    var enabled by rememberSaveable { mutableStateOf(existing?.enabled ?: true) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    var addTask by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Activity edits made here, applied on save: "id \t scope \t minutes \t name" (scope H = only here, E = everywhere)
    var editsRaw by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingRaw by rememberSaveable { mutableStateOf<String?>(null) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    // Routines the user agreed to switch off (they cover days this one needs); applied on save. "toggle" or "day:N".
    var disabledOthers by rememberSaveable { mutableStateOf<List<Long>>(emptyList()) }
    var conflictAsk by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { if (existing == null) color = viewModel.nextRoutineColor() }

    val daySet = days.map { DayOfWeek.of(it) }.toSet()
    val exitTime = LocalTime.ofSecondOfDay(exit.toLong())
    val edits = editsRaw.mapNotNull(::parseEdit)
    val editById = edits.associateBy { it.taskId }
    fun Task.withEdit() = editById[id]?.let { copy(name = it.name, minutes = it.minutes) } ?: this
    val routineTasks = state.tasks.filter { it.id in taskIds }.map { it.withEdit() }
    val total = routineTasks.sumOf { it.minutes }
    val labels = remember(state.tasks, state.routines) { TaskLabels.of(state.tasks, state.routines) }
    // Other routines as they will be once saved; only those that are on can clash, and only with a routine that is on
    val others = state.routines.filter { it.id != routineId }.map { if (it.id in disabledOthers) it.copy(enabled = false) else it }
    val owners: Map<DayOfWeek, Routine> = if (enabled) others.filter { it.enabled }.flatMap { r -> r.days.map { it to r } }.toMap() else emptyMap()

    fun applyDay(d: DayOfWeek) {
        days = if (d.value in days) days - d.value else days + d.value
        if (!nameEdited) {
            name = AlarmPlanner.suggestedName(days.map { DayOfWeek.of(it) }.toSet(), weekdaysName, weekendName, fallbackName)
        }
    }

    /** A day another routine that is on covers is not taken silently: the user decides whether to switch that one off. */
    fun toggleDay(d: DayOfWeek) {
        if (d.value !in days && owners[d] != null) conflictAsk = "day:${d.value}" else applyDay(d)
    }

    fun buildRoutine() = Routine(
        id = existing?.id ?: 0L,
        name = name.trim().ifEmpty { fallbackName },
        color = color,
        exitTime = exitTime,
        taskIds = taskIds.toSet(),
        days = daySet,
        enabled = enabled,
        position = existing?.position ?: 0,
    )

    fun save() {
        viewModel.saveRoutine(buildRoutine(), forDate, edits, disabledOthers)
        onBack()
    }

    /** Saves what is on screen, then opens a copy of it (same exit time and activities, no days). */
    fun duplicate() {
        viewModel.saveRoutine(buildRoutine(), null, edits, disabledOthers) { saved -> viewModel.duplicateRoutine(saved, onDuplicated) }
    }

    fun putEdit(task: Task, newName: String, newMinutes: Int, onlyHere: Boolean) {
        editsRaw = editsRaw.filterNot { parseEdit(it)?.taskId == task.id } +
            if (newName == task.name && newMinutes == task.minutes) emptyList() else listOf(encodeEdit(TaskEdit(task.id, newName, newMinutes, onlyHere)))
    }

    /** An activity of other routines too asks "only here or everywhere"; otherwise the question has no point. */
    fun requestEdit(task: Task, newName: String, newMinutes: Int) {
        val earlier = editById[task.id]
        when {
            newName == task.name && newMinutes == task.minutes -> putEdit(task, newName, newMinutes, false)
            earlier != null -> putEdit(task, newName, newMinutes, earlier.onlyHere)
            state.routines.any { it.id != routineId && task.id in it.taskIds } ->
                pendingRaw = encodeEdit(TaskEdit(task.id, newName, newMinutes, false))
            else -> putEdit(task, newName, newMinutes, false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) stringResource(R.string.routine_new) else existing.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back)) }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = ::duplicate) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.routine_duplicate))
                        }
                    }
                    IconButton(onClick = ::save) { Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 640.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; nameEdited = true },
                        label = { Text(stringResource(R.string.routine_name)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Label(R.string.routine_color)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RoutineColors.forEach { c ->
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .selectable(selected = c == color, role = Role.RadioButton, onClick = { color = c }),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (c == color) Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF1A2205))
                            }
                        }
                    }
                }
                item {
                    Label(R.string.routine_days)
                    WeekdayChips(
                        selected = daySet,
                        color = Color(color),
                        onToggle = ::toggleDay,
                        owners = owners.mapValues { it.value.uiColor() },
                    )
                    Text(
                        when {
                            daySet.isEmpty() -> stringResource(R.string.routine_days_none)
                            else -> stringResource(R.string.onb_when_weekly, daySet.shortList())
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                }
                if (existing != null) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                                .toggleable(value = enabled, role = Role.Switch, onValueChange = { on ->
                                    if (on && RoutineRules.conflicts(routineId, daySet, others).isNotEmpty()) conflictAsk = "toggle" else enabled = on
                                })
                                .heightIn(min = 56.dp).padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.routine_enabled), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Switch(checked = enabled, onCheckedChange = null)
                        }
                    }
                }
                item {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { pickTime = true }
                            .heightIn(min = 64.dp).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.exit_at), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        LcdText(exitTime.lcd(), 34.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        stringResource(R.string.routine_summary, exitTime.minusMinutes(total.toLong()).lcd(), exitTime.lcd()) +
                            " · " + formatDuration(total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 44.dp),
                    )
                }
                item { Label(R.string.routine_activities) }
                if (routineTasks.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.routine_tasks_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        )
                    }
                }
                items(routineTasks, key = { it.id }) { task ->
                    RoutineTaskRow(
                        task = task,
                        color = Color(color),
                        onEdit = { editingId = task.id },
                        onRemove = {
                            taskIds = taskIds - task.id
                            editsRaw = editsRaw.filterNot { parseEdit(it)?.taskId == task.id }
                        },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { showPicker = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_task))
                    }
                }
                if (state.showBufferHint) {
                    item(key = "bufferHint") {
                        BufferHint(
                            onAdd = { viewModel.addCatalogBuffer { id -> taskIds = taskIds + id } },
                            onDismiss = viewModel::dismissBufferHint,
                        )
                    }
                }
                item {
                    androidx.compose.material3.Button(
                        onClick = ::save,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp).navigationBarsPadding(),
                        shape = MaterialTheme.shapes.large,
                    ) { Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium) }
                }
                if (existing != null) {
                    item {
                        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.routine_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (pickTime) {
        TimePickerDialog(initial = exitTime, onConfirm = { exit = it.toSecondOfDay(); pickTime = false }, onDismiss = { pickTime = false })
    }
    conflictAsk?.let { ask ->
        if (ask == "toggle") {
            val conflicts = RoutineRules.conflicts(routineId, daySet, others)
            if (conflicts.isNotEmpty()) {
                RoutineConflictDialog(
                    name = name.trim().ifEmpty { fallbackName },
                    conflicts = conflicts,
                    onConfirm = { disabledOthers = disabledOthers + conflicts.map { it.routine.id }; enabled = true; conflictAsk = null },
                    onDismiss = { conflictAsk = null },
                )
            }
        } else {
            val day = DayOfWeek.of(ask.removePrefix("day:").toInt())
            owners[day]?.let { owner ->
                ConflictDialog(
                    body = stringResource(R.string.routine_conflict_day_body, day.fullName().replaceFirstChar { it.uppercase() }, owner.name),
                    confirmLabel = stringResource(R.string.routine_conflict_turn_off, day.fullName()),
                    onConfirm = { disabledOthers = disabledOthers + owner.id; applyDay(day); conflictAsk = null },
                    onDismiss = { conflictAsk = null },
                )
            }
        }
    }
    state.tasks.firstOrNull { it.id == editingId }?.let { base ->
        TaskDialog(
            task = base.withEdit(),
            onSave = { n, m -> editingId = null; requestEdit(base, n, m) },
            onDelete = null,
            onDismiss = { editingId = null },
        )
    }
    pendingRaw?.let(::parseEdit)?.let { pending ->
        val base = state.tasks.firstOrNull { it.id == pending.taskId }
        if (base == null) pendingRaw = null else AlertDialog(
            onDismissRequest = { pendingRaw = null },
            title = { Text(stringResource(R.string.task_scope_title)) },
            text = { Text(stringResource(R.string.task_scope_body, base.name)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { putEdit(base, pending.name, pending.minutes, true); pendingRaw = null }) {
                        Text(stringResource(R.string.task_scope_only_here))
                    }
                    TextButton(onClick = { putEdit(base, pending.name, pending.minutes, false); pendingRaw = null }) {
                        Text(stringResource(R.string.task_scope_everywhere))
                    }
                    TextButton(onClick = { pendingRaw = null }) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
        )
    }
    if (showPicker) {
        val candidates = state.tasks.filter { it.id !in taskIds }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.routine_pick_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (candidates.isEmpty()) {
                        Text(stringResource(R.string.routine_pick_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    candidates.forEach { task ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable { taskIds = taskIds + task.id }
                                .heightIn(min = 52.dp).padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(labels[task.id] ?: task.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(formatDuration(task.minutes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false; addTask = true }) { Text(stringResource(R.string.routine_pick_new)) } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.routine_pick_done)) } },
        )
    }
    if (addTask) {
        TaskDialog(
            task = null,
            onSave = { n, m -> viewModel.addCatalogTask(n, m) { id -> taskIds = taskIds + id }; addTask = false },
            onDelete = null,
            onDismiss = { addTask = false },
            onSaveAndContinue = { n, m -> viewModel.addCatalogTask(n, m) { id -> taskIds = taskIds + id } },
        )
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.routine_delete_confirm, existing.name)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteRoutine(existing); onBack() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Label(res: Int) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
    )
}

/** One activity of the routine: tap to edit, X to take it out of this routine (it stays in the catalog). */
@Composable
private fun RoutineTaskRow(task: Task, color: Color, onEdit: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.16f))
            .clickable(onClickLabel = stringResource(R.string.cd_edit), onClick = onEdit)
            .heightIn(min = 60.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (task.buffer) {
            Icon(
                Icons.Outlined.HourglassBottom, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp).size(18.dp),
            )
        }
        Text(task.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(formatDuration(task.minutes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_edit_task, task.name), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.routine_remove_task, task.name), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun encodeEdit(e: TaskEdit) = "${e.taskId}\t${if (e.onlyHere) "H" else "E"}\t${e.minutes}\t${e.name}"

private fun parseEdit(raw: String): TaskEdit? {
    val p = raw.split('\t', limit = 4)
    if (p.size < 4) return null
    return TaskEdit(p[0].toLongOrNull() ?: return null, p[3], p[2].toIntOrNull() ?: return null, p[1] == "H")
}

/** "You can't switch this routine on: another one that is on already covers these days", with the way out. */
@Composable
private fun RoutineConflictDialog(name: String, conflicts: List<DayConflict>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val line = stringResource(R.string.routine_conflict_line)
    val lines = conflicts.joinToString("\n") { String.format(line, it.routine.name, it.days.fullList()) }
    ConflictDialog(
        body = stringResource(R.string.routine_conflict_body, name, lines),
        confirmLabel = stringResource(if (conflicts.size == 1) R.string.routine_conflict_disable_one else R.string.routine_conflict_disable_many),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** The way out is a real button (filled, full width), with Cancel under it. */
@Composable
private fun ConflictDialog(body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routine_conflict_title)) },
        text = { Text(body) },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(confirmLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}
