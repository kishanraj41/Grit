package com.kishan.preptracker.domain

import java.time.DayOfWeek
import java.time.LocalDate

data class DayMark(val date: LocalDate, val trained: Boolean, val isToday: Boolean)

data class GymPulse(
    val week: List<DayMark>,
    val sessionsThisWeek: Int,
    val setsThisWeek: Int,
    val streakWeeks: Int,
    val monthSessions: Int,
    val weekDeltaKg: Double?,
)

object Attendance {
    fun trained(date: LocalDate, sessions: Map<LocalDate, SessionData>, days: Map<LocalDate, DayLogModel>): Boolean {
        val s = sessions[date]
        if (s != null && s.sets.values.any { rows -> rows.any { it.done && !it.isWarmup() } }) return true
        return days[date]?.gymAt != null
    }

    fun startOfWeek(today: LocalDate, weekStart: String): LocalDate {
        val first = if (weekStart == "sun") DayOfWeek.SUNDAY else DayOfWeek.MONDAY
        val back = (today.dayOfWeek.value - first.value + 7) % 7
        return today.minusDays(back.toLong())
    }

    fun pulse(
        today: LocalDate,
        sessions: Map<LocalDate, SessionData>,
        days: Map<LocalDate, DayLogModel>,
        weekStart: String = "mon",
    ): GymPulse {
        val start = startOfWeek(today, weekStart)
        val week = (0..6).map { i ->
            val d = start.plusDays(i.toLong())
            DayMark(d, trained(d, sessions, days), d == today)
        }
        val sessionsThisWeek = sessions.keys.count { !it.isBefore(start) && !it.isAfter(today) && trained(it, sessions, days) }
        val setsThisWeek = sessions.filterKeys { !it.isBefore(start) && !it.isAfter(today) }
            .values.sumOf { s -> s.sets.values.sumOf { rows -> rows.count { it.done && !it.isWarmup() } } }
        val monthStart = today.withDayOfMonth(1)
        val monthSessions = sessions.keys.count { !it.isBefore(monthStart) && !it.isAfter(today) && trained(it, sessions, days) }
        val weekAgo = days[today.minusDays(7)]?.weight
        val nowW = days[today]?.weight
        val weekDeltaKg = if (weekAgo != null && nowW != null) nowW - weekAgo else null
        return GymPulse(week, sessionsThisWeek, setsThisWeek, streakWeeks(today, sessions, days, weekStart), monthSessions, weekDeltaKg)
    }

    fun streakWeeks(
        today: LocalDate,
        sessions: Map<LocalDate, SessionData>,
        days: Map<LocalDate, DayLogModel>,
        weekStart: String = "mon",
    ): Int {
        var cursor = startOfWeek(today, weekStart)
        val thisWeekHit = (0..6).map { cursor.plusDays(it.toLong()) }.any { !it.isAfter(today) && trained(it, sessions, days) }
        if (!thisWeekHit) cursor = cursor.minusWeeks(1)
        var n = 0
        while (n < 520) {
            val hit = (0..6).map { cursor.plusDays(it.toLong()) }.any { !it.isAfter(today) && trained(it, sessions, days) }
            if (!hit) break
            n++
            cursor = cursor.minusWeeks(1)
        }
        return n
    }
}
