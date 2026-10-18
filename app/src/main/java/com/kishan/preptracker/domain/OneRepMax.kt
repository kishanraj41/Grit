package com.kishan.preptracker.domain

/**
 * Submaximal 1RM estimators. Epley stays the protocol default.
 * The blend is Grit's own weighting: formulas decay as reps climb because they
 * disagree more about work capacity than about a true max.
 */
object OneRepMax {
    const val REP_CAP = 12

    enum class Formula { EPLEY, BRZYCKI, LOMBARDI, OCONNER, MAYHEW, WATHAN, LANDER, BLEND }

    fun parse(name: String?): Formula = when (name?.lowercase()) {
        "brzycki" -> Formula.BRZYCKI
        "lombardi" -> Formula.LOMBARDI
        "oconner", "o'conner" -> Formula.OCONNER
        "mayhew" -> Formula.MAYHEW
        "wathan" -> Formula.WATHAN
        "lander" -> Formula.LANDER
        "blend", "weighted" -> Formula.BLEND
        else -> Formula.EPLEY
    }

    fun estimate(loadKg: Double, reps: Int, formula: Formula = Formula.EPLEY, rir: Int? = null): Double? {
        if (loadKg <= 0 || reps < 1) return null
        val effective = reps + (rir ?: 0).coerceAtLeast(0)
        if (reps == 1 && (rir == null || rir == 0)) return loadKg
        if (effective > REP_CAP && formula != Formula.BLEND) return null
        if (effective > 15) return null
        return when (formula) {
            Formula.EPLEY -> epley(loadKg, effective)
            Formula.BRZYCKI -> if (effective >= 37) null else loadKg * 36.0 / (37.0 - effective)
            Formula.LOMBARDI -> loadKg * Math.pow(effective.toDouble(), 0.1)
            Formula.OCONNER -> loadKg * (1 + effective / 40.0)
            Formula.MAYHEW -> loadKg * 100.0 / (52.2 + 41.9 * Math.exp(-0.055 * effective))
            Formula.WATHAN -> loadKg * 100.0 / (48.8 + 53.8 * Math.exp(-0.075 * effective))
            Formula.LANDER -> loadKg * 100.0 / (101.3 - 2.67123 * effective)
            Formula.BLEND -> blend(loadKg, effective)
        }
    }

    fun epley(loadKg: Double, reps: Int): Double = loadKg * (1 + reps / 30.0)

    private fun blend(w: Double, r: Int): Double {
        val parts = listOf(
            epley(w, r) to decay(0.95, r, 6, 0.12),
            (w * 36.0 / (37.0 - r).coerceAtLeast(1.0)) to decay(1.10, r, 8, 0.18),
            (w * Math.pow(r.toDouble(), 0.1)) to decay(0.85, r, 5, 0.10),
            (w * (1 + r / 40.0)) to decay(0.90, r, 6, 0.12),
            (w * 100.0 / (52.2 + 41.9 * Math.exp(-0.055 * r))) to decay(0.95, r, 8, 0.10),
            (w * 100.0 / (48.8 + 53.8 * Math.exp(-0.075 * r))) to decay(0.90, r, 7, 0.12),
            (w * 100.0 / (101.3 - 2.67123 * r)) to decay(0.85, r, 7, 0.15),
        )
        val sumW = parts.sumOf { it.second }
        return parts.sumOf { it.first * it.second } / sumW
    }

    private fun decay(base: Double, reps: Int, hinge: Int, slope: Double): Double {
        val v = if (reps <= hinge) 1.0 else (1 - (reps - hinge) * slope).coerceAtLeast(0.3)
        return base * v
    }
}
