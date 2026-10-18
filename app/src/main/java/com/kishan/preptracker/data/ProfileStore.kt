package com.kishan.preptracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.kishan.preptracker.domain.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "profile")

class ProfileStore(private val context: Context) {
    private object K {
        val ffm = doublePreferencesKey("ffm"); val height = doublePreferencesKey("height")
        val startWeight = doublePreferencesKey("startWeight"); val startDate = stringPreferencesKey("startDate")
        val goal = stringPreferencesKey("goal"); val targetBf = doublePreferencesKey("targetBf")
        val shootDate = stringPreferencesKey("shootDate"); val dexaDate = stringPreferencesKey("dexaDate")
        val lever = intPreferencesKey("lever"); val leverDate = stringPreferencesKey("leverDate")
        val hcAutoSync = booleanPreferencesKey("hcAutoSync")
        val lastTargetChange = stringPreferencesKey("lastTargetChange")
        val kcalTarget = intPreferencesKey("kcalTarget")
        val proteinTarget = intPreferencesKey("proteinTarget")
        val fatTarget = intPreferencesKey("fatTarget")
        val carbTarget = intPreferencesKey("carbTarget")
        val targetSource = stringPreferencesKey("targetSource")
        val trendAlpha = doublePreferencesKey("trendAlpha")
        val energyWindowDays = intPreferencesKey("energyWindowDays")
        val minPlausibleKcal = intPreferencesKey("minPlausibleKcal")
        val kcalPerKg = doublePreferencesKey("kcalPerKg")
        val proteinLow = intPreferencesKey("proteinLow")
        val proteinHigh = intPreferencesKey("proteinHigh")
        val fatFloorG = intPreferencesKey("fatFloorG")
        val kcalFloor = intPreferencesKey("kcalFloor")
        val maxWeeklyChange = intPreferencesKey("maxWeeklyChange")
        val barWeightKg = doublePreferencesKey("barWeightKg")
        val platesKg = stringPreferencesKey("platesKg")
        val progPolicy = stringPreferencesKey("progPolicy")
        val oneRmFormula = stringPreferencesKey("oneRmFormula")
        val wearablePkg = stringPreferencesKey("wearablePkg")
        val foodPkg = stringPreferencesKey("foodPkg")
    }
    val profile: Flow<Profile> = context.dataStore.data.map { p ->
        val d = Profile()
        Profile(
            ffm = p[K.ffm] ?: d.ffm, heightCm = p[K.height] ?: d.heightCm,
            startWeight = p[K.startWeight] ?: d.startWeight, startDate = p[K.startDate] ?: d.startDate,
            goal = p[K.goal] ?: d.goal, targetBf = p[K.targetBf] ?: d.targetBf,
            shootDate = p[K.shootDate] ?: d.shootDate, dexaDate = p[K.dexaDate] ?: d.dexaDate,
            lever = p[K.lever] ?: d.lever, leverDate = p[K.leverDate], hcAutoSync = p[K.hcAutoSync] ?: d.hcAutoSync,
            lastTargetChange = p[K.lastTargetChange],
            kcalTarget = p[K.kcalTarget], proteinTarget = p[K.proteinTarget],
            fatTarget = p[K.fatTarget], carbTarget = p[K.carbTarget], targetSource = p[K.targetSource],
            trendAlpha = p[K.trendAlpha] ?: d.trendAlpha,
            energyWindowDays = p[K.energyWindowDays] ?: d.energyWindowDays,
            minPlausibleKcal = p[K.minPlausibleKcal] ?: d.minPlausibleKcal,
            kcalPerKg = p[K.kcalPerKg] ?: d.kcalPerKg,
            proteinLow = p[K.proteinLow] ?: d.proteinLow,
            proteinHigh = p[K.proteinHigh] ?: d.proteinHigh,
            fatFloorG = p[K.fatFloorG] ?: d.fatFloorG,
            kcalFloor = p[K.kcalFloor] ?: d.kcalFloor,
            maxWeeklyChange = p[K.maxWeeklyChange] ?: d.maxWeeklyChange,
            barWeightKg = p[K.barWeightKg] ?: d.barWeightKg,
            platesKg = p[K.platesKg]?.split(",")?.mapNotNull { it.trim().toDoubleOrNull() }?.takeIf { it.isNotEmpty() } ?: d.platesKg,
            progPolicy = p[K.progPolicy] ?: d.progPolicy,
            oneRmFormula = p[K.oneRmFormula] ?: d.oneRmFormula,
            wearablePkg = p[K.wearablePkg],
            foodPkg = p[K.foodPkg],
        )
    }
    suspend fun save(pr: Profile) {
        context.dataStore.edit { e ->
            e[K.ffm] = pr.ffm; e[K.height] = pr.heightCm; e[K.startWeight] = pr.startWeight; e[K.startDate] = pr.startDate
            e[K.goal] = pr.goal; e[K.targetBf] = pr.targetBf; e[K.shootDate] = pr.shootDate; e[K.dexaDate] = pr.dexaDate
            e[K.lever] = pr.lever; if (pr.leverDate != null) e[K.leverDate] = pr.leverDate else e.remove(K.leverDate)
            e[K.hcAutoSync] = pr.hcAutoSync
            if (pr.lastTargetChange != null) e[K.lastTargetChange] = pr.lastTargetChange else e.remove(K.lastTargetChange)
            if (pr.kcalTarget != null) e[K.kcalTarget] = pr.kcalTarget else e.remove(K.kcalTarget)
            if (pr.proteinTarget != null) e[K.proteinTarget] = pr.proteinTarget else e.remove(K.proteinTarget)
            if (pr.fatTarget != null) e[K.fatTarget] = pr.fatTarget else e.remove(K.fatTarget)
            if (pr.carbTarget != null) e[K.carbTarget] = pr.carbTarget else e.remove(K.carbTarget)
            if (pr.targetSource != null) e[K.targetSource] = pr.targetSource else e.remove(K.targetSource)
            e[K.trendAlpha] = pr.trendAlpha
            e[K.energyWindowDays] = pr.energyWindowDays
            e[K.minPlausibleKcal] = pr.minPlausibleKcal
            e[K.kcalPerKg] = pr.kcalPerKg
            e[K.proteinLow] = pr.proteinLow
            e[K.proteinHigh] = pr.proteinHigh
            e[K.fatFloorG] = pr.fatFloorG
            e[K.kcalFloor] = pr.kcalFloor
            e[K.maxWeeklyChange] = pr.maxWeeklyChange
            e[K.barWeightKg] = pr.barWeightKg
            e[K.platesKg] = pr.platesKg.joinToString(",")
            e[K.progPolicy] = pr.progPolicy
            e[K.oneRmFormula] = pr.oneRmFormula
            if (pr.wearablePkg != null) e[K.wearablePkg] = pr.wearablePkg else e.remove(K.wearablePkg)
            if (pr.foodPkg != null) e[K.foodPkg] = pr.foodPkg else e.remove(K.foodPkg)
        }
    }
}
