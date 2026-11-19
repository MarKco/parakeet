package com.ilsecondodasinistra.parakeet.ui.postwake

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ilsecondodasinistra.parakeet.R
import com.ilsecondodasinistra.parakeet.domain.PostWakeAction
import com.ilsecondodasinistra.parakeet.domain.StepState
import com.ilsecondodasinistra.parakeet.domain.StepStatus
import com.ilsecondodasinistra.parakeet.domain.TimelineState
import com.ilsecondodasinistra.parakeet.ui.LcdText
import com.ilsecondodasinistra.parakeet.ui.formatDuration
import com.ilsecondodasinistra.parakeet.ui.theme.OverlineStyle
import com.ilsecondodasinistra.parakeet.ui.theme.ParakeetLime
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
fun PostWakeScreen(state: TimelineState, onAct: (Long, PostWakeAction) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    // No confetti for people who turned animations off
    val animationsOn = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val celebrate = state.allResolved && !state.exitDue && state.marginMillis > 0
    // Only when the last activity is finished on screen, not each time the screen is opened already finished
    var celebrated by rememberSaveable { mutableStateOf(celebrate) }
    var burst by remember { mutableIntStateOf(0) }
    var ringCenter by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(celebrate) {
        if (celebrate && !celebrated) {
            celebrated = true
            if (animationsOn) burst++
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080A06), contentColor = MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    Modifier.widthIn(max = 480.dp).fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "ring") { RingHeader(state) { ringCenter = it } }
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

@Composable
private fun RingHeader(state: TimelineState, onRingCenter: (Offset) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.postwake_title).uppercase(), style = OverlineStyle, color = ParakeetLime.copy(alpha = 0.9f))
        Spacer(Modifier.height(12.dp))
        val ringDescription = stringResource(R.string.cd_postwake_ring)
        SegmentedRing(
            state,
            Modifier
                .widthIn(max = 280.dp).fillMaxWidth(0.8f)
                .onGloballyPositioned { onRingCenter(it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f)) }
                .semantics { contentDescription = ringDescription },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.exitDue) {
                    Text(stringResource(R.string.postwake_time_up), style = MaterialTheme.typography.titleLarge, color = ParakeetLime, textAlign = TextAlign.Center)
                } else {
                    // Always the real time to the exit: the ring may show margin, this number never lies
                    LcdText(remaining(state.remainingToExitMillis), 64.sp, color = ParakeetLime)
                    Text(
                        stringResource(R.string.postwake_to_exit, exitTime(state)),
                        style = MaterialTheme.typography.labelLarge,
                        color = ParakeetLime.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (state.allResolved && !state.exitDue) {
            if (state.marginMillis > 0) {
                Text(
                    stringResource(R.string.postwake_congrats), style = MaterialTheme.typography.titleMedium,
                    color = ParakeetLime, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                StatusLine(state)
            } else {
                Text(stringResource(R.string.postwake_all_done), style = MaterialTheme.typography.titleMedium, color = ParakeetLime)
            }
        } else {
            StatusLine(state)
        }
    }
}

/** Ahead, on time or late, in words: margin in grey (amber while it is being eaten), lateness in coral. */
@Composable
private fun StatusLine(state: TimelineState) {
    val (text, target) = when {
        state.overflowMillis > 0 -> stringResource(R.string.postwake_status_late, state.overflowMinutes) to LateColor
        state.marginMillis > 0 -> stringResource(R.string.postwake_status_margin, maxOf(1, state.marginMinutes)) to
            (if (state.marginEroding) ErodingColor else Color.White.copy(alpha = 0.65f))
        else -> stringResource(R.string.postwake_status_on_time) to Color.White.copy(alpha = 0.65f)
    }
    val color by animateColorAsState(target, tween(SLIDE_MILLIS / 2), label = "statusColor")
    Text(text, style = MaterialTheme.typography.titleSmall, color = color, textAlign = TextAlign.Center)
}

@Composable
private fun StepRow(step: StepState, state: TimelineState, onAct: (Long, PostWakeAction) -> Unit, modifier: Modifier = Modifier) {
    val color = stepColor(step.colorIndex)
    val current = step.status == StepStatus.CURRENT
    val faded = step.status == StepStatus.SKIPPED
    val shape = MaterialTheme.shapes.large
    // The state changes are eased too: a row fading out, taking the highlight, or showing its buttons
    val fill by animateColorAsState(
        if (current) color.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f),
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
                    color = Color.White.copy(alpha = if (step.status == StepStatus.DONE) 0.6f else 1f),
                    textDecoration = if (step.status == StepStatus.DONE) TextDecoration.LineThrough else null,
                )
                val detail = when (step.status) {
                    StepStatus.CURRENT ->
                        if (step.overrun) stringResource(R.string.postwake_late_row, remaining(state.nowMillis - step.plannedEndMillis))
                        else stringResource(R.string.postwake_left, remaining(step.plannedEndMillis - state.nowMillis))
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
                        color = if (step.overrun) LateColor else Color.White.copy(alpha = 0.65f),
                    )
                }
                if (step.beyondExit) {
                    Text(stringResource(R.string.postwake_beyond_exit), style = MaterialTheme.typography.bodySmall, color = LateColor)
                }
            }
            if (step.status == StepStatus.DONE) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = ParakeetLime)
            } else {
                if (step.beyondExit) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(LateColor).semantics { contentDescription = beyondDescription })
                    Spacer(Modifier.width(8.dp))
                }
                Text(formatDuration(step.task.minutes), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.65f))
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
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        ) { Text(stringResource(R.string.postwake_later), color = Color.White, maxLines = 1) }
                    }
                    OutlinedButton(
                        onClick = { onAct(step.task.id, PostWakeAction.SKIP) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    ) { Text(stringResource(R.string.postwake_skip), color = Color.White, maxLines = 1) }
                }
            }
        }
    }
}

private fun exitTime(state: TimelineState): String =
    Instant.ofEpochMilli(state.exitAtMillis).atZone(ZoneId.systemDefault()).format(TIME)

/** "H:MM" from an hour up, "MM:SS" below: the LCD shows what is left until the exit. */
private fun remaining(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d".format(h, m) else "%02d:%02d".format(m, s)
}
