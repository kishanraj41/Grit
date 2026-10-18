package com.kishan.preptracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class WindowAvg(val avg: Double?, val n: Int)

data class AnchorStatus(val id: String, val name: String, val now: Double, val prev: Double, val prev2: Double) {
    val drop get() = now > 0 && prev > 0 && now < prev * 0.98
    val dropPrev get() = prev > 0 && prev2 > 0 && prev < prev2 * 0.98
}

data class Recommendation(val kind: String, val title: String, val why: String, val lever: Int? = null)

data class Review(
    val date: LocalDate,
    val avg7: Double?, val n7: Int, val prevAvg: Double?,
    val rate: Double?, val rate2: Double?, val target: Double, val ratePct: Double?,
    val sleep7: WindowAvg, val steps7: WindowAvg, val kcal7: WindowAvg, val pro7: WindowAvg, val rhr7: WindowAvg, val hrv7: WindowAvg,
    val waistNow: Pair<LocalDate, Double>?, val waistDelta: Double?,
    val anchors: List<AnchorStatus>, val drops: Int, val dropsPrev: Int,
    val phase: Phase, val mode: String,
    val bf: Double?, val targetWeight: Double, val toLose: Double?, val weeksAtRate: Double?, val weeksAtTarget: Double?,
    val recs: List<Recommendation>, val flags: List<String>,
    val trendKg: Double? = null, val trendRate: Double? = null,
)

/** Straight port of the web tracker's weeklyReview() — the protocol's rules, applied to logged data. */
object RulesEngine {

    private fun avgWindow(days: Map<LocalDate, DayLogModel>, end: LocalDate, n: Int, pick: (DayLogModel) -> Double?): WindowAvg {
        val vals = (0 until n).mapNotNull { i -> days[end.minusDays(i.toLong())]?.let(pick) }
        return WindowAvg(if (vals.isEmpty()) null else vals.average(), vals.size)
    }

    fun bodyweightOn(days: Map<LocalDate, DayLogModel>, date: LocalDate, fallback: Double): Double {
        val onOrBefore = days.entries.filter { !it.key.isAfter(date) && it.value.weight != null }.maxByOrNull { it.key }
        val any = days.entries.filter { it.value.weight != null }.minByOrNull { it.key }
        return onOrBefore?.value?.weight ?: any?.value?.weight ?: fallback
    }

    private fun anchorWeekBest(exId: String, weekMon: LocalDate, sessions: Map<LocalDate, SessionData>, days: Map<LocalDate, DayLogModel>, fallbackBw: Double): Double {
        val ex = Program.EX[exId] ?: return 0.0
        return (0 until 7).maxOf { i ->
            val d = weekMon.plusDays(i.toLong())
            sessions[d]?.let { Progression.bestE1rm(ex, it, bodyweightOn(days, d, fallbackBw)) } ?: 0.0
        }
    }

    fun mondayOf(d: LocalDate): LocalDate = d.minusDays(((d.dayOfWeek.value + 6) % 7).toLong())

    /** Working sets marked done in the week starting [monday]. Warm-ups are not stored on the session. */
    fun weekSetCount(sessions: Map<LocalDate, SessionData>, monday: LocalDate): Int =
        (0 until 7).sumOf { i ->
            sessions[monday.plusDays(i.toLong())]?.sets?.values?.sumOf { sets -> sets.count { it.done } } ?: 0
        }

    fun weeklyReview(ref: LocalDate, days: Map<LocalDate, DayLogModel>, sessions: Map<LocalDate, SessionData>, p: Profile): Review {
        val now7 = avgWindow(days, ref, 7) { it.weight }
        val prev7 = avgWindow(days, ref.minusDays(7), 7) { it.weight }
        val prev14 = avgWindow(days, ref.minusDays(14), 7) { it.weight }
        val rate = if (now7.n >= 4 && prev7.n >= 4) prev7.avg!! - now7.avg!! else null
        val rate2 = if (prev7.n >= 4 && prev14.n >= 4) prev14.avg!! - prev7.avg!! else null
        val weightSeries = days.entries.mapNotNull { e -> e.value.weight?.let { e.key to it } }.filter { !it.first.isAfter(ref) }
        val trend = TrendWeight.compute(weightSeries, p.trendAlpha)
        val trendKg = trend.lastOrNull()?.second
        val trendRate = TrendWeight.rateKgPerWeek(trend, 14)
        val trendRate10 = TrendWeight.rateKgPerWeek(trend, 10)
        val energyNow = AdaptiveEnergy.estimate(days, ref, p.energyWindowDays, p.trendAlpha, p.minPlausibleKcal, p.kcalPerKg)
        val energyPrev = AdaptiveEnergy.estimate(days, ref.minusDays(p.energyWindowDays.toLong()), p.energyWindowDays, p.trendAlpha, p.minPlausibleKcal, p.kcalPerKg)
        val target = Program.GOAL_RATE[p.goal] ?: 0.45
        val ratePct = if (rate != null && now7.avg != null) rate / now7.avg * 100 else null
        val sleep7 = avgWindow(days, ref, 7) { it.sleepH }
        val steps7 = avgWindow(days, ref, 7) { it.steps?.toDouble() }
        val kcal7 = avgWindow(days, ref, 7) { it.kcal?.toDouble() }
        val pro7 = avgWindow(days, ref, 7) { it.protein?.toDouble() }
        val rhr7 = avgWindow(days, ref, 7) { it.rhr?.toDouble() }
        val hrv7 = avgWindow(days, ref, 7) { it.hrvMs }

        val waistSeries = days.entries.filter { it.value.waistCm != null }.sortedBy { it.key }.map { it.key to it.value.waistCm!! }
        val waistNow = waistSeries.lastOrNull()
        val waistPrev = waistNow?.let { wn -> waistSeries.lastOrNull { !it.first.isAfter(wn.first.minusDays(12)) } }
        val waistDelta = if (waistNow != null && waistPrev != null) waistNow.second - waistPrev.second else null

        val wk = mondayOf(ref); val lastWk = wk.minusDays(7); val wk2 = wk.minusDays(14)
        val anchors = Program.ANCHORS.map { id ->
            AnchorStatus(id, Program.EX[id]!!.name,
                anchorWeekBest(id, wk, sessions, days, p.startWeight),
                anchorWeekBest(id, lastWk, sessions, days, p.startWeight),
                anchorWeekBest(id, wk2, sessions, days, p.startWeight))
        }
        val drops = anchors.count { it.drop }; val dropsPrev = anchors.count { it.dropPrev }
        val phase = Program.phaseFor(ref)

        val w = now7.avg ?: bodyweightOn(days, ref, p.startWeight)
        val bf = (w - p.ffm) / w * 100
        val targetWeight = p.ffm / (1 - p.targetBf / 100)
        val toLose = w - targetWeight
        val weeksAtRate = if (rate != null && rate > 0.1 && toLose > 0) toLose / rate else null
        val weeksAtTarget = if (toLose > 0) toLose / target else null

        val recs = mutableListOf<Recommendation>(); val flags = mutableListOf<String>()
        var mode = phase.mode
        val lowSleep = sleep7.n >= 3 && sleep7.avg!! < 6.5
        when {
            lowSleep -> { mode = "C"; recs += Recommendation("volume", "Run Compressed this week", "7-day sleep average ${"%.1f".format(sleep7.avg)} h (<6.5). Volume comes down before effort does.") }
            drops >= 2 && dropsPrev >= 2 -> { mode = "C"; recs += Recommendation("volume", "Run Compressed this week", "Two or more anchors dropped two weeks running — reduce volume, not just effort.") }
            phase.mode == "C" -> recs += Recommendation("volume", "Run Compressed (planned block)", "${phase.name}: ${phase.note}")
            else -> recs += Recommendation("volume", "Run Standard this week", "No trigger fired. Match last week's loads on the six anchors.")
        }
        if (drops >= 2 && !(drops >= 2 && dropsPrev >= 2)) recs += Recommendation("effort", "Pull 0-RIR sets back to 2 RIR this week", "$drops anchors dropped this week vs last. One week of that is noise; two is a signal.")
        val lastNight = days[ref]?.sleepH
        if (lastNight != null && lastNight < 6) recs += Recommendation("effort", "Today: isolation work at 2 RIR", "Last night ${"%.1f".format(lastNight)} h. You cannot recover the failure sets.")

        if (rate == null) recs += Recommendation("diet", "Hold — not enough weigh-ins yet", "Need 4+ morning weigh-ins in each of the last two weeks to judge the rate.")
        else {
            val slow = rate < target - 0.15; val slow2 = rate2 != null && rate2 < target - 0.15
            val fast = rate > 0.85; val fast2 = rate2 != null && rate2 > 0.85
            when {
                slow && slow2 -> {
                    val tdeeStable = energyNow.tdee != null && energyPrev.tdee != null && energyPrev.tdee!! > 0 &&
                        abs(energyNow.tdee!! - energyPrev.tdee!!) / energyPrev.tdee!! <= AdaptiveEnergy.NEAT_DROP
                    val trendFlat = trendRate10 != null && abs(trendRate10) < 0.15
                    if (trendFlat && waistDelta != null && waistDelta <= -0.5 && tdeeStable)
                        recs += Recommendation("diet", "Hold — water retention",
                            "Trend weight flat 10 days, waist still falling, Prep Tracker TDEE stable. Not a stall — do not advance the lever.")
                    else if (waistDelta != null && waistDelta <= -0.5) recs += Recommendation("diet", "Hold — scale is masking", "Rate ${"%.2f".format(rate)} kg/wk but waist down ${"%.1f".format(abs(waistDelta))} cm in two weeks. Water, not a stall.")
                    else {
                        val since = p.leverDate?.let { ChronoUnit.DAYS.between(LocalDate.parse(it), ref) } ?: 99
                        when {
                            since < 14 -> recs += Recommendation("diet", "Hold — last lever applied $since days ago", "Give a lever two full weeks before pulling the next one.")
                            p.lever == 0 -> recs += Recommendation("diet", "Lever 1: audit the food log", "Two slow weeks. Before cutting anything: check logged meals against 4×(C+P)+9×F and re-weigh the portions that drift.", 1)
                            p.lever == 1 -> recs += Recommendation("diet", "Lever 2: add the 45-min LISS block", "Log verified and still slow. 5–6 × 45 min walking/incline/bike, separated from lifting. Not food yet.", 2)
                            p.lever == 2 -> recs += Recommendation("diet", "Lever 3: −25 g carbs (rice at dinner)", "The only food cut authorised. ~100 kcal/day.", 3)
                            else -> recs += Recommendation("diet", "All levers used — hold and review", "Rate still below target after logging, cardio and the carb cut. Look at sleep, adherence, water.")
                        }
                    }
                }
                fast && fast2 -> recs += Recommendation("diet", "Too fast — add 25 g carbs (+100 kcal)", "Two weeks above 0.85 kg/wk (~1% BW). Past this rate the loss starts coming from muscle.")
                slow -> recs += Recommendation("diet", "Hold — one slow week", "Rate ${"%.2f".format(rate)} kg/wk vs target $target. One week is noise; act only if next week repeats it.")
                else -> recs += Recommendation("diet", "Hold — on target", "Rate ${"%.2f".format(rate)} kg/wk (${"%.2f".format(ratePct)}% BW) vs target $target kg/wk.")
            }
        }
        if (steps7.n >= 3 && steps7.avg!! < 8500) flags += "Steps averaging ${"%,d".format(steps7.avg.toInt())} — floor is 8,500 including rest days."
        if (sleep7.n >= 3 && sleep7.avg!! < 7 && !lowSleep) flags += "Sleep averaging ${"%.1f".format(sleep7.avg)} h — floor is 7, aim 7.5–9. Caffeine cut-off 3 pm."
        if (pro7.n >= 3 && pro7.avg!! < p.proteinLow) flags += "Protein averaging ${pro7.avg.toInt()} g — range is ${p.proteinLow}–${p.proteinHigh}."
        val hrvNow = hrv7.avg; val rhrNow = rhr7.avg
        if (hrv7.n >= 3 && rhr7.n >= 3 && hrvNow != null && rhrNow != null) {
            val hrvBase = avgWindow(days, ref.minusDays(7), 14) { it.hrvMs }.let { if (it.n >= 5) it.avg else null }
            val rhrBase = avgWindow(days, ref.minusDays(7), 14) { it.rhr?.toDouble() }.let { if (it.n >= 5) it.avg else null }
            if (hrvBase != null && rhrBase != null && hrvNow < hrvBase * 0.9 && rhrNow > rhrBase + 3)
                flags += "HRV down ${"%.0f".format((1 - hrvNow / hrvBase) * 100)}% and RHR up ${"%.0f".format(rhrNow - rhrBase)} bpm vs the prior two weeks — fatigue signal. Sleep first, then Compressed."
        }
        val hard = (0 until 7).sumOf { i ->
            sessions[wk.plusDays(i.toLong())]?.feel?.values?.count { it.equals("hard", ignoreCase = true) } ?: 0
        }
        if (hard > 0) flags += "$hard exercise${if (hard == 1) "" else "s"} marked hard this week — soft fatigue signal, read alongside HRV."

        val tdeeNow = energyNow.tdee; val tdeeWas = energyPrev.tdee
        if (tdeeNow != null && tdeeWas != null && tdeeWas > 0) {
            val drop = (tdeeWas - tdeeNow) / tdeeWas
            val volNow = weekSetCount(sessions, wk)
            val volPrev = weekSetCount(sessions, wk.minusDays(21))
            if (drop > AdaptiveEnergy.NEAT_DROP && volNow > 0 && volNow == volPrev)
                flags += "Prep Tracker TDEE dropped ${(drop * 100).toInt()}% over ${energyNow.windowDays} days while training volume held ($volNow sets/wk). Likely reduced NEAT — check steps before touching food."
        }

        return Review(ref, now7.avg, now7.n, prev7.avg, rate, rate2, target, ratePct, sleep7, steps7, kcal7, pro7, rhr7, hrv7,
            waistNow, waistDelta, anchors, drops, dropsPrev, phase, mode, bf, targetWeight, toLose, weeksAtRate, weeksAtTarget, recs, flags,
            trendKg, trendRate)
    }
}
