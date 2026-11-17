package com.ilsecondodasinistra.parakeet.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.ilsecondodasinistra.parakeet.ui.theme.ParakeetDarker
import com.ilsecondodasinistra.parakeet.ui.theme.ParakeetLime
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ilsecondodasinistra.parakeet.R
import com.ilsecondodasinistra.parakeet.domain.Task
import com.ilsecondodasinistra.parakeet.domain.WakeUpCalculator
import com.ilsecondodasinistra.parakeet.ui.AnimatedParakeetIcon
import com.ilsecondodasinistra.parakeet.ui.LcdText
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import com.ilsecondodasinistra.parakeet.ui.ChoiceCard
import com.ilsecondodasinistra.parakeet.ui.lcd
import com.ilsecondodasinistra.parakeet.ui.main.MainViewModel
import com.ilsecondodasinistra.parakeet.domain.AlarmPlanner
import com.ilsecondodasinistra.parakeet.ui.dayLabel
import com.ilsecondodasinistra.parakeet.ui.main.WeekdayChips
import com.ilsecondodasinistra.parakeet.ui.main.shortList
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 4 short steps that collect real data, so at the end there is already a computed wake-up time.
 * Every step can be skipped and has a sensible default.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val names = stringArrayResource(R.array.suggestion_names)
    val defaultMinutes = integerArrayResource(R.array.suggestion_minutes)

    var step by rememberSaveable { mutableIntStateOf(0) }
    var welcomeBounce by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { delay(600); welcomeBounce++ }
    val timeState = rememberTimePickerState(8, 0, true)
    var selected by rememberSaveable { mutableStateOf(ArrayList((0..2).toList())) }   // pre-selected suggestions
    var minutes by rememberSaveable { mutableStateOf(ArrayList(defaultMinutes.toList())) }
    var scheduled by rememberSaveable { mutableStateOf(false) }          // simple mode by default
    var days by rememberSaveable { mutableStateOf(ArrayList<Int>()) }   // empty = only the next morning
    var notify by rememberSaveable { mutableStateOf(true) }
    var internalAlarm by rememberSaveable { mutableStateOf(true) }
    var notifyGranted by rememberSaveable { mutableStateOf(hasNotificationPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifyGranted = granted
        notify = granted
    }

    val exitTime = LocalTime.of(timeState.hour, timeState.minute)
    val chosen = selected.sorted().map { names[it] to minutes[it] }
    val preview = WakeUpCalculator.plan(
        exitTime,
        chosen.map { Task(name = it.first, minutes = it.second, checked = true) },
        LocalDateTime.now(),
    )

    // The "When?" step exists only in scheduled mode
    val steps = remember(scheduled) { Step.entries.filter { it != Step.WHEN || scheduled } }
    val daySet = days.map { DayOfWeek.of(it) }.toSet()
    val usedDays = if (scheduled) daySet else emptySet()
    val weekdaysName = stringResource(R.string.routine_name_weekdays)
    val weekendName = stringResource(R.string.routine_name_weekend)
    val defaultName = stringResource(R.string.routine_default_name)
    fun finish() = viewModel.completeOnboarding(
        exitTime, chosen, scheduled, usedDays,
        AlarmPlanner.suggestedName(usedDays, weekdaysName, weekendName, defaultName),
        notify && notifyGranted,
        internalAlarm && notifyGranted, // the in-app alarm needs notifications to appear when it rings
    )

    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier.fillMaxHeight().widthIn(max = 560.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(targetState = step, modifier = Modifier.weight(1f), label = "onboarding") { current ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (current == 0) Arrangement.Center else Arrangement.Top,
            ) {
                when (steps[current]) {
                    Step.WELCOME -> {
                        AnimatedParakeetIcon(trigger = welcomeBounce, modifier = Modifier.size(140.dp))
                        Spacer(Modifier.size(24.dp))
                        Text(
                            stringResource(R.string.onb_welcome_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.onb_welcome_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    }
                    Step.MODE -> {
                        Title(R.string.onb_mode_title)
                        Text(stringResource(R.string.onb_mode_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.size(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChoiceCard(
                                selected = !scheduled,
                                title = stringResource(R.string.plan_simple),
                                description = stringResource(R.string.plan_simple_desc),
                                onClick = { scheduled = false },
                            )
                            ChoiceCard(
                                selected = scheduled,
                                title = stringResource(R.string.plan_scheduled),
                                description = stringResource(R.string.plan_scheduled_desc),
                                onClick = { scheduled = true },
                            )
                        }
                    }
                    Step.EXIT -> {
                        Title(R.string.onb_exit_title)
                        TimePicker(state = timeState)
                    }
                    Step.TASKS -> {
                        Title(R.string.onb_tasks_title)
                        Text(stringResource(R.string.onb_tasks_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        names.forEachIndexed { i, name ->
                            SuggestionRow(
                                name = name,
                                minutes = minutes[i],
                                checked = i in selected,
                                onToggle = {
                                    selected = ArrayList(if (i in selected) selected - i else selected + i)
                                },
                                onMinutes = { delta ->
                                    minutes = ArrayList(minutes).also { it[i] = (it[i] + delta).coerceIn(5, 180) }
                                },
                            )
                        }
                        }
                    }
                    Step.WHEN -> {
                        Title(R.string.onb_when_title)
                        Text(stringResource(R.string.onb_when_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.size(20.dp))
                        val once = stringResource(R.string.onb_when_once, dayLabel(preview.wakeAt, LocalDate.now()))
                        val workdays = DayOfWeek.entries.take(5).map { it.value }
                        val weekend = listOf(DayOfWeek.SATURDAY.value, DayOfWeek.SUNDAY.value)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                            FilterChip(selected = days.isEmpty(), onClick = { days = ArrayList() }, label = { Text(once) }, modifier = Modifier.heightIn(min = 40.dp))
                            FilterChip(selected = days.toSet() == workdays.toSet(), onClick = { days = ArrayList(workdays) }, label = { Text(weekdaysName) }, modifier = Modifier.heightIn(min = 40.dp))
                            FilterChip(selected = days.toSet() == weekend.toSet(), onClick = { days = ArrayList(weekend) }, label = { Text(weekendName) }, modifier = Modifier.heightIn(min = 40.dp))
                        }
                        Spacer(Modifier.size(20.dp))
                        WeekdayChips(
                            selected = daySet,
                            color = ParakeetLime,
                            onToggle = { d -> days = ArrayList(if (d.value in days) days - d.value else days + d.value) },
                        )
                        Spacer(Modifier.size(20.dp))
                        Text(
                            if (days.isEmpty()) once else stringResource(R.string.onb_when_weekly, daySet.shortList()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Step.ALARM -> {
                        Title(R.string.onb_alarm_title)
                        Text(stringResource(R.string.onb_alarm_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.size(20.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChoiceCard(
                                selected = internalAlarm,
                                title = stringResource(R.string.mode_internal),
                                description = stringResource(R.string.mode_internal_desc),
                                onClick = { internalAlarm = true },
                            )
                            ChoiceCard(
                                selected = !internalAlarm,
                                title = stringResource(R.string.mode_clock),
                                description = stringResource(R.string.mode_clock_desc),
                                onClick = { internalAlarm = false },
                            )
                        }
                    }
                    Step.NOTIFY -> {
                        Title(R.string.onb_notify_title)
                        Text(stringResource(R.string.onb_notify_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        if (internalAlarm && !notifyGranted) {
                            Spacer(Modifier.size(12.dp))
                            Text(
                                stringResource(R.string.onb_notify_alarm_needed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(Modifier.size(24.dp))
                        if (notifyGranted) {
                            Text(stringResource(R.string.onb_notify_ready), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        } else {
                            Button(onClick = {
                                if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else { notifyGranted = true; notify = true }
                            }, modifier = Modifier.heightIn(min = 56.dp), shape = MaterialTheme.shapes.large) { Text(stringResource(R.string.onb_allow)) }
                        }
                    }
                }
            }
        }

        if (steps[step] == Step.TASKS) {
            // Live preview stays visible while the list scrolls
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(MaterialTheme.shapes.extraLarge).background(ParakeetDarker).padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.onb_wake_preview, "").trim(), color = ParakeetLime.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                LcdText(preview.wakeAt.lcd(), 56.sp, color = ParakeetLime)
            }
            Spacer(Modifier.size(12.dp))
        }
        if (step > 0) {
            StepDots(step - 1, steps.size - 1)
            Spacer(Modifier.size(12.dp))
        }
        // Navigation row
        Row(Modifier.fillMaxWidth().navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            when (step) {
                0 -> {
                    Button(onClick = { step = 1 }, modifier = Modifier.heightIn(min = 56.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large) { Text(stringResource(R.string.onb_start)) }
                }
                else -> {
                    TextButton(onClick = { step -= 1 }) { Text(stringResource(R.string.onb_back)) }
                    Spacer(Modifier.weight(1f))
                    if (step == steps.lastIndex) {
                        if (!notifyGranted) {
                            TextButton(onClick = { notify = false; finish() }) { Text(stringResource(R.string.onb_no_thanks)) }
                        }
                        Button(onClick = { finish() }, modifier = Modifier.heightIn(min = 56.dp), shape = MaterialTheme.shapes.large) { Text(stringResource(R.string.onb_done)) }
                    } else {
                        Button(onClick = { step += 1 }, modifier = Modifier.heightIn(min = 56.dp), shape = MaterialTheme.shapes.large) { Text(stringResource(R.string.onb_next)) }
                    }
                }
            }
        }
    }
    }
}

/** Onboarding pages in order; WHEN (weekdays) only exists in scheduled mode. */
private enum class Step { WELCOME, MODE, EXIT, TASKS, WHEN, ALARM, NOTIFY }

@Composable
private fun Title(res: Int) {
    Text(
        stringResource(res),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun SuggestionRow(name: String, minutes: Int, checked: Boolean, onToggle: () -> Unit, onMinutes: (Int) -> Unit) {
    val container by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        label = "suggestionColor",
    )
    Surface(shape = MaterialTheme.shapes.large, color = container, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = checked,
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
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            if (checked) {
                IconButton(onClick = { onMinutes(-5) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Remove, contentDescription = "-5")
                }
            }
            Text(
                stringResource(R.string.duration_m, minutes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = if (checked) 0.dp else 12.dp),
            )
            if (checked) {
                IconButton(onClick = { onMinutes(5) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "+5")
                }
            } else {
                Spacer(Modifier.size(4.dp))
            }
        }
    }
}

@Composable
private fun StepDots(step: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == step) 24.dp else 8.dp, label = "dot")
            Box(
                Modifier.height(8.dp).width(width).clip(CircleShape)
                    .background(if (i == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

private fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
