package com.vknxworks.truectrl.data.storage

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The cron schedules TrueNAS uses for its tasks (minute, hour, day of month, month, day of week),
 * with `*`, `*`/n, lists (`1,15`) and ranges (`1-5`). Like cron, when both day of month and day of
 * week are restricted, a day matching either one runs. TrueNAS runs schedules in its own time zone;
 * the phone's zone is used here, which matches for a NAS in the same place.
 */
class Cron(minute: String, hour: String, dom: String, month: String, dow: String) {
    private val minutes = parse(minute, 0, 59)
    private val hours = parse(hour, 0, 23)
    private val doms = parse(dom, 1, 31)
    private val months = parse(month, 1, 12)
    // 0 and 7 are both Sunday.
    private val dows = parse(dow, 0, 7)?.map { if (it == 7) 0 else it }?.toSet()
    private val domAny = dom.trim() == "*"
    private val dowAny = dow.trim() == "*"

    /** The first run at or after [from] (ms), within about 14 months; null when the schedule never runs. */
    fun nextAfter(from: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val hourList = hours?.sorted() ?: return null
        val minuteList = minutes?.sorted() ?: return null
        val start = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(from), zone)
        var day: LocalDate = start.toLocalDate()
        repeat(430) {
            if (dayMatches(day)) {
                for (h in hourList) for (m in minuteList) {
                    val t = day.atTime(h, m)
                    if (!t.isBefore(start)) return t.atZone(zone).toInstant().toEpochMilli()
                }
            }
            day = day.plusDays(1)
        }
        return null
    }

    private fun dayMatches(d: LocalDate): Boolean {
        if (months?.contains(d.monthValue) != true) return false
        val domOk = doms?.contains(d.dayOfMonth) == true
        val dowValue = if (d.dayOfWeek == DayOfWeek.SUNDAY) 0 else d.dayOfWeek.value
        val dowOk = dows?.contains(dowValue) == true
        return when {
            domAny && dowAny -> true
            domAny -> dowOk
            dowAny -> domOk
            else -> domOk || dowOk
        }
    }

    private companion object {
        private val NAMES = mapOf(
            "sun" to 0, "mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6,
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
            "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
        )

        private fun value(s: String): Int? = s.trim().lowercase().let { NAMES[it.take(3)] ?: it.toIntOrNull() }

        /** The set of values a field allows; null when it can't be read. */
        fun parse(field: String, min: Int, max: Int): Set<Int>? {
            val out = mutableSetOf<Int>()
            for (part in field.split(',')) {
                val p = part.trim()
                if (p.isEmpty()) return null
                val (range, step) = p.split('/').let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 1) }
                if (step <= 0) return null
                val (lo, hi) = when {
                    range == "*" -> min to max
                    range.contains('-') -> (value(range.substringBefore('-')) ?: return null) to (value(range.substringAfter('-')) ?: return null)
                    else -> (value(range) ?: return null).let { v -> v to (if (p.contains('/')) max else v) }
                }
                if (lo > hi) return null
                var v = lo
                while (v <= hi) {
                    if (v in min..max) out += v
                    v += step
                }
            }
            return out.takeIf { it.isNotEmpty() }
        }
    }
}
