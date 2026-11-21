package com.ilsecondodasinistra.parakeet.sync

import com.ilsecondodasinistra.parakeet.data.Challenge
import com.ilsecondodasinistra.parakeet.data.PlanMode
import com.ilsecondodasinistra.parakeet.data.PlanPrefs
import com.ilsecondodasinistra.parakeet.data.PostWakePrefs
import com.ilsecondodasinistra.parakeet.data.RingingPrefs
import com.ilsecondodasinistra.parakeet.data.Settings
import com.ilsecondodasinistra.parakeet.data.SettingsRepository

/**
 * The settings every device shares: how the plan works, how the alarm rings (not the ringtone: its address is
 * the phone's), the reminders and "After the alarm". Everything else stays on the phone (see docs/sync.md).
 * Values travel as text; a setting that never changed has no entry.
 */
object SharedSettings {
    private const val PLAN_MODE = "plan_mode"
    private const val EXIT_TEXT = "exit_text"
    private const val VIBRATE = "vibrate"
    private const val SNOOZE = "snooze_minutes"
    private const val CHALLENGE = "challenge"
    private const val MATH_OPS = "math_ops"
    private const val SHAKE_COUNT = "shake_count"
    private const val QR_VALUE = "qr_value"
    private const val PRE_ALARM_NOTIFY = "pre_alarm_notify"
    private const val PRE_ALARM_MINUTES = "pre_alarm_minutes"
    private const val EVENING_SUMMARY = "evening_summary"
    private const val EVENING_MINUTE = "evening_minute"
    private const val PW_ENABLED = "postwake_enabled"
    private const val PW_RING = "postwake_ring"
    private const val PW_NOTIFY = "postwake_notify"
    private const val PW_LOCK = "postwake_lock_screen"

    private val ringing = RingingPrefs()
    private val plan = PlanPrefs()
    private val postWake = PostWakePrefs()

    /** What each setting is until someone changes it. */
    val defaults: Map<String, String> = mapOf(
        PLAN_MODE to PlanMode.SIMPLE.name,
        EXIT_TEXT to "",
        VIBRATE to ringing.vibrate.toString(),
        SNOOZE to ringing.snoozeMinutes.toString(),
        CHALLENGE to ringing.challenge.name,
        MATH_OPS to ringing.mathOps.toString(),
        SHAKE_COUNT to ringing.shakeCount.toString(),
        QR_VALUE to ringing.qrValue,
        PRE_ALARM_NOTIFY to plan.preAlarmNotify.toString(),
        PRE_ALARM_MINUTES to plan.preAlarmMinutes.toString(),
        EVENING_SUMMARY to plan.eveningSummary.toString(),
        EVENING_MINUTE to plan.eveningMinute.toString(),
        PW_ENABLED to postWake.enabled.toString(),
        PW_RING to postWake.ring.toString(),
        PW_NOTIFY to postWake.notify.toString(),
        PW_LOCK to postWake.lockScreen.toString(),
    )

    val keys: Set<String> get() = defaults.keys

    /** The current value of every shared setting. */
    fun values(s: Settings): Map<String, String> = mapOf(
        PLAN_MODE to s.planMode.name,
        EXIT_TEXT to s.exitText,
        VIBRATE to s.ringing.vibrate.toString(),
        SNOOZE to s.ringing.snoozeMinutes.toString(),
        CHALLENGE to s.ringing.challenge.name,
        MATH_OPS to s.ringing.mathOps.toString(),
        SHAKE_COUNT to s.ringing.shakeCount.toString(),
        QR_VALUE to s.ringing.qrValue,
        PRE_ALARM_NOTIFY to s.plan.preAlarmNotify.toString(),
        PRE_ALARM_MINUTES to s.plan.preAlarmMinutes.toString(),
        EVENING_SUMMARY to s.plan.eveningSummary.toString(),
        EVENING_MINUTE to s.plan.eveningMinute.toString(),
        PW_ENABLED to s.postWake.enabled.toString(),
        PW_RING to s.postWake.ring.toString(),
        PW_NOTIFY to s.postWake.notify.toString(),
        PW_LOCK to s.postWake.lockScreen.toString(),
    )

    /** Writes the settings in [v] (unknown keys and unreadable values are ignored). */
    suspend fun apply(repo: SettingsRepository, v: Map<String, String>) {
        v[PLAN_MODE]?.let { m -> PlanMode.entries.firstOrNull { it.name == m }?.let { repo.setPlanMode(it) } }
        v[EXIT_TEXT]?.let { repo.setExitText(it) }
        if (v.keys.any { it in RINGING_KEYS }) repo.updateRinging { r ->
            r.copy(
                vibrate = v[VIBRATE]?.toBooleanStrictOrNull() ?: r.vibrate,
                snoozeMinutes = v[SNOOZE]?.toIntOrNull() ?: r.snoozeMinutes,
                challenge = v[CHALLENGE]?.let { c -> Challenge.entries.firstOrNull { it.name == c } } ?: r.challenge,
                mathOps = v[MATH_OPS]?.toIntOrNull() ?: r.mathOps,
                shakeCount = v[SHAKE_COUNT]?.toIntOrNull() ?: r.shakeCount,
                qrValue = v[QR_VALUE] ?: r.qrValue,
            )
        }
        v[PRE_ALARM_NOTIFY]?.toBooleanStrictOrNull()?.let { repo.setPreAlarmNotify(it) }
        v[PRE_ALARM_MINUTES]?.toIntOrNull()?.let { repo.setPreAlarmMinutes(it) }
        v[EVENING_SUMMARY]?.toBooleanStrictOrNull()?.let { repo.setEveningSummary(it) }
        v[EVENING_MINUTE]?.toIntOrNull()?.let { repo.setEveningMinute(it) }
        if (v.keys.any { it in POST_WAKE_KEYS }) repo.updatePostWake { p ->
            p.copy(
                enabled = v[PW_ENABLED]?.toBooleanStrictOrNull() ?: p.enabled,
                ring = v[PW_RING]?.toBooleanStrictOrNull() ?: p.ring,
                notify = v[PW_NOTIFY]?.toBooleanStrictOrNull() ?: p.notify,
                lockScreen = v[PW_LOCK]?.toBooleanStrictOrNull() ?: p.lockScreen,
            )
        }
    }

    private val RINGING_KEYS = setOf(VIBRATE, SNOOZE, CHALLENGE, MATH_OPS, SHAKE_COUNT, QR_VALUE)
    private val POST_WAKE_KEYS = setOf(PW_ENABLED, PW_RING, PW_NOTIFY, PW_LOCK)
}
