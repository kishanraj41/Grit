package com.kishan.preptracker.domain

import java.time.LocalDate
import kotlin.math.pow

data class MuscleState(
    val id: String,
    val name: String,
    val fatigue: Double,
    val status: String,
    val lastTrained: LocalDate?,
    val volume7: Double = 0.0,
    val quietDays: Int? = null,
)

/**
 * Eighteen drawable regions. Primary movers count 1.0 hard-set, assistants 0.4.
 * Fatigue decays with a per-region half-life (hours). Status bands: ready / recovering / tired.
 */
object Muscles {
    val ORDER = listOf(
        "traps", "delts", "chest", "upper-back", "serratus",
        "biceps", "triceps", "forearms",
        "abs", "obliques", "lower-back",
        "glutes", "quads", "hams", "adductors", "hip-flexors",
        "calves", "shins",
    )
    val NAMES = mapOf(
        "traps" to "Traps", "delts" to "Shoulders", "chest" to "Chest", "upper-back" to "Upper back",
        "serratus" to "Serratus", "biceps" to "Biceps", "triceps" to "Triceps", "forearms" to "Forearms",
        "abs" to "Abs", "obliques" to "Obliques", "lower-back" to "Lower back", "glutes" to "Glutes",
        "quads" to "Quads", "hams" to "Hamstrings", "adductors" to "Adductors", "hip-flexors" to "Hip flexors",
        "calves" to "Calves", "shins" to "Shins",
    )
    private val HALF_LIFE_H = mapOf(
        "lower-back" to 48.0, "glutes" to 48.0, "quads" to 48.0, "hams" to 48.0, "adductors" to 48.0, "calves" to 48.0,
        "chest" to 30.0, "upper-back" to 30.0, "traps" to 30.0,
    )
    private val SETS_PER_UNIT = 8.0
    private val ASSIST = 0.4

    private val of = mapOf(
        "ohp" to (listOf("delts") to listOf("triceps", "traps")),
        "cablat" to (listOf("delts") to listOf("traps")),
        "dblat" to (listOf("delts") to listOf("traps")),
        "cgb" to (listOf("chest", "triceps") to listOf("delts")),
        "preacher" to (listOf("biceps") to listOf("forearms")),
        "ohext" to (listOf("triceps") to emptyList()),
        "inccurl" to (listOf("biceps") to emptyList()),
        "squat" to (listOf("quads", "glutes") to listOf("hams", "lower-back")),
        "legpress" to (listOf("quads") to listOf("glutes")),
        "seatcurl" to (listOf("hams") to emptyList()),
        "stcalf" to (listOf("calves") to emptyList()),
        "hlr" to (listOf("abs") to listOf("hip-flexors")),
        "reardelt" to (listOf("delts") to listOf("upper-back")),
        "incbench" to (listOf("chest") to listOf("delts", "triceps")),
        "fly" to (listOf("chest") to emptyList()),
        "csrow" to (listOf("upper-back") to listOf("biceps", "traps")),
        "pushdown" to (listOf("triceps") to emptyList()),
        "hammer" to (listOf("biceps") to listOf("forearms")),
        "rdl" to (listOf("hams", "glutes") to listOf("lower-back")),
        "thrust" to (listOf("glutes") to listOf("hams")),
        "hack" to (listOf("quads") to listOf("glutes")),
        "seatcalf" to (listOf("calves") to emptyList()),
        "crunch" to (listOf("abs") to listOf("obliques")),
        "pullup" to (listOf("upper-back") to listOf("biceps", "forearms")),
        "pulldown" to (listOf("upper-back") to listOf("biceps")),
        "dips" to (listOf("chest", "triceps") to listOf("delts")),
        "bayes" to (listOf("biceps") to emptyList()),
        "ezcurl" to (listOf("biceps") to listOf("forearms")),
    )

    fun parts(exId: String): Pair<List<String>, List<String>> =
        of[exId] ?: Lifts.BY_ID[exId]?.let { it.primary to it.assist } ?: (emptyList<String>() to emptyList())

    fun snapshot(now: LocalDate, sessions: Map<LocalDate, SessionData>): List<MuscleState> {
        val fatigue = ORDER.associateWith { 0.0 }.toMutableMap()
        val last = mutableMapOf<String, LocalDate>()
        val volume7 = ORDER.associateWith { 0.0 }.toMutableMap()
        sessions.forEach { (date, session) ->
            val ageH = java.time.temporal.ChronoUnit.HOURS.between(date.atStartOfDay(), now.atStartOfDay()).toDouble().coerceAtLeast(0.0)
            session.sets.forEach { (id, sets) ->
                val hard = sets.count { it.done && !it.isWarmup() }.toDouble().coerceAtMost(12.0)
                if (hard <= 0) return@forEach
                val (pri, sec) = parts(id)
                fun add(m: String, w: Double) {
                    val hl = HALF_LIFE_H[m] ?: 24.0
                    val remain = 0.5.pow(ageH / hl)
                    fatigue[m] = (fatigue[m] ?: 0.0) + (hard * w / SETS_PER_UNIT) * remain
                    last[m] = maxOf(last[m] ?: date, date)
                }
                pri.forEach { add(it, 1.0) }
                sec.forEach { add(it, ASSIST) }
                if (ageH <= 7 * 24) {
                    pri.forEach { m -> volume7[m] = (volume7[m] ?: 0.0) + hard }
                    sec.forEach { m -> volume7[m] = (volume7[m] ?: 0.0) + hard * ASSIST }
                }
            }
        }
        return ORDER.map { id ->
            val f = fatigue[id] ?: 0.0
            val status = when {
                f < 0.25 -> "ready"
                f < 0.5 -> "recovering"
                else -> "tired"
            }
            val quiet = last[id]?.let { java.time.temporal.ChronoUnit.DAYS.between(it, now).toInt() }
            MuscleState(id, NAMES[id] ?: id, f, status, last[id], volume7[id] ?: 0.0, quiet)
        }
    }
}
