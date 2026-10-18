package com.kishan.preptracker

import com.kishan.preptracker.health.StepSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepSourcesTest {
    private val watchA = "com.brand.watch"
    private val watchB = "com.other.watch"
    private val food = "com.diet.logger"

    @Test fun `Auto keeps the largest writer and does not sum`() {
        val picked = StepSources.pick(mapOf(watchA to 9_200L, watchB to 18_000L))
        assertEquals(watchB to 18_000L, picked)
    }

    @Test fun `a pinned package wins even when another reports more`() {
        val picked = StepSources.pick(mapOf(watchA to 9_200L, watchB to 18_000L), prefer = watchA)
        assertEquals(watchA to 9_200L, picked)
    }

    @Test fun `a missing pin falls back to Auto`() {
        val picked = StepSources.pick(mapOf(watchA to 11_000L), prefer = "com.not.installed")
        assertEquals(watchA to 11_000L, picked)
    }

    @Test fun `empty or zero origins yield nothing`() {
        assertNull(StepSources.pick(emptyMap()))
        assertNull(StepSources.pick(mapOf(watchA to 0L)))
    }

    @Test fun `food pick uses kcal totals the same way`() {
        val picked = StepSources.pickDouble(mapOf(food to 2100.0, watchA to 400.0), prefer = food)
        assertEquals(food to 2100.0, picked)
    }
}
