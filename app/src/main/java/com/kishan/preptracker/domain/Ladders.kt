package com.kishan.preptracker.domain

import java.time.LocalDate

/**
 * Named load ladders. Grit's v4 default stays [DOUBLE] (rep-range then load).
 * Missed work never adds load. Three straight misses on linear/double/triple deload 10%.
 */
enum class Ladder {
    OFF, LINEAR, GREYSKULL, DOUBLE, TRIPLE, TIME;

    companion object {
        fun parse(name: String?): Ladder = when (name?.lowercase()) {
            "linear" -> LINEAR
            "greyskull", "gslp" -> GREYSKULL
            "triple" -> TRIPLE
            "time" -> TIME
            "off", "none" -> OFF
            else -> DOUBLE
        }
    }
}

object Ladders {
    const val DELOAD = 0.9

    fun prescribe(ladder: Ladder, ex: Exercise, date: LocalDate, sessions: Map<LocalDate, SessionData>): Prescription {
        if (ladder == Ladder.DOUBLE || ladder == Ladder.OFF) return Progression.prescribe(ex, date, sessions)
        val last = Progression.lastSessionWith(ex.id, date, sessions)
            ?: return Prescription(null, ex.lo, "first log — pick a load for ${ex.lo}–${ex.hi}")
        val work = last.second.filter { it.done && !it.isWarmup() && it.load != null && it.reps != null }
        if (work.isEmpty()) return Prescription(null, ex.lo, "")
        val load = work.maxOf { it.load!! }
        val hits = work.filter { it.load == load }
        val allHit = hits.isNotEmpty() && hits.all { it.reps!! >= ex.hi }
        val misses = streakMisses(ex.id, date, sessions, ex.hi)
        return when (ladder) {
            Ladder.LINEAR -> {
                if (misses >= 3) Prescription(load * DELOAD, ex.lo, "three misses → 10% deload")
                else if (allHit && ex.inc > 0) Prescription(load + ex.inc, ex.hi, "every set hit → add ${ex.inc} kg")
                else Prescription(load, ex.hi, "same load, hit ${ex.hi}")
            }
            Ladder.GREYSKULL -> {
                val amrap = hits.lastOrNull()?.reps ?: 0
                when {
                    amrap <= 0 -> Prescription(load * DELOAD, ex.lo, "failed AMRAP → 10% deload")
                    amrap >= ex.hi * 2 && ex.inc > 0 -> Prescription(load + ex.inc * 2, ex.lo, "doubled the AMRAP → double step")
                    amrap > ex.hi && ex.inc > 0 -> Prescription(load + ex.inc, ex.lo, "beat the AMRAP → add ${ex.inc} kg")
                    else -> Prescription(load, ex.hi, "same load, last set AMRAP")
                }
            }
            Ladder.TRIPLE -> {
                val setCount = hits.size
                when {
                    allHit && setCount < 5 -> Prescription(load, ex.lo, "top of range → add a set next time")
                    allHit && ex.inc > 0 -> Prescription(load + ex.inc, ex.lo, "sets and reps topped out → add load")
                    else -> Prescription(load, minOf((hits.minOf { it.reps!! } + 1), ex.hi), "nudge reps, then sets, then load")
                }
            }
            Ladder.TIME -> {
                val held = work.mapNotNull { it.durationSec }.minOrNull() ?: 0
                val target = (ex.restSec).coerceAtLeast(20)
                if (held >= target) Prescription(load, ex.lo, "held the clock → add time next session")
                else Prescription(load, ex.lo, "same load, hold the clock")
            }
            else -> Progression.prescribe(ex, date, sessions)
        }
    }

    private fun streakMisses(exId: String, before: LocalDate, sessions: Map<LocalDate, SessionData>, hi: Int): Int {
        val past = sessions.entries.filter { it.key.isBefore(before) }.sortedByDescending { it.key }
        var n = 0
        for ((_, s) in past) {
            val work = s.sets[exId].orEmpty().filter { it.done && !it.isWarmup() && it.reps != null }
            if (work.isEmpty()) continue
            val hit = work.all { it.reps!! >= hi }
            if (hit) break else n++
        }
        return n
    }
}
