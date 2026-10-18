package com.kishan.preptracker.domain

import kotlinx.serialization.Serializable

@Serializable
data class DropBurst(
    var load: Double? = null,
    var reps: Int? = null,
)

@Serializable
data class PauseBurst(
    var reps: Int? = null,
    var restSec: Int = 15,
)

@Serializable
data class SetEntry(
    var load: Double? = null,   // kg; for pull-up/dips this is ADDED weight
    var reps: Int? = null,
    var rir: Int? = null,
    var done: Boolean = false,
    var phase: String = "work",          // work | warmup
    var kind: String = "straight",       // straight | drop | restpause | timed | cardio
    var durationSec: Int? = null,
    var speedKph: Double? = null,
    var perSide: Boolean = false,
    var leftReps: Int? = null,
    var rightReps: Int? = null,
    var rpe: Double? = null,
    var drops: List<DropBurst> = emptyList(),
    var clusters: List<PauseBurst> = emptyList(),
    var failure: Boolean = false,
) {
    fun isWarmup(): Boolean = phase == "warmup"
    fun workReps(): Int? = when {
        perSide -> listOfNotNull(leftReps, rightReps).takeIf { it.size == 2 }?.sum()?.div(2)
        else -> reps
    }
    fun extraVolumeKg(): Double = drops.sumOf { (it.load ?: 0.0) * (it.reps ?: 0) }
}

@Serializable
data class SessionData(
    val dayKey: String,
    var mode: String,                                   // "S" or "C"
    val sets: MutableMap<String, MutableList<SetEntry>>, // exerciseId -> sets
    val startedAt: String,
    var finishedAt: String? = null,
    var notes: String? = null,
    val feel: MutableMap<String, String> = mutableMapOf(),       // easy / normal / hard
    val variant: MutableMap<String, String> = mutableMapOf(),    // original id -> swapped name
    val extras: MutableMap<String, ExtraExercise> = mutableMapOf(), // user-added custom exercises
    val supersets: MutableMap<String, String> = mutableMapOf(),     // exerciseId -> group letter
    var prSummary: String? = null,
)

/** An exercise added to a session that is not in the v4 template. Fields are set in the Train add dialog. */
@Serializable
data class ExtraExercise(
    val id: String,
    val name: String,
    val lo: Int,
    val hi: Int,
    val rir: String,
    val restSec: Int,
    val inc: Double,
    val bodyweight: Boolean = false,
) {
    fun toExercise() = Exercise(id, name, lo, hi, rir, restSec, inc, bodyweight = bodyweight)
}

@Serializable
data class Profile(
    val ffm: Double = 67.86,
    val heightCm: Double = 177.0,
    val startWeight: Double = 87.9,
    val startDate: String = "2026-09-14",
    val goal: String = "A",
    val targetBf: Double = 15.0,
    val shootDate: String = "2026-10-24",
    val dexaDate: String = "2026-12-15",
    val lever: Int = 0,
    val leverDate: String? = null,
    val hcAutoSync: Boolean = true,
    val lastTargetChange: String? = null,
    val kcalTarget: Int? = null,
    val proteinTarget: Int? = null,
    val fatTarget: Int? = null,
    val carbTarget: Int? = null,
    val targetSource: String? = null,
    val trendAlpha: Double = TrendWeight.DEFAULT_ALPHA,
    val energyWindowDays: Int = 21,
    val minPlausibleKcal: Int = AdaptiveEnergy.MIN_PLAUSIBLE_KCAL,
    val kcalPerKg: Double = KCAL_PER_KG,
    val proteinLow: Int = NutritionTargets.PROTEIN_LOW,
    val proteinHigh: Int = NutritionTargets.PROTEIN_HIGH,
    val fatFloorG: Int = NutritionTargets.FAT_FLOOR_G,
    val kcalFloor: Int = NutritionTargets.KCAL_FLOOR,
    val maxWeeklyChange: Int = NutritionTargets.MAX_WEEKLY_CHANGE,
    val barWeightKg: Double = Plates.DEFAULT_BAR_KG,
    val platesKg: List<Double> = Plates.DEFAULT_PLATES,
    val progPolicy: String = "double",
    val oneRmFormula: String = "epley",
    /** Health Connect package for steps/sleep/RHR/HRV. Null = Auto (largest writer that day). */
    val wearablePkg: String? = null,
    /** Health Connect package for nutrition and scale weight. Null = Auto. */
    val foodPkg: String? = null,
    val weekTemplate: String = "v4",
    val schedule: String = "week",
    val barKind: String = "barbell45",
    val weekStart: String = "mon",
    val restFlash: Boolean = true,
)

/** One day's log. *Src fields track where a value came from ("hc" or "manual"); manual always wins on sync. */
@Serializable
data class DayLogModel(
    val date: String,
    val weight: Double? = null, val weightSrc: String? = null,
    val sleepH: Double? = null, val sleepSrc: String? = null,
    val steps: Int? = null, val stepsSrc: String? = null,
    val kcal: Int? = null, val kcalSrc: String? = null,
    val protein: Int? = null, val proteinSrc: String? = null,
    val rhr: Int? = null, val rhrSrc: String? = null,
    val hrvMs: Double? = null, val hrvSrc: String? = null,
    val waistCm: Double? = null,
    val armCm: Double? = null,
    val note: String? = null,
)
