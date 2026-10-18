package com.kishan.preptracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kishan.preptracker.domain.DayLogModel
import com.kishan.preptracker.domain.TodayLine
import com.kishan.preptracker.health.StepSources
import com.kishan.preptracker.ui.AppViewModel
import com.kishan.preptracker.ui.components.MiniStat
import com.kishan.preptracker.ui.components.RecCard
import com.kishan.preptracker.ui.components.ScreenHeader
import com.kishan.preptracker.ui.components.Stat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

@Composable
fun TodayScreen(vm: AppViewModel) {
    val days by vm.days.collectAsStateWithLifecycle()
    val review by vm.review.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val target by vm.target.collectAsStateWithLifecycle()
    val syncMsg by vm.syncMessage.collectAsStateWithLifecycle()
    val permitted by vm.hcPermitted.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val cur = days[today] ?: DayLogModel(today.toString())
    val toShoot = ChronoUnit.DAYS.between(today, LocalDate.parse(profile.shootDate))

    var weight by remember(cur.weight, cur.weightSrc) { mutableStateOf(cur.weight?.toString() ?: "") }
    var sleep by remember(cur.sleepH, cur.sleepSrc) { mutableStateOf(if (cur.sleepSrc == "manual") cur.sleepH?.toString() ?: "" else "") }
    var steps by remember(cur.steps, cur.stepsSrc) { mutableStateOf(if (cur.stepsSrc == "manual") cur.steps?.toString() ?: "" else "") }
    var waist by remember(cur.waistCm) { mutableStateOf(cur.waistCm?.toString() ?: "") }
    var arm by remember(cur.armCm) { mutableStateOf(cur.armCm?.toString() ?: "") }
    var note by remember(cur.note) { mutableStateOf(cur.note ?: "") }
    var more by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("Today", trailing = "$toShoot d")

        RecCard(TodayLine.compose(review, target), "One thing. The rest is noise.")

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Today’s log", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text(
                        when (permitted) {
                            true -> if (syncMsg.isNotEmpty()) syncMsg else "Health Connect"
                            false -> "Not connected"
                            null -> "Checking…"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { vm.syncNow() }, enabled = permitted == true, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Sync") }
                }
                Row(Modifier.fillMaxWidth()) {
                    MiniStat("Weight", cur.weight?.let { "%.1f kg".format(it) } ?: "—", Modifier.weight(1f))
                    MiniStat("kcal", cur.kcal?.toString() ?: "—", Modifier.weight(1f))
                    MiniStat("Protein", cur.protein?.let { "$it g" } ?: "—", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    MiniStat("Sleep", cur.sleepH?.let { "%.1f h".format(it) } ?: "—", Modifier.weight(1f))
                    MiniStat(
                        when {
                            cur.stepsSrc == "manual" -> "Steps · manual"
                            cur.stepsSrc?.startsWith("hc:") == true -> "Steps · ${StepSources.label(StepSources.packageOf(cur.stepsSrc))}"
                            cur.steps != null -> "Steps · Health Connect"
                            else -> "Steps"
                        },
                        cur.steps?.let { "%,d".format(it) } ?: "—",
                        Modifier.weight(1f),
                    )
                    MiniStat("RHR / HRV", listOfNotNull(cur.rhr?.toString(), cur.hrvMs?.let { "%.0f".format(it) }).joinToString(" / ").ifBlank { "—" }, Modifier.weight(1f))
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("7-day avg", review.avg7?.let { "%.2f kg".format(it) } ?: "—", "${review.n7} weigh-ins", Modifier.weight(1f))
            Stat("Trend", review.trendKg?.let { "%.2f kg".format(it) } ?: "—", review.trendRate?.let { (if (it >= 0) "−" else "+") + "%.2f /wk".format(abs(it)) } ?: "—", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Sleep 7d", review.sleep7.avg?.let { "%.1f h".format(it) } ?: "—", "floor 7 h", Modifier.weight(1f))
            Stat("Steps 7d", review.steps7.avg?.let { "%,d".format(it.toInt()) } ?: "—", "floor 8,500", Modifier.weight(1f))
        }

        OutlinedTextField(
            weight, { weight = it },
            label = { Text("Weight (kg)") },
            supportingText = {
                Text(when (cur.weightSrc) {
                    "manual" -> "Manual — sync will not overwrite. Clear and save to let Health Connect refill."
                    "hc" -> "Health Connect — edit and save to take over."
                    else -> "Type a weigh-in or wait for sync."
                })
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            val w = weight.toDoubleOrNull(); val s = sleep.toDoubleOrNull(); val st = steps.toIntOrNull()
            val weightChanged = weight.isBlank() || w != cur.weight
            vm.saveDay(cur.copy(
                weight = if (weight.isBlank()) null else w,
                weightSrc = when { weight.isBlank() -> null; weightChanged -> "manual"; else -> cur.weightSrc },
                sleepH = s ?: cur.sleepH, sleepSrc = if (s != null) "manual" else cur.sleepSrc,
                steps = st ?: cur.steps, stepsSrc = if (st != null) "manual" else cur.stepsSrc,
                waistCm = waist.toDoubleOrNull(), armCm = arm.toDoubleOrNull(), note = note.ifBlank { null },
            ))
        }, modifier = Modifier.fillMaxWidth()) { Text("Save day") }

        TextButton(onClick = { more = !more }) { Text(if (more) "Hide tape, sleep, steps" else "Tape, sleep, steps, note") }
        if (more) {
            NumField("Sleep (h) — override", sleep, cur.sleepH?.toString()) { sleep = it }
            NumField("Steps — override", steps, cur.steps?.toString()) { steps = it }
            NumField(if (today.dayOfWeek == DayOfWeek.MONDAY) "Waist at navel (cm) — Monday tape day" else "Waist at navel (cm)", waist, null) { waist = it }
            NumField("Arm, flexed (cm)", arm, null) { arm = it }
            OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NumField(label: String, value: String, placeholder: String?, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, placeholder = { Text(placeholder ?: "") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
}
