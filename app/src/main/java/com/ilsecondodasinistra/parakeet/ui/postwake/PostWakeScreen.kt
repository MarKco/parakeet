package com.ilsecondodasinistra.parakeet.ui.postwake

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * Ring + list for the morning in progress. Elapsed activities that were not done fade and blur: their time
 * is gone. The current one carries the "Done" / "Next" buttons.
 */
@Composable
fun PostWakeScreen(state: TimelineState, onAct: (Long, PostWakeAction) -> Unit, onClose: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF080A06), contentColor = MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.postwake_title).uppercase(), style = OverlineStyle, color = ParakeetLime.copy(alpha = 0.9f))
                Spacer(Modifier.height(12.dp))
                val ringDescription = stringResource(R.string.cd_postwake_ring)
                SegmentedRing(
                    state,
                    Modifier.widthIn(max = 280.dp).fillMaxWidth(0.8f).semantics { contentDescription = ringDescription },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.exitDue) {
                            Text(stringResource(R.string.postwake_time_up), style = MaterialTheme.typography.titleLarge, color = ParakeetLime, textAlign = TextAlign.Center)
                        } else {
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
                if (state.allResolved && !state.exitDue) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.postwake_all_done), style = MaterialTheme.typography.titleMedium, color = ParakeetLime)
                    if (state.marginMinutes > 0) {
                        Text(
                            stringResource(R.string.postwake_margin, state.marginMinutes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.steps.forEachIndexed { index, step ->
                        StepRow(index, step, state, onAct)
                    }
                }
                Spacer(Modifier.height(20.dp))
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.postwake_close))
                }
            }
        }
    }
}

@Composable
private fun StepRow(index: Int, step: StepState, state: TimelineState, onAct: (Long, PostWakeAction) -> Unit) {
    val color = stepColor(index)
    val current = step.status == StepStatus.CURRENT
    val faded = step.status == StepStatus.EXPIRED || step.status == StepStatus.SKIPPED
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (current) color.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        // Only the content fades and blurs, so the buttons of the current row stay crisp
        Row(
            Modifier.fillMaxWidth().then(if (faded) Modifier.alpha(0.38f).blur(1.6.dp) else Modifier),
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
                    StepStatus.CURRENT -> stringResource(R.string.postwake_left, remaining(step.endMillis - state.nowMillis))
                    StepStatus.SKIPPED -> stringResource(R.string.postwake_skipped)
                    StepStatus.UPCOMING -> stringResource(
                        R.string.postwake_from, Instant.ofEpochMilli(step.startMillis).atZone(ZoneId.systemDefault()).format(TIME),
                    )
                    else -> null
                }
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
                }
            }
            if (step.status == StepStatus.DONE) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = ParakeetLime)
            } else {
                Text(formatDuration(step.task.minutes), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.65f))
            }
        }
        if (current) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onAct(step.task.id, PostWakeAction.DONE) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ParakeetLime, contentColor = Color(0xFF1E290A)),
                ) { Text(stringResource(R.string.postwake_done)) }
                OutlinedButton(
                    onClick = { onAct(step.task.id, PostWakeAction.SKIP) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                ) { Text(stringResource(R.string.postwake_next), color = Color.White) }
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
