package com.kishan.preptracker.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/** Everything Health Connect can give us for one calendar day. Null = not recorded. */
data class HealthDay(
    val date: LocalDate,
    val steps: Long?,
    val sleepHours: Double?,
    val rhr: Long?,
    val hrvMs: Double?,
    val weightKg: Double?,
    val kcal: Double?,
    val proteinG: Double?,
    val stepsApp: String? = null,
)

class HealthConnectManager(private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
    )

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)
    val isAvailable: Boolean get() = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasAllPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(permissions)

    suspend fun readDay(date: LocalDate): HealthDay = readRange(date, date).getValue(date)

    /**
     * One pass over Health Connect for every local day in [from]..[toInclusive].
     * - steps, nutrition, weight, resting HR: records whose time falls on that calendar day
     * - sleep + HRV: records overlapping 18:00 the previous day to 14:00 that day
     */
    suspend fun readRange(from: LocalDate, toInclusive: LocalDate): Map<LocalDate, HealthDay> {
        require(!toInclusive.isBefore(from)) { "range inverted" }
        val zone = ZoneId.systemDefault()
        val dates = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(toInclusive) }.toList()
        val dayFilter = TimeRangeFilter.between(
            from.atStartOfDay(),
            toInclusive.plusDays(1).atStartOfDay(),
        )
        val nightFilter = TimeRangeFilter.between(
            HealthWindows.nightStart(from, zone),
            HealthWindows.nightEnd(toInclusive, zone),
        )

        val stepsBy = mutableMapOf<LocalDate, Long>()
        val stepsAppBy = mutableMapOf<LocalDate, String>()
        val origins = readAll(StepsRecord::class, dayFilter).map { it.metadata.dataOrigin }.toSet()
        val byDateOrigin = mutableMapOf<LocalDate, MutableMap<String, Long>>()
        for (origin in origins) {
            val grouped = client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = dayFilter,
                    timeRangeSlicer = Period.ofDays(1),
                    dataOriginFilter = setOf(origin),
                ),
            )
            grouped.forEach { bucket ->
                val d = bucket.startTime.toLocalDate()
                val n = bucket.result[StepsRecord.COUNT_TOTAL] ?: return@forEach
                if (d in dates) byDateOrigin.getOrPut(d) { mutableMapOf() }[origin.packageName] = n
            }
        }
        dates.forEach { date ->
            val picked = StepSources.pick(byDateOrigin[date].orEmpty())
            if (picked != null) {
                stepsBy[date] = picked.second
                stepsAppBy[date] = picked.first
            }
        }

        val kcalBy = mutableMapOf<LocalDate, Double>()
        val proteinBy = mutableMapOf<LocalDate, Double>()
        readAll(NutritionRecord::class, dayFilter).forEach { r ->
            val d = r.startTime.atZone(zone).toLocalDate()
            if (d !in dates) return@forEach
            r.energy?.inKilocalories?.let { kcalBy[d] = (kcalBy[d] ?: 0.0) + it }
            r.protein?.inGrams?.let { proteinBy[d] = (proteinBy[d] ?: 0.0) + it }
        }

        val weightSum = mutableMapOf<LocalDate, Double>()
        val weightN = mutableMapOf<LocalDate, Int>()
        readAll(WeightRecord::class, dayFilter).forEach { r ->
            val d = r.time.atZone(zone).toLocalDate()
            if (d !in dates) return@forEach
            weightSum[d] = (weightSum[d] ?: 0.0) + r.weight.inKilograms
            weightN[d] = (weightN[d] ?: 0) + 1
        }

        val rhrSum = mutableMapOf<LocalDate, Double>()
        val rhrN = mutableMapOf<LocalDate, Int>()
        readAll(RestingHeartRateRecord::class, dayFilter).forEach { r ->
            val d = r.time.atZone(zone).toLocalDate()
            if (d !in dates) return@forEach
            rhrSum[d] = (rhrSum[d] ?: 0.0) + r.beatsPerMinute
            rhrN[d] = (rhrN[d] ?: 0) + 1
        }

        val sleepSessions = readAll(SleepSessionRecord::class, nightFilter)
        val hrvRecords = readAll(HeartRateVariabilityRmssdRecord::class, nightFilter)

        return dates.associateWith { date ->
            val nightStart = HealthWindows.nightStart(date, zone)
            val nightEnd = HealthWindows.nightEnd(date, zone)
            val sleepSec = sleepSessions.sumOf { HealthWindows.overlapSeconds(it.startTime, it.endTime, nightStart, nightEnd) }
            val hrvVals = hrvRecords.mapNotNull { r ->
                r.time.takeIf { HealthWindows.inNight(it, date, zone) }?.let { r.heartRateVariabilityMillis }
            }
            val wn = weightN[date] ?: 0
            val rn = rhrN[date] ?: 0
            HealthDay(
                date = date,
                steps = stepsBy[date],
                sleepHours = sleepSec.takeIf { it > 0 }?.div(3600.0),
                rhr = if (rn > 0) (rhrSum[date]!! / rn).toLong() else null,
                hrvMs = hrvVals.takeIf { it.isNotEmpty() }?.average(),
                weightKg = if (wn > 0) weightSum[date]!! / wn else null,
                kcal = kcalBy[date],
                proteinG = proteinBy[date],
                stepsApp = stepsAppBy[date],
            )
        }
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, filter: TimeRangeFilter): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val page = client.readRecords(
                ReadRecordsRequest(recordType = type, timeRangeFilter = filter, pageSize = 1000, pageToken = token),
            )
            out += page.records
            token = page.pageToken?.takeIf { it.isNotEmpty() }
        } while (token != null)
        return out
    }
}
