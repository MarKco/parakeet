package com.ilsecondodasinistra.parakeet.alarm

import android.app.AlarmManager
import android.os.Build

/**
 * Whether exact alarms may be set. [AlarmManager.canScheduleExactAlarms] exists from API 31; before that an
 * app that holds the alarm permissions can always schedule them.
 */
fun AlarmManager.canScheduleExact(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()
