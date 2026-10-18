package com.kishan.preptracker

import com.kishan.preptracker.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AdaptiveEnergyTest {

    private fun linearWeights(start: LocalDate, n: Int, fromKg: Double, kgPerWeek: Double): List<Pair<LocalDate, Double>> =
        (0 until n).map { i -> start.plusDays(i.toLong()) to (fromKg - kgPerWeek * i / 7.0) }

    @Test fun `trend weight tracks a linear loss within 0_1 kg after 21 days`() {
        val start = LocalDate.parse("2026-08-26")
        val raw = linearWeights(start, 21, 88.0, 0.45)
        val trend = TrendWeight.compute(raw)
        assertEquals(21, trend.size)
        val rate = TrendWeight.rateKgPerWeek(trend, 14)
        assertNotNull(rate)
        assertEquals(0.45, rate!!, 0.1)
    }

    @Test fun `trend weight ignores a single 1_5 kg refeed spike`() {
        val start = LocalDate.parse("2026-08-26")
        val raw = linearWeights(start, 21, 88.0, 0.0).toMutableList()
        raw[10] = raw[10].first to raw[10].second + 1.5
        val trend = TrendWeight.compute(raw)
        val moved = kotlin.math.abs(trend[11].second - trend[9].second)
        assertTrue("trend moved $moved kg across the spike", moved < 0.3)
    }

    @Test fun `TDEE recovers a known expenditure from synthetic data`() {
        val ref = LocalDate.parse("2026-09-15")
        val kgPerWeek = 0.64
        val days = (0 until 21).associate { i ->
            val d = ref.minusDays(i.toLong())
            val w = 88.0 + kgPerWeek * i / 7.0
            d to DayLogModel(d.toString(), weight = w, kcal = 2000)
        }
        val e = AdaptiveEnergy.estimate(days, ref)
        assertNotNull(e.tdee)
        val trend = TrendWeight.compute(days.mapNotNull { (d, m) -> m.weight?.let { d to it } })
        val rate = TrendWeight.rateKgPerWeek(trend, 14)
        assertNotNull(rate)
        assertEquals(0.64, rate!!, 0.1)
        // Energy balance: TDEE ≈ intake + rate * kcalPerKg / 7. EWMA lag on a 21-day
        // window biases the start–end difference, so check against the trend rate.
        val expected = 2000.0 + rate * KCAL_PER_KG / 7.0
        assertEquals(expected, e.tdee!!, 75.0)
    }

    @Test fun `TDEE returns NONE confidence with fewer than 10 logged intake days`() {
        val ref = LocalDate.parse("2026-09-15")
        val days = (0 until 21).associate { i ->
            val d = ref.minusDays(i.toLong())
            val kcal = if (i < 9) 2000 else null
            d to DayLogModel(d.toString(), weight = 88.0, kcal = kcal)
        }
        val e = AdaptiveEnergy.estimate(days, ref)
        assertNull(e.tdee)
        assertEquals(EnergyEstimate.Confidence.NONE, e.confidence)
        assertEquals(9, e.intakeDays)
    }

    @Test fun `days below 800 kcal are excluded as missed logs, not counted as fasting`() {
        val ref = LocalDate.parse("2026-09-15")
        val days = (0 until 21).associate { i ->
            val d = ref.minusDays(i.toLong())
            val kcal = if (i % 2 == 0) 2000 else 400
            d to DayLogModel(d.toString(), weight = 88.0 - 0.02 * i, kcal = kcal)
        }
        val e = AdaptiveEnergy.estimate(days, ref)
        assertEquals(11, e.intakeDays)
        assertNotEquals(EnergyEstimate.Confidence.NONE, e.confidence)
        assertNotNull(e.tdee)
        // If 400 kcal days were treated as fasts, mean intake would be ~1200 and TDEE would collapse.
        assertTrue("TDEE ${e.tdee} treated 400 kcal days as real intake", e.tdee!! > 1800)
    }

    @Test fun `target never moves more than 150 kcal from the previous target in one step`() {
        val energy = EnergyEstimate(3000.0, 21, 21, EnergyEstimate.Confidence.HIGH, "ok")
        val t = NutritionTargets.compute(energy, Profile(), currentTarget = 2050)
        assertEquals("adaptive", t.source)
        assertTrue(kotlin.math.abs(t.kcal - 2050) <= NutritionTargets.MAX_WEEKLY_CHANGE)
        assertEquals(2200, t.kcal)
    }

    @Test fun `target refuses to go below the 1900 floor and returns the cardio-lever rationale`() {
        val energy = EnergyEstimate(2200.0, 21, 21, EnergyEstimate.Confidence.HIGH, "ok")
        val t = NutritionTargets.compute(energy, Profile(), currentTarget = 2000)
        assertEquals("fixed", t.source)
        assertEquals(NutritionTargets.FIXED_KCAL, t.kcal)
        assertTrue(t.rationale.contains("floor"))
        assertTrue(t.rationale.contains("cardio"))
    }
}
