package com.ilsecondodasinistra.parakeet.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ilsecondodasinistra.parakeet.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-app alarm: an `AlarmManager.setAlarmClock()` alarm (exact, survives Doze, shown in the system status
 * area) that wakes [AlarmReceiver], which starts the ringing [AlarmService].
 */
@Singleton
class InternalAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlarmScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override suspend fun set(wakeAt: LocalDateTime): Boolean =
        setAt(wakeAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())

    /** Arms the alarm for [atMillis]; used for the wake-up time, for snooze and for the "test alarm". */
    fun setAt(atMillis: Long, snooze: Boolean = false, test: Boolean = false): Boolean {
        if (!alarmManager.canScheduleExactAlarms()) return false
        val showApp = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Wake-up, snooze and test use separate slots: re-arming the next wake-up never cancels a snooze
        val requestCode = when {
            test -> TEST_REQUEST_CODE
            snooze -> SNOOZE_REQUEST_CODE
            else -> REQUEST_CODE
        }
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, showApp), ringIntent(snooze, test, requestCode))
        return true
    }

    override suspend fun cancel(): Boolean {
        alarmManager.cancel(ringIntent(false, false, REQUEST_CODE))
        return true
    }

    private fun ringIntent(snooze: Boolean, test: Boolean, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_SNOOZE, snooze).putExtra(EXTRA_TEST, test),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val EXTRA_SNOOZE = "snooze"
        const val EXTRA_TEST = "test"
        private const val REQUEST_CODE = 2
        private const val TEST_REQUEST_CODE = 3
        private const val SNOOZE_REQUEST_CODE = 4
    }
}
