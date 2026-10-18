package com.kishan.preptracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kishan.preptracker.data.Repository
import com.kishan.preptracker.domain.DayLogModel
import com.kishan.preptracker.health.HealthConnectManager
import com.kishan.preptracker.health.HealthDay
import com.kishan.preptracker.health.StepSources
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object HealthSync {
    /**
     * Pulls the last [daysBack] days from Health Connect into the day log.
     * Manual entries always win; HC only fills fields that are empty or were HC-sourced last time.
     * Returns the number of days updated, or -1 if HC is unavailable / not permitted.
     */
    suspend fun syncRecent(context: Context, daysBack: Int = 3): Int {
        val hc = HealthConnectManager(context)
        if (!hc.isAvailable || !hc.hasAllPermissions()) return -1
        val today = LocalDate.now()
        val from = today.minusDays((daysBack - 1).coerceAtLeast(0).toLong())
        val byDate = runCatching { hc.readRange(from, today) }.getOrElse { return -1 }
        val repo = Repository(context)
        var updated = 0
        for (i in 0 until daysBack) {
            val date = today.minusDays(i.toLong())
            val h = byDate[date] ?: continue
            val cur = repo.getDay(date) ?: DayLogModel(date.toString())
            val next = merge(cur, h)
            if (next != cur) { repo.saveDay(next); updated++ }
        }
        return updated
    }

    /** Manual source always wins. Null incoming leaves the existing value in place. */
    fun <T> pick(existing: T?, src: String?, incoming: T?): Pair<T?, String?> =
        if (src == "manual") existing to src else if (incoming != null) incoming to "hc" else existing to src

    fun merge(cur: DayLogModel, h: HealthDay): DayLogModel {
        val (w, wS) = pick(cur.weight, cur.weightSrc, h.weightKg?.let { (it * 100).roundToInt() / 100.0 })
        val (sl, slS) = pick(cur.sleepH, cur.sleepSrc, h.sleepHours?.let { (it * 100).roundToInt() / 100.0 })
        val (st, stS) = pick(cur.steps, cur.stepsSrc, h.steps?.toInt()).let { (v, src) ->
            if (cur.stepsSrc == "manual") v to src
            else if (h.steps != null) v to StepSources.srcTag(h.stepsApp)
            else v to src
        }
        val (kc, kcS) = pick(cur.kcal, cur.kcalSrc, h.kcal?.roundToInt())
        val (pr, prS) = pick(cur.protein, cur.proteinSrc, h.proteinG?.roundToInt())
        val (rhr, rhrS) = pick(cur.rhr, cur.rhrSrc, h.rhr?.toInt())
        val (hrv, hrvS) = pick(cur.hrvMs, cur.hrvSrc, h.hrvMs?.let { (it * 10).roundToInt() / 10.0 })
        return cur.copy(
            weight = w, weightSrc = wS, sleepH = sl, sleepSrc = slS, steps = st, stepsSrc = stS,
            kcal = kc, kcalSrc = kcS, protein = pr, proteinSrc = prS,
            rhr = rhr, rhrSrc = rhrS, hrvMs = hrv, hrvSrc = hrvS,
        )
    }
}

class HealthSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val n = HealthSync.syncRecent(applicationContext, 3)
        return if (n >= 0) Result.success() else Result.retry()
    }

    companion object {
        /** Best-effort background refresh every 6 h. The foreground sync on app resume is the reliable path. */
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<HealthSyncWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("hc-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
