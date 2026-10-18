package com.kishan.preptracker.domain

import java.time.LocalDate

data class PrHit(val id: String, val name: String, val prev: Double, val now: Double)

object PersonalRecords {
    fun detect(
        date: LocalDate,
        session: SessionData,
        sessions: Map<LocalDate, SessionData>,
        days: Map<LocalDate, DayLogModel>,
        p: Profile,
    ): List<PrHit> {
        val bw = RulesEngine.bodyweightOn(days, date, p.startWeight)
        return session.sets.keys.mapNotNull { id ->
            val ex = Program.resolve(id, session) ?: return@mapNotNull null
            val now = Progression.bestE1rm(ex, session, bw)
            if (now <= 0) return@mapNotNull null
            val prev = sessions.entries
                .filter { it.key.isBefore(date) }
                .maxOfOrNull { Progression.bestE1rm(ex, it.value, RulesEngine.bodyweightOn(days, it.key, p.startWeight)) }
                ?: 0.0
            if (now > prev && prev > 0) PrHit(id, ex.name, prev, now) else null
        }
    }

    fun summary(hits: List<PrHit>): String? {
        if (hits.isEmpty()) return null
        return hits.joinToString("; ") { "${it.name}: new best est. 1RM, ${Load.displayLb(it.prev)} → ${Load.displayLb(it.now)} lb" }
    }
}
