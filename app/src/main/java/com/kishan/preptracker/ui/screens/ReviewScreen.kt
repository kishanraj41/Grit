package com.kishan.preptracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kishan.preptracker.domain.EnergyEstimate
import com.kishan.preptracker.ui.AppViewModel
import com.kishan.preptracker.ui.components.RecCard
import com.kishan.preptracker.ui.components.ScreenHeader
import com.kishan.preptracker.ui.components.Stat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ReviewScreen(vm: AppViewModel) {
    val r by vm.review.collectAsStateWithLifecycle()
    val p by vm.profile.collectAsStateWithLifecycle()
    val energy by vm.energy.collectAsStateWithLifecycle()
    val target by vm.target.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Review")
        RecCard(
            if (target.source == "fixed") "Prep Tracker estimate — holding fixed ${target.kcal} kcal"
            else "Prep Tracker estimate — ${energy.tdee?.roundToInt()?.toString() ?: "—"} kcal TDEE → ${target.kcal} kcal",
            buildString {
                append("Window ${energy.windowDays} days · ${energy.confidence} · ${energy.intakeDays} logged intake days. ")
                append("This is Grit's own TDEE from intake vs scale. Health Connect has no TDEE field, so another diet app may disagree by 100 to 200 kcal. ")
                if (energy.confidence == EnergyEstimate.Confidence.NONE || energy.confidence == EnergyEstimate.Confidence.LOW)
                    append("Early in a cut, glycogen and water inflate apparent loss; trustworthy after ~3 weeks of steady logging. ")
                append(target.rationale)
            },
        )
        RecCard("This week: ${if (r.mode == "C") "Compressed" else "Standard"}", "${r.phase.name} · ${r.phase.note}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("7-day avg", r.avg7?.let { "%.2f".format(it) } ?: "—", "prev ${r.prevAvg?.let { "%.2f".format(it) } ?: "—"}", Modifier.weight(1f))
            Stat("Trend", r.trendKg?.let { "%.2f".format(it) } ?: "—", r.trendRate?.let { (if (it >= 0) "−" else "+") + "%.2f /wk".format(abs(it)) } ?: "", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Rate", r.rate?.let { (if (it >= 0) "−" else "+") + "%.2f".format(abs(it)) } ?: "—", r.rate2?.let { "before %.2f".format(it) } ?: "", Modifier.weight(1f))
            Stat("Waist", r.waistNow?.let { "%.1f".format(it.second) } ?: "—", r.waistDelta?.let { "%+.1f cm/2wk".format(it) } ?: "", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Sleep", r.sleep7.avg?.let { "%.1f h".format(it) } ?: "—", "${r.sleep7.n} nights", Modifier.weight(1f))
            Stat("Steps", r.steps7.avg?.let { "%,d".format(it.toInt()) } ?: "—", "${r.steps7.n} days", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Kcal", r.kcal7.avg?.roundToInt()?.toString() ?: "—", if (target.source == "fixed") "fixed ${target.kcal}" else "target ${target.kcal}", Modifier.weight(1f))
            Stat("Protein", r.pro7.avg?.let { "${it.roundToInt()} g" } ?: "—", "${p.proteinLow}–${p.proteinHigh}", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("RHR", r.rhr7.avg?.let { "${it.roundToInt()}" } ?: "—", "7-day", Modifier.weight(1f))
            Stat("HRV", r.hrv7.avg?.let { "${it.roundToInt()} ms" } ?: "—", "7-day", Modifier.weight(1f))
        }
        Text("Recommendations", style = MaterialTheme.typography.titleMedium)
        r.recs.forEach { rec ->
            RecCard(rec.title, rec.why, action = rec.lever?.let { n -> { TextButton(onClick = { vm.markLever(n) }) { Text("Mark applied today") } } })
        }
        if (r.flags.isNotEmpty()) { Text("Flags", style = MaterialTheme.typography.titleMedium); r.flags.forEach { Card(Modifier.fillMaxWidth()) { Text(it, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall) } } }
        Text("Anchor lifts · best est. 1RM this week vs last", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                r.anchors.forEach { a ->
                    val d = if (a.now > 0 && a.prev > 0) (a.now - a.prev) / a.prev * 100 else null
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(a.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(if (a.now > 0) "%.0f".format(a.now) else "—", Modifier.width(48.dp))
                        Text(if (a.prev > 0) "%.0f".format(a.prev) else "—", Modifier.width(48.dp))
                        Text(d?.let { "%+.1f%%".format(it) } ?: "—", Modifier.width(60.dp), fontWeight = FontWeight.SemiBold,
                            color = if (a.drop) MaterialTheme.colorScheme.tertiary else if (d != null && d > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface)
                    }
                }
                Text("Strength is a monitoring signal, not proof of muscle. Drop = est. 1RM more than 2% below last week. Pull-up and dips include bodyweight.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Projection", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                r.toLose?.let { Text("%.1f kg to %.1f kg (%d%% with FFM held at %.2f kg)".format(it, r.targetWeight, p.targetBf.toInt(), p.ffm)) }
                r.weeksAtTarget?.let { Text("At target rate (${r.target} kg/wk): ${LocalDate.now().plusDays((it * 7).roundToInt().toLong())} (%.1f wk)".format(it)) }
                r.weeksAtRate?.let { Text("At current rate (%.2f kg/wk): ${LocalDate.now().plusDays((it * 7).roundToInt().toLong())} (%.1f wk)".format(r.rate, it)) }
                    ?: Text("Current-rate projection needs two clean weeks of weigh-ins.", style = MaterialTheme.typography.bodySmall)
                Text("Shoot ${p.shootDate} (${ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(p.shootDate))} d) · DEXA ${p.dexaDate} (${ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(p.dexaDate))} d)", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}
