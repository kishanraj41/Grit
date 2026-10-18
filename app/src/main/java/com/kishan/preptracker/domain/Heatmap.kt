package com.kishan.preptracker.domain

import java.time.DayOfWeek
import java.time.LocalDate

data class HeatCell(val date: LocalDate, val sets: Int)

object Heatmap {
    fun cells(
        sessions: Map<LocalDate, SessionData>,
        weeks: Int = 20,
        today: LocalDate = LocalDate.now(),
        weekStart: String = "mon",
    ): List<HeatCell> {
        val first = if (weekStart == "sun") DayOfWeek.SUNDAY else DayOfWeek.MONDAY
        val anchor = today.minusWeeks(weeks.toLong() - 1)
        val back = (anchor.dayOfWeek.value - first.value + 7) % 7
        val start = anchor.minusDays(back.toLong())
        val out = mutableListOf<HeatCell>()
        var d = start
        while (!d.isAfter(today)) {
            val s = sessions[d]
            val n = s?.sets?.values?.sumOf { list -> list.count { it.done && !it.isWarmup() } } ?: 0
            out += HeatCell(d, n)
            d = d.plusDays(1)
        }
        return out
    }

    fun level(sets: Int): Int = when {
        sets <= 0 -> 0
        sets < 8 -> 1
        sets < 16 -> 2
        sets < 24 -> 3
        else -> 4
    }
}
