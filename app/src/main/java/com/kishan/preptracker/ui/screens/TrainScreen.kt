package com.kishan.preptracker.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kishan.preptracker.domain.*
import com.kishan.preptracker.ui.AppViewModel
import com.kishan.preptracker.ui.components.LineChart
import com.kishan.preptracker.ui.components.PlateCalculatorDialog
import com.kishan.preptracker.ui.components.RecCard
import com.kishan.preptracker.ui.components.ScreenHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TrainScreen(vm: AppViewModel) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val days by vm.days.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    var date by remember { mutableStateOf(LocalDate.now()) }
    val plan = Templates.dayFor(date, profile, sessions)
    val session = sessions[date]
    val review = remember(days, sessions, profile, date) { RulesEngine.weeklyReview(date, days, sessions, profile) }
    var plateLoad by remember { mutableStateOf<Double?>(null) }
    var historyId by remember { mutableStateOf<String?>(null) }
    var notes by remember(session?.notes) { mutableStateOf(session?.notes ?: "") }
    var addOpen by remember { mutableStateOf(false) }
    var showMeas by remember { mutableStateOf(session == null) }
    var moveOpen by remember { mutableStateOf(false) }
    var cloneOpen by remember { mutableStateOf(false) }
    var runMode by remember { mutableStateOf(true) }
    var focus by remember { mutableIntStateOf(0) }
    val lastDone = sessions.keys.filter { it.isBefore(date) && sessions[it]?.sets?.values?.any { rows -> rows.any { s -> s.done } } == true }.maxOrNull()
    val day = days[date] ?: DayLogModel(date.toString())
    var measWeight by remember(day.weight, day.weightSrc) { mutableStateOf(day.weight?.toString() ?: "") }
    var measWaist by remember(day.waistCm) { mutableStateOf(day.waistCm?.toString() ?: "") }
    var measArm by remember(day.armCm) { mutableStateOf(day.armCm?.toString() ?: "") }
    val view = LocalView.current
    DisposableEffect(session != null) {
        val window = (view.context as? android.app.Activity)?.window
        if (session != null) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val plannedIds = plan?.exercises?.map { it.id }.orEmpty()
    val exerciseIds = remember(session, plan) {
        val extra = session?.sets?.keys?.filter { it !in plannedIds }.orEmpty()
        plannedIds.filter { session?.sets?.containsKey(it) == true } + extra
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScreenHeader("Train", modifier = Modifier.weight(1f))
            TextButton(onClick = { date = date.minusDays(1) }) { Text("◀") }
            Text(date.format(DateTimeFormatter.ofPattern("EEE d MMM")), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { date = date.plusDays(1) }) { Text("▶") }
        }
        Text(plan?.name ?: "No template today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text((plan?.sub ?: "Add whatever you actually did") + " · ${review.phase.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        if (session == null) {
            MeasurementsCard(day, measWeight, { measWeight = it }, measWaist, { measWaist = it }, measArm, { measArm = it }, vm)
            Spacer(Modifier.height(8.dp))
            if (plan == null) RecCard("Rest-day template", "8,500 steps; Sunday is the higher-carb day. Start a session if you still trained.")
            else RecCard("Recommended: ${if (review.mode == "C") "Compressed" else "Standard"}", review.recs.firstOrNull()?.why ?: "")
            Spacer(Modifier.height(12.dp))
            if (plan != null) {
                Button(onClick = { vm.startSession(date, "S") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Start Standard · ${plan.exercises.sumOf { it.standardSets }} sets")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.startSession(date, "C") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Start Compressed · ${plan.exercises.sumOf { it.compressedSets }} sets")
                }
            } else {
                Button(onClick = { vm.startSession(date) }, modifier = Modifier.fillMaxWidth()) { Text("Start session") }
            }
            if (lastDone != null && sessions[date] == null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.cloneInto(lastDone, date) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Repeat ${lastDone.format(DateTimeFormatter.ofPattern("EEE d MMM"))}")
                }
            }
            return
        }

        val mode = session.mode
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == "S",
                onClick = { if (mode != "S") vm.setMode(date, "S") },
                label = { Text("Standard") },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = mode == "C",
                onClick = { if (mode != "C") vm.setMode(date, "C") },
                label = { Text("Compressed") },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            if (mode == "C") "Fewer sets. Switching back to Standard adds the missing planned sets."
            else "Full v4 volume. Switching to Compressed drops unfinished extra sets on planned lifts.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { moveOpen = true }) { Text("Move") }
            TextButton(onClick = { cloneOpen = true }) { Text("Clone") }
            if (session.finishedAt != null) TextButton(onClick = { vm.reopenSession(date) }) { Text("Reopen") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = runMode, onClick = { runMode = true }, label = { Text("Run") }, modifier = Modifier.weight(1f))
            FilterChip(selected = !runMode, onClick = { runMode = false }, label = { Text("List") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                TextButton(onClick = { showMeas = !showMeas }) { Text(if (showMeas) "Hide measurements" else "Measurements") }
                if (showMeas) MeasurementsCard(day, measWeight, { measWeight = it }, measWaist, { measWaist = it }, measArm, { measArm = it }, vm)
            }
            if (runMode && exerciseIds.isNotEmpty()) {
                item {
                    val i = focus.coerceIn(0, exerciseIds.lastIndex)
                    val lift = Lifts.BY_ID[exerciseIds[i]]
                    Text("Lift ${i + 1} of ${exerciseIds.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (lift != null) Text(lift.cue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { focus = (i - 1).coerceAtLeast(0) }, enabled = i > 0, modifier = Modifier.weight(1f)) { Text("Previous") }
                        Button(onClick = { focus = (i + 1).coerceAtMost(exerciseIds.lastIndex) }, enabled = i < exerciseIds.lastIndex, modifier = Modifier.weight(1f)) { Text("Next lift") }
                    }
                }
            }
            items(if (runMode) exerciseIds.getOrNull(focus.coerceIn(0, (exerciseIds.size - 1).coerceAtLeast(0)))?.let { listOf(it) }.orEmpty() else exerciseIds, key = { it }) { id ->
                val ex = Program.resolve(id, session) ?: return@items
                val sets = session.sets[id].orEmpty()
                val presc = remember(sessions, date, id, profile.progPolicy) {
                    Ladders.prescribe(Ladder.parse(profile.progPolicy), ex, date, sessions)
                }
                val last = remember(sessions, date, id) { Progression.lastSessionWith(ex.id, date, sessions) }
                val added = id !in plannedIds
                ExerciseCard(
                    ex = ex, sets = sets, presc = presc, last = last,
                    variant = session.variant[ex.id],
                    feel = session.feel[ex.id] ?: "normal",
                    superset = session.supersets[ex.id],
                    canRemove = added,
                    onUpdate = { i, l, r, rir -> vm.updateSet(date, ex.id, i, l, r, rir) },
                    onTick = { i -> vm.toggleDone(date, ex.id, i, presc.load, presc.reps) },
                    onPatch = { i, block -> vm.patchSet(date, ex.id, i, block) },
                    onLoadTap = { load -> plateLoad = load },
                    onLongName = { historyId = ex.id },
                    onAddSet = { vm.addSet(date, ex.id) },
                    onDuplicateSet = { i -> vm.duplicateSet(date, ex.id, i) },
                    onRemoveSet = { vm.removeSet(date, ex.id) },
                    onFeel = { vm.setFeel(date, ex.id, it) },
                    onSwap = { vm.setVariant(date, ex.id, it) },
                    onSuperset = { vm.setSuperset(date, ex.id, it) },
                    onRemoveExercise = { vm.removeExercise(date, ex.id) },
                )
            }
            item {
                OutlinedButton(onClick = { addOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Add exercise") }
            }
            item {
                OutlinedTextField(notes, { notes = it; vm.setNotes(date, it) }, label = { Text("Session notes") }, modifier = Modifier.fillMaxWidth())
            }
            item {
                session.prSummary?.let { RecCard("PRs this session", it) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (session.finishedAt == null) Button(onClick = { vm.finishSession(date) }) { Text("Finish session") }
                    else AssistChip(onClick = {}, label = { Text("Finished") })
                    TextButton(onClick = { vm.discardSession(date) }) { Text("Discard") }
                }
            }
        }
    }

    plateLoad?.let { load ->
        PlateCalculatorDialog(load, barKind = profile.barKind) { plateLoad = null }
    }
    if (moveOpen) {
        DatePickDialog(
            title = "Move session",
            start = date.plusDays(1),
            occupied = sessions.keys,
            onConfirm = { to -> vm.moveSession(date, to); date = to; moveOpen = false },
            onDismiss = { moveOpen = false },
        )
    }
    if (cloneOpen) {
        DatePickDialog(
            title = "Clone session",
            start = date.plusDays(1),
            occupied = sessions.keys,
            confirm = "Clone",
            onConfirm = { to -> vm.cloneInto(date, to); date = to; cloneOpen = false },
            onDismiss = { cloneOpen = false },
        )
    }
    historyId?.let { id ->
        ExerciseHistorySheet(id, date, sessions, days, profile, onDismiss = { historyId = null })
    }
    if (addOpen && session != null) {
        AddExerciseDialog(
            already = session.sets.keys,
            onCatalog = { vm.addCatalogExercise(date, it); addOpen = false },
            onCustom = { vm.addCustomExercise(date, it); addOpen = false },
            onDismiss = { addOpen = false },
        )
    }
}

@Composable
private fun MeasurementsCard(
    day: DayLogModel,
    weight: String, onWeight: (String) -> Unit,
    waist: String, onWaist: (String) -> Unit,
    arm: String, onArm: (String) -> Unit,
    vm: AppViewModel,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Body measurements (scale / tape)", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(weight, onWeight, label = { Text("Bodyweight (kg)") },
                supportingText = { Text(if (day.weightSrc == "manual") "Manual — sync will not overwrite" else if (day.weightSrc == "hc") "Health Connect — edit and save to take over" else "") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(waist, onWaist, label = { Text("Waist at navel (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(arm, onArm, label = { Text("Arm, flexed (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                val w = weight.toDoubleOrNull()
                val weightChanged = weight.isBlank() || w != day.weight
                vm.saveDay(day.copy(
                    weight = if (weight.isBlank()) null else w,
                    weightSrc = when { weight.isBlank() -> null; weightChanged -> "manual"; else -> day.weightSrc },
                    waistCm = waist.toDoubleOrNull(), armCm = arm.toDoubleOrNull(),
                ))
            }, modifier = Modifier.fillMaxWidth()) { Text("Save measurements") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseCard(
    ex: Exercise, sets: List<SetEntry>, presc: Prescription, last: Pair<LocalDate, List<SetEntry>>?,
    variant: String?, feel: String,
    superset: String? = null,
    canRemove: Boolean = false,
    onUpdate: (Int, Double?, Int?, Int?) -> Unit, onTick: (Int) -> Unit,
    onPatch: (Int, SetEntry.() -> Unit) -> Unit,
    onLoadTap: (Double) -> Unit, onLongName: () -> Unit,
    onAddSet: () -> Unit, onDuplicateSet: (Int) -> Unit = {}, onRemoveSet: () -> Unit,
    onFeel: (String) -> Unit, onSwap: (String?) -> Unit,
    onSuperset: (String?) -> Unit = {},
    onRemoveExercise: () -> Unit = {},
) {
    val display = variant?.let { Program.EX[it]?.name ?: it } ?: ex.name
    Card(Modifier.fillMaxWidth(), border = if (ex.anchor) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                (if (ex.anchor) "▲ " else "") + display +
                    (if (superset != null) "  · SS $superset" else "") +
                    if (variant != null) "  (logged as ${ex.name})" else "",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongName),
            )
            Text("${ex.lo}–${ex.hi} reps · ${ex.rir} RIR · rest ${if (ex.restSec >= 120) "${ex.restSec / 60} min" else "${ex.restSec} s"}", style = MaterialTheme.typography.bodySmall)
            val ghost = last?.let { (d, s) ->
                "Last (${d.format(DateTimeFormatter.ofPattern("d MMM"))}): " +
                    s.filter { it.done }.joinToString(" · ") {
                        val lb = it.load?.let { l -> Load.displayLb(l) } ?: "bw"
                        "${lb}×${it.reps ?: "?"}"
                    }
            } ?: "No history yet"
            val prescNote = when {
                presc.note.startsWith("ladder full") ->
                    " — ladder full last time → +${Load.displayLb(ex.inc)} lb, back to ${ex.lo} reps"
                presc.note.isNotEmpty() -> " — ${presc.note}"
                else -> ""
            }
            Text(ghost + prescNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (ex.anchor && presc.load != null) {
                val ramps = Warmup.forLoad(presc.load)
                Text("Warm-up (untracked): " + ramps.joinToString(" → ") { "${Load.displayLb(it.loadKg)}×${it.reps}" }, style = MaterialTheme.typography.labelSmall)
            }
            sets.forEachIndexed { i, s ->
                SetBlock(
                    index = i,
                    set = s,
                    presc = presc,
                    ex = ex,
                    onUpdate = { l, r, rir -> onUpdate(i, l, r, rir) },
                    onTick = { onTick(i) },
                    onPatch = { block -> onPatch(i, block) },
                    onPlates = { (s.load ?: presc.load)?.let(onLoadTap) },
                    onDuplicate = { onDuplicateSet(i) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAddSet, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("+ set") }
                TextButton(onClick = { if (sets.isNotEmpty()) onDuplicateSet(sets.lastIndex) }, enabled = sets.isNotEmpty(), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("copy last") }
                TextButton(onClick = onRemoveSet, enabled = sets.isNotEmpty(), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("− set") }
                Spacer(Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("easy", "normal", "hard").forEach { f ->
                    FilterChip(selected = feel == f, onClick = { onFeel(f) }, label = { Text(f) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ex.alternatives.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) { Text(if (variant == null) "Swap exercise" else "Swap: ${Program.EX[variant]?.name ?: variant}") }
                        DropdownMenu(expanded, { expanded = false }) {
                            DropdownMenuItem(text = { Text(ex.name + " (original)") }, onClick = { expanded = false; onSwap(null) })
                            ex.alternatives.forEach { alt ->
                                DropdownMenuItem(text = { Text(Program.EX[alt]?.name ?: alt) }, onClick = { expanded = false; onSwap(alt) })
                            }
                        }
                    }
                }
                TextButton(onClick = { onSuperset(if (superset == "A") "B" else if (superset == "B") null else "A") }) {
                    Text(if (superset == null) "Pair superset" else "Superset $superset")
                }
                Spacer(Modifier.weight(1f))
                if (canRemove) TextButton(onClick = onRemoveExercise) { Text("Remove") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetBlock(
    index: Int,
    set: SetEntry,
    presc: Prescription,
    ex: Exercise,
    onUpdate: (Double?, Int?, Int?) -> Unit,
    onTick: () -> Unit,
    onPatch: (SetEntry.() -> Unit) -> Unit,
    onPlates: () -> Unit,
    onDuplicate: () -> Unit = {},
) {
    var typeLoad by remember { mutableStateOf(false) }
    var typed by remember(set.load) { mutableStateOf(Load.displayLb(set.load)) }
    val loadLb = set.load?.let { Load.displayLb(it) } ?: "—"
    val aim = presc.load?.let { Load.displayLb(it) }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Set ${index + 1}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (aim != null && set.load == null) Text("aim $aim lb", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onDuplicate, enabled = !set.done, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("copy") }
                FilledIconToggleButton(checked = set.done, onCheckedChange = { onTick() }, modifier = Modifier.size(44.dp)) { Text("✓") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick = { onUpdate(Load.stepLb(set.load, -5.0), set.reps, set.rir) }, enabled = !set.done, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("−5") }
                Text(
                    if (ex.bodyweight) "+$loadLb" else loadLb,
                    modifier = Modifier.weight(1f).clickable(enabled = !set.done) { typeLoad = true },
                    textAlign = TextAlign.Center,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                FilledTonalButton(onClick = { onUpdate(Load.stepLb(set.load, 5.0), set.reps, set.rir) }, enabled = !set.done, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("+5") }
                Text("lb", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onPlates, enabled = set.load != null || presc.load != null) { Text("plates") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reps", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(36.dp))
                Stepper(set.reps ?: presc.reps, enabled = !set.done, onChange = { onUpdate(set.load, it, set.rir) })
                Spacer(Modifier.weight(1f))
                Text("RIR", style = MaterialTheme.typography.labelMedium)
                Stepper(set.rir ?: Regex("\\d+").find(ex.rir)?.value?.toInt() ?: 1, enabled = !set.done, min = 0, max = 5, onChange = { onUpdate(set.load, set.reps, it) })
                Spacer(Modifier.width(8.dp))
                Text("RPE", style = MaterialTheme.typography.labelMedium)
                Stepper((set.rpe ?: 8.0).toInt(), enabled = !set.done, min = 5, max = 10, onChange = { onPatch { rpe = it.toDouble() } })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = set.phase == "warmup", onClick = { onPatch { phase = if (phase == "warmup") "work" else "warmup" } }, enabled = !set.done, label = { Text("WU") })
                FilterChip(selected = set.kind == "drop", onClick = {
                    onPatch {
                        kind = if (kind == "drop") "straight" else "drop"
                        if (kind == "drop" && drops.isEmpty() && load != null) {
                            drops = listOf(DropBurst(Load.stepLb(load, -20.0), (reps ?: 6).coerceAtLeast(4)))
                        }
                    }
                }, enabled = !set.done, label = { Text("Drop") })
                FilterChip(selected = set.kind == "restpause", onClick = { onPatch { kind = if (kind == "restpause") "straight" else "restpause" } }, enabled = !set.done, label = { Text("RP") })
                FilterChip(selected = set.kind == "timed", onClick = { onPatch { kind = if (kind == "timed") "straight" else "timed"; if (durationSec == null) durationSec = 30 } }, enabled = !set.done, label = { Text("Time") })
                FilterChip(selected = set.kind == "cardio", onClick = { onPatch { kind = if (kind == "cardio") "straight" else "cardio"; if (durationSec == null) durationSec = 600 } }, enabled = !set.done, label = { Text("Cardio") })
                FilterChip(selected = set.perSide, onClick = { onPatch { perSide = !perSide } }, enabled = !set.done, label = { Text("L/R") })
                FilterChip(selected = set.failure, onClick = { onPatch { failure = !failure } }, enabled = !set.done, label = { Text("Fail") })
            }
            if (set.kind == "timed") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hold s", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp))
                    Stepper(set.durationSec ?: 30, enabled = !set.done, min = 5, max = 300, onChange = { onPatch { durationSec = it } })
                }
            }
            if (set.kind == "cardio") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Min", style = MaterialTheme.typography.labelMedium)
                    Stepper(((set.durationSec ?: 600) / 60).coerceAtLeast(1), enabled = !set.done, min = 1, max = 180, onChange = { onPatch { durationSec = it * 60 } })
                    Text("kph", style = MaterialTheme.typography.labelMedium)
                    Stepper((set.speedKph ?: 8.0).toInt(), enabled = !set.done, min = 1, max = 25, onChange = { onPatch { speedKph = it.toDouble() } })
                }
            }
            if (set.perSide) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("L", style = MaterialTheme.typography.labelMedium)
                    Stepper(set.leftReps ?: set.reps ?: presc.reps, enabled = !set.done, onChange = { onPatch { leftReps = it } })
                    Text("R", style = MaterialTheme.typography.labelMedium)
                    Stepper(set.rightReps ?: set.reps ?: presc.reps, enabled = !set.done, onChange = { onPatch { rightReps = it } })
                }
            }
            if (set.kind == "drop") {
                set.drops.forEachIndexed { di, drop ->
                    Text("Drop ${di + 1}: ${Load.displayLb(drop.load)} lb × ${drop.reps ?: "—"}", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = {
                    onPatch {
                        val prev = drops.lastOrNull()?.load ?: load
                        drops = drops + DropBurst(Load.stepLb(prev, -10.0), 6)
                    }
                }, enabled = !set.done) { Text("+ drop") }
            }
        }
    }
    if (typeLoad) {
        AlertDialog(
            onDismissRequest = { typeLoad = false },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(Load.parseLb(typed), set.reps, set.rir)
                    typeLoad = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { typeLoad = false }) { Text("Cancel") } },
            title = { Text(if (ex.bodyweight) "Added weight (lb)" else "Load (lb)") },
            text = {
                OutlinedTextField(
                    typed, { typed = it },
                    label = { Text("Pounds") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

@Composable
private fun Stepper(value: Int, enabled: Boolean, min: Int = 1, max: Int = 30, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = enabled, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("−") }
        Text("$value", Modifier.width(36.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        FilledTonalButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = enabled, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseHistorySheet(
    exId: String, before: LocalDate,
    sessions: Map<LocalDate, SessionData>,
    days: Map<LocalDate, DayLogModel>,
    profile: Profile,
    onDismiss: () -> Unit,
) {
    val ex = Program.EX[exId]
        ?: sessions.values.firstNotNullOfOrNull { it.extras[exId]?.toExercise() }
        ?: return
    val rows = sessions.entries
        .filter { it.key.isBefore(before) && it.value.sets[exId].orEmpty().any { s -> s.done } }
        .sortedBy { it.key }
        .map { (d, s) ->
            val bw = RulesEngine.bodyweightOn(days, d, profile.startWeight)
            Triple(d, Progression.bestE1rm(ex, s, bw), Progression.sessionVolume(ex, s, bw))
        }
    val spark = rows.map { it.first to Load.kgToLb(it.second) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(ex.name) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (spark.isEmpty()) Text("No history yet.")
                else {
                    LineChart(spark, modifier = Modifier.fillMaxWidth(), unit = "lb")
                    rows.asReversed().forEach { (d, e1, vol) ->
                        Text("${d.format(DateTimeFormatter.ofPattern("d MMM"))} · est. 1RM ${Load.displayLb(e1)} lb · volume ${Load.displayLb(vol)} lb", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddExerciseDialog(
    already: Set<String>,
    onCatalog: (String) -> Unit,
    onCustom: (ExtraExercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var lo by remember { mutableStateOf("") }
    var hi by remember { mutableStateOf("") }
    var rir by remember { mutableStateOf("") }
    var rest by remember { mutableStateOf("") }
    var inc by remember { mutableStateOf("") }
    var bodyweight by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    val available = Lifts.search(query, group, null).filter { it.id !in already }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Add exercise") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("From the lift library", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = group == null, onClick = { group = null }, label = { Text("All") })
                    Lifts.GROUPS.forEach { g ->
                        FilterChip(selected = group == g, onClick = { group = if (group == g) null else g }, label = { Text(g) })
                    }
                }
                if (available.isEmpty()) Text("Nothing matches, or it is already on this session.", style = MaterialTheme.typography.bodySmall)
                available.forEach { lift ->
                    TextButton(onClick = { onCatalog(lift.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${lift.name} · ${lift.equipment}", modifier = Modifier.fillMaxWidth())
                    }
                }
                HorizontalDivider()
                Text("Or add something that is not in the catalog", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(lo, { lo = it }, label = { Text("Rep low") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(hi, { hi = it }, label = { Text("Rep high") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(rir, { rir = it }, label = { Text("RIR") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(rest, { rest = it }, label = { Text("Rest (s)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(inc, { inc = it }, label = { Text("Increment (lb)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(bodyweight, { bodyweight = it })
                    Text("Load is added weight (pull-up / dip style)")
                }
                val loN = lo.toIntOrNull(); val hiN = hi.toIntOrNull(); val restN = rest.toIntOrNull(); val incLb = inc.toDoubleOrNull()
                val ready = name.isNotBlank() && loN != null && hiN != null && rir.isNotBlank() && restN != null && incLb != null
                Button(
                    onClick = {
                        val id = "x" + System.currentTimeMillis()
                        onCustom(ExtraExercise(id, name.trim(), loN!!, hiN!!, rir.trim(), restN!!, Load.lbToKg(incLb!!), bodyweight))
                    },
                    enabled = ready,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add custom exercise") }
            }
        },
    )
}

@Composable
private fun DatePickDialog(
    title: String,
    start: LocalDate,
    occupied: Set<LocalDate>,
    confirm: String = "Move",
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var d by remember { mutableStateOf(start) }
    val taken = d in occupied
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { d = d.minusDays(1) }) { Text("◀") }
                    Text(d.format(DateTimeFormatter.ofPattern("EEE d MMM")), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    TextButton(onClick = { d = d.plusDays(1) }) { Text("▶") }
                }
                if (taken) Text("That day already has a session.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(d) }, enabled = !taken) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
