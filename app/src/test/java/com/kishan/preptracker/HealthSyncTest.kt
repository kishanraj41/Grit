package com.kishan.preptracker

import com.kishan.preptracker.domain.DayLogModel
import com.kishan.preptracker.health.HealthDay
import com.kishan.preptracker.health.HealthWindows
import com.kishan.preptracker.sync.HealthSync
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class HealthSyncTest {

    private fun incoming(
        date: String = "2026-09-20",
        steps: Long? = 9000,
        sleepHours: Double? = 7.2,
        rhr: Long? = 52,
        hrvMs: Double? = 41.0,
        weightKg: Double? = 86.54,
        kcal: Double? = 2050.4,
        proteinG: Double? = 190.6,
    ) = HealthDay(LocalDate.parse(date), steps, sleepHours, rhr, hrvMs, weightKg, kcal, proteinG)

    @Test fun `manual weight is not overwritten`() {
        val cur = DayLogModel("2026-09-20", weight = 86.1, weightSrc = "manual")
        val next = HealthSync.merge(cur, incoming())
        assertEquals(86.1, next.weight)
        assertEquals("manual", next.weightSrc)
        assertEquals(52, next.rhr)
        assertEquals("hc", next.rhrSrc)
    }

    @Test fun `empty day takes Health Connect values including rhr and hrv`() {
        val cur = DayLogModel("2026-09-20")
        val next = HealthSync.merge(cur, incoming())
        assertEquals(86.54, next.weight)
        assertEquals("hc", next.weightSrc)
        assertEquals(52, next.rhr)
        assertEquals("hc", next.rhrSrc)
        assertEquals(41.0, next.hrvMs)
        assertEquals("hc", next.hrvSrc)
        assertEquals(2050, next.kcal)
        assertEquals(191, next.protein)
        assertEquals(9000, next.steps)
        assertEquals("hc", next.stepsSrc)
    }

    @Test fun `steps keep the chosen Health Connect writer`() {
        val cur = DayLogModel("2026-09-20")
        val next = HealthSync.merge(cur, incoming(steps = 9200).copy(stepsApp = "com.brand.watch"))
        assertEquals(9200, next.steps)
        assertEquals("hc:com.brand.watch", next.stepsSrc)
    }

    @Test fun `hc-sourced fields update when Health Connect sends a new value`() {
        val cur = DayLogModel("2026-09-20", rhr = 55, rhrSrc = "hc", hrvMs = 38.0, hrvSrc = "hc")
        val next = HealthSync.merge(cur, incoming(rhr = 51, hrvMs = 44.44))
        assertEquals(51, next.rhr)
        assertEquals("hc", next.rhrSrc)
        assertEquals(44.4, next.hrvMs)
        assertEquals("hc", next.hrvSrc)
    }

    @Test fun `Health Connect gap leaves the previous value`() {
        val cur = DayLogModel("2026-09-20", rhr = 52, rhrSrc = "hc", sleepH = 7.0, sleepSrc = "hc")
        val next = HealthSync.merge(cur, incoming(rhr = null, sleepHours = null, hrvMs = null))
        assertEquals(52, next.rhr)
        assertEquals("hc", next.rhrSrc)
        assertEquals(7.0, next.sleepH)
        assertEquals("hc", next.sleepSrc)
        assertNull(next.hrvMs)
    }
}

class HealthWindowsTest {
    private val zone = ZoneOffset.UTC
    private val date = LocalDate.parse("2026-09-20")

    @Test fun `night window is previous 18 00 to today 14 00`() {
        val start = HealthWindows.nightStart(date, zone).atZone(zone)
        val end = HealthWindows.nightEnd(date, zone).atZone(zone)
        assertEquals(LocalDate.parse("2026-09-19"), start.toLocalDate())
        assertEquals(LocalTime.of(18, 0), start.toLocalTime())
        assertEquals(date, end.toLocalDate())
        assertEquals(LocalTime.of(14, 0), end.toLocalTime())
    }

    @Test fun `sleep overlapping the night window is clipped not double-counted`() {
        val nightStart = HealthWindows.nightStart(date, zone)
        val nightEnd = HealthWindows.nightEnd(date, zone)
        val sessionStart = date.minusDays(1).atTime(16, 0).atZone(zone).toInstant()
        val sessionEnd = date.atTime(7, 0).atZone(zone).toInstant()
        val sec = HealthWindows.overlapSeconds(sessionStart, sessionEnd, nightStart, nightEnd)
        assertEquals(13 * 3600L, sec)
        val nextNight = HealthWindows.nightStart(date.plusDays(1), zone)
        val nextEnd = HealthWindows.nightEnd(date.plusDays(1), zone)
        assertEquals(0L, HealthWindows.overlapSeconds(sessionStart, sessionEnd, nextNight, nextEnd))
    }

    @Test fun `HRV at 10 00 belongs to that morning, 16 00 does not`() {
        val morning = date.atTime(10, 0).atZone(zone).toInstant()
        val afternoon = date.atTime(16, 0).atZone(zone).toInstant()
        assertTrue(HealthWindows.inNight(morning, date, zone))
        assertFalse(HealthWindows.inNight(afternoon, date, zone))
    }
}
