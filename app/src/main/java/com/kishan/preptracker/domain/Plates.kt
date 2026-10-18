package com.kishan.preptracker.domain

data class PlateResult(
    val barKg: Double,
    val perSide: List<Double>,
    val leftoverKg: Double,
)

object Plates {
    val DEFAULT_BAR_KG = 20.0
    val DEFAULT_PLATES = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)

    /**
     * Greedy plates per side for a loaded barbell. [leftoverKg] is non-zero when
     * the available plates cannot make the load exactly — never invent a plate.
     */
    fun forLoad(loadKg: Double, barKg: Double = DEFAULT_BAR_KG, available: List<Double> = DEFAULT_PLATES): PlateResult? {
        if (loadKg < barKg) return null
        val perSideTarget = (loadKg - barKg) / 2.0
        val sorted = available.filter { it > 0 }.sortedDescending()
        if (sorted.isEmpty()) return PlateResult(barKg, emptyList(), perSideTarget * 2)
        val used = mutableListOf<Double>()
        var remain = perSideTarget
        for (p in sorted) {
            while (remain + 1e-9 >= p) {
                used += p
                remain -= p
            }
        }
        if (remain < 1e-9) remain = 0.0
        return PlateResult(barKg, used, remain * 2)
    }
}
