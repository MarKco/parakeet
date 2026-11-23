package com.ilsecondodasinistra.parakeet.ui.alarm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ilsecondodasinistra.parakeet.R
import com.ilsecondodasinistra.parakeet.domain.MathProblem
import com.ilsecondodasinistra.parakeet.ui.LcdText
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Slide the thumb to the end: a deliberate gesture that is hard to do asleep. */
@Composable
fun SlideChallenge(onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val thumb = 64.dp
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var trackWidth by remember { mutableIntStateOf(0) }
    val thumbPx = with(density) { thumb.toPx() }
    val maxOffset = (trackWidth - thumbPx).coerceAtLeast(1f)
    val solved = stringResource(R.string.challenge_slide)

    Box(
        modifier
            .fillMaxWidth()
            .height(thumb)
            .clip(RoundedCornerShape(thumb))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { customActions = listOf(CustomAccessibilityAction(solved) { onSolved(); true }) }
            .pointerInput(maxOffset) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offset.value >= maxOffset * 0.9f) onSolved() else offset.animateTo(0f)
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, drag ->
                    change.consume()
                    scope.launch { offset.snapTo((offset.value + drag).coerceIn(0f, maxOffset)) }
                }
            }
            .onSizeChanged { trackWidth = it.width },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            stringResource(R.string.challenge_slide),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
fun MathChallenge(operators: Int, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    var problem by remember { mutableStateOf(MathProblem.generate(operators)) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val check = {
        if (input.trim().toIntOrNull() == problem.answer) onSolved()
        else { wrong = true; problem = MathProblem.generate(operators); input = "" }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.challenge_math), style = MaterialTheme.typography.titleMedium)
        Text(problem.text, fontSize = 36.sp, style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.filter { c -> c.isDigit() || c == '-' }.take(6); wrong = false },
            isError = wrong,
            supportingText = if (wrong) ({ Text(stringResource(R.string.challenge_math_wrong)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { check() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = check, enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.challenge_confirm))
        }
    }
}

@Composable
fun ShakeChallenge(count: Int, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var shakes by remember { mutableIntStateOf(0) }
    val done = rememberUpdatedState(onSolved)

    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                val g = sqrt(x * x + y * y + z * z)
                val now = System.currentTimeMillis()
                if (g > SHAKE_THRESHOLD && now - last > 300) {
                    last = now
                    shakes++
                    if (shakes >= count) done.value()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensor?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        onDispose { manager.unregisterListener(listener) }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.challenge_shake), style = MaterialTheme.typography.titleMedium)
        LcdText("$shakes / $count", 56.sp, color = MaterialTheme.colorScheme.primary)
        LinearProgressIndicator(progress = { shakes / count.toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp))
    }
}

private const val SHAKE_THRESHOLD = 18f // m/s², a firm shake (gravity alone is ~9.8)

/**
 * Scan the saved QR code to stop the alarm. After [emergencyAfterMillis] of ringing a "can't scan" button appears,
 * so a broken camera or a lost code can never leave the alarm ringing forever.
 */
@Composable
fun QrChallenge(
    expected: String,
    ringingSinceMillis: Long,
    onSolved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var emergency by remember { mutableStateOf(System.currentTimeMillis() - ringingSinceMillis >= QR_EMERGENCY_MILLIS) }
    var wrong by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!emergency) {
            kotlinx.coroutines.delay((QR_EMERGENCY_MILLIS - (System.currentTimeMillis() - ringingSinceMillis)).coerceAtLeast(0))
            emergency = true
        }
    }
    LaunchedEffect(granted) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.challenge_qr), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (granted) {
            QrScanner(
                onCode = { if (it == expected) onSolved() else wrong = true },
                modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(24.dp)),
            )
            if (wrong) Text(stringResource(R.string.challenge_qr_wrong), color = MaterialTheme.colorScheme.error)
        } else {
            Text(stringResource(R.string.challenge_qr_no_camera), textAlign = TextAlign.Center)
            OutlinedButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.challenge_qr_grant)) }
        }
        if (emergency || !granted) {
            OutlinedButton(onClick = onSolved, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.challenge_qr_cant_scan))
            }
        }
    }
}

const val QR_EMERGENCY_MILLIS = 5 * 60_000L
