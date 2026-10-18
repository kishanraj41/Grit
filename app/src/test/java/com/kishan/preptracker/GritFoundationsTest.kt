package com.kishan.preptracker

import com.kishan.preptracker.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class OneRepMaxTest {
    @Test fun `epley 100 x 5 is 116_7`() {
        assertEquals(100 * (1 + 5 / 30.0), OneRepMax.epley(100.0, 5), 1e-9)
    }

    @Test fun `a true single is the measurement`() {
        assertEquals(140.0, OneRepMax.estimate(140.0, 1, OneRepMax.Formula.EPLEY)!!, 1e-9)
    }

    @Test fun `past the cap epley refuses to guess`() {
        assertNull(OneRepMax.estimate(60.0, 20, OneRepMax.Formula.EPLEY))
    }
}

class LaddersTest {
    private val squat = Program.EX["squat"]!!
    private val d = LocalDate.parse("2026-09-22")

    @Test fun `linear adds the increment when every set hits the top`() {
        val sessions = mapOf(
            d.minusDays(1) to SessionData("tue", "S", mutableMapOf(
                "squat" to mutableListOf(SetEntry(100.0, 8, 2, true), SetEntry(100.0, 8, 2, true)),
            ), "t"),
        )
        val p = Ladders.prescribe(Ladder.LINEAR, squat, d, sessions)
        assertEquals(102.5, p.load!!, 1e-9)
    }

    @Test fun `warmup sets do not advance the ladder`() {
        val sessions = mapOf(
            d.minusDays(1) to SessionData("tue", "S", mutableMapOf(
                "squat" to mutableListOf(
                    SetEntry(60.0, 8, 2, true, phase = "warmup"),
                    SetEntry(100.0, 5, 2, true),
                ),
            ), "t"),
        )
        val p = Ladders.prescribe(Ladder.DOUBLE, squat, d, sessions)
        assertEquals(100.0, p.load!!, 1e-9)
        assertTrue(p.note.contains("aim"))
    }
}

class MusclesHeatmapCsvTest {
    @Test fun `a squat session lights quads and glutes`() {
        val d = LocalDate.parse("2026-09-22")
        val sessions = mapOf(
            d to SessionData("tue", "S", mutableMapOf(
                "squat" to MutableList(8) { SetEntry(100.0, 5, 2, true) },
            ), "t"),
        )
        val snap = Muscles.snapshot(d, sessions)
        val quads = snap.first { it.id == "quads" }
        assertEquals("tired", quads.status)
        assertEquals(d, quads.lastTrained)
        assertEquals("ready", snap.first { it.id == "biceps" }.status)
    }

    @Test fun `heatmap levels scale with work sets`() {
        assertEquals(0, Heatmap.level(0))
        assertEquals(4, Heatmap.level(30))
    }

    @Test fun `heatmap week start shifts the first cell`() {
        val today = LocalDate.parse("2026-09-23") // Wednesday
        val mon = Heatmap.cells(emptyMap(), weeks = 1, today = today, weekStart = "mon")
        val sun = Heatmap.cells(emptyMap(), weeks = 1, today = today, weekStart = "sun")
        assertEquals(java.time.DayOfWeek.MONDAY, mon.first().date.dayOfWeek)
        assertEquals(java.time.DayOfWeek.SUNDAY, sun.first().date.dayOfWeek)
    }

    @Test fun `Strong CSV maps squat onto the v4 catalog`() {
        val csv = """
            Date,Exercise Name,Weight,Reps,Set Order
            2026-09-15,High-bar back squat,100,5,1
            2026-09-15,High-bar back squat,100,5,2
        """.trimIndent()
        val parsed = WorkoutCsv.parse(csv)
        assertEquals(1, parsed.sessions.size)
        val s = parsed.sessions[LocalDate.parse("2026-09-15")]!!
        assertEquals(2, s.sets["squat"]?.size)
        assertEquals(100.0, s.sets["squat"]!![0].load)
        assertTrue(parsed.unmatched.isEmpty())
    }
}

class BalanceTest {
    @Test fun `no pressing history is no-data not a fake ratio`() {
        val rows = Balance.snapshot(emptyMap(), emptyMap(), Profile())
        assertTrue(rows.all { it.status == "no-data" })
    }
}

class TemplatesTest {
    @Test fun `v4 weekday clock still lands Monday on arms`() {
        val d = LocalDate.parse("2026-09-21") // Monday
        val plan = Templates.dayFor(d, Profile(weekTemplate = "v4", schedule = "week"), emptyMap())
        assertEquals("mon", plan?.key)
        assertEquals("Arms + Delts", plan?.name)
    }

    @Test fun `queue advances to pull after a logged push`() {
        val d = LocalDate.parse("2026-09-22")
        val prior = mapOf(
            d.minusDays(1) to SessionData("push", "S", mutableMapOf(
                "incbench" to mutableListOf(SetEntry(80.0, 6, 2, true)),
            ), "t"),
        )
        val plan = Templates.dayFor(d, Profile(weekTemplate = "ppl", schedule = "queue"), prior)
        assertEquals("pull", plan?.key)
    }

    @Test fun `queue with no history starts at the first slot`() {
        val d = LocalDate.parse("2026-09-20") // Sunday
        val plan = Templates.dayFor(d, Profile(weekTemplate = "ppl", schedule = "queue"), emptyMap())
        assertEquals("push", plan?.key)
    }
}

class AttendanceTest {
    @Test fun `a finished work set counts as a gym hit`() {
        val d = LocalDate.parse("2026-09-22")
        val sessions = mapOf(d to SessionData("tue", "S", mutableMapOf(
            "squat" to mutableListOf(SetEntry(100.0, 5, 2, true)),
        ), "t"))
        assertTrue(Attendance.trained(d, sessions, emptyMap()))
        assertFalse(Attendance.trained(d.plusDays(1), sessions, emptyMap()))
    }

    @Test fun `check-in without sets still counts`() {
        val d = LocalDate.parse("2026-09-22")
        val days = mapOf(d to DayLogModel(d.toString(), gymAt = "2026-09-22T18:00"))
        assertTrue(Attendance.trained(d, emptyMap(), days))
    }

    @Test fun `streak skips an empty current week`() {
        val today = LocalDate.parse("2026-09-22") // Tuesday
        val lastMon = LocalDate.parse("2026-09-14")
        val sessions = mapOf(lastMon to SessionData("mon", "S", mutableMapOf(
            "ohp" to mutableListOf(SetEntry(60.0, 6, 2, true)),
        ), "t"))
        assertEquals(1, Attendance.streakWeeks(today, sessions, emptyMap(), "mon"))
    }
}

class WeekBoardTest {
    @Test fun `blank slots follow the v4 weekday clock`() {
        val d = LocalDate.parse("2026-09-21")
        val plan = Templates.dayFor(d, Profile(weekTemplate = "v4", weekSlots = ""), emptyMap())
        assertEquals("mon", plan?.key)
    }

    @Test fun `a saved rest day wins over the template`() {
        val d = LocalDate.parse("2026-09-21")
        val slots = "MON=,TUE=pull,WED=,THU=,FRI=,SAT=,SUN="
        val plan = Templates.dayFor(d, Profile(weekTemplate = "ppl", weekSlots = slots), emptyMap())
        assertNull(plan)
    }
}

class LiftsLibraryTest {
    @Test fun `every v4 id is in the library`() {
        Program.EX.keys.forEach { id ->
            assertNotNull(id, Lifts.BY_ID[id])
        }
    }

    @Test fun `chest filter hides squats`() {
        val hits = Lifts.search("", "chest", null)
        assertTrue(hits.any { it.id == "incbench" })
        assertTrue(hits.none { it.id == "squat" })
    }
}

class MuscleDoseTest {
    @Test fun `seven-day volume counts squat work sets on quads`() {
        val d = LocalDate.parse("2026-09-22")
        val sessions = mapOf(
            d to SessionData("tue", "S", mutableMapOf(
                "squat" to MutableList(8) { SetEntry(100.0, 5, 2, true) },
            ), "t"),
        )
        val quads = Muscles.snapshot(d, sessions).first { it.id == "quads" }
        assertEquals(8.0, quads.volume7, 1e-9)
        assertEquals(0, quads.quietDays)
    }
}
