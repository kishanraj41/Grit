package com.kishan.preptracker

import com.kishan.preptracker.data.BackupCodec
import com.kishan.preptracker.data.BackupFile
import com.kishan.preptracker.data.BackupSession
import com.kishan.preptracker.domain.DayLogModel
import com.kishan.preptracker.domain.Profile
import com.kishan.preptracker.domain.SessionData
import com.kishan.preptracker.domain.SetEntry
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {

    private fun sample(): BackupFile = BackupFile(
        format = BackupFile.FORMAT,
        version = BackupFile.VERSION,
        exportedAt = "2026-09-20T22:00:00",
        profile = Profile(goal = "B", lever = 1, leverDate = "2026-09-14"),
        days = listOf(DayLogModel("2026-09-20", weight = 86.5, weightSrc = "manual", waistCm = 91.0)),
        sessions = listOf(
            BackupSession(
                "2026-09-15",
                SessionData("mon", "S", mutableMapOf("ohp" to mutableListOf(SetEntry(60.0, 6, 2, true))), "2026-09-15T18:00"),
            ),
        ),
    )

    @Test fun `round-trip keeps days sessions and profile fields`() {
        val original = sample()
        val restored = BackupCodec.decode(BackupCodec.encode(original))
        assertEquals(original.format, restored.format)
        assertEquals(original.version, restored.version)
        assertEquals(original.exportedAt, restored.exportedAt)
        assertEquals("B", restored.profile.goal)
        assertEquals(1, restored.profile.lever)
        assertEquals("2026-09-14", restored.profile.leverDate)
        assertEquals(1, restored.days.size)
        assertEquals(86.5, restored.days[0].weight)
        assertEquals("manual", restored.days[0].weightSrc)
        assertEquals(91.0, restored.days[0].waistCm)
        assertEquals(1, restored.sessions.size)
        assertEquals("mon", restored.sessions[0].session.dayKey)
        assertEquals(60.0, restored.sessions[0].session.sets["ohp"]!![0].load)
        assertTrue(restored.sessions[0].session.sets["ohp"]!![0].done)
    }

    @Test fun `unknown JSON keys are ignored so older app files still load`() {
        val extra = BackupCodec.encode(sample()).replaceFirst("{", """{"futureField": true, """)
        val restored = BackupCodec.decode(extra)
        assertEquals(BackupFile.FORMAT, restored.format)
        assertEquals(86.5, restored.days[0].weight)
    }

    @Test fun `wrong format is rejected`() {
        val json = BackupCodec.encode(sample()).replace(BackupFile.FORMAT, "not-grit")
        try {
            BackupCodec.decode(json)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Not a Grit backup"))
        }
    }

    @Test fun `newer backup version is rejected`() {
        val json = BackupCodec.encode(sample()).replace("\"version\": ${BackupFile.VERSION}", "\"version\": ${BackupFile.VERSION + 1}")
        try {
            BackupCodec.decode(json)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("newer than this app"))
        }
    }

    @Test fun `empty JSON is rejected`() {
        try {
            BackupCodec.decode("{}")
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Could not read backup file"))
        }
    }
}
