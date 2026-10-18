package com.kishan.preptracker.domain

/**
 * Workout loads are stored in kg (domain, Health Connect, tests).
 * Train UI converts at the edge using the international pound (0.45359237 kg).
 */
object Load {
    const val LB_PER_KG = 2.2046226218

    /** Standard US gym plates and 45 lb bar — used only by the plate dialog. */
    const val US_BAR_LB = 45.0
    val US_PLATES_LB = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)

    fun kgToLb(kg: Double): Double = kg * LB_PER_KG
    fun lbToKg(lb: Double): Double = lb / LB_PER_KG

    /** Nearest half-pound, the usual gym display. */
    fun roundLb(lb: Double): Double = kotlin.math.round(lb * 2.0) / 2.0

    fun displayLb(kg: Double?): String {
        if (kg == null) return ""
        val lb = roundLb(kgToLb(kg))
        return if (lb == lb.toLong().toDouble()) lb.toLong().toString() else "%.1f".format(lb)
    }

    fun parseLb(text: String): Double? = text.toDoubleOrNull()?.let { lbToKg(it) }

    fun stepLb(currentKg: Double?, deltaLb: Double): Double {
        val current = currentKg?.let { roundLb(kgToLb(it)) } ?: 0.0
        return lbToKg((current + deltaLb).coerceAtLeast(0.0))
    }
}
