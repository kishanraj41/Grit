package com.kishan.preptracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kishan.preptracker.domain.*
import com.kishan.preptracker.ui.AppViewModel
import com.kishan.preptracker.ui.components.LineChart
import com.kishan.preptracker.ui.components.MuscleMap
import com.kishan.preptracker.ui.components.ScreenHeader
import com.kishan.preptracker.ui.components.Stat
import java.time.LocalDate

@Composable
fun TrendsScreen(vm: AppViewModel) {
    val days by vm.days.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val review by vm.review.collectAsStateWithLifecycle()
    val from = LocalDate.now().minusDays(70)
    fun series(pick: (DayLogModel) -> Double?) = days.entries.filter { !it.key.isBefore(from) }.mapNotNull { e -> pick(e.value)?.let { e.key to it } }.sortedBy { it.first }

    val weight = series { it.weight }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Trends")
        val pulse = remember(sessions, days, profile.weekStart) { Attendance.pulse(LocalDate.now(), sessions, days, profile.weekStart) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Month", "${pulse.monthSessions}", "sessions", Modifier.weight(1f))
            Stat("Week", "${pulse.sessionsThisWeek}", "${pulse.setsThisWeek} sets", Modifier.weight(1f))
            Stat("Weight 7d", pulse.weekDeltaKg?.let { (if (it >= 0) "+" else "") + "%.1f kg".format(it) } ?: "—", "vs 7 days ago", Modifier.weight(1f))
        }
        val heat = remember(sessions, profile.weekStart) { Heatmap.cells(sessions, weeks = 52, weekStart = profile.weekStart) }
        ChartCard("Training heatmap (52 weeks)") {
            Row(Modifier.horizontalScroll(rememberScrollState())) { HeatGrid(heat) }
        }
        val muscles = remember(sessions) { Muscles.snapshot(LocalDate.now(), sessions) }
        ChartCard("Muscle map") { MuscleMap(muscles) }
        var muscleMode by remember { mutableStateOf("recover") }
        ChartCard("Muscle status") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = muscleMode == "recover", onClick = { muscleMode = "recover" }, label = { Text("Recover") })
                FilterChip(selected = muscleMode == "dose", onClick = { muscleMode = "dose" }, label = { Text("Dose") })
                FilterChip(selected = muscleMode == "quiet", onClick = { muscleMode = "quiet" }, label = { Text("Quiet") })
            }
            val shown = when (muscleMode) {
                "dose" -> muscles.sortedByDescending { it.volume7 }
                "quiet" -> muscles.sortedByDescending { it.quietDays ?: 999 }
                else -> muscles
            }
            shown.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { m ->
                        val label = when (muscleMode) {
                            "dose" -> "${m.name} · ${"%.0f".format(m.volume7)} sets"
                            "quiet" -> "${m.name} · ${m.quietDays?.let { "$it d" } ?: "fresh"}"
                            else -> "${m.name} · ${m.status}"
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text(label) },
                            modifier = Modifier.weight(1f),
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = when (muscleMode) {
                                    "dose" -> if (m.volume7 >= 12) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    "quiet" -> if ((m.quietDays ?: 0) >= 7) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    else -> when (m.status) {
                                        "tired" -> MaterialTheme.colorScheme.error
                                        "recovering" -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                },
                            ),
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text(
                when (muscleMode) {
                    "dose" -> "Hard sets in the last 7 days. Assistants count 0.4."
                    "quiet" -> "Days since that region last took a work set."
                    else -> "Ready < recovering < tired. Half-life is longer for legs and posterior chain."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val balance = remember(sessions, days, profile) { Balance.snapshot(sessions, days, profile) }
        ChartCard("Balance vs close-grip bench") {
            balance.forEach { row ->
                val have = row.haveLb?.let { Load.roundLb(it) }
                val want = row.wantLb?.let { Load.roundLb(it) }
                Text(
                    "${row.name}: ${have?.let { Load.displayLb(Load.lbToKg(it)) } ?: "—"} / ${want?.let { Load.displayLb(Load.lbToKg(it)) } ?: "—"} lb · ${row.status}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        ChartCard("Weight · dots daily, line = trend (α from Plan)") {
            val trend = TrendWeight.compute(weight, profile.trendAlpha)
            LineChart(weight, overlay = trend, ref = review.targetWeight, unit = "kg")
        }
        Text("Tap or drag a chart to read a day. X-axis is calendar time, so gaps are real missed days — not a smoothed cartoon.", style = MaterialTheme.typography.bodySmall)
        Text("Target ${"%.1f".format(review.targetWeight)} kg at ${profile.targetBf.toInt()}% (FFM held)${review.toLose?.let { " · %.1f kg to go".format(it) } ?: ""}", style = MaterialTheme.typography.bodySmall)
        ChartCard("Waist (cm)") { LineChart(days.entries.mapNotNull { e -> e.value.waistCm?.let { e.key to it } }.sortedBy { it.first }, unit = "cm") }
        ChartCard("Arm, flexed (cm)") { LineChart(days.entries.mapNotNull { e -> e.value.armCm?.let { e.key to it } }.sortedBy { it.first }, unit = "cm") }
        ChartCard("Sleep (h) · line = 7 h floor") { LineChart(series { it.sleepH }, ref = 7.0, bars = true, unit = "h") }
        ChartCard("Steps · line = 8,500 floor") { LineChart(series { it.steps?.toDouble() }, ref = 8500.0, bars = true, unit = "steps", formatValue = { "%,d".format(it.toInt()) }) }
        ChartCard("Resting HR (bpm)") { LineChart(series { it.rhr?.toDouble() }, unit = "bpm") }
        ChartCard("HRV overnight (ms)") { LineChart(series { it.hrvMs }, unit = "ms") }
        Program.ANCHORS.forEach { id ->
            val ex = Program.EX[id]!!
            val pts = sessions.entries.mapNotNull { (d, s) ->
                Progression.bestE1rm(ex, s, RulesEngine.bodyweightOn(days, d, profile.startWeight)).takeIf { it > 0 }?.let { d to Load.kgToLb(it) }
            }.sortedBy { it.first }
            ChartCard("▲ ${ex.name} · est. 1RM (lb)${if (ex.bodyweight) " incl. bodyweight" else ""}") {
                LineChart(pts, unit = "lb")
            }
        }
    }
}

@Composable
private fun HeatGrid(cells: List<HeatCell>) {
    if (cells.isEmpty()) {
        Text("No sessions yet", style = MaterialTheme.typography.bodySmall)
        return
    }
    val weeks = cells.chunked(7)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        weeks.forEach { week ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { cell ->
                    val a = when (Heatmap.level(cell.sets)) {
                        0 -> 0.12f
                        1 -> 0.35f
                        2 -> 0.55f
                        3 -> 0.75f
                        else -> 1f
                    }
                    Box(
                        Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = a), RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            content()
        }
    }
}
