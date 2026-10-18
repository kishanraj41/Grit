package com.kishan.preptracker

import com.kishan.preptracker.health.StepSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepSourcesTest {
    @Test fun `Fitbit wins even when Samsung reports more`() {
        val picked = StepSources.pick(
            mapOf(
                StepSources.SAMSUNG_HEALTH to 18_000L,
                StepSources.FITBIT to 9_200L,
            ),
        )
        assertEquals(StepSources.FITBIT to 9_200L, picked)
    }

    @Test fun `Samsung is used when Fitbit is absent`() {
        val picked = StepSources.pick(mapOf(StepSources.SAMSUNG_HEALTH to 11_000L))
        assertEquals(StepSources.SAMSUNG_HEALTH to 11_000L, picked)
    }

    @Test fun `unknown writers are not summed — largest single package wins`() {
        val picked = StepSources.pick(mapOf("com.other.a" to 4_000L, "com.other.b" to 7_000L))
        assertEquals("com.other.b" to 7_000L, picked)
    }

    @Test fun `empty or zero origins yield nothing`() {
        assertNull(StepSources.pick(emptyMap()))
        assertNull(StepSources.pick(mapOf(StepSources.FITBIT to 0L)))
    }
}
