package com.ilsecondodasinistra.parakeet.ui.alarm

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilsecondodasinistra.parakeet.R
import com.ilsecondodasinistra.parakeet.alarm.AlarmService
import com.ilsecondodasinistra.parakeet.alarm.AlarmState
import com.ilsecondodasinistra.parakeet.data.Challenge
import com.ilsecondodasinistra.parakeet.data.RingingPrefs
import com.ilsecondodasinistra.parakeet.data.SettingsRepository
import com.ilsecondodasinistra.parakeet.ui.LcdText
import com.ilsecondodasinistra.parakeet.ui.theme.ParakeetDarker
import com.ilsecondodasinistra.parakeet.ui.theme.ParakeetLime
import com.ilsecondodasinistra.parakeet.ui.theme.ParakeetTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Full-screen ringing screen, shown over the lock screen while [AlarmService] plays. */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            ParakeetTheme(darkTheme = true) {
                val ringing by AlarmState.ringing.collectAsStateWithLifecycle()
                val prefs by settings.settings.map { it.ringing }
                    .collectAsStateWithLifecycle(RingingPrefs())

                // The service sets the state right after starting; if it never does, there is nothing to show
                var seen by remember { mutableStateOf(false) }
                LaunchedEffect(ringing) {
                    if (ringing != null) seen = true
                    else {
                        delay(if (seen) 0 else 1500)
                        if (AlarmState.ringing.value == null) finish()
                    }
                }
                BackHandler { /* the alarm can only be stopped from the screen */ }

                ringing?.let { ring ->
                    AlarmScreen(
                        prefs = prefs,
                        ringingSince = ring.startedAtMillis,
                        onDismiss = { AlarmService.stop(this) },
                        onSnooze = { AlarmService.snooze(this) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmScreen(prefs: RingingPrefs, ringingSince: Long, onDismiss: () -> Unit, onSnooze: () -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(1000) } }
    // An unset QR code can't be matched: behave as the simple slide
    val challenge = if (prefs.challenge == Challenge.QR && prefs.qrValue.isEmpty()) Challenge.SLIDE else prefs.challenge

    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = androidx.compose.ui.graphics.Color(0xFF080A06),
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.alarm_ring_title), style = MaterialTheme.typography.titleLarge, color = ParakeetLime)
            LcdText(now.format(DateTimeFormatter.ofPattern("H:mm")), 120.sp, color = ParakeetLime)
            Spacer(Modifier.height(32.dp))
            when (challenge) {
                Challenge.SLIDE -> SlideChallenge(onSolved = onDismiss)
                Challenge.MATH -> MathChallenge(prefs.mathOps, onSolved = onDismiss)
                Challenge.SHAKE -> ShakeChallenge(prefs.shakeCount, onSolved = onDismiss)
                Challenge.QR -> QrChallenge(prefs.qrValue, ringingSince, onSolved = onDismiss)
            }
            if (prefs.snoozeMinutes > 0) {
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.alarm_snooze, prefs.snoozeMinutes))
                }
            }
        }
    }
    }
}
