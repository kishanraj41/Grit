package com.kishan.preptracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class MacroTarget(
    val kcal: Int, val proteinG: Int, val fatG: Int, val carbG: Int,
    val source: String,   // "adaptive" or "fixed"
    val rationale: String,
)

object NutritionTargets {
    // Guardrails — defaults; Profile copies are the Plan-editable values used at call sites.
    const val PROTEIN_LOW = 190; const val PROTEIN_HIGH = 210
    const val FAT_FLOOR_G = 50
    const val KCAL_FLOOR = 1900          // below measured RMR 2029 is already aggressive
    const val MAX_WEEKLY_CHANGE = 150    // kcal; never move the target more than this at once
    const val FIXED_KCAL = 2050
    const val FIXED_PROTEIN = 205
    const val FIXED_FAT = 55
    const val FIXED_CARB = 165
    const val MIN_DAYS_BETWEEN_CHANGES = 7

    fun compute(energy: EnergyEstimate, p: Profile, currentTarget: Int?): MacroTarget {
        val goalRate = Program.GOAL_RATE[p.goal] ?: 0.45
        val dailyDeficit = goalRate * p.kcalPerKg / 7.0

        if (energy.tdee == null || energy.confidence == EnergyEstimate.Confidence.NONE ||
            energy.confidence == EnergyEstimate.Confidence.LOW) {
            return fixed(p, "Not enough clean data — holding the planned $FIXED_KCAL kcal. ${energy.note}")
        }

        val raw = (energy.tdee - dailyDeficit).toInt()
        val capped = currentTarget?.let { raw.coerceIn(it - p.maxWeeklyChange, it + p.maxWeeklyChange) } ?: raw

        if (capped < p.kcalFloor) {
            return fixed(p, "Hitting $goalRate kg/wk would need $capped kcal, below the ${p.kcalFloor} floor. " +
                "Do not cut further — pull the cardio lever instead (Review tab).")
        }

        val protein = p.proteinHigh
        val fat = maxOf(p.fatFloorG, (capped * 0.25 / 9).toInt())
        val carbs = ((capped - protein * 4 - fat * 9) / 4).coerceAtLeast(0)
        return MacroTarget(capped, protein, fat, carbs, "adaptive",
            "TDEE ${energy.tdee.toInt()} kcal (${energy.windowDays}-day window, ${energy.confidence}) " +
            "minus ${dailyDeficit.toInt()} kcal for $goalRate kg/wk. ${energy.note}")
    }

    /**
     * Recalculate weekly, not daily. A stored target is held until
     * [MIN_DAYS_BETWEEN_CHANGES] have passed since [Profile.lastTargetChange].
     */
    fun applyWeekly(energy: EnergyEstimate, p: Profile, today: LocalDate): MacroTarget {
        val proposed = compute(energy, p, p.kcalTarget)
        val last = p.lastTargetChange?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (p.kcalTarget != null && last != null && ChronoUnit.DAYS.between(last, today) < MIN_DAYS_BETWEEN_CHANGES) {
            return MacroTarget(
                p.kcalTarget,
                p.proteinTarget ?: proposed.proteinG,
                p.fatTarget ?: proposed.fatG,
                p.carbTarget ?: proposed.carbG,
                p.targetSource ?: proposed.source,
                "Held until weekly recalculation (last change $last). ${proposed.rationale}",
            )
        }
        return proposed
    }

    fun shouldPersist(p: Profile, target: MacroTarget, today: LocalDate): Boolean {
        if (p.kcalTarget == target.kcal && p.targetSource == target.source &&
            p.proteinTarget == target.proteinG && p.fatTarget == target.fatG && p.carbTarget == target.carbG) return false
        val last = p.lastTargetChange?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return last == null || ChronoUnit.DAYS.between(last, today) >= MIN_DAYS_BETWEEN_CHANGES
    }

    private fun fixed(p: Profile, why: String) =
        MacroTarget(FIXED_KCAL, FIXED_PROTEIN, FIXED_FAT, FIXED_CARB, "fixed", why)
}
