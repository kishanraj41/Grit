package com.kishan.preptracker.domain

import java.time.LocalDate

data class Prescription(val load: Double?, val reps: Int, val note: String)

object Progression {
    /** Epley estimated 1RM. */
    fun e1rm(load: Double, reps: Int): Double = when {
        reps <= 0 -> 0.0
        reps == 1 -> load
        else -> load * (1 + reps / 30.0)
    }

    /** Best est. 1RM for an exercise inside one session; bodyweight lifts add the athlete's bodyweight. */
    fun bestE1rm(ex: Exercise, session: SessionData, bodyweightKg: Double): Double {
        val add = if (ex.bodyweight) bodyweightKg else 0.0
        return session.sets[ex.id].orEmpty()
            .filter { it.done && !it.isWarmup() && it.load != null && it.workReps() != null }
            .maxOfOrNull { e1rm(it.load!! + add, it.workReps()!!) } ?: 0.0
    }

    fun lastSessionWith(exId: String, before: LocalDate, sessions: Map<LocalDate, SessionData>): Pair<LocalDate, List<SetEntry>>? =
        sessions.entries
            .filter { it.key.isBefore(before) && it.value.sets[exId].orEmpty().any { s -> s.done && !s.isWarmup() } }
            .maxByOrNull { it.key }
            ?.let { it.key to it.value.sets[exId]!! }

    /**
     * Double progression (Gravl-style ladder):
     * every top-load set hit the top of the range -> +inc kg and back to the bottom of the range;
     * otherwise same load, aim one more rep than the weakest top-load set.
     */
    fun sessionVolume(ex: Exercise, session: SessionData, bodyweightKg: Double): Double {
        val add = if (ex.bodyweight) bodyweightKg else 0.0
        return session.sets[ex.id].orEmpty()
            .filter { it.done && !it.isWarmup() && it.load != null && it.workReps() != null }
            .sumOf { (it.load!! + add) * it.workReps()!! + it.extraVolumeKg() }
    }

    fun prescribe(ex: Exercise, date: LocalDate, sessions: Map<LocalDate, SessionData>): Prescription {
        val last = lastSessionWith(ex.id, date, sessions)
            ?: return Prescription(null, ex.lo, "first log — pick a weight you can hit for ${ex.lo}–${ex.hi} at ${ex.rir} RIR")
        val done = last.second.filter { it.done && !it.isWarmup() && it.load != null && it.reps != null }
        if (done.isEmpty()) return Prescription(null, ex.lo, "")
        val maxLoad = done.maxOf { it.load!! }
        val top = done.filter { it.load == maxLoad }
        val allTop = top.all { it.reps!! >= ex.hi }
        if (allTop && ex.inc > 0) return Prescription(maxLoad + ex.inc, ex.lo, "ladder full last time → +${ex.inc} kg, back to ${ex.lo} reps")
        val minReps = top.minOf { it.reps!! }
        val aim = minOf(minReps + 1, ex.hi)
        return Prescription(maxLoad, aim, "same load, aim $aim+ reps")
    }
}
