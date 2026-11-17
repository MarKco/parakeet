package com.ilsecondodasinistra.parakeet.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilsecondodasinistra.parakeet.R
import com.ilsecondodasinistra.parakeet.domain.DaySchedule
import com.ilsecondodasinistra.parakeet.domain.Routine
import com.ilsecondodasinistra.parakeet.ui.LcdText
import com.ilsecondodasinistra.parakeet.ui.lcd
import com.ilsecondodasinistra.parakeet.ui.theme.LcdFont
import com.ilsecondodasinistra.parakeet.ui.theme.OverlineStyle
import com.ilsecondodasinistra.parakeet.ui.theme.ParakeetDarker
import com.ilsecondodasinistra.parakeet.ui.theme.ParakeetLime
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ---- formatting --------------------------------------------------------------------------------

fun Routine.uiColor(): Color = Color(color)

/** "in 9 h 12 min" style countdown, minute precision. */
@Composable
fun countdown(now: LocalDateTime, to: LocalDateTime): String {
    val minutes = maxOf(0L, Duration.between(now, to).toMinutes() + 1).toInt()
    return when {
        minutes < 60 -> stringResource(R.string.countdown_min, minutes)
        minutes < 24 * 60 -> stringResource(R.string.countdown_hm, minutes / 60, minutes % 60)
        else -> stringResource(R.string.countdown_dh, minutes / (24 * 60), (minutes / 60) % 24)
    }
}

fun LocalDate.fullLabel(): String =
    format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())).replaceFirstChar { it.titlecase(Locale.getDefault()) }

fun LocalDate.shortLabel(): String = format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()))

fun DayOfWeek.fullName(): String = getDisplayName(TextStyle.FULL, Locale.getDefault())

fun DayOfWeek.letter(): String = getDisplayName(TextStyle.NARROW_STANDALONE, Locale.getDefault()).uppercase()

/** Short weekday list in week order, e.g. "lun, mar, gio". */
fun Set<DayOfWeek>.shortList(): String =
    sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

// ---- the radio-clock display: next alarm + countdown ------------------------------------------------

/**
 * Gap between the LCD digits and the line under them. The digital-7 line box has more room above the ink than
 * below it, so the visible gaps (measured on screen: label→digits 12 dp, digits→date 12 dp) need this extra.
 */
val LcdToCaption = 7.dp

/** The dark radio-clock display shared by both modes: one container, one vertical rhythm (20 dp above, 17 dp below). */
@Composable
fun LcdPanel(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(if (dark) Color(0xFF080A06) else ParakeetDarker)
            .border(1.dp, ParakeetLime.copy(alpha = if (dark) 0.5f else 0f), MaterialTheme.shapes.extraLarge)
            .padding(top = 20.dp, bottom = 17.dp, start = 16.dp, end = 16.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
fun NextAlarmPanel(state: MainUiState, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val next = state.next
    val paused = state.pausedUntil
    LcdPanel(modifier) {
        Text(
            stringResource(R.string.next_alarm_label).uppercase(),
            style = OverlineStyle,
            color = ParakeetLime.copy(alpha = 0.9f),
        )
        // Digits roll like a flip clock when the alarm moves
        AnimatedContent(
            targetState = next?.wakeAt?.lcd() ?: "--:--",
            transitionSpec = {
                (slideInVertically(tween(260)) { -it / 2 } + fadeIn(tween(260))) togetherWith
                    (slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(160)))
            },
            label = "lcd",
        ) { text ->
            LcdText(text = text, size = 92.sp, color = ParakeetLime.copy(alpha = if (next == null) 0.45f else 1f), modifier = Modifier.padding(top = 2.dp, bottom = LcdToCaption))
        }
        if (next?.wakeAt != null) {
            Text(
                "${com.ilsecondodasinistra.parakeet.ui.dayLabel(next.wakeAt, state.now.toLocalDate())} · ${countdown(state.now, next.wakeAt)}",
                style = MaterialTheme.typography.titleMedium,
                color = ParakeetLime,
                textAlign = TextAlign.Center,
            )
            next.routine?.let { r ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(r.uiColor()))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (next.oneTime) "${r.name} · ${stringResource(R.string.one_time)}" else r.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = ParakeetLime.copy(alpha = 0.85f),
                    )
                }
            }
        } else {
            Text(stringResource(R.string.no_alarm), style = MaterialTheme.typography.titleMedium, color = ParakeetLime.copy(alpha = 0.8f))
        }
        if (paused != null) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${stringResource(R.string.paused_label).uppercase()} · ${stringResource(R.string.paused_until, paused.shortLabel())}",
                    style = OverlineStyle,
                    color = Color(0xFFF2A516),
                )
                TextButton(onClick = onResume, modifier = Modifier.heightIn(min = 40.dp)) {
                    Text(stringResource(R.string.resume), color = ParakeetLime)
                }
            }
        }
    }
}

// ---- date ribbon ---------------------------------------------------------------------------------

@Composable
fun DateRibbon(
    days: List<DaySchedule>,
    selected: LocalDate?,
    today: LocalDate,
    pausedUntil: LocalDate?,
    lastRung: LocalDate?,
    now: LocalDateTime,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Keep the selected day in view (e.g. after the next alarm moves to another week)
    LaunchedEffect(selected) {
        val i = days.indexOfFirst { it.date == selected }
        if (i >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == i }) listState.animateScrollToItem(maxOf(0, i - 2))
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
    ) {
        itemsIndexed(days, key = { _, d -> d.date.toEpochDay() }) { _, d ->
            DayCell(
                day = d,
                selected = d.date == selected,
                today = d.date == today,
                paused = pausedUntil != null && !d.date.isAfter(pausedUntil),
                rung = (lastRung != null && !d.date.isAfter(lastRung)) || (d.wakeAt != null && !d.wakeAt.isAfter(now)),
                onClick = { onSelect(d.date) },
            )
        }
    }
}

@Composable
private fun DayCell(day: DaySchedule, selected: Boolean, today: Boolean, paused: Boolean, rung: Boolean, onClick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val accent = day.routine?.uiColor() ?: MaterialTheme.colorScheme.outline
    val fill by animateColorAsState(
        when {
            selected && day.rings -> accent.copy(alpha = if (dark) 0.42f else 0.38f)
            selected -> MaterialTheme.colorScheme.surfaceContainerHighest
            day.rings -> accent.copy(alpha = if (dark) 0.2f else 0.18f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "dayFill",
    )
    val shape = RoundedCornerShape(18.dp)
    val outline = MaterialTheme.colorScheme.onSurface
    val dashColor = accent
    val label = if (today) stringResource(R.string.day_today) else day.date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
    val description = buildString {
        append(day.date.fullLabel())
        if (day.wakeAt != null) append(", ").append(day.wakeAt.lcd())
    }
    Column(
        Modifier
            .width(54.dp)
            .alpha(if (paused || rung) 0.5f else 1f)
            .clip(shape)
            .background(fill)
            .then(if (selected) Modifier.border(2.dp, outline, shape) else Modifier)
            .then(if (!selected && day.oneTime && (day.rings || day.skipped)) Modifier.dashedBorder(dashColor, 18.dp) else Modifier)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label.trimEnd('.').lowercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
            when {
                day.wakeAt != null -> Text(
                    day.wakeAt.lcd(),
                    style = androidx.compose.ui.text.TextStyle(fontFamily = LcdFont, fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                day.skipped -> Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                else -> Text("·", color = MaterialTheme.colorScheme.outline)
            }
        }
        // Routine colour bar
        Box(
            Modifier.padding(top = 2.dp).width(18.dp).height(4.dp).clip(CircleShape)
                .background(if (day.rings) accent else Color.Transparent),
        )
    }
}

/** Dashed rounded outline drawn over the content: "one-time" days and changes. */
fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawWithContent {
    drawContent()
    val stroke = 1.5.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

// ---- selected day: header with switch and routine chip ---------------------------------------------

@Composable
fun DayHeader(day: DayUi, onToggle: (Boolean) -> Unit, onPickRoutine: () -> Unit) {
    val status = when {
        day.rung -> stringResource(R.string.day_rung)
        day.paused -> stringResource(R.string.day_paused, day.date.shortLabel())
        day.rings -> stringResource(R.string.day_wake, day.schedule.wakeAt!!.lcd()) +
            if (day.schedule.oneTime) " · ${stringResource(R.string.one_time)}" else ""
        day.override is com.ilsecondodasinistra.parakeet.domain.DayPlan.Skip && day.weekly != null ->
            stringResource(R.string.day_skipped, day.weekly.name, day.weekly.exitTime.lcd())
        else -> stringResource(R.string.day_free)
    }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .toggleable(
                    value = day.rings,
                    enabled = !day.paused && !day.rung,
                    role = Role.Switch,
                    onValueChange = onToggle,
                )
                .heightIn(min = 64.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(day.date.fullLabel(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = day.rings, onCheckedChange = null, enabled = !day.paused && !day.rung)
        }
        if (!day.rings && day.weekly == null && day.override == null && !day.paused && !day.rung) {
            Text(
                stringResource(R.string.day_free_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (day.rings && !day.rung) {
            RoutineChip(day.routine, onClick = onPickRoutine, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun RoutineChip(routine: Routine?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = routine?.uiColor()?.copy(alpha = 0.16f) ?: MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.heightIn(min = 40.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(routine?.uiColor() ?: MaterialTheme.colorScheme.outline))
            Spacer(Modifier.width(8.dp))
            Text(
                routine?.name ?: stringResource(R.string.day_choose_routine),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
    }
}

/** "Changed only for Tue 18 · Reset / Save in Work". */
@Composable
fun ModifiedBanner(day: DayUi, onReset: () -> Unit, onSave: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().dashedBorder(day.routine?.uiColor() ?: MaterialTheme.colorScheme.outline, 20.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                stringResource(R.string.day_modified, day.date.shortLabel()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.day_reset)) }
                if (day.routine != null) {
                    TextButton(onClick = onSave, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.day_save_to, day.routine.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** First single alarm set: suggests making the routine weekly. */
@Composable
fun DaysHint(onChoose: () -> Unit, onDismiss: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.days_hint), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
            TextButton(onClick = onChoose, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.days_hint_action)) }
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close)) }
        }
    }
}

// ---- weekday chips (routine editor, onboarding) ---------------------------------------------------

/**
 * L M M G V S D circles. [owners] marks days used by another routine with its colour: choosing one
 * moves that day here.
 */
@Composable
fun WeekdayChips(
    selected: Set<DayOfWeek>,
    color: Color,
    onToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
    owners: Map<DayOfWeek, Color> = emptyMap(),
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { d ->
            val on = d in selected
            val bg by animateColorAsState(if (on) color else MaterialTheme.colorScheme.surfaceContainerHigh, label = "chip")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(d) })
                        .semantics { contentDescription = d.fullName() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        d.letter(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (on) Color(0xFF1A2205) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier.padding(top = 4.dp).size(6.dp).clip(CircleShape)
                        .background(if (!on) owners[d] ?: Color.Transparent else Color.Transparent),
                )
            }
        }
    }
}

// ---- routine of the day (bottom sheet) ----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayRoutineSheet(
    day: DayUi,
    routines: List<Routine>,
    onApply: (routineId: Long, always: Boolean) -> Unit,
    onNew: () -> Unit,
    onEdit: (Routine) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var chosen by rememberSaveable { mutableStateOf(day.routine?.id ?: routines.firstOrNull()?.id ?: 0L) }
    val weekday = day.date.dayOfWeek
    var always by rememberSaveable { mutableStateOf(day.weekly != null && day.weekly.id == day.routine?.id && day.override == null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                stringResource(R.string.routine_sheet_title, day.date.shortLabel()),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
            routines.forEach { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .selectable(selected = chosen == r.id, role = Role.RadioButton, onClick = { chosen = r.id })
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = chosen == r.id, onClick = null, modifier = Modifier.padding(8.dp))
                    Box(Modifier.size(14.dp).clip(CircleShape).background(r.uiColor()))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            buildString {
                                append(stringResource(R.string.routine_exit_short, r.exitTime.lcd()))
                                if (r.days.isNotEmpty()) append(" · ").append(r.days.shortList())
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onNew).heightIn(min = 56.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.routine_new), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .toggleable(value = always, role = Role.Switch, onValueChange = { always = it })
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.routine_always, weekday.fullName()), style = MaterialTheme.typography.titleMedium)
                    if (!always) {
                        Text(
                            stringResource(R.string.routine_always_off, day.date.shortLabel()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(checked = always, onCheckedChange = null)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                routines.firstOrNull { it.id == chosen }?.let { r ->
                    TextButton(onClick = { onEdit(r) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.routine_edit_named, r.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onApply(chosen, always) },
                    enabled = routines.any { it.id == chosen },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.apply)) }
            }
        }
    }
}
