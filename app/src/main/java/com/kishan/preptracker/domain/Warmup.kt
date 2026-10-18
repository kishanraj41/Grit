package com.kishan.preptracker.domain

data class WarmupSet(val pct: Double, val reps: Int, val loadKg: Double)

object Warmup {
    /** Spec ramps for anchors only: untracked, not counted in weekly volume. */
    val ANCHOR_RAMPS = listOf(0.40 to 5, 0.60 to 3, 0.80 to 1)

    fun forLoad(workingKg: Double, ramps: List<Pair<Double, Int>> = ANCHOR_RAMPS): List<WarmupSet> =
        ramps.map { (pct, reps) -> WarmupSet(pct, reps, workingKg * pct) }
}
