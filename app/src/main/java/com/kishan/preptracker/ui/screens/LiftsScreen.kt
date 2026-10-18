package com.kishan.preptracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kishan.preptracker.domain.ExtraExercise
import com.kishan.preptracker.domain.Lift
import com.kishan.preptracker.domain.Lifts
import com.kishan.preptracker.domain.Load
import com.kishan.preptracker.ui.AppViewModel
import com.kishan.preptracker.ui.components.ScreenHeader
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiftsScreen(vm: AppViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val already = sessions[today]?.sets?.keys.orEmpty()
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    var equipment by remember { mutableStateOf<String?>(null) }
    val hits = remember(query, group, equipment) { Lifts.search(query, group, equipment) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader("Lifts", trailing = "${Lifts.ALL.size}")
        Text("Search, filter, log onto today. Cues are text. No stolen animation pack.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            FilterChip(selected = group == null, onClick = { group = null }, label = { Text("All") })
            Lifts.GROUPS.forEach { g ->
                FilterChip(selected = group == g, onClick = { group = if (group == g) null else g }, label = { Text(g) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Lifts.EQUIPMENT.forEach { e ->
                FilterChip(selected = equipment == e, onClick = { equipment = if (equipment == e) null else e }, label = { Text(e) })
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
            items(hits, key = { it.id }) { lift ->
                LiftRow(lift, logged = lift.id in already, onLog = { vm.logLift(today, lift.id) })
            }
            item { HorizontalDivider() }
            item { CustomLiftForm { vm.addCustomExercise(today, it) } }
        }
    }
}

@Composable
private fun LiftRow(lift: Lift, logged: Boolean, onLog: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(lift.name, fontWeight = FontWeight.SemiBold)
            Text("${lift.group} · ${lift.equipment} · ${lift.ex.lo}–${lift.ex.hi} · rest ${lift.ex.restSec}s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(lift.cue, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (logged) Text("On today’s session", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                else OutlinedButton(onClick = onLog) { Text("Log today") }
            }
        }
    }
}

@Composable
private fun CustomLiftForm(onCreate: (ExtraExercise) -> Unit) {
    var name by remember { mutableStateOf("") }
    var lo by remember { mutableStateOf("8") }
    var hi by remember { mutableStateOf("12") }
    var rir by remember { mutableStateOf("1") }
    var rest by remember { mutableStateOf("90") }
    var inc by remember { mutableStateOf("5") }
    var bodyweight by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your lift", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(lo, { lo = it }, label = { Text("Low") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(hi, { hi = it }, label = { Text("High") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(inc, { inc = it }, label = { Text("Inc lb") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(bodyweight, { bodyweight = it })
                Text("Added weight (pull-up style)", style = MaterialTheme.typography.bodySmall)
            }
            val loN = lo.toIntOrNull(); val hiN = hi.toIntOrNull(); val restN = rest.toIntOrNull(); val incLb = inc.toDoubleOrNull()
            Button(
                onClick = {
                    onCreate(ExtraExercise("x" + System.currentTimeMillis(), name.trim(), loN!!, hiN!!, rir.ifBlank { "1" }, restN ?: 90, Load.lbToKg(incLb!!), bodyweight))
                    name = ""
                },
                enabled = name.isNotBlank() && loN != null && hiN != null && incLb != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create and log today") }
        }
    }
}
