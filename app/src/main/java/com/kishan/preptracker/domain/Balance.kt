package com.kishan.preptracker.domain

import java.time.LocalDate

data class BalanceRow(
    val id: String,
    val name: String,
    val haveLb: Double?,
    val wantLb: Double?,
    val pctOfTarget: Double?,
    val status: String,
)

/**
 * Grit's own snapshot against close-grip bench as the pressing yardstick.
 * Ratios are public training heuristics (Poliquin-style close-grip = 100),
 * applied only to v4 anchors we actually log — not a copied table.
 */
object Balance {
    private const val BAND = 0.05

    data class Role(val id: String, val name: String, val ofAnchor: Double)

    private val roles = listOf(
        Role("cgb", "Close-grip bench (yardstick)", 1.00),
        Role("incbench", "Incline bench", 0.91),
        Role("ohp", "Overhead press", 0.66),
        Role("dips", "Dips (body + load)", 1.17),
        Role("pullup", "Pull-up (body + load)", 0.87),
        Role("squat", "High-bar squat", 1.50),
        Role("rdl", "Romanian deadlift", 1.35),
    )

    fun snapshot(
        sessions: Map<LocalDate, SessionData>,
        days: Map<LocalDate, DayLogModel>,
        profile: Profile,
        today: LocalDate = LocalDate.now(),
    ): List<BalanceRow> {
        fun best(id: String): Double? {
            val ex = Program.EX[id] ?: return null
            return sessions.entries.map { (d, s) ->
                Progression.bestE1rm(ex, s, RulesEngine.bodyweightOn(days, d, profile.startWeight))
            }.maxOrNull()?.takeIf { it > 0 }
        }
        val anchor = best("cgb") ?: return roles.map { BalanceRow(it.id, it.name, best(it.id)?.let { Load.kgToLb(it) }, null, null, "no-data") }
        return roles.map { role ->
            val have = best(role.id)
            val want = anchor * role.ofAnchor
            val pct = have?.let { it / want }
            val status = when {
                have == null -> "no-data"
                pct!! >= 1.0 -> "on-pace"
                pct >= 1.0 - BAND -> "close"
                else -> "lagging"
            }
            BalanceRow(role.id, role.name, have?.let { Load.kgToLb(it) }, Load.kgToLb(want), pct, status)
        }
    }
}
