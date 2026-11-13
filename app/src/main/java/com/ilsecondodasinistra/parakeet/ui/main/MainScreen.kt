package com.ilsecondodasinistra.parakeet.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import com.ilsecondodasinistra.parakeet.R
import dev.chrisbanes.haze.HazeState
import com.ilsecondodasinistra.parakeet.ui.glass
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.ilsecondodasinistra.parakeet.domain.Task
import com.ilsecondodasinistra.parakeet.ui.AnimatedParakeetIcon
import com.ilsecondodasinistra.parakeet.ui.LcdText
import com.ilsecondodasinistra.parakeet.ui.TaskDialog
import com.ilsecondodasinistra.parakeet.ui.TimePickerDialog
import com.ilsecondodasinistra.parakeet.ui.WhatsNewDialog
import com.ilsecondodasinistra.parakeet.ui.dayLabel
import com.ilsecondodasinistra.parakeet.ui.formatDuration
import com.ilsecondodasinistra.parakeet.ui.lcd
import com.ilsecondodasinistra.parakeet.ui.theme.OverlineStyle
import com.ilsecondodasinistra.parakeet.ui.theme.ParakeetDarker
import com.ilsecondodasinistra.parakeet.ui.theme.ParakeetLime
import java.time.LocalDate
import java.time.LocalTime

private val ScreenPadding = 16.dp
private val ContentMaxWidth = 640.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var iconTrigger by remember { mutableIntStateOf(0) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val haze = rememberHazeState()
    val config = LocalConfiguration.current
    // Two panes only when there is room side by side; tall tablets keep one centred column
    val wide = config.screenWidthDp >= 720 && config.screenWidthDp > config.screenHeightDp

    // One-shot events -> snackbars (+ icon animation when the alarm is really set)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbar.currentSnackbarData?.dismiss()
            when (event) {
                is MainEvent.AlarmSet -> {
                    iconTrigger++
                    snackbar.showSnackbar(context.getString(R.string.alarm_set_done, event.wakeAt.lcd()), duration = SnackbarDuration.Short)
                }
                MainEvent.NoClockApp -> snackbar.showSnackbar(context.getString(R.string.alarm_no_clock_app))
                MainEvent.AlarmCancelled -> snackbar.showSnackbar(context.getString(R.string.alarm_cancelled))
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
                    IconButton(onClick = onOpenAbout) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.cd_info))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.cd_settings))
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
        bottomBar = { if (!wide) BottomCta(state, haze, onSetAlarm = viewModel::setAlarm) },
    ) { padding ->
        val taskItems: LazyListScope.() -> Unit = {
            item(key = "section") { SectionHeader(state) }
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
                    task = task,
                    onToggle = { viewModel.toggleTask(it); iconTrigger++ },
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

        if (wide) {
            // Tablet / landscape: the "result" (clock, exit time, button) on the left, the list on the right
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
                    HeaderCard(state, onEditExit = { showTimePicker = true })
                    state.alarm?.let { AlarmStatusCard(it, viewModel::cancelAlarm) }
                    Spacer(Modifier.weight(1f, fill = false).heightIn(min = 8.dp))
                    SetAlarmButton(state, onSetAlarm = viewModel::setAlarm)
                }
                LazyColumn(
                    modifier = Modifier.weight(0.54f).fillMaxHeight(),
                    contentPadding = PaddingValues(
                        start = 8.dp, end = ScreenPadding, top = 4.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = taskItems,
                )
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    // The list scrolls under the frosted bottom bar
                    modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().hazeSource(haze),
                    contentPadding = PaddingValues(
                        start = ScreenPadding,
                        end = ScreenPadding,
                        top = padding.calculateTopPadding() + 4.dp,
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "header") { HeaderCard(state, onEditExit = { showTimePicker = true }) }
                    state.alarm?.let { alarm ->
                        item(key = "alarm") { AlarmStatusCard(alarm, viewModel::cancelAlarm) }
                    }
                    taskItems()
                }
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initial = state.exitTime ?: LocalTime.of(8, 0),
            onConfirm = { viewModel.setExitTime(it); showTimePicker = false },
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
    if (state.showWhatsNew) WhatsNewDialog(onDismiss = viewModel::dismissWhatsNew)
}

// ---- Header: radio-clock display + two flat rows ---------------------------------------------

@Composable
private fun HeaderCard(state: MainUiState, onEditExit: () -> Unit) {
    val plan = state.plan
    Column(Modifier.fillMaxWidth()) {
        // The only "card": the dark LCD panel of the radio clock
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(ParakeetDarker)
                .border(1.dp, ParakeetLime.copy(alpha = if (isSystemInDarkTheme()) 0.22f else 0f), MaterialTheme.shapes.extraLarge)
                .padding(top = 20.dp, bottom = 22.dp)
                .semantics(mergeDescendants = true) {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.wake_up_label).uppercase(),
                style = OverlineStyle,
                color = ParakeetLime.copy(alpha = 0.75f),
            )
            LcdText(
                text = plan?.wakeAt?.lcd() ?: "--:--",
                size = 96.sp,
                color = ParakeetLime,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
            )
            Text(
                plan?.let { dayLabel(it.wakeAt, LocalDate.now()) } ?: stringResource(R.string.exit_not_set),
                style = MaterialTheme.typography.titleMedium,
                color = ParakeetLime.copy(alpha = 0.85f),
            )
        }

        Spacer(Modifier.size(8.dp))

        // Exit time: the whole row opens the time picker
        HeaderRow(
            icon = Icons.Outlined.DirectionsWalk,
            label = stringResource(R.string.exit_at),
            onClick = onEditExit,
            onClickLabel = stringResource(R.string.cd_set_exit),
            value = {
                if (state.exitTime != null) LcdText(state.exitTime.lcd(), 34.sp, color = MaterialTheme.colorScheme.onSurface)
                else Text(stringResource(R.string.exit_not_set), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            },
            trailing = {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            },
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        HeaderRow(
            icon = Icons.Outlined.HourglassBottom,
            label = stringResource(R.string.prep_label),
            value = {
                Text(
                    formatDuration(plan?.totalMinutes ?: 0),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
        )
    }
}

/** Flat row of the header: icon, label, value pushed to the end. Same paddings for both rows. */
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

// ---- Alarm status, section header ----------------------------------------------------------

@Composable
private fun AlarmStatusCard(alarm: AlarmStatus, onCancel: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.alarm_status, alarm.wakeAt.lcd(), alarm.exitAt.lcd()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.alarm_cancel))
            }
        }
    }
}

@Composable
private fun SectionHeader(state: MainUiState) {
    val selected = state.tasks.count { it.checked }
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            stringResource(R.string.tasks_section),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (state.tasks.isNotEmpty()) {
            Text(
                stringResource(R.string.tasks_selected, selected, state.tasks.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        if (task.checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        label = "rowColor",
    )
    val textAlpha by animateFloatAsState(if (task.checked) 1f else 0.72f, label = "rowAlpha")
    Surface(shape = shape, color = container, modifier = Modifier.fillMaxWidth()) {
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

// ---- Bottom CTA -----------------------------------------------------------------------------

@Composable
private fun BottomCta(state: MainUiState, haze: HazeState, onSetAlarm: () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(haze, tintAlpha = 0.3f, blur = 16.dp)) {
        // Hairline that marks the edge of the glass over the list
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier
                    .widthIn(max = ContentMaxWidth)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
            ) {
                SetAlarmButton(state, onSetAlarm)
            }
        }
    }
}

@Composable
private fun SetAlarmButton(state: MainUiState, onSetAlarm: () -> Unit) {
    val plan = state.plan
    val enabled = plan != null && plan.totalMinutes > 0
    Button(
        onClick = onSetAlarm,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
    ) {
        Icon(Icons.Filled.Alarm, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (enabled) stringResource(R.string.set_alarm_cta, plan!!.wakeAt.lcd())
            else stringResource(R.string.select_something),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
