package com.ilsecondodasinistra.parakeet.alarm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Sets the wake-up alarm: delegated to the system Clock app or rung by Parakeet itself. */
interface AlarmScheduler {
    /** @return false when no app can handle the request. */
    suspend fun set(wakeAt: LocalDateTime): Boolean
    suspend fun cancel(): Boolean
}

@Singleton
class ClockAppScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlarmScheduler {

    override suspend fun set(wakeAt: LocalDateTime): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, wakeAt.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, wakeAt.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, LABEL)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(intent)
    }

    /** Best effort: asks the Clock app to dismiss alarms labelled "Parakeet". Not every Clock app supports it. */
    override suspend fun cancel(): Boolean {
        val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
            putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_LABEL)
            putExtra(AlarmClock.EXTRA_MESSAGE, LABEL)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(intent)
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    companion object {
        const val LABEL = "Parakeet"
    }
}
