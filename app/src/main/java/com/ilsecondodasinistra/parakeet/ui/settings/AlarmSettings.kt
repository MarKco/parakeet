package com.ilsecondodasinistra.parakeet.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ilsecondodasinistra.parakeet.R
import com.ilsecondodasinistra.parakeet.alarm.Notifications
import com.ilsecondodasinistra.parakeet.data.AlarmMode
import com.ilsecondodasinistra.parakeet.data.Challenge
import com.ilsecondodasinistra.parakeet.ui.ChoiceCard
import com.ilsecondodasinistra.parakeet.ui.alarm.QrScanner
import com.ilsecondodasinistra.parakeet.ui.main.MainUiState
import com.ilsecondodasinistra.parakeet.ui.main.MainViewModel

/** Settings of the wake-up alarm: Clock app or Parakeet, and everything about how Parakeet rings. */
@Composable
fun AlarmSection(state: MainUiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    var exactAllowed by remember { mutableStateOf(viewModel.canScheduleExactAlarms()) }
    var wantsInternal by remember { mutableStateOf(false) }
    var fullScreenAllowed by remember { mutableStateOf(canUseFullScreen(context)) }
    var showScanner by remember { mutableStateOf(false) }
    var askedNotifications by remember { mutableStateOf(false) }
    var notificationLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>? = null

    var notificationsOn by remember { mutableStateOf(Notifications.canPost(context)) }

    // The in-app alarm needs notifications first (that's how it appears), then "Alarms & reminders"
    fun tryInternal() {
        notificationsOn = Notifications.canPost(context)
        exactAllowed = viewModel.canScheduleExactAlarms()
        when {
            !notificationsOn -> {
                wantsInternal = true
                if (Build.VERSION.SDK_INT >= 33 && !askedNotifications) {
                    askedNotifications = true
                    notificationLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            }
            !exactAllowed -> {
                wantsInternal = true
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            }
            else -> { wantsInternal = false; viewModel.setAlarmMode(AlarmMode.INTERNAL) }
        }
    }
    notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = Notifications.canPost(context)
        if (granted) tryInternal()
    }

    // Back from the system notification / "Alarms & reminders" / full-screen screens
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        exactAllowed = viewModel.canScheduleExactAlarms()
        notificationsOn = Notifications.canPost(context)
        fullScreenAllowed = canUseFullScreen(context)
        if (wantsInternal && viewModel.canUseInternalAlarm()) { viewModel.setAlarmMode(AlarmMode.INTERNAL); wantsInternal = false }
    }

    Text(stringResource(R.string.settings_alarm_section), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))

    ChoiceCard(
        selected = state.alarmMode == AlarmMode.INTERNAL,
        title = stringResource(R.string.mode_internal),
        description = stringResource(R.string.mode_internal_desc),
        onClick = ::tryInternal,
    )
    ChoiceCard(
        selected = state.alarmMode == AlarmMode.CLOCK,
        title = stringResource(R.string.mode_clock),
        description = stringResource(R.string.mode_clock_desc),
        onClick = { wantsInternal = false; viewModel.setAlarmMode(AlarmMode.CLOCK) },
    )
    if (wantsInternal && state.alarmMode != AlarmMode.INTERNAL) {
        Text(
            stringResource(if (!notificationsOn) R.string.mode_needs_notifications else R.string.mode_needs_permission),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    if (state.alarmMode != AlarmMode.INTERNAL) return

    val prefs = state.ringing

    // Sound
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri: Uri? = result.data?.let {
                if (Build.VERSION.SDK_INT >= 33) it.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                else @Suppress("DEPRECATION") it.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            viewModel.updateRinging { it.copy(ringtone = uri?.toString().orEmpty()) }
        }
    }
    val ringtoneName = remember(prefs.ringtone) {
        runCatching {
            val uri = prefs.ringtone.takeIf { it.isNotEmpty() }?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)
        }.getOrNull()
    }
    Card {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = false, role = Role.Button, onValueChange = {
                    picker.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            if (prefs.ringtone.isNotEmpty()) putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(prefs.ringtone))
                        },
                    )
                })
                .heightIn(min = 64.dp)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ringtone_title), style = MaterialTheme.typography.titleMedium)
                Text(ringtoneName ?: stringResource(R.string.ringtone_default), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // Vibration
    Card {
        Row(
            Modifier
                .toggleable(value = prefs.vibrate, role = Role.Switch, onValueChange = { v -> viewModel.updateRinging { it.copy(vibrate = v) } })
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.vibrate_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Switch(checked = prefs.vibrate, onCheckedChange = null)
        }
    }

    // Snooze
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.snooze_title), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 5, 10, 15).forEach { m ->
                    FilterChip(
                        selected = prefs.snoozeMinutes == m,
                        onClick = { viewModel.updateRinging { it.copy(snoozeMinutes = m) } },
                        label = { Text(if (m == 0) stringResource(R.string.snooze_off) else stringResource(R.string.snooze_minutes, m)) },
                        modifier = Modifier.heightIn(min = 40.dp),
                    )
                }
            }
        }
    }

    // How to stop the alarm
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.challenge_title), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Challenge.SLIDE to R.string.challenge_opt_slide,
                    Challenge.MATH to R.string.challenge_opt_math,
                    Challenge.SHAKE to R.string.challenge_opt_shake,
                    Challenge.QR to R.string.challenge_opt_qr,
                ).forEach { (c, label) ->
                    FilterChip(
                        selected = prefs.challenge == c,
                        onClick = { viewModel.updateRinging { it.copy(challenge = c) } },
                        label = { Text(stringResource(label)) },
                        modifier = Modifier.heightIn(min = 40.dp),
                    )
                }
            }
            when (prefs.challenge) {
                Challenge.MATH -> {
                    Text(stringResource(R.string.math_ops_title, prefs.mathOps), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = prefs.mathOps.toFloat(),
                        onValueChange = { v -> viewModel.updateRinging { it.copy(mathOps = v.toInt()) } },
                        valueRange = 2f..5f, steps = 2,
                    )
                }
                Challenge.SHAKE -> {
                    Text(stringResource(R.string.shake_count_title, prefs.shakeCount), style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = prefs.shakeCount.toFloat(),
                        onValueChange = { v -> viewModel.updateRinging { it.copy(shakeCount = (v / 5).toInt() * 5) } },
                        valueRange = 10f..50f, steps = 7,
                    )
                }
                Challenge.QR -> {
                    Text(
                        stringResource(if (prefs.qrValue.isEmpty()) R.string.qr_not_set else R.string.qr_saved),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = { showScanner = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(if (prefs.qrValue.isEmpty()) R.string.qr_scan else R.string.qr_replace))
                    }
                }
                Challenge.SLIDE -> Unit
            }
        }
    }

    // Lock-screen display (Android 14+: can be revoked by the user)
    if (Build.VERSION.SDK_INT >= 34) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.fsi_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (fullScreenAllowed) R.string.fsi_granted else R.string.fsi_denied),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!fullScreenAllowed) {
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                        },
                        modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.mode_allow)) }
                }
            }
        }
    }

    OutlinedButton(
        onClick = {
            val ok = viewModel.testAlarm()
            Toast.makeText(
                context,
                if (ok) R.string.alarm_test_started else R.string.mode_needs_permission,
                Toast.LENGTH_SHORT,
            ).show()
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text(stringResource(R.string.alarm_test)) }
    Spacer(Modifier.height(4.dp))

    if (showScanner) QrSetupDialog(
        onCode = { code -> viewModel.updateRinging { it.copy(qrValue = code) }; showScanner = false },
        onDismiss = { showScanner = false },
    )
}

@Composable
private fun QrSetupDialog(onCode: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qr_scan)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.qr_scan_hint), style = MaterialTheme.typography.bodyMedium)
                if (granted) {
                    QrScanner(
                        onCode = onCode,
                        modifier = Modifier.fillMaxWidth().height(260.dp).clip(MaterialTheme.shapes.large),
                    )
                } else {
                    Text(stringResource(R.string.challenge_qr_no_camera))
                    OutlinedButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.challenge_qr_grant)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun canUseFullScreen(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) { content() }
}
