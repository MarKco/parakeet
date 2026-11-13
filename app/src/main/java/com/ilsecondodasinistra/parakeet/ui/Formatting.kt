package com.ilsecondodasinistra.parakeet.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ilsecondodasinistra.parakeet.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("H:mm")

fun LocalTime.lcd(): String = format(timeFormatter)
fun LocalDateTime.lcd(): String = format(timeFormatter)

@Composable
fun formatDuration(minutes: Int): String =
    if (minutes >= 60) stringResource(R.string.duration_hm, minutes / 60, minutes % 60)
    else stringResource(R.string.duration_m, minutes)

/** "Today" / "Tomorrow" / weekday, followed by the short date, e.g. "Tomorrow, Fri 14 Nov". */
@Composable
fun dayLabel(moment: LocalDateTime, today: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
    val prefix = when (moment.toLocalDate()) {
        today -> stringResource(R.string.day_today)
        today.plusDays(1) -> stringResource(R.string.day_tomorrow)
        else -> null
    }
    val date = moment.format(formatter)
    return if (prefix != null) "$prefix, $date" else date
}
