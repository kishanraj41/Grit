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

    data class Writers(val movement: List<String>, val food: List<String>)

    /** Packages that wrote movement or food in the last [daysBack] days. */
    suspend fun listWriters(daysBack: Int = 14): Writers {
        val zone = ZoneId.systemDefault()
        val to = LocalDate.now()
        val from = to.minusDays((daysBack - 1).coerceAtLeast(0).toLong())
        val filter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay())
        val night = TimeRangeFilter.between(HealthWindows.nightStart(from, zone), HealthWindows.nightEnd(to, zone))
        val movement = (
            readAll(StepsRecord::class, filter).map { it.metadata.dataOrigin.packageName } +
            readAll(SleepSessionRecord::class, night).map { it.metadata.dataOrigin.packageName } +
            readAll(RestingHeartRateRecord::class, filter).map { it.metadata.dataOrigin.packageName } +
            readAll(HeartRateVariabilityRmssdRecord::class, night).map { it.metadata.dataOrigin.packageName }
        ).distinct().sorted()
        val food = (
            readAll(NutritionRecord::class, filter).map { it.metadata.dataOrigin.packageName } +
            readAll(WeightRecord::class, filter).map { it.metadata.dataOrigin.packageName }
        ).distinct().sorted()
        return Writers(movement, food)
    }

    /**
     * One pass over Health Connect for every local day in [from]..[toInclusive].
     * Each metric uses one writer: [wearablePkg] / [foodPkg] if set, else the largest that day.
     */
    suspend fun readRange(
        from: LocalDate,
        toInclusive: LocalDate,
        wearablePkg: String? = null,
        foodPkg: String? = null,
    ): Map<LocalDate, HealthDay> {
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
            val picked = StepSources.pick(byDateOrigin[date].orEmpty(), wearablePkg)
            if (picked != null) {
                stepsBy[date] = picked.second
                stepsAppBy[date] = picked.first
            }
        }

        val kcalByOrigin = mutableMapOf<LocalDate, MutableMap<String, Double>>()
        val proteinByOrigin = mutableMapOf<LocalDate, MutableMap<String, Double>>()
        readAll(NutritionRecord::class, dayFilter).forEach { r ->
            val d = r.startTime.atZone(zone).toLocalDate()
            if (d !in dates) return@forEach
            val pkg = r.metadata.dataOrigin.packageName
            r.energy?.inKilocalories?.let {
                kcalByOrigin.getOrPut(d) { mutableMapOf() }[pkg] = (kcalByOrigin[d]?.get(pkg) ?: 0.0) + it
            }
            r.protein?.inGrams?.let {
                proteinByOrigin.getOrPut(d) { mutableMapOf() }[pkg] = (proteinByOrigin[d]?.get(pkg) ?: 0.0) + it
            }
        }
        val kcalBy = mutableMapOf<LocalDate, Double>()
        val proteinBy = mutableMapOf<LocalDate, Double>()
        dates.forEach { date ->
            val food = StepSources.pickDouble(kcalByOrigin[date].orEmpty(), foodPkg)
            if (food != null) {
                kcalBy[date] = food.second
                proteinByOrigin[date]?.get(food.first)?.let { proteinBy[date] = it }
            }
        }

        val weightByOrigin = mutableMapOf<LocalDate, MutableMap<String, Pair<Double, Int>>>()
        readAll(WeightRecord::class, dayFilter).forEach { r ->
            val d = r.time.atZone(zone).toLocalDate()
            if (d !in dates) return@forEach
            val pkg = r.metadata.dataOrigin.packageName
            val cur = weightByOrigin.getOrPut(d) { mutableMapOf() }[pkg] ?: (0.0 to 0)
            weightByOrigin.getValue(d)[pkg] = (cur.first + r.weight.inKilograms) to (cur.second + 1)
        }
        val weightBy = mutableMapOf<LocalDate, Double>()
        dates.forEach { date ->
            val avgs = weightByOrigin[date].orEmpty().mapValues { it.value.first / it.value.second.coerceAtLeast(1) }
            StepSources.pickDouble(avgs, foodPkg)?.let { weightBy[date] = it.second }
        }

        val rhrByOrigin = mutableMapOf<LocalDate, MutableMap<String, Pair<Double, Int>>>()
        readAll(RestingHeartRateRecord::class, dayFilter).forEach { r ->
            val d = r.time.atZone(zone).toLocalDate()
            if (d !in dates) return@forEach
            val pkg = r.metadata.dataOrigin.packageName
            val cur = rhrByOrigin.getOrPut(d) { mutableMapOf() }[pkg] ?: (0.0 to 0)
            rhrByOrigin.getValue(d)[pkg] = (cur.first + r.beatsPerMinute) to (cur.second + 1)
        }

        val sleepSessions = readAll(SleepSessionRecord::class, nightFilter)
        val hrvRecords = readAll(HeartRateVariabilityRmssdRecord::class, nightFilter)

        return dates.associateWith { date ->
            val nightStart = HealthWindows.nightStart(date, zone)
            val nightEnd = HealthWindows.nightEnd(date, zone)
            val sleepByOrigin = mutableMapOf<String, Long>()
            sleepSessions.forEach { s ->
                val sec = HealthWindows.overlapSeconds(s.startTime, s.endTime, nightStart, nightEnd)
                if (sec > 0) {
                    val pkg = s.metadata.dataOrigin.packageName
                    sleepByOrigin[pkg] = (sleepByOrigin[pkg] ?: 0L) + sec
                }
            }
            val sleepPick = StepSources.pick(sleepByOrigin, wearablePkg)
            val hrvByOrigin = mutableMapOf<String, MutableList<Double>>()
            hrvRecords.forEach { r ->
                if (HealthWindows.inNight(r.time, date, zone)) {
                    hrvByOrigin.getOrPut(r.metadata.dataOrigin.packageName) { mutableListOf() } += r.heartRateVariabilityMillis
                }
            }
            val hrvPick = StepSources.pickDouble(hrvByOrigin.mapValues { it.value.average() }, wearablePkg)
            val rhrAvgs = rhrByOrigin[date].orEmpty().mapValues { it.value.first / it.value.second.coerceAtLeast(1) }
            val rhrPick = StepSources.pickDouble(rhrAvgs, wearablePkg)
            HealthDay(
                date = date,
                steps = stepsBy[date],
                sleepHours = sleepPick?.second?.div(3600.0),
                rhr = rhrPick?.second?.toLong(),
                hrvMs = hrvPick?.second,
                weightKg = weightBy[date],
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
