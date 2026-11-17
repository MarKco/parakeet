package com.ilsecondodasinistra.parakeet.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ilsecondodasinistra.parakeet.alarm.Notifications
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilsecondodasinistra.parakeet.R
import com.ilsecondodasinistra.parakeet.data.AlarmMode
import com.ilsecondodasinistra.parakeet.domain.DaySchedule
import com.ilsecondodasinistra.parakeet.domain.Task
import com.ilsecondodasinistra.parakeet.ui.AlarmIntroDialog
import com.ilsecondodasinistra.parakeet.ui.AnimatedParakeetIcon
import com.ilsecondodasinistra.parakeet.ui.LcdText
import com.ilsecondodasinistra.parakeet.ui.TaskDialog
import com.ilsecondodasinistra.parakeet.ui.TimePickerDialog
import com.ilsecondodasinistra.parakeet.ui.formatDuration
import com.ilsecondodasinistra.parakeet.ui.glass
import com.ilsecondodasinistra.parakeet.ui.lcd
import com.ilsecondodasinistra.parakeet.ui.theme.ParakeetLime
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

private val ScreenPadding = 16.dp
private val ContentMaxWidth = 640.dp

/**
 * "The day at the centre": next alarm (radio-clock display), the date ribbon, and the selected day,
 * editable like the old single screen. Editing a day is the one-time exception; routines (weekly templates)
 * live in their own screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenRoutines: () -> Unit,
    onEditRoutine: (routineId: Long, forDate: LocalDate?) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showRoutineSheet by rememberSaveable { mutableStateOf(false) }
    var showPause by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var editingTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var iconTrigger by remember { mutableIntStateOf(0) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val haze = rememberHazeState()
    val config = LocalConfiguration.current
    // Two panes only when there is room side by side; tall tablets keep one centred column
    val wide = config.screenWidthDp >= 720 && config.screenWidthDp > config.screenHeightDp
    val day = state.day

    // The in-app alarm needs notifications: without them it rings but cannot be stopped from the lock screen
    var notificationsOn by remember { mutableStateOf(Notifications.canPost(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsOn = Notifications.canPost(context) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = Notifications.canPost(context)
        if (!granted || !notificationsOn) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }

    // One-shot events -> snackbars (+ icon animation when the alarm moves)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbar.currentSnackbarData?.dismiss()
            when (event) {
                is MainEvent.NextChanged -> {
                    iconTrigger++
                    snackbar.showSnackbar(nextMessage(context, event.next), duration = SnackbarDuration.Short)
                }
                is MainEvent.AlarmSentToClock -> {
                    iconTrigger++
                    snackbar.showSnackbar(context.getString(R.string.clock_sent, event.wakeAt.lcd()), duration = SnackbarDuration.Short)
                }
                MainEvent.NoClockApp -> snackbar.showSnackbar(context.getString(R.string.alarm_no_clock_app))
                MainEvent.NeedsExactAlarm -> snackbar.showSnackbar(context.getString(R.string.mode_needs_permission))
                MainEvent.AlarmCancelled -> snackbar.showSnackbar(context.getString(R.string.alarm_cancelled))
                is MainEvent.CreateRoutineFor -> onEditRoutine(0L, event.date)
                is MainEvent.TaskRemoved -> {
                    val result = snackbar.showSnackbar(
                        message = context.getString(R.string.task_deleted, event.task.name),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.restoreTask(event.task)
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedParakeetIcon(trigger = iconTrigger, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenRoutines) {
                        Icon(Icons.Outlined.EventRepeat, contentDescription = stringResource(R.string.cd_routines))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.cd_settings))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (state.alarmMode == AlarmMode.INTERNAL) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_pause)) },
                                    leadingIcon = { Icon(Icons.Outlined.PauseCircle, contentDescription = null) },
                                    onClick = { showMenu = false; showPause = true },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.about_title)) },
                                leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                onClick = { showMenu = false; onOpenAbout() },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                modifier = Modifier.glass(haze),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (!wide && state.alarmMode == AlarmMode.CLOCK) BottomCta(state, haze, onSend = viewModel::sendToClock) },
    ) { padding ->
        val overview: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NextAlarmPanel(state, onResume = viewModel::resume)
                DateRibbon(
                    days = state.ribbon,
                    selected = day?.date,
                    today = state.now.toLocalDate(),
                    pausedUntil = state.pausedUntil,
                    lastRung = state.lastRungDay,
                    now = state.now,
                    onSelect = viewModel::selectDate,
                )
                if (state.showDaysHint) {
                    val r = state.next?.routine
                    DaysHint(onChoose = { if (r != null) onEditRoutine(r.id, null) else onOpenRoutines() }, onDismiss = viewModel::dismissDaysHint)
                }
                state.clockAlarm?.let { ClockStatusCard(it, viewModel::cancelClockAlarm) }
                if (state.alarmMode == AlarmMode.INTERNAL && !notificationsOn) {
                    NotificationsWarning(onAllow = {
                        if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    })
                }
            }
        }
        val dayItems: LazyListScope.() -> Unit = {
            if (day != null) {
                item(key = "dayHeader") {
                    DayHeader(day, onToggle = viewModel::setDayOn, onPickRoutine = { showRoutineSheet = true })
                }
                // A day that already rang cannot change any more: only its summary is shown
                if (day.rings && !day.rung && day.exitTime != null) {
                    item(key = "exit") {
                        Column {
                            HeaderRow(
                                icon = Icons.Outlined.DirectionsWalk,
                                label = stringResource(R.string.exit_at),
                                onClick = { showTimePicker = true },
                                onClickLabel = stringResource(R.string.cd_set_exit),
                                value = { LcdText(day.exitTime.lcd(), 34.sp, color = MaterialTheme.colorScheme.onSurface) },
                                trailing = {
                                    Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                },
                            )
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            HeaderRow(
                                icon = Icons.Outlined.HourglassBottom,
                                label = stringResource(R.string.prep_label),
                                value = {
                                    Text(formatDuration(day.schedule.totalMinutes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                },
                            )
                        }
                    }
                    if (day.modified) {
                        item(key = "modified") {
                            ModifiedBanner(day, onReset = viewModel::resetDay, onSave = viewModel::saveDayToRoutine)
                        }
                    }
                    item(key = "section") { SectionHeader(selected = day.taskIds.size, total = state.tasks.size) }
                    if (state.tasks.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.tasks_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 8.dp),
                            )
                        }
                    }
                    items(state.tasks, key = { it.id }) { task ->
                        SwipeableTaskRow(
                            task = task.copy(checked = task.id in day.taskIds),
                            onToggle = { viewModel.toggleDayTask(it) },
                            onEdit = { editingTaskId = it.id },
                            onRemove = viewModel::removeTask,
                            modifier = Modifier.animateItem(),
                        )
                    }
                    item(key = "add") {
                        OutlinedButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 56.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.add_task), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }

        if (wide) {
            // Tablet / landscape: display and ribbon on the left, the selected day on the right
            Row(
                Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    Modifier
                        .weight(0.46f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = ScreenPadding, top = 4.dp, bottom = padding.calculateBottomPadding() + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    overview()
                    if (state.alarmMode == AlarmMode.CLOCK) SendToClockButton(state, viewModel::sendToClock)
                }
                LazyColumn(
                    modifier = Modifier.weight(0.54f).fillMaxHeight(),
                    contentPadding = PaddingValues(start = 8.dp, end = ScreenPadding, top = 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = dayItems,
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().hazeSource(haze),
                    contentPadding = PaddingValues(
                        start = ScreenPadding,
                        end = ScreenPadding,
                        top = padding.calculateTopPadding() + 4.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "overview") { overview() }
                    dayItems()
                }
            }
        }
    }

    if (showTimePicker && day?.exitTime != null) {
        TimePickerDialog(
            initial = day.exitTime,
            onConfirm = { viewModel.setDayExit(it); showTimePicker = false },
            onDismiss = { showTimePicker = false },
        )
    }
    if (showAddDialog) {
        TaskDialog(
            task = null,
            onSave = { name, minutes -> viewModel.addTask(name, minutes); showAddDialog = false },
            onDelete = null,
            onDismiss = { showAddDialog = false },
        )
    }
    state.tasks.firstOrNull { it.id == editingTaskId }?.let { task ->
        TaskDialog(
            task = task,
            onSave = { name, minutes -> viewModel.updateTask(task.copy(name = name, minutes = minutes)); editingTaskId = null },
            onDelete = { viewModel.removeTask(task); editingTaskId = null },
            onDismiss = { editingTaskId = null },
        )
    }
    if (showRoutineSheet && day != null) {
        DayRoutineSheet(
            day = day,
            routines = state.routines,
            onApply = { id, always -> viewModel.setDayRoutine(id, always); showRoutineSheet = false },
            onNew = { showRoutineSheet = false; onEditRoutine(0L, day.date) },
            onEdit = { showRoutineSheet = false; onEditRoutine(it.id, null) },
            onDismiss = { showRoutineSheet = false },
        )
    }
    if (showPause) PauseDialog(today = state.now.toLocalDate(), onPause = { viewModel.pauseUntil(it); showPause = false }, onDismiss = { showPause = false })
    if (state.showAlarmIntro) AlarmIntroDialog(onAnswer = viewModel::answerAlarmIntro)
}

/** Snackbar text after a change: "Alarm tue 18 at 6:45 · in 9 h 12 min". */
private fun nextMessage(context: Context, next: DaySchedule?): String {
    val wake = next?.wakeAt ?: return context.getString(R.string.no_alarm)
    val minutes = maxOf(0L, Duration.between(LocalDateTime.now(), wake).toMinutes() + 1).toInt()
    val inText = when {
        minutes < 60 -> context.getString(R.string.countdown_min, minutes)
        minutes < 24 * 60 -> context.getString(R.string.countdown_hm, minutes / 60, minutes % 60)
        else -> context.getString(R.string.countdown_dh, minutes / (24 * 60), (minutes / 60) % 24)
    }
    return context.getString(R.string.snack_next, wake.toLocalDate().shortLabel(), wake.lcd(), inText)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PauseDialog(today: LocalDate, onPause: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val todayUtc = today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = today.plusDays(7).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = pickerState.selectedDateMillis != null,
                onClick = {
                    pickerState.selectedDateMillis?.let { onPause(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                },
            ) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(
            state = pickerState,
            title = { Text(stringResource(R.string.pause_title), modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
        )
    }
}

/** Flat row: icon, label, value pushed to the end. */
@Composable
private fun HeaderRow(
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    value: @Composable () -> Unit,
) {
    var modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
    if (onClick != null) modifier = modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
    Row(
        modifier = modifier.heightIn(min = 64.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        value()
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}

/** In-app alarm with notifications blocked: explains why they matter and asks for them. */
@Composable
private fun NotificationsWarning(onAllow: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Text(
                stringResource(R.string.notif_blocked_alarm),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onAllow, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.mode_allow)) }
            }
        }
    }
}

/** Clock mode: the alarm sent to the Clock app, with cancel. */
@Composable
private fun ClockStatusCard(alarm: AlarmStatus, onCancel: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.clock_status, alarm.wakeAt.lcd(), alarm.exitAt.lcd()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.alarm_cancel)) }
        }
    }
}

@Composable
private fun SectionHeader(selected: Int, total: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
        Text(stringResource(R.string.tasks_section), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (total > 0) {
            Text(stringResource(R.string.tasks_selected, selected, total), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- Task rows ------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskRow(
    task: Task,
    onToggle: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    onRemove: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) { onRemove(task); true } else false
        },
    )
    val shape = RoundedCornerShape(20.dp)
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.clip(shape),
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        TaskRow(task, shape, onToggle, onEdit)
    }
}

@Composable
private fun TaskRow(task: Task, shape: RoundedCornerShape, onToggle: (Task) -> Unit, onEdit: (Task) -> Unit) {
    val container by animateColorAsState(
        when {
            !task.checked -> MaterialTheme.colorScheme.surfaceContainer
            // Dark: a lighter neutral row (the lime check badge and edge say "selected"), not a heavy green bar
            isSystemInDarkTheme() -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        label = "rowColor",
    )
    val textAlpha by animateFloatAsState(if (task.checked) 1f else 0.72f, label = "rowAlpha")
    Surface(
        shape = shape,
        color = container,
        border = if (task.checked && isSystemInDarkTheme()) BorderStroke(1.dp, ParakeetLime.copy(alpha = 0.55f)) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                // The whole row is the toggle (except the edit button)
                .toggleable(value = task.checked, role = Role.Checkbox, onValueChange = { onToggle(task) })
                .heightIn(min = 72.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = task.checked,
                enter = expandHorizontally(expandFrom = Alignment.Start) + scaleIn(initialScale = 0.4f) + fadeIn(),
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + scaleOut(targetScale = 0.4f) + fadeOut(),
            ) {
                Box(
                    Modifier.padding(end = 14.dp).size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                task.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            Text(
                formatDuration(task.minutes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
            IconButton(onClick = { onEdit(task) }, modifier = Modifier.padding(start = 4.dp).size(48.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.cd_edit_task, task.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


// ---- Bottom CTA (Clock app mode only) ----------------------------------------------------------

@Composable
private fun BottomCta(state: MainUiState, haze: HazeState, onSend: () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(haze, tintAlpha = 0.3f, blur = 16.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)) {
                SendToClockButton(state, onSend)
            }
        }
    }
}

@Composable
private fun SendToClockButton(state: MainUiState, onSend: () -> Unit) {
    val wake = state.next?.wakeAt
    Button(
        onClick = onSend,
        enabled = wake != null,
        shape = MaterialTheme.shapes.large,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        Icon(Icons.Filled.Alarm, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (wake != null) stringResource(R.string.send_to_clock, wake.toLocalDate().shortLabel(), wake.lcd())
            else stringResource(R.string.no_alarm),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
