package com.kishan.preptracker.domain

import java.time.DayOfWeek
import java.time.LocalDate

data class WeekPack(
    val id: String,
    val name: String,
    val blurb: String,
    val byWeekday: Map<DayOfWeek, DayPlan>,
    val queue: List<DayPlan>,
)

/**
 * Grit week shapes. v4 is the prep default. The others reuse the same catalog,
 * not a second exercise library.
 */
object Templates {
    private fun p(id: String, s: Int, c: Int) = PlannedExercise(id, s, c)

    val V4 = WeekPack(
        id = "v4",
        name = "v4 prep",
        blurb = "Arms and side-delts, weekday clock.",
        byWeekday = Program.DAYS,
        queue = listOf(
            Program.DAYS.getValue(DayOfWeek.MONDAY),
            Program.DAYS.getValue(DayOfWeek.TUESDAY),
            Program.DAYS.getValue(DayOfWeek.WEDNESDAY),
            Program.DAYS.getValue(DayOfWeek.THURSDAY),
            Program.DAYS.getValue(DayOfWeek.FRIDAY),
        ),
    )

    val PPL = WeekPack(
        id = "ppl",
        name = "Push / Pull / Legs",
        blurb = "Three slots, still the v4 lifts.",
        byWeekday = mapOf(
            DayOfWeek.MONDAY to push(),
            DayOfWeek.TUESDAY to pull(),
            DayOfWeek.WEDNESDAY to legs(),
            DayOfWeek.THURSDAY to push(),
            DayOfWeek.FRIDAY to pull(),
        ),
        queue = listOf(push(), pull(), legs()),
    )

    val UL = WeekPack(
        id = "ul",
        name = "Upper / Lower",
        blurb = "Four training days.",
        byWeekday = mapOf(
            DayOfWeek.MONDAY to upper(),
            DayOfWeek.TUESDAY to lower(),
            DayOfWeek.THURSDAY to upper(),
            DayOfWeek.FRIDAY to lower(),
        ),
        queue = listOf(upper(), lower()),
    )

    val FULL = WeekPack(
        id = "full",
        name = "Full body",
        blurb = "Three hits, squat-bench-pull every time.",
        byWeekday = mapOf(
            DayOfWeek.MONDAY to full(),
            DayOfWeek.WEDNESDAY to full(),
            DayOfWeek.FRIDAY to full(),
        ),
        queue = listOf(full()),
    )

    val ALL = listOf(V4, PPL, UL, FULL)

    fun pack(id: String?): WeekPack = ALL.firstOrNull { it.id == id } ?: V4

    fun dayFor(date: LocalDate, profile: Profile, sessions: Map<LocalDate, SessionData>): DayPlan? {
        val pack = pack(profile.weekTemplate)
        return if (profile.schedule == "queue") nextInQueue(date, sessions, pack.queue) else pack.byWeekday[date.dayOfWeek]
    }

    fun nextInQueue(date: LocalDate, sessions: Map<LocalDate, SessionData>, queue: List<DayPlan>): DayPlan? {
        if (queue.isEmpty()) return null
        val lastKey = sessions.entries
            .filter { it.key.isBefore(date) && it.value.sets.values.any { sets -> sets.any { s -> s.done && !s.isWarmup() } } }
            .maxByOrNull { it.key }
            ?.value?.dayKey
        val i = queue.indexOfFirst { it.key == lastKey }
        return queue[(i + 1).mod(queue.size)]
    }

    private fun push() = DayPlan("push", "Push", "presses and delts",
        listOf(p("incbench",4,3), p("ohp",3,2), p("cgb",3,2), p("fly",2,2), p("cablat",3,2), p("ohext",3,2), p("pushdown",2,1)))
    private fun pull() = DayPlan("pull", "Pull", "vertical and arms",
        listOf(p("pullup",4,3), p("csrow",3,2), p("pulldown",2,2), p("reardelt",3,2), p("bayes",3,2), p("ezcurl",3,2), p("hammer",2,2)))
    private fun legs() = DayPlan("legs", "Legs", "squat and hinge",
        listOf(p("squat",3,3), p("rdl",3,2), p("legpress",2,2), p("seatcurl",3,2), p("thrust",2,2), p("stcalf",3,2), p("hlr",3,2)))
    private fun upper() = DayPlan("upper", "Upper", "press and pull",
        listOf(p("incbench",4,3), p("pullup",4,3), p("ohp",3,2), p("csrow",3,2), p("cablat",3,2), p("cgb",3,2), p("hammer",3,2)))
    private fun lower() = DayPlan("lower", "Lower", "squat hinge calves",
        listOf(p("squat",3,3), p("rdl",3,2), p("hack",2,2), p("seatcurl",3,2), p("stcalf",3,2), p("crunch",3,2)))
    private fun full() = DayPlan("full", "Full body", "anchors plus arms",
        listOf(p("squat",3,3), p("incbench",3,2), p("pullup",3,2), p("rdl",2,2), p("ohp",2,2), p("cablat",3,2), p("preacher",2,2)))
}
