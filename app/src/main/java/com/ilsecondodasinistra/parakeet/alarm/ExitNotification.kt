package com.ilsecondodasinistra.parakeet.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ilsecondodasinistra.parakeet.MainActivity
import com.ilsecondodasinistra.parakeet.R
import com.ilsecondodasinistra.parakeet.data.AlarmMode
import com.ilsecondodasinistra.parakeet.data.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

object Notifications {
    const val CHANNEL_EXIT = "exit"
    const val CHANNEL_ALARM = "alarm"
    const val CHANNEL_UPCOMING = "upcoming"
    const val CHANNEL_POST_WAKE = "postwake"

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_EXIT,
            context.getString(R.string.channel_exit_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_exit_description) }
        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alarm_description)
            setSound(null, null) // the alarm sound is played by AlarmService on the alarm stream
            enableVibration(false)
            setBypassDnd(true)
        }
        val upcoming = NotificationChannel(
            CHANNEL_UPCOMING,
            context.getString(R.string.channel_upcoming_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.channel_upcoming_description) }
        val postWake = NotificationChannel(
            CHANNEL_POST_WAKE,
            context.getString(R.string.channel_postwake_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.channel_postwake_description) }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(channel, alarm, upcoming, postWake))
    }

    fun canPost(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/** Schedules the "time to leave" notification. Exact when the user allowed it, otherwise best effort. */
@Singleton
class ExitNotificationScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = alarmManager.canScheduleExact()

    fun schedule(atMillis: Long) {
        if (atMillis <= System.currentTimeMillis()) return
        val pi = pendingIntent()
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    fun cancel() {
        alarmManager.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ExitAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val REQUEST_CODE = 1
    }
}

@AndroidEntryPoint
class ExitAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var settings: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val current = settings.current()
                if (current.notifyOnExit && Notifications.canPost(context)) {
                    val text = current.exitText.ifBlank { context.getString(R.string.notification_exit_text) }
                    val open = PendingIntent.getActivity(
                        context, 0, Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_EXIT)
                        .setSmallIcon(R.drawable.ic_stat_parakeet)
                        .setContentTitle(context.getString(R.string.notification_exit_title))
                        .setContentText(text)
                        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(open)
                        .build()
                    @Suppress("MissingPermission") // checked by Notifications.canPost
                    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 1
    }
}

/** Re-arms everything after reboot, clock/time-zone changes or when exact-alarm permission changes. */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var scheduler: ExitNotificationScheduler
    @Inject lateinit var alarmSync: AlarmSync
    @Inject lateinit var postWake: PostWake

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val current = settings.current()
                if (current.alarmMode == AlarmMode.INTERNAL) {
                    // Weekly plan: the next occurrence is recomputed from the data, never from a stored moment
                    alarmSync.sync()
                    postWake.refresh() // a morning in progress survives a reboot
                } else {
                    val alarm = current.activeAlarm
                    if (current.notifyOnExit && alarm != null && alarm.exitAtMillis > System.currentTimeMillis()) {
                        scheduler.schedule(alarm.exitAtMillis)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
