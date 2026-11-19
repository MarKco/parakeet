package com.ilsecondodasinistra.parakeet.ui.postwake

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.ilsecondodasinistra.parakeet.R
import com.ilsecondodasinistra.parakeet.domain.PostWakeAction
import com.ilsecondodasinistra.parakeet.domain.StepState
import com.ilsecondodasinistra.parakeet.domain.StepStatus
import com.ilsecondodasinistra.parakeet.domain.TimelineState
import com.ilsecondodasinistra.parakeet.ui.LcdText
import com.ilsecondodasinistra.parakeet.ui.formatDuration
import com.ilsecondodasinistra.parakeet.ui.main.LcdPanel
import com.ilsecondodasinistra.parakeet.ui.theme.OverlineStyle
import com.ilsecondodasinistra.parakeet.ui.theme.ParakeetLime
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val TIME: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/**
 * Ring + list for the morning in progress. The current activity stays current until the user acts: "Done",
 * "Do it later" or "Skip for today". It is a list so that a postponed row can slide to the back in step with its
 * arc on the ring. Finishing the last activity with margin left starts a burst of confetti.
 */
@Composable
fun PostWakeScreen(state: TimelineState, onLockScreen: Boolean, onAct: (Long, PostWakeAction) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    // No confetti for people who turned animations off
    val animationsOn = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val celebrate = state.allResolved && !state.exitDue && state.marginMillis > 0
    // Only when the last activity is finished on screen, not each time the screen is opened already finished
    var celebrated by rememberSaveable { mutableStateOf(celebrate) }
    var burst by remember { mutableIntStateOf(0) }
    var ringCenter by remember { mutableStateOf(Offset.Zero) }
    // Celebrations and boxes only when the screen is in front: what happened while away is marked as seen, not replayed
    // The state is not collected while the screen is away, so what changed meanwhile arrives just after it is back
    // in front: the first moments after coming back count as "not looking" too.
    val visibleNow = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    var settleAt by remember { mutableLongStateOf(0L) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { settleAt = SystemClock.elapsedRealtime() + RESUME_GRACE_MILLIS }
    val visible = visibleNow && SystemClock.elapsedRealtime() >= settleAt
    LaunchedEffect(celebrate, visible) {
        if (celebrate && !celebrated) {
            celebrated = true
            if (animationsOn && visible) burst++
        }
    }
    // Two moments explained once, in a box that comes and goes: the margin appears (you are ahead) and the margin
    // is gone (you are late). Not when the screen is opened with the margin already there.
    var callout by remember { mutableStateOf<Callout?>(null) }
    // "Gained" is what is above the cushion planned from the start, not the cushion itself
    var gainedShown by rememberSaveable { mutableStateOf(state.gainedMillis > 0) }
    var lateShown by rememberSaveable { mutableStateOf(state.overflowMillis > 0) }
    // Whether there ever was a lead: "your lead is gone" only makes sense then, otherwise just "from here you are late"
    var hadLead by rememberSaveable { mutableStateOf(state.marginMillis > 0) }
    LaunchedEffect(state.marginMillis > 0) { if (state.marginMillis > 0) hadLead = true }
    LaunchedEffect(state.gainedMillis > 0, visible) {
        if (state.gainedMillis > 0 && !gainedShown) { gainedShown = true; if (visible) callout = Callout.GAINED }
    }
    LaunchedEffect(state.overflowMillis > 0, visible) {
        if (state.overflowMillis > 0 && !lateShown) {
            lateShown = true
            if (visible) callout = if (hadLead) Callout.LEAD_GONE else Callout.LATE
        }
    }
    LaunchedEffect(callout) {
        if (callout != null) {
            delay(CALLOUT_MILLIS)
            callout = null
        }
    }

    // Light app theme, not over the lock screen: ring and digits straight on the page, in the theme's dark green
    val lightHeader = !onLockScreen && !isSystemInDarkTheme()
    Surface(
        modifier = Modifier.fillMaxSize(),
        // Over the lock screen: near black, like the alarm. Otherwise the colours of the app
        color = if (onLockScreen) Color(0xFF080A06) else MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = 480.dp).fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "ring") { RingHeader(state, callout, onLockScreen, lightHeader, onDismissCallout = { callout = null }) { ringCenter = it } }
                    items(state.steps, key = { it.task.id }) { step ->
                        StepRow(
                            step, state, onAct,
                            Modifier.animateItem(placementSpec = tween(SLIDE_MILLIS, easing = FastOutSlowInEasing)),
                        )
                    }
                    item(key = "close") {
                        OutlinedButton(onClick = onClose, modifier = Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.postwake_close))
                        }
                    }
                }
            }
            ConfettiBurst(burst, ringCenter)
        }
    }
}

/**
 * Ring, number and status. Over the lock screen they sit on the near-black screen; otherwise in the same dark
 * "radio display" panel as the next alarm on the main screen, where the lime digits stay readable on a light theme.
 */
@Composable
private fun HeaderContainer(onLockScreen: Boolean, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (onLockScreen) Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = content)
    else Box(modifier) { LcdPanel(content = content) }
}

@Composable
private fun RingHeader(state: TimelineState, callout: Callout?, onLockScreen: Boolean, light: Boolean, onDismissCallout: () -> Unit, onRingCenter: (Offset) -> Unit) {
    val accent = if (light) MaterialTheme.colorScheme.primary else ParakeetLime
    val soft = (if (light) MaterialTheme.colorScheme.onBackground else Color.White).copy(alpha = 0.65f)
    val dot = if (light) MaterialTheme.colorScheme.onBackground else Color.White
    HeaderContainer(onLockScreen || light, Modifier.padding(bottom = 12.dp)) {
        Text(stringResource(R.string.postwake_title).uppercase(), style = OverlineStyle, color = accent.copy(alpha = 0.9f))
        Spacer(Modifier.height(12.dp))
        val ringDescription = stringResource(R.string.cd_postwake_ring)
        Box(Modifier.fillMaxWidth()) {
        SegmentedRing(
            state,
            dotColor = dot,
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 280.dp).fillMaxWidth(0.8f)
                .onGloballyPositioned { onRingCenter(it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)) }
                .semantics { contentDescription = ringDescription },
        ) {
            // The inside of the ring: the number never gets wider than this, it shrinks to fit
            Column(Modifier.fillMaxWidth(0.58f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.exitDue) {
                    FitWidth { Text(stringResource(R.string.postwake_time_up), style = MaterialTheme.typography.titleLarge, color = accent) }
                } else {
                    // Always the real time to the exit: the ring may show margin, this number never lies
                    LcdDuration(state.remainingToExitMillis, accent)
                    FitWidth(Modifier.padding(top = 6.dp)) {
                        Text(
                            stringResource(R.string.postwake_to_exit, exitTime(state)),
                            style = MaterialTheme.typography.labelLarge,
                            color = accent.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
        CalloutBox(callout, state, onDismissCallout, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        if (state.allResolved && !state.exitDue) {
            if (state.marginMillis > 0) {
                Text(
                    stringResource(R.string.postwake_congrats), style = MaterialTheme.typography.titleMedium,
                    color = accent, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                StatusLine(state, soft)
            } else {
                Text(stringResource(R.string.postwake_all_done), style = MaterialTheme.typography.titleMedium, color = accent)
            }
        } else {
            StatusLine(state, soft)
        }
    }
}

private enum class Callout { GAINED, LEAD_GONE, LATE }

private const val CALLOUT_MILLIS = 4500L
private const val RESUME_GRACE_MILLIS = 1500L

/**
 * A short explanation next to the ring, on top of it a little: it grows in from the corner, stays a few seconds,
 * fades out, and goes away on a tap. Keeps the last text while it fades out.
 */
@Composable
private fun CalloutBox(callout: Callout?, state: TimelineState, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(callout) }
    if (callout != null) shown = callout
    val origin = TransformOrigin(1f, 0f)
    AnimatedVisibility(
        visible = callout != null,
        modifier = modifier,
        enter = fadeIn(tween(300)) + scaleIn(tween(400, easing = FastOutSlowInEasing), initialScale = 0.7f, transformOrigin = origin) +
            slideInVertically(tween(400, easing = FastOutSlowInEasing)) { -it / 3 },
        exit = fadeOut(tween(350)) + scaleOut(tween(300), targetScale = 0.9f, transformOrigin = origin),
    ) {
        val kind = shown ?: return@AnimatedVisibility
        val accent = if (kind == Callout.GAINED) ParakeetLime else LateColor
        val shape = RoundedCornerShape(16.dp)
        Text(
            when (kind) {
                Callout.GAINED -> stringResource(R.string.postwake_callout_gained, maxOf(1, state.marginMinutes))
                Callout.LEAD_GONE -> stringResource(R.string.postwake_callout_lead_gone)
                Callout.LATE -> stringResource(R.string.postwake_callout_late)
            },
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier
                .widthIn(max = 190.dp)
                .clip(shape)
                .background(Color(0xFF161B10))
                .border(1.dp, accent.copy(alpha = 0.8f), shape)
                .clickable(onClick = onDismiss)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/**
 * Ahead, on time or late, in words: the lead in grey (amber while it is being eaten), lateness in coral. A small
 * sample of the arc it talks about stands in front of the words, with the arc's colour and thickness, so the line
 * works as the legend of the ring: the thick grey arc is the lead, the thin coral one outside the ring is the delay.
 */
@Composable
private fun StatusLine(state: TimelineState, soft: Color) {
    val (text, target) = when {
        state.overflowMillis > 0 -> stringResource(R.string.postwake_status_late, state.overflowMinutes) to LateColor
        state.marginMillis > 0 -> stringResource(R.string.postwake_status_margin, maxOf(1, state.marginMinutes)) to
            (if (state.marginEroding) ErodingColor else soft)
        else -> stringResource(R.string.postwake_status_on_time) to soft
    }
    val color by animateColorAsState(target, tween(SLIDE_MILLIS / 2), label = "statusColor")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        // A sample of what this line is about: the thick grey arc of the lead, or a wave of the water of lateness
        when {
            state.overflowMillis > 0 -> { WaveSample(LateColor); Spacer(Modifier.width(8.dp)) }
            state.marginMillis > 0 -> {
                Box(Modifier.width(22.dp).height(8.dp).clip(CircleShape).background(MarginColor))
                Spacer(Modifier.width(8.dp))
            }
        }
        Text(text, style = MaterialTheme.typography.titleSmall, color = color, textAlign = TextAlign.Center)
    }
}

/** A small wave, the sign of the water of lateness. */
@Composable
private fun WaveSample(color: Color) {
    Canvas(Modifier.size(width = 22.dp, height = 12.dp)) {
        val path = Path()
        val amplitude = size.height * 0.28f
        var x = 0f
        path.moveTo(0f, size.height / 2)
        while (x <= size.width) {
            path.lineTo(x, size.height / 2 + amplitude * kotlin.math.sin(2f * Math.PI.toFloat() * 1.5f * x / size.width))
            x += 1.5f
        }
        drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun StepRow(step: StepState, state: TimelineState, onAct: (Long, PostWakeAction) -> Unit, modifier: Modifier = Modifier) {
    val color = stepColor(step.colorIndex)
    val ink = MaterialTheme.colorScheme.onBackground
    val current = step.status == StepStatus.CURRENT
    val faded = step.status == StepStatus.SKIPPED
    val shape = MaterialTheme.shapes.large
    // The state changes are eased too: a row fading out, taking the highlight, or showing its buttons
    val fill by animateColorAsState(
        if (current) color.copy(alpha = 0.14f) else ink.copy(alpha = 0.04f),
        tween(SLIDE_MILLIS / 2), label = "rowFill",
    )
    val contentAlpha by animateFloatAsState(if (faded) 0.38f else 1f, tween(SLIDE_MILLIS), label = "rowAlpha")
    val blurRadius by animateDpAsState(if (faded) 1.6.dp else 0.dp, tween(SLIDE_MILLIS), label = "rowBlur")
    val beyondDescription = stringResource(R.string.cd_postwake_beyond)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .animateContentSize(tween(SLIDE_MILLIS / 2))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // Only the content fades and blurs, so the buttons of the current row stay crisp
        Row(
            Modifier.fillMaxWidth().alpha(contentAlpha).then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    step.task.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = ink.copy(alpha = if (step.status == StepStatus.DONE) 0.6f else 1f),
                    textDecoration = if (step.status == StepStatus.DONE) TextDecoration.LineThrough else null,
                )
                val detail = when (step.status) {
                    StepStatus.CURRENT ->
                        if (step.overrun) stringResource(R.string.postwake_late_row, durationText(state.nowMillis - step.plannedEndMillis))
                        else stringResource(R.string.postwake_left, durationText(step.plannedEndMillis - state.nowMillis))
                    StepStatus.UPCOMING -> stringResource(
                        if (step.postponed) R.string.postwake_postponed_from else R.string.postwake_from,
                        Instant.ofEpochMilli(step.startMillis).atZone(ZoneId.systemDefault()).format(TIME),
                    )
                    StepStatus.SKIPPED -> stringResource(R.string.postwake_skipped)
                    else -> null
                }
                if (detail != null) {
                    Text(
                        detail, style = MaterialTheme.typography.bodySmall,
                        color = if (step.overrun) LateColor else ink.copy(alpha = 0.65f),
                    )
                }
                if (step.beyondExit) {
                    Text(stringResource(R.string.postwake_beyond_exit), style = MaterialTheme.typography.bodySmall, color = LateColor)
                }
            }
            if (step.status == StepStatus.DONE) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else {
                if (step.beyondExit) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(LateColor).semantics { contentDescription = beyondDescription })
                    Spacer(Modifier.width(8.dp))
                }
                Text(formatDuration(step.task.minutes), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.65f))
            }
        }
        if (current) {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAct(step.task.id, PostWakeAction.DONE) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ParakeetLime, contentColor = Color(0xFF1E290A)),
                ) { Text(stringResource(R.string.postwake_done)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.canDefer) {
                        OutlinedButton(
                            onClick = { onAct(step.task.id, PostWakeAction.DEFER) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            border = BorderStroke(1.dp, ink.copy(alpha = 0.4f)),
                        ) { Text(stringResource(R.string.postwake_later), color = ink, maxLines = 1) }
                    }
                    OutlinedButton(
                        onClick = { onAct(step.task.id, PostWakeAction.SKIP) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        border = BorderStroke(1.dp, ink.copy(alpha = 0.4f)),
                    ) { Text(stringResource(R.string.postwake_skip), color = ink, maxLines = 1) }
                }
            }
        }
    }
}

private fun exitTime(state: TimelineState): String =
    Instant.ofEpochMilli(state.exitAtMillis).atZone(ZoneId.systemDefault()).format(TIME)

/**
 * The time left on the LCD: the digits in the LCD font, the units in plain text next to them, so "1 h 24 m" cannot
 * be read as minutes and seconds. Hours and minutes from an hour up, minutes and seconds below, then seconds.
 */
@Composable
private fun LcdDuration(millis: Long, color: Color) {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val parts = when {
        h > 0 -> listOf(h.toString() to "h", "%02d".format(m) to "m")
        m > 0 -> listOf(m.toString() to "m", "%02d".format(s) to "s")
        else -> listOf(s.toString() to "s")
    }
    // Four digits plus two units must still fit inside the ring
    val size = if (parts.sumOf { it.first.length } >= 4) 52.sp else 64.sp
    FitWidth {
        Row {
            parts.forEachIndexed { index, (digits, unit) ->
                if (index > 0) Spacer(Modifier.width(8.dp))
                LcdText(digits, size, Modifier.alignByBaseline(), color)
                Text(
                    unit, style = MaterialTheme.typography.titleMedium, color = color.copy(alpha = 0.85f),
                    modifier = Modifier.padding(start = 3.dp).alignByBaseline(),
                )
            }
        }
    }
}

/** Measures [content] at its natural size and, if it is wider than the space it is given, scales it down to fit. */
@Composable
private fun FitWidth(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val placeable = measurables.first().measure(Constraints())
        val scale = if (constraints.hasBoundedWidth && placeable.width > constraints.maxWidth) {
            constraints.maxWidth.toFloat() / placeable.width
        } else 1f
        layout((placeable.width * scale).toInt(), (placeable.height * scale).toInt()) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/** "1 h 24 min", "14 min 45 s" or "45 s": words, not a clock face, for the rows of the list. */
@Composable
private fun durationText(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = (total / 3600).toInt()
    val m = ((total % 3600) / 60).toInt()
    val s = (total % 60).toInt()
    return when {
        h > 0 -> stringResource(R.string.duration_h_m, h, m)
        m > 0 -> stringResource(R.string.duration_m_s, m, s)
        else -> stringResource(R.string.duration_s, s)
    }
}
