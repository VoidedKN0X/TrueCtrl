package com.vknxworks.truectrl.ui.protection

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.data.protection.LastRun
import com.vknxworks.truectrl.data.protection.Schedule
import com.vknxworks.truectrl.data.storage.Cron
import com.vknxworks.truectrl.ui.components.relativeTime
import java.text.DateFormat
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale

/** A schedule in words when it's a common one ("Every day at 00:00"), otherwise as cron fields. */
@Composable
fun scheduleText(s: Schedule?): String {
    if (s == null) return stringResource(R.string.schedule_none)
    val minute = s.minute.toIntOrNull()
    val hour = s.hour.toIntOrNull()
    val allDays = s.dom == "*" && s.month == "*"
    val time = if (minute != null && hour != null) String.format(Locale.getDefault(), "%02d:%02d", hour, minute) else null
    return when {
        minute != null && s.hour == "*" && allDays && s.dow == "*" -> stringResource(R.string.schedule_hourly, minute)
        time != null && allDays && s.dow == "*" -> stringResource(R.string.schedule_daily, time)
        time != null && allDays && s.dow.toIntOrNull() != null -> {
            val d = s.dow.toInt().let { if (it == 0 || it == 7) DayOfWeek.SUNDAY else DayOfWeek.of(it.coerceIn(1, 7)) }
            stringResource(R.string.schedule_weekly, d.getDisplayName(TextStyle.FULL, Locale.getDefault()), time)
        }
        time != null && s.dom.toIntOrNull() != null && s.month == "*" && s.dow == "*" -> stringResource(R.string.schedule_monthly, s.dom.toInt(), time)
        else -> stringResource(R.string.schedule_cron, "${s.minute} ${s.hour} ${s.dom} ${s.month} ${s.dow}")
    }
}

/** When the schedule runs next, or null when it can't be worked out. */
fun nextRun(s: Schedule?): Long? = s?.let { Cron(it.minute, it.hour, it.dom, it.month, it.dow).nextAfter(System.currentTimeMillis()) }

@Composable
fun nextRunText(s: Schedule?, enabled: Boolean): String? {
    if (!enabled) return stringResource(R.string.task_disabled)
    val next = nextRun(s) ?: return null
    return stringResource(R.string.task_next, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(next)))
}

/** "Succeeded 2 hours ago", "Failed yesterday: <error>", "Running, 45%". */
@Composable
fun lastRunText(last: LastRun?): String = when {
    last == null || last.state.isBlank() -> stringResource(R.string.task_never_ran)
    last.running -> last.progress?.let { stringResource(R.string.task_running_pct, it.toInt()) } ?: stringResource(R.string.task_running)
    last.succeeded -> last.atMs?.let { stringResource(R.string.task_ok_at, relativeTime(it)) } ?: stringResource(R.string.task_ok)
    last.failed -> listOfNotNull(
        last.atMs?.let { stringResource(R.string.task_failed_at, relativeTime(it)) } ?: stringResource(R.string.task_failed),
        last.error,
    ).joinToString(": ")
    else -> listOfNotNull(last.state.lowercase().replaceFirstChar { it.uppercase() }, last.atMs?.let { relativeTime(it) }).joinToString("  ·  ")
}
