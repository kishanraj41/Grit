package com.kishan.preptracker

import com.kishan.preptracker.domain.Load
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadTest {
    @Test fun `100 kg is 220 point 5 lb`() {
        assertEquals(220.5, Load.roundLb(Load.kgToLb(100.0)), 0.01)
    }

    @Test fun `45 lb bar converts back to the same half-pound`() {
        val kg = Load.lbToKg(45.0)
        assertEquals("45", Load.displayLb(kg))
    }

    @Test fun `step 5 lb from empty starts at 5`() {
        val kg = Load.stepLb(null, 5.0)
        assertEquals("5", Load.displayLb(kg))
    }

    @Test fun `step minus 5 from 45 lands on 40`() {
        val kg = Load.stepLb(Load.lbToKg(45.0), -5.0)
        assertEquals("40", Load.displayLb(kg))
    }
}
