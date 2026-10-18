package com.kishan.preptracker.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class CsvImport(val sessions: Map<LocalDate, SessionData>, val unmatched: List<String>)

/**
 * Reads Strong / Hevy / FitNotes-style workout CSVs into Grit sessions.
 * Loads that look like pounds (> 250 with no unit column) stay as written kilograms
 * only when a kg header is present; a Weight (lbs) header converts.
 */
object WorkoutCsv {
    fun parse(text: String): CsvImport {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.size < 2) return CsvImport(emptyMap(), emptyList())
        val header = split(lines.first()).map { it.trim().lowercase() }
        val idx = header.mapIndexed { i, h -> h to i }.toMap()
        fun col(row: List<String>, vararg names: String): String? {
            val i = names.firstNotNullOfOrNull { idx[it] } ?: return null
            return row.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
        }
        val sessions = mutableMapOf<LocalDate, SessionData>()
        val unmatched = mutableListOf<String>()
        val lbHeader = header.any { it.contains("lb") || it.contains("pound") }
        val kgHeader = header.any { it.contains("kg") }
        for (line in lines.drop(1)) {
            val row = split(line)
            val dateRaw = col(row, "date", "start_time", "workout date", "start time") ?: continue
            val date = parseDate(dateRaw) ?: continue
            val name = col(row, "exercise name", "exercise", "exercise_title", "exercise title") ?: continue
            val reps = col(row, "reps")?.toIntOrNull()
            val weightRaw = col(row, "weight", "weight_kg", "weight (kg)", "weight (lbs)", "weight_lbs")?.toDoubleOrNull()
            val setType = col(row, "set_type", "set type", "set order") ?: ""
            val warmup = setType.contains("warm", ignoreCase = true)
            val loadKg = when {
                weightRaw == null -> null
                lbHeader && !kgHeader -> Load.lbToKg(weightRaw)
                else -> weightRaw
            }
            val catalogId = matchName(name)
            val id = catalogId ?: ("imp-" + name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'))
            if (catalogId == null) unmatched += name
            val session = sessions.getOrPut(date) {
                SessionData(Program.dayFor(date)?.key ?: "imp", "S", mutableMapOf(), date.atTime(12, 0).toString())
            }
            if (catalogId == null && id !in session.extras) {
                session.extras[id] = ExtraExercise(id, name, 6, 12, "2", 90, 2.5)
            }
            val list = session.sets.getOrPut(id) { mutableListOf() }
            list += SetEntry(
                load = loadKg,
                reps = reps,
                done = true,
                phase = if (warmup) "warmup" else "work",
            )
        }
        return CsvImport(sessions, unmatched.distinct())
    }

    internal fun matchName(name: String): String? {
        val n = name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        return Program.EX.entries.firstOrNull { (_, ex) ->
            val e = ex.name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
            e == n || e.contains(n) || n.contains(e)
        }?.key
    }

    private fun parseDate(raw: String): LocalDate? {
        val t = raw.trim()
        runCatching { return LocalDate.parse(t.take(10), DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
        val fmts = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        )
        for (f in fmts) {
            runCatching { return LocalDateTime.parse(t.take(19), f).toLocalDate() }.getOrNull()
            runCatching { return LocalDate.parse(t.take(10), f) }.getOrNull()
        }
        return null
    }

    private fun split(line: String): List<String> {
        val out = mutableListOf<String>()
        val buf = StringBuilder()
        var q = false
        for (c in line) {
            when {
                c == '"' -> q = !q
                c == ',' && !q -> { out += buf.toString(); buf.clear() }
                else -> buf.append(c)
            }
        }
        out += buf.toString()
        return out
    }
}
