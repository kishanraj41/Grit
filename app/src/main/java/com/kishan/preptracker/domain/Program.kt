package com.kishan.preptracker.domain

import java.time.DayOfWeek
import java.time.LocalDate

data class Exercise(
    val id: String,
    val name: String,
    val lo: Int,
    val hi: Int,
    val rir: String,
    val restSec: Int,
    val inc: Double,
    val anchor: Boolean = false,
    val bodyweight: Boolean = false,
    val alternatives: List<String> = emptyList(),
)

/** id, sets in Standard week, sets in Compressed week */
data class PlannedExercise(val id: String, val standardSets: Int, val compressedSets: Int)

data class DayPlan(val key: String, val name: String, val sub: String, val exercises: List<PlannedExercise>)

data class Phase(val from: LocalDate, val to: LocalDate, val name: String, val mode: String, val note: String)

/** Retention-Recomp Protocol v4 — arms + side-delt specialisation. */
object Program {
    val EX: Map<String, Exercise> = listOf(
        Exercise("ohp", "Seated overhead press", 6, 8, "2", 150, 2.5, anchor = true),
        Exercise("cablat", "Cable lateral raise", 12, 15, "0–1", 60, 2.5, alternatives = listOf("dblat")),
        Exercise("cgb", "Close-grip bench press", 6, 8, "2", 150, 2.5, anchor = true),
        Exercise("preacher", "Preacher curl", 8, 10, "0–1", 90, 2.5, alternatives = listOf("inccurl", "ezcurl")),
        Exercise("ohext", "Overhead cable triceps ext.", 10, 12, "0–1", 75, 2.5, alternatives = listOf("pushdown")),
        Exercise("inccurl", "Incline dumbbell curl", 10, 12, "0–1", 75, 1.0, alternatives = listOf("preacher", "bayes")),
        Exercise("squat", "High-bar back squat", 5, 8, "2", 180, 2.5, anchor = true, alternatives = listOf("hack")),
        Exercise("legpress", "Leg press (deep)", 10, 12, "1", 120, 5.0, alternatives = listOf("hack")),
        Exercise("seatcurl", "Seated leg curl", 10, 12, "0–1", 90, 2.5),
        Exercise("stcalf", "Standing calf raise (2-s pause)", 10, 15, "0–1", 90, 2.5, alternatives = listOf("seatcalf")),
        Exercise("hlr", "Hanging leg raise", 10, 15, "1", 60, 0.0, alternatives = listOf("crunch")),
        Exercise("reardelt", "Rear-delt fly", 15, 20, "0–1", 60, 1.0),
        Exercise("incbench", "Incline barbell bench", 5, 8, "2", 180, 2.5, anchor = true),
        Exercise("fly", "Cable fly (full stretch)", 12, 15, "0–1", 75, 2.5),
        Exercise("csrow", "Chest-supported row", 8, 10, "1–2", 120, 2.5),
        Exercise("pushdown", "Rope pushdown", 12, 15, "0–1", 60, 2.5, alternatives = listOf("ohext")),
        Exercise("hammer", "Hammer curl", 10, 12, "0–1", 60, 1.0, alternatives = listOf("bayes")),
        Exercise("dblat", "Dumbbell lateral raise (lengthened partials last set)", 15, 20, "0–1", 60, 1.0, alternatives = listOf("cablat")),
        Exercise("rdl", "Romanian deadlift", 6, 8, "2", 180, 2.5, anchor = true),
        Exercise("thrust", "Barbell hip thrust", 8, 10, "1–2", 120, 5.0),
        Exercise("hack", "Hack squat", 8, 10, "1–2", 150, 5.0, alternatives = listOf("squat", "legpress")),
        Exercise("seatcalf", "Seated calf raise", 12, 15, "0–1", 60, 2.5, alternatives = listOf("stcalf")),
        Exercise("crunch", "Cable crunch", 12, 15, "1", 60, 2.5, alternatives = listOf("hlr")),
        Exercise("pullup", "Weighted pull-up", 5, 8, "2", 180, 2.5, anchor = true, bodyweight = true, alternatives = listOf("pulldown")),
        Exercise("pulldown", "Lat pulldown (full stretch)", 10, 12, "0–1", 90, 2.5, alternatives = listOf("pullup")),
        Exercise("dips", "Dips (or decline skull-crusher)", 8, 12, "1", 90, 2.5, bodyweight = true, alternatives = listOf("ohext")),
        Exercise("bayes", "Bayesian cable curl", 12, 15, "0–1", 60, 2.5, alternatives = listOf("inccurl", "hammer")),
        Exercise("ezcurl", "EZ-bar curl", 10, 12, "0–1", 75, 2.5, alternatives = listOf("preacher")),
    ).associateBy { it.id }

    private fun p(id: String, s: Int, c: Int) = PlannedExercise(id, s, c)

    val DAYS: Map<DayOfWeek, DayPlan> = mapOf(
        DayOfWeek.MONDAY to DayPlan("mon", "Arms + Delts", "heavy arm day",
            listOf(p("ohp",3,3), p("cablat",4,3), p("cgb",4,3), p("preacher",3,2), p("ohext",3,2), p("inccurl",3,2))),
        DayOfWeek.TUESDAY to DayPlan("tue", "Legs + Abs", "squat day",
            listOf(p("cablat",3,2), p("squat",3,3), p("legpress",2,2), p("seatcurl",3,2), p("stcalf",3,2), p("hlr",3,2), p("reardelt",3,2))),
        DayOfWeek.WEDNESDAY to DayPlan("wed", "Upper — chest focus", "moderate arm day",
            listOf(p("incbench",4,3), p("fly",2,2), p("csrow",3,2), p("cablat",2,2), p("ohext",3,2), p("pushdown",2,1), p("hammer",3,2))),
        DayOfWeek.THURSDAY to DayPlan("thu", "Legs + Abs", "hinge day",
            listOf(p("dblat",4,3), p("rdl",3,2), p("thrust",3,2), p("hack",2,2), p("seatcalf",3,2), p("crunch",3,2))),
        DayOfWeek.FRIDAY to DayPlan("fri", "Upper — back focus", "moderate arm day",
            listOf(p("pullup",4,3), p("pulldown",2,2), p("cablat",3,2), p("reardelt",3,2), p("dips",3,2), p("bayes",3,2), p("ezcurl",3,2))),
    )

    val ANCHORS = listOf("incbench", "pullup", "squat", "rdl", "cgb", "ohp")

    val PHASES = listOf(
        Phase(LocalDate.of(2026,9,14), LocalDate.of(2026,9,27), "Standard block", "S", "Full v4 week, anchors logged."),
        Phase(LocalDate.of(2026,9,28), LocalDate.of(2026,10,18), "Compressed block", "C", "Fewer sets, every set 0–1 RIR. Optional deload Oct 5–11 (3 RIR) — skip if anchors climbing and sleep 7 h+."),
        Phase(LocalDate.of(2026,10,19), LocalDate.of(2026,10,24), "Peak week", "C", "Mon–Wed ~1,865 kcal light training; Thu–Sat carb-up; shoot Sat Oct 24."),
        Phase(LocalDate.of(2026,10,25), LocalDate.of(2026,10,25), "Post-shoot", "S", "Rest."),
        Phase(LocalDate.of(2026,10,26), LocalDate.of(2026,12,31), "Block 2 — to December DEXA", "S", "Resume same loads; first week back feels heavy. Compressed by trigger only."),
    )

    fun phaseFor(d: LocalDate): Phase = PHASES.firstOrNull { !d.isBefore(it.from) && !d.isAfter(it.to) } ?: PHASES.last()
    fun dayFor(d: LocalDate): DayPlan? = DAYS[d.dayOfWeek]

    fun resolve(id: String, session: SessionData? = null): Exercise? =
        EX[id] ?: Lifts.BY_ID[id]?.ex ?: session?.extras?.get(id)?.toExercise()

    val GOAL_RATE = mapOf("A" to 0.45, "B" to 0.62)
}
