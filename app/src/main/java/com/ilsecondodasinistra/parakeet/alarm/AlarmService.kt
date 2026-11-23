package com.ilsecondodasinistra.parakeet.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.ilsecondodasinistra.parakeet.R
import com.ilsecondodasinistra.parakeet.data.SettingsRepository
import com.ilsecondodasinistra.parakeet.ui.alarm.AlarmActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What [AlarmActivity] needs to know about the current ring. */
data class Ringing(val startedAtMillis: Long)

object AlarmState {
    private val _ringing = MutableStateFlow<Ringing?>(null)
    val ringing: StateFlow<Ringing?> = _ringing.asStateFlow()
    internal fun set(value: Ringing?) { _ringing.value = value }
}

/**
 * Plays the alarm: foreground service (type systemExempted, allowed for exact-alarm apps) with a
 * full-screen notification, looping alarm-stream sound with rising volume, vibration and a wake lock.
 */
@AndroidEntryPoint
class AlarmService : Service() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var scheduler: InternalAlarmScheduler
    @Inject lateinit var postWake: PostWake

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var rampJob: Job? = null
    private var autoStop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // Only the user's Stop starts the morning guide: not a snooze, not the 10-minute auto-stop
                stopRinging(); postWake.beginAsync(); return START_NOT_STICKY
            }
            ACTION_SNOOZE -> { snooze(); return START_NOT_STICKY }
        }
        if (AlarmState.ringing.value != null) return START_NOT_STICKY // already ringing
        startRinging()
        return START_NOT_STICKY
    }

    private fun startRinging() {
        val now = System.currentTimeMillis()
        val full = Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val fullPi = PendingIntent.getActivity(this, 0, full, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(NOTIFICATION_ID, ringingNotification(fullPi, snoozeMinutes = 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)

        AlarmState.set(Ringing(now))
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Parakeet:alarm").apply { acquire(MAX_RING_MILLIS + 60_000) }

        scope.launch {
            val prefs = settings.current().ringing
            playSound(prefs.ringtone)
            if (prefs.vibrate) vibrate()
            // Now that the snooze length is known, add the "Snooze" button (same notification, no new alert)
            if (prefs.snoozeMinutes > 0 && Notifications.canPost(this@AlarmService)) {
                @Suppress("MissingPermission") // checked by Notifications.canPost
                androidx.core.app.NotificationManagerCompat.from(this@AlarmService)
                    .notify(NOTIFICATION_ID, ringingNotification(fullPi, prefs.snoozeMinutes))
            }
        }
        autoStop = scope.launch { delay(MAX_RING_MILLIS); stopRinging() }
    }

    /**
     * The ringing notification. FOREGROUND_SERVICE_IMMEDIATE matters: without it Android 12+ holds a
     * foreground-service notification back for 10 s, so the full-screen alarm over the lock screen appeared
     * only after 10 s of ringing (measured on API 37). The buttons also help when the phone is unlocked,
     * where Android shows only a heads-up banner instead of the full screen.
     */
    private fun ringingNotification(fullPi: PendingIntent, snoozeMinutes: Int): Notification {
        val builder = NotificationCompat.Builder(this, Notifications.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_parakeet)
            .setContentTitle(getString(R.string.alarm_ring_title))
            .setContentText(getString(R.string.alarm_ring_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(fullPi)
            .setFullScreenIntent(fullPi, true)
            // "Stop" opens the alarm screen, so the chosen challenge (slide, math, shake, QR) still applies
            .addAction(0, getString(R.string.alarm_action_stop), fullPi)
        if (snoozeMinutes > 0) {
            val snoozePi = PendingIntent.getService(
                this, 1, Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, getString(R.string.alarm_snooze, snoozeMinutes), snoozePi)
        }
        return builder.build()
    }

    private fun playSound(ringtone: String) {
        val uri: Uri = ringtone.takeIf { it.isNotEmpty() }?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(this@AlarmService, uri)
                isLooping = true
                setVolume(START_VOLUME, START_VOLUME)
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Chosen ringtone no longer readable: fall back to the default alarm sound
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(this@AlarmService, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                    isLooping = true
                    prepare()
                    start()
                }
            }.getOrNull()
        }
        rampJob = scope.launch {
            val steps = 60
            for (i in 1..steps) {
                delay(RAMP_MILLIS / steps)
                val v = START_VOLUME + (1f - START_VOLUME) * i / steps
                player?.setVolume(v, v)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
    }

    private fun snooze() {
        scope.launch {
            val minutes = settings.current().ringing.snoozeMinutes
            if (minutes > 0) scheduler.setAt(System.currentTimeMillis() + minutes * 60_000L, snooze = true)
            stopRinging()
        }
    }

    private fun stopRinging() {
        autoStop?.cancel(); rampJob?.cancel()
        runCatching { player?.stop() }
        player?.release(); player = null
        getSystemService(Vibrator::class.java)?.cancel()
        wakeLock?.takeIf { it.isHeld }?.release(); wakeLock = null
        AlarmState.set(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (AlarmState.ringing.value != null) stopRinging()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val ACTION_STOP = "com.ilsecondodasinistra.parakeet.STOP_ALARM"
        private const val ACTION_SNOOZE = "com.ilsecondodasinistra.parakeet.SNOOZE_ALARM"
        private const val MAX_RING_MILLIS = 10 * 60_000L
        private const val RAMP_MILLIS = 30_000L
        private const val START_VOLUME = 0.15f

        fun stop(context: Context) = context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP))
        fun snooze(context: Context) = context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_SNOOZE))
    }
}
