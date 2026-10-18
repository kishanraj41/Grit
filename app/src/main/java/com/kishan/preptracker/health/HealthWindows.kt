package com.kishan.preptracker.health

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Calendar-day and overnight windows used when folding Health Connect records into a [HealthDay].
 * Night bounds match the original per-day filters: 18:00 the previous day to 14:00 today.
 */
object HealthWindows {
    val NIGHT_START: LocalTime = LocalTime.of(18, 0)
    val NIGHT_END: LocalTime = LocalTime.of(14, 0)

    fun dayStart(date: LocalDate, zone: ZoneId): Instant = date.atStartOfDay(zone).toInstant()
    fun nightStart(date: LocalDate, zone: ZoneId): Instant =
        date.minusDays(1).atTime(NIGHT_START).atZone(zone).toInstant()
    fun nightEnd(date: LocalDate, zone: ZoneId): Instant =
        date.atTime(NIGHT_END).atZone(zone).toInstant()

    fun inNight(time: Instant, date: LocalDate, zone: ZoneId): Boolean {
        val start = nightStart(date, zone)
        val end = nightEnd(date, zone)
        return !time.isBefore(start) && time.isBefore(end)
    }

    fun overlapSeconds(aStart: Instant, aEnd: Instant, bStart: Instant, bEnd: Instant): Long {
        val start = if (aStart.isAfter(bStart)) aStart else bStart
        val end = if (aEnd.isBefore(bEnd)) aEnd else bEnd
        val seconds = Duration.between(start, end).seconds
        return if (seconds > 0) seconds else 0
    }
}
