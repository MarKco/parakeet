package com.ilsecondodasinistra.parakeet.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Surface
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedButton
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ilsecondodasinistra.parakeet.R
import com.ilsecondodasinistra.parakeet.alarm.Notifications
import com.ilsecondodasinistra.parakeet.ui.main.MainUiState
import com.ilsecondodasinistra.parakeet.ui.main.MainViewModel
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.ilsecondodasinistra.parakeet.data.AlarmMode
import kotlinx.coroutines.launch

/** The pages of Settings, in the order of the tab bar. */
internal enum class SettingsTab(val title: Int) {
    General(R.string.settings_tab_general),
    Alarm(R.string.settings_alarm_section),
    Reminders(R.string.settings_reminders),
    AfterWake(R.string.settings_postwake),
    Sync(R.string.settings_tab_sync),
    Backup(R.string.settings_tab_backup),
}

/** The tab left open last, kept while the app lives: Settings reopens where it was. */
private var lastSettingsTab = SettingsTab.General

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: MainUiState, viewModel: MainViewModel, onBack: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = lastSettingsTab.ordinal) { SettingsTab.entries.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState.currentPage) { lastSettingsTab = SettingsTab.entries[pagerState.currentPage] }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 8.dp) {
                SettingsTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(stringResource(tab.title)) },
                    )
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                val goToAlarm = { scope.launch { pagerState.animateScrollToPage(SettingsTab.Alarm.ordinal) }; Unit }
                SettingsPage {
                    when (SettingsTab.entries[page]) {
                        SettingsTab.General -> GeneralTab(state, viewModel)
                        SettingsTab.Alarm -> AlarmSection(state, viewModel)
                        SettingsTab.Reminders ->
                            if (state.alarmMode == AlarmMode.INTERNAL) RemindersSection(state, viewModel) else NeedsInternalCard(goToAlarm)
                        SettingsTab.AfterWake ->
                            if (state.alarmMode == AlarmMode.INTERNAL) PostWakeSection(state, viewModel) else NeedsInternalCard(goToAlarm)
                        SettingsTab.Sync -> SyncTab()
                        SettingsTab.Backup -> BackupTab()
                    }
                }
            }
        }
    }
}

/** One page of the pager: scrolls on its own, content no wider than a phone. */
@Composable
internal fun SettingsPage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

/** Reminders and "After the alarm" only exist with Parakeet's own alarm: say so, and take the way to switch it on. */
@Composable
private fun NeedsInternalCard(onGoToAlarm: () -> Unit) {
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.needs_internal_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.needs_internal_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onGoToAlarm, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.needs_internal_button))
            }
        }
    }
}

/** Plan mode, the "time to leave" notification (with its exact-timing permission and text). */
@Composable
private fun GeneralTab(state: MainUiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    var exactAllowed by remember { mutableStateOf(viewModel.canScheduleExact()) }
    var notificationsBlocked by remember { mutableStateOf(false) }
    var text by remember(state.exitText) { mutableStateOf(state.exitText) }

    // Coming back from the system "Alarms & reminders" / notification screens: refresh status
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val before = exactAllowed
        exactAllowed = viewModel.canScheduleExact()
        if (before != exactAllowed) viewModel.refreshExitNotification()
        if (state.notifyOnExit && Notifications.canPost(context)) notificationsBlocked = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsBlocked = !granted
        viewModel.setNotifyOnExit(granted)
    }

    PlanModeSection(state, viewModel)


    // Notification switch: the whole row toggles
    SettingsCard {
        Row(
            Modifier
                .toggleable(
                    value = state.notifyOnExit,
                    role = Role.Switch,
                    onValueChange = { enable ->
                        if (enable && Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(context)) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else viewModel.setNotifyOnExit(enable)
                    },
                )
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.notify_on_exit), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.notify_on_exit_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = state.notifyOnExit, onCheckedChange = null)
        }
    }
    if (notificationsBlocked) {
        SettingsCard(container = MaterialTheme.colorScheme.errorContainer) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.notif_permission_denied), color = MaterialTheme.colorScheme.onErrorContainer)
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.open_system_settings)) }
            }
        }
    }

    if (state.notifyOnExit) {
        SettingsCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.exact_alarm_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (exactAllowed) R.string.exact_alarm_granted else R.string.exact_alarm_denied),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!exactAllowed) {
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                            )
                        },
                        modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.exact_alarm_allow)) }
                }
            }
        }

        SettingsCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.exit_text_title), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; viewModel.setExitText(it) },
                    placeholder = { Text(stringResource(R.string.notification_exit_text)) },
                    supportingText = { Text(stringResource(R.string.exit_text_hint)) },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (text.isNotEmpty()) {
                    TextButton(onClick = { text = ""; viewModel.setExitText("") }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.exit_text_reset))
                    }
                }
            }
        }
    }
}

/** Simple (wake-up time + activities, one alarm at a time) or scheduled (weekday routines). Always switchable. */
@Composable
internal fun PlanModeSection(state: MainUiState, viewModel: MainViewModel) {
    Text(
        stringResource(R.string.settings_mode_section),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
    com.ilsecondodasinistra.parakeet.ui.ChoiceCard(
        selected = state.planMode == com.ilsecondodasinistra.parakeet.data.PlanMode.SIMPLE,
        title = stringResource(R.string.plan_simple),
        description = stringResource(R.string.plan_simple_desc),
        onClick = { viewModel.setPlanMode(com.ilsecondodasinistra.parakeet.data.PlanMode.SIMPLE) },
    )
    com.ilsecondodasinistra.parakeet.ui.ChoiceCard(
        selected = state.planMode == com.ilsecondodasinistra.parakeet.data.PlanMode.SCHEDULED,
        title = stringResource(R.string.plan_scheduled),
        description = stringResource(R.string.plan_scheduled_desc),
        onClick = { viewModel.setPlanMode(com.ilsecondodasinistra.parakeet.data.PlanMode.SCHEDULED) },
    )
}

/** Notifications before the alarm: "upcoming · skip" (2 h before) and the evening summary. */
@Composable
internal fun RemindersSection(state: MainUiState, viewModel: MainViewModel) {
    var pickEvening by remember { mutableStateOf(false) }
    var pickOffset by remember { mutableStateOf(false) }
    val prefs = state.planPrefs
    val evening = java.time.LocalTime.of(prefs.eveningMinute / 60, prefs.eveningMinute % 60)
    SettingsCard {
        Column {
            SwitchRow(
                title = stringResource(R.string.pre_alarm_title),
                description = stringResource(R.string.pre_alarm_desc),
                checked = prefs.preAlarmNotify,
                onChange = viewModel::setPreAlarmNotify,
            )
            androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ValueRow(
                icon = androidx.compose.material.icons.Icons.Outlined.Timer,
                label = stringResource(R.string.pre_alarm_offset_title),
                active = prefs.preAlarmNotify,
                onClick = { pickOffset = true },
            ) {
                Text(offsetLabel(prefs.preAlarmMinutes), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }
    SettingsCard {
        Column {
            SwitchRow(
                title = stringResource(R.string.evening_title),
                description = stringResource(R.string.evening_desc_plain),
                checked = prefs.eveningSummary,
                onChange = viewModel::setEveningSummary,
            )
            // The time is always visible and the whole row opens the picker (dimmed while the summary is off)
            androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ValueRow(
                icon = androidx.compose.material.icons.Icons.Outlined.Schedule,
                label = stringResource(R.string.evening_time_title),
                active = prefs.eveningSummary,
                onClick = { pickEvening = true },
            ) {
                com.ilsecondodasinistra.parakeet.ui.LcdText(
                    evening.format(java.time.format.DateTimeFormatter.ofPattern("H:mm")),
                    30.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    if (pickOffset) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickOffset = false },
            title = { Text(stringResource(R.string.pre_alarm_offset_title)) },
            text = {
                Column {
                    PreAlarmOffsets.forEach { m ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .selectable(selected = m == prefs.preAlarmMinutes, role = Role.RadioButton) {
                                    viewModel.setPreAlarmMinutes(m); pickOffset = false
                                }
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(selected = m == prefs.preAlarmMinutes, onClick = null, modifier = Modifier.padding(horizontal = 8.dp))
                            Text(stringResource(R.string.pre_alarm_offset_before, offsetLabel(m)), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickOffset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (pickEvening) {
        com.ilsecondodasinistra.parakeet.ui.TimePickerDialog(
            initial = evening,
            title = stringResource(R.string.evening_time_title),
            onConfirm = { viewModel.setEveningTime(it); pickEvening = false },
            onDismiss = { pickEvening = false },
        )
    }
}

/**
 * "After the alarm": the day's activities as a guided list until the exit. Off by default; the master switch
 * turns on the three parts, each of which can be switched off on its own. "Over the lock screen" needs the
 * ring + list screen, so it is dimmed without it.
 */
@Composable
internal fun PostWakeSection(state: MainUiState, viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = state.postWake
    SettingsCard {
        Column {
            SwitchRow(
                title = stringResource(R.string.postwake_enable_title),
                description = stringResource(R.string.postwake_enable_desc),
                checked = prefs.enabled,
                onChange = viewModel::setPostWakeEnabled,
            )
            if (prefs.enabled) {
                val divider = @Composable {
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
                divider()
                SwitchRow(
                    title = stringResource(R.string.postwake_ring_title),
                    description = stringResource(R.string.postwake_ring_desc),
                    checked = prefs.ring,
                    onChange = { on -> viewModel.updatePostWake { it.copy(ring = on) } },
                )
                divider()
                SwitchRow(
                    title = stringResource(R.string.postwake_lock_title),
                    description = stringResource(R.string.postwake_lock_desc),
                    checked = prefs.lockScreen && prefs.ring,
                    enabled = prefs.ring,
                    onChange = { on -> viewModel.updatePostWake { it.copy(lockScreen = on) } },
                )
                divider()
                SwitchRow(
                    title = stringResource(R.string.postwake_notify_title),
                    description = stringResource(R.string.postwake_notify_desc),
                    checked = prefs.notify,
                    onChange = { on -> viewModel.updatePostWake { it.copy(notify = on) } },
                )
            }
        }
    }
    if (prefs.enabled) {
        OutlinedButton(
            onClick = {
                viewModel.tryPostWake { ok ->
                    if (ok) context.startActivity(android.content.Intent(context, com.ilsecondodasinistra.parakeet.ui.postwake.PostWakeActivity::class.java))
                    else Toast.makeText(context, R.string.postwake_try_failed, Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text(stringResource(R.string.postwake_try)) }
    }
}

private val PreAlarmOffsets = listOf(15, 30, 60, 90, 120, 180)

/** "30 min", "1 h", "1 h 30 min". */
@Composable
private fun offsetLabel(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.duration_m, minutes)
    minutes % 60 == 0 -> stringResource(R.string.duration_h, minutes / 60)
    else -> stringResource(R.string.duration_h_m, minutes / 60, minutes % 60)
}

/** A setting value always on show; the whole row opens its editor (dimmed while its switch is off). */
@Composable
internal fun ValueRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    value: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .alpha(if (active) 1f else 0.6f)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        value()
        Spacer(Modifier.width(16.dp))
        Icon(
            androidx.compose.material.icons.Icons.Filled.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
internal fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .alpha(if (enabled) 1f else 0.5f)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun SettingsCard(
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large, color = container, modifier = Modifier.fillMaxWidth()) { content() }
}
