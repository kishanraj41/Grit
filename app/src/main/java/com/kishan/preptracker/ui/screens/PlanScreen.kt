package com.kishan.preptracker.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kishan.preptracker.domain.Program
import com.kishan.preptracker.ui.AppViewModel
import com.kishan.preptracker.ui.components.ScreenHeader
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(vm: AppViewModel, onRequestPermissions: () -> Unit) {
    val p by vm.profile.collectAsStateWithLifecycle()
    val permitted by vm.hcPermitted.collectAsStateWithLifecycle()
    val backupMsg by vm.backupMessage.collectAsStateWithLifecycle()
    var confirmImport by remember { mutableStateOf(false) }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportTo(it) }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importFrom(it) }
    }
    val openCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importWorkoutCsv(it) }
    }
    var policy by remember(p.progPolicy) { mutableStateOf(p.progPolicy) }
    var formula by remember(p.oneRmFormula) { mutableStateOf(p.oneRmFormula) }
    var showEnergy by remember { mutableStateOf(false) }
    var ffm by remember(p.ffm) { mutableStateOf(p.ffm.toString()) }
    var targetBf by remember(p.targetBf) { mutableStateOf(p.targetBf.toString()) }
    var startWeight by remember(p.startWeight) { mutableStateOf(p.startWeight.toString()) }
    var goal by remember(p.goal) { mutableStateOf(p.goal) }
    var trendAlpha by remember(p.trendAlpha) { mutableStateOf(p.trendAlpha.toString()) }
    var energyWindow by remember(p.energyWindowDays) { mutableStateOf(p.energyWindowDays.toString()) }
    var minKcal by remember(p.minPlausibleKcal) { mutableStateOf(p.minPlausibleKcal.toString()) }
    var kcalPerKg by remember(p.kcalPerKg) { mutableStateOf(p.kcalPerKg.toString()) }
    var proteinLow by remember(p.proteinLow) { mutableStateOf(p.proteinLow.toString()) }
    var proteinHigh by remember(p.proteinHigh) { mutableStateOf(p.proteinHigh.toString()) }
    var fatFloor by remember(p.fatFloorG) { mutableStateOf(p.fatFloorG.toString()) }
    var kcalFloor by remember(p.kcalFloor) { mutableStateOf(p.kcalFloor.toString()) }
    var maxWeekly by remember(p.maxWeeklyChange) { mutableStateOf(p.maxWeeklyChange.toString()) }
    var barKg by remember(p.barWeightKg) { mutableStateOf(p.barWeightKg.toString()) }
    var plates by remember(p.platesKg) { mutableStateOf(p.platesKg.joinToString(",")) }
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenHeader("Plan")

        Text("Health Connect", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (!vm.hc.isAvailable) "Health Connect is not available on this device." else if (permitted == true) "Connected — reads steps, sleep, heart rate, HRV, weight, nutrition." else "Not connected.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRequestPermissions, enabled = vm.hc.isAvailable) { Text(if (permitted == true) "Re-check permissions" else "Connect") }
                    OutlinedButton(onClick = { vm.syncNow(daysBack = 30) }, enabled = permitted == true) { Text("Backfill 30 days") }
                }
                Text("MacroFactor → More → Integrations → Health Connect: turn on nutrition and weight export so calories, protein and scale weight flow in.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("Backup", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Days, Train sessions, and Plan settings live only on this phone. Export a JSON file before an app update or phone swap. Import replaces everything currently stored.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { createBackup.launch(vm.suggestedBackupName()) }) { Text("Export") }
                    OutlinedButton(onClick = { confirmImport = true }) { Text("Import") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openCsv.launch(arrayOf("text/csv", "text/plain", "*/*")) }) { Text("Import workout CSV") }
                }
                Text("Strong, Hevy, or FitNotes CSV lands as Train sessions. Names that do not match the v4 catalog become custom exercises.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (backupMsg.isNotEmpty()) Text(backupMsg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (confirmImport) {
            AlertDialog(
                onDismissRequest = { confirmImport = false },
                title = { Text("Replace local data?") },
                text = { Text("Import overwrites every day log, Train session, and Plan setting on this phone with the file you pick. Export first if you are not sure.") },
                confirmButton = { TextButton(onClick = { confirmImport = false; openBackup.launch(arrayOf("application/json", "*/*")) }) { Text("Pick file") } },
                dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } },
            )
        }

        Text("Phases", style = MaterialTheme.typography.titleMedium)
        Program.PHASES.forEach { ph ->
            val cur = !today.isBefore(ph.from) && !today.isAfter(ph.to)
            Card(Modifier.fillMaxWidth(), colors = if (cur) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()) {
                Column(Modifier.padding(10.dp)) {
                    Text("${ph.from} – ${ph.to} · ${ph.name} · ${if (ph.mode == "C") "Compressed" else "Standard"}", style = MaterialTheme.typography.labelLarge)
                    Text(ph.note, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text("Training rules", style = MaterialTheme.typography.titleMedium)
        Text("Ladder (v4 default is double progression)", style = MaterialTheme.typography.labelMedium)
        val policies = listOf("double", "linear", "greyskull", "triple", "off")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            policies.take(3).forEachIndexed { i, id ->
                SegmentedButton(selected = policy == id, onClick = { policy = id }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(id) }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            policies.drop(3).forEachIndexed { i, id ->
                SegmentedButton(selected = policy == id, onClick = { policy = id }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(id) }
            }
        }
        Text("Estimated 1RM formula for Trends", style = MaterialTheme.typography.labelMedium)
        val formulas = listOf("epley", "brzycki", "blend")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            formulas.forEachIndexed { i, id ->
                SegmentedButton(selected = formula == id, onClick = { formula = id }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(id) }
            }
        }

        Text("Settings", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = goal == "A", onClick = { goal = "A" }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("A · 0.45 kg/wk") }
            SegmentedButton(selected = goal == "B", onClick = { goal = "B" }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("B · 0.62 kg/wk") }
        }
        OutlinedTextField(ffm, { ffm = it }, label = { Text("Fat-free mass (kg, DEXA)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(targetBf, { targetBf = it }, label = { Text("Target body fat %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(startWeight, { startWeight = it }, label = { Text("Start weight (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Deficit lever stage: ${p.lever} of 3${p.leverDate?.let { " · applied $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { showEnergy = !showEnergy }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showEnergy) "Hide energy & equipment knobs" else "Energy, macros & equipment")
        }
        if (showEnergy) {
            Text("Energy & macros", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(trendAlpha, { trendAlpha = it }, label = { Text("Trend-weight α") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(energyWindow, { energyWindow = it }, label = { Text("TDEE window (days)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(minKcal, { minKcal = it }, label = { Text("Min plausible intake (kcal)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(kcalPerKg, { kcalPerKg = it }, label = { Text("kcal per kg body-mass change") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(proteinLow, { proteinLow = it }, label = { Text("Protein low (g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(proteinHigh, { proteinHigh = it }, label = { Text("Protein high (g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(fatFloor, { fatFloor = it }, label = { Text("Fat floor (g)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(kcalFloor, { kcalFloor = it }, label = { Text("kcal floor") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(maxWeekly, { maxWeekly = it }, label = { Text("Max weekly target change (kcal)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Training equipment", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(barKg, { barKg = it }, label = { Text("Bar weight (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(plates, { plates = it }, label = { Text("Plates (kg, comma-separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            p.lastTargetChange?.let { Text("Last calorie-target change: $it (${p.kcalTarget ?: "—"} kcal, ${p.targetSource ?: "—"})", style = MaterialTheme.typography.bodySmall) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveProfile(p.copy(
                ffm = ffm.toDoubleOrNull() ?: p.ffm, targetBf = targetBf.toDoubleOrNull() ?: p.targetBf,
                startWeight = startWeight.toDoubleOrNull() ?: p.startWeight, goal = goal,
                trendAlpha = trendAlpha.toDoubleOrNull() ?: p.trendAlpha,
                energyWindowDays = energyWindow.toIntOrNull() ?: p.energyWindowDays,
                minPlausibleKcal = minKcal.toIntOrNull() ?: p.minPlausibleKcal,
                kcalPerKg = kcalPerKg.toDoubleOrNull() ?: p.kcalPerKg,
                proteinLow = proteinLow.toIntOrNull() ?: p.proteinLow,
                proteinHigh = proteinHigh.toIntOrNull() ?: p.proteinHigh,
                fatFloorG = fatFloor.toIntOrNull() ?: p.fatFloorG,
                kcalFloor = kcalFloor.toIntOrNull() ?: p.kcalFloor,
                maxWeeklyChange = maxWeekly.toIntOrNull() ?: p.maxWeeklyChange,
                barWeightKg = barKg.toDoubleOrNull() ?: p.barWeightKg,
                platesKg = plates.split(",").mapNotNull { it.trim().toDoubleOrNull() }.ifEmpty { p.platesKg },
                progPolicy = policy,
                oneRmFormula = formula,
            )) }) { Text("Save") }
            TextButton(onClick = { vm.saveProfile(p.copy(lever = 0, leverDate = null)) }) { Text("Reset lever") }
        }
        Spacer(Modifier.height(80.dp))
    }
}
