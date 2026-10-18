package com.kishan.preptracker

import com.kishan.preptracker.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Guards the two pieces of logic that decide what the app tells Kishan to do.
 * Run with: ./gradlew test
 */
class RulesEngineTest {

    private fun days(vararg e: Pair<String, DayLogModel>) = e.associate { LocalDate.parse(it.first) to it.second }

    private fun weightRun(start: String, n: Int, from: Double, perWeek: Double, sleep: Double = 7.2, steps: Int = 8600): Map<LocalDate, DayLogModel> {
        var d = LocalDate.parse(start)
        val out = mutableMapOf<LocalDate, DayLogModel>()
        repeat(n) { i ->
            out[d] = DayLogModel(d.toString(), weight = from - perWeek * i / 7.0, sleepH = sleep, steps = steps)
            d = d.plusDays(1)
        }
        return out
    }

    @Test fun `rate is computed from two 7-day windows`() {
        val d = weightRun("2026-08-25", 22, 88.4, 0.45)
        val r = RulesEngine.weeklyReview(LocalDate.parse("2026-09-15"), d, emptyMap(), Profile())
        assertNotNull(r.rate)
        assertEquals(0.45, r.rate!!, 0.05)
    }

    @Test fun `on-target rate holds everything`() {
        val d = weightRun("2026-08-25", 22, 88.4, 0.45)
        val r = RulesEngine.weeklyReview(LocalDate.parse("2026-09-15"), d, emptyMap(), Profile())
        assertEquals("S", r.mode)
        assertTrue(r.recs.any { it.kind == "diet" && it.title.contains("on target") })
    }

    @Test fun `short sleep forces Compressed`() {
        val d = weightRun("2026-08-25", 22, 88.4, 0.45, sleep = 6.0)
        val r = RulesEngine.weeklyReview(LocalDate.parse("2026-09-15"), d, emptyMap(), Profile())
        assertEquals("C", r.mode)
        assertTrue(r.recs.first().why.contains("6.0"))
    }

    @Test fun `two slow weeks with falling waist is held, not cut`() {
        val d = weightRun("2026-08-25", 22, 88.4, 0.20).toMutableMap()
        d[LocalDate.parse("2026-09-01")] = d[LocalDate.parse("2026-09-01")]!!.copy(waistCm = 92.0)
        d[LocalDate.parse("2026-09-14")] = d[LocalDate.parse("2026-09-14")]!!.copy(waistCm = 91.0)
        val r = RulesEngine.weeklyReview(LocalDate.parse("2026-09-15"), d, emptyMap(), Profile())
        assertTrue(r.recs.any { it.title.contains("scale is masking") })
        assertTrue(r.recs.none { it.lever != null })
    }

    @Test fun `two slow weeks with flat waist pulls lever one`() {
        val d = weightRun("2026-08-25", 22, 88.4, 0.20)
        val r = RulesEngine.weeklyReview(LocalDate.parse("2026-09-15"), d, emptyMap(), Profile())
        assertEquals(1, r.recs.firstOrNull { it.lever != null }?.lever)
    }

    @Test fun `lever does not advance inside the two-week wait`() {
        val d = weightRun("2026-08-25", 22, 88.4, 0.20)
        val p = Profile(lever = 1, leverDate = "2026-09-10")
        val r = RulesEngine.weeklyReview(LocalDate.parse("2026-09-15"), d, emptyMap(), p)
        assertTrue(r.recs.any { it.title.contains("last lever applied") })
        assertTrue(r.recs.none { it.lever != null })
    }

    @Test fun `too fast adds carbs back`() {
        val d = weightRun("2026-08-25", 22, 90.0, 1.0)
        val r = RulesEngine.weeklyReview(LocalDate.parse("2026-09-15"), d, emptyMap(), Profile())
        assertTrue(r.recs.any { it.title.contains("Too fast") })
    }

    @Test fun `step and protein floors raise flags`() {
        val d = weightRun("2026-08-25", 22, 88.4, 0.45, steps = 7000)
            .mapValues { it.value.copy(protein = 170) }
        val r = RulesEngine.weeklyReview(LocalDate.parse("2026-09-15"), d, emptyMap(), Profile())
        assertTrue(r.flags.any { it.contains("Steps averaging") })
        assertTrue(r.flags.any { it.contains("Protein averaging") })
    }

    @Test fun `anchor drop over two weeks forces Compressed`() {
        val d = weightRun("2026-08-25", 22, 88.4, 0.45)
        fun sess(load: Double) = SessionData("mon", "S",
            mutableMapOf(
                "cgb" to mutableListOf(SetEntry(load, 8, 2, true)),
                "ohp" to mutableListOf(SetEntry(load, 8, 2, true)),
            ), "x")
        val s = mapOf(
            LocalDate.parse("2026-08-31") to sess(90.0),
            LocalDate.parse("2026-09-07") to sess(84.0),
            LocalDate.parse("2026-09-14") to sess(78.0),
        )
        val r = RulesEngine.weeklyReview(LocalDate.parse("2026-09-15"), d, s, Profile())
        assertEquals(2, r.drops)
        assertEquals(2, r.dropsPrev)
        assertEquals("C", r.mode)
    }

    @Test fun `target weight and body fat follow the DEXA FFM`() {
        val d = days("2026-09-15" to DayLogModel("2026-09-15", weight = 87.9))
        val r = RulesEngine.weeklyReview(LocalDate.parse("2026-09-15"), d, emptyMap(), Profile())
        assertEquals(79.84, r.targetWeight, 0.05)
        assertEquals(22.8, r.bf!!, 0.1)
    }
}

class ProgressionTest {

    private fun session(exId: String, vararg sets: Pair<Double, Int>) = SessionData(
        "mon", "S", mutableMapOf(exId to sets.map { SetEntry(it.first, it.second, 2, true) }.toMutableList()), "x"
    )

    @Test fun `full ladder adds load and resets reps`() {
        val ex = Program.EX["cgb"]!!
        val s = mapOf(LocalDate.parse("2026-09-07") to session("cgb", 80.0 to 8, 80.0 to 8, 80.0 to 8))
        val p = Progression.prescribe(ex, LocalDate.parse("2026-09-14"), s)
        assertEquals(82.5, p.load!!, 0.001)
        assertEquals(6, p.reps)
    }

    @Test fun `incomplete ladder keeps load and asks for one more rep`() {
        val ex = Program.EX["cgb"]!!
        val s = mapOf(LocalDate.parse("2026-09-07") to session("cgb", 82.5 to 6, 82.5 to 6, 82.5 to 5))
        val p = Progression.prescribe(ex, LocalDate.parse("2026-09-14"), s)
        assertEquals(82.5, p.load!!, 0.001)
        assertEquals(6, p.reps)
    }

    @Test fun `no history asks for the bottom of the range`() {
        val p = Progression.prescribe(Program.EX["squat"]!!, LocalDate.parse("2026-09-14"), emptyMap())
        assertNull(p.load)
        assertEquals(5, p.reps)
    }

    @Test fun `bodyweight lifts fold bodyweight into est 1RM`() {
        val ex = Program.EX["pullup"]!!
        val s = session("pullup", 10.0 to 6)
        val e = Progression.bestE1rm(ex, s, 87.0)
        assertEquals(Progression.e1rm(97.0, 6), e, 0.001)
    }

    @Test fun `program totals match the published v4 plan`() {
        val counts = mutableMapOf<String, Int>()
        Program.DAYS.values.forEach { d -> d.exercises.forEach { counts[it.id] = (counts[it.id] ?: 0) + it.standardSets } }
        val standardTotal = counts.values.sum()
        assertEquals(98, standardTotal)
        val compressed = Program.DAYS.values.sumOf { d -> d.exercises.sumOf { it.compressedSets } }
        assertEquals(72, compressed)
        // side delts 16 = cablat 12 + dblat 4
        assertEquals(16, (counts["cablat"] ?: 0) + (counts["dblat"] ?: 0))
        // biceps 15, triceps 15
        assertEquals(15, listOf("preacher", "inccurl", "hammer", "bayes", "ezcurl").sumOf { counts[it] ?: 0 })
        assertEquals(15, listOf("cgb", "ohext", "pushdown", "dips").sumOf { counts[it] ?: 0 })
    }
}
