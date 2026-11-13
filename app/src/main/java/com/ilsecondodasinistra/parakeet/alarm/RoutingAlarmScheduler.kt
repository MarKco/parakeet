package com.ilsecondodasinistra.parakeet.alarm

import com.ilsecondodasinistra.parakeet.data.AlarmMode
import com.ilsecondodasinistra.parakeet.data.SettingsRepository
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Picks the Clock-app or the in-app alarm according to the user's setting. */
@Singleton
class RoutingAlarmScheduler @Inject constructor(
    private val settings: SettingsRepository,
    private val clock: ClockAppScheduler,
    private val internal: InternalAlarmScheduler,
) : AlarmScheduler {

    override suspend fun set(wakeAt: LocalDateTime): Boolean = when (settings.current().alarmMode) {
        AlarmMode.CLOCK -> clock.set(wakeAt)
        AlarmMode.INTERNAL -> internal.set(wakeAt)
    }

    override suspend fun cancel(): Boolean {
        internal.cancel()
        return if (settings.current().alarmMode == AlarmMode.CLOCK) clock.cancel() else true
    }
}
