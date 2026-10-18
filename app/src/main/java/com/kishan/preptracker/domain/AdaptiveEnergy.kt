package com.kishan.preptracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** kcal per kg of body mass change. 7700 is the standard fat figure. */
const val KCAL_PER_KG = 7700.0

data class EnergyEstimate(
    val tdee: Double?,          // kcal/day
    val windowDays: Int,
    val intakeDays: Int,        // complete intake days inside the window
    val confidence: Confidence,
    val note: String,
) {
    enum class Confidence { NONE, LOW, MEDIUM, HIGH }
}

object AdaptiveEnergy {

    /** A day counts as logged only if intake looks real. Below this, assume a missed log. */
    const val MIN_PLAUSIBLE_KCAL = 800

    /** Spec: flag likely reduced NEAT if measured TDEE drops more than this over three weeks. */
    const val NEAT_DROP = 0.08

    /**
     * TDEE from energy balance:
     *     TDEE = meanIntake + (trendWeightLostKg * kcalPerKg) / days
     *
     * Requires complete intake logging. Missing days are excluded from the mean
     * but still count in the elapsed period, which biases TDEE downward — so
     * confidence drops sharply as logged days fall below the window.
     */
    fun estimate(
        days: Map<LocalDate, DayLogModel>,
        ref: LocalDate,
        windowDays: Int = 21,
        alpha: Double = TrendWeight.DEFAULT_ALPHA,
        minPlausibleKcal: Int = MIN_PLAUSIBLE_KCAL,
        kcalPerKg: Double = KCAL_PER_KG,
    ): EnergyEstimate {
        val window = (0 until windowDays).map { ref.minusDays(it.toLong()) }
        val intakes = window.mapNotNull { days[it]?.kcal?.takeIf { k -> k >= minPlausibleKcal } }
        val weights = window.mapNotNull { d -> days[d]?.weight?.let { d to it } }
        if (intakes.size < 10 || weights.size < 8)
            return EnergyEstimate(null, windowDays, intakes.size, EnergyEstimate.Confidence.NONE,
                "Need ~10 logged intake days and ~8 weigh-ins in the window.")

        val trend = TrendWeight.compute(weights, alpha)
        val start = trend.first(); val end = trend.last()
        val elapsed = ChronoUnit.DAYS.between(start.first, end.first).toDouble()
        if (elapsed < 10) return EnergyEstimate(null, windowDays, intakes.size,
            EnergyEstimate.Confidence.NONE, "Window too short.")

        val rate = TrendWeight.rateKgPerWeek(trend) ?: return EnergyEstimate(null, windowDays, intakes.size,
            EnergyEstimate.Confidence.NONE, "Window too short.")
        val tdee = intakes.average() + rate * kcalPerKg / 7.0

        val coverage = intakes.size.toDouble() / windowDays
        val confidence = when {
            coverage >= 0.9 && weights.size >= 15 -> EnergyEstimate.Confidence.HIGH
            coverage >= 0.75 -> EnergyEstimate.Confidence.MEDIUM
            else -> EnergyEstimate.Confidence.LOW
        }
        val note = when (confidence) {
            EnergyEstimate.Confidence.HIGH -> "Based on ${intakes.size} logged days and ${weights.size} weigh-ins."
            EnergyEstimate.Confidence.MEDIUM -> "Some days unlogged — treat as approximate."
            else -> "Logging gaps make this unreliable. Do not act on it yet."
        }
        return EnergyEstimate(tdee, windowDays, intakes.size, confidence, note)
    }
}
