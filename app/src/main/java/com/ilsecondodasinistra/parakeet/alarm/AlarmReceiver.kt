package com.ilsecondodasinistra.parakeet.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fired by AlarmManager at the wake-up time: starts the ringing foreground service. A real wake-up (not a
 * snooze, not the test) marks its date as done and arms the next occurrence of the weekly plan.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var sync: AlarmSync

    override fun onReceive(context: Context, intent: Intent) {
        ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java))
        val snooze = intent.getBooleanExtra(InternalAlarmScheduler.EXTRA_SNOOZE, false)
        val test = intent.getBooleanExtra(InternalAlarmScheduler.EXTRA_TEST, false)
        if (snooze || test) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { sync.onRang() } finally { pending.finish() }
        }
    }
}
