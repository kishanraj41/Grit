package com.kishan.preptracker.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kishan.preptracker.data.BackupCodec
import com.kishan.preptracker.data.Repository
import com.kishan.preptracker.domain.*
import com.kishan.preptracker.health.HealthConnectManager
import com.kishan.preptracker.sync.HealthSync
import com.kishan.preptracker.train.RestAlerts
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val hc = HealthConnectManager(app)

    val profile: StateFlow<Profile> = repo.profile.stateIn(viewModelScope, SharingStarted.Eagerly, Profile())
    val days: StateFlow<Map<LocalDate, DayLogModel>> = repo.days.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val sessions: StateFlow<Map<LocalDate, SessionData>> = repo.sessions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val review: StateFlow<Review> = combine(days, sessions, profile) { d, s, p -> RulesEngine.weeklyReview(LocalDate.now(), d, s, p) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RulesEngine.weeklyReview(LocalDate.now(), emptyMap(), emptyMap(), Profile()))

    val energy: StateFlow<EnergyEstimate> = combine(days, profile) { d, p ->
        AdaptiveEnergy.estimate(d, LocalDate.now(), p.energyWindowDays, p.trendAlpha, p.minPlausibleKcal, p.kcalPerKg)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdaptiveEnergy.estimate(emptyMap(), LocalDate.now()))

    val target: StateFlow<MacroTarget> = combine(energy, profile) { e, p ->
        NutritionTargets.applyWeekly(e, p, LocalDate.now())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, NutritionTargets.applyWeekly(AdaptiveEnergy.estimate(emptyMap(), LocalDate.now()), Profile(), LocalDate.now()))

    init {
        viewModelScope.launch {
            combine(target, profile) { t, p -> t to p }.collect { (t, p) ->
                if (NutritionTargets.shouldPersist(p, t, LocalDate.now())) {
                    repo.saveProfile(p.copy(
                        kcalTarget = t.kcal, proteinTarget = t.proteinG, fatTarget = t.fatG, carbTarget = t.carbG,
                        targetSource = t.source, lastTargetChange = LocalDate.now().toString(),
                    ))
                }
            }
        }
    }

    /* ---- sync ---- */
    val syncMessage = MutableStateFlow("")
    val hcPermitted = MutableStateFlow<Boolean?>(null)
    fun refreshPermissionState() = viewModelScope.launch { hcPermitted.value = if (hc.isAvailable) runCatching { hc.hasAllPermissions() }.getOrDefault(false) else false }
    fun syncNow(daysBack: Int = 3) = viewModelScope.launch {
        syncMessage.value = "Syncing…"
        val n = runCatching { HealthSync.syncRecent(getApplication(), daysBack) }.getOrElse { -1 }
        syncMessage.value = when { n < 0 -> "Health Connect not connected"; n == 0 -> "Up to date"; else -> "Updated $n day${if (n > 1) "s" else ""}" }
        refreshPermissionState()
    }

    /* ---- day log ---- */
    fun saveDay(m: DayLogModel) = viewModelScope.launch { repo.saveDay(m) }

    /* ---- sessions ---- */
    fun startSession(date: LocalDate, forcedMode: String? = null) = viewModelScope.launch {
        if (sessions.value[date] != null) return@launch
        val plan = Program.dayFor(date)
        val mode = forcedMode ?: RulesEngine.weeklyReview(date, days.value, sessions.value, profile.value).mode
        val sets = plan?.exercises?.associate { pe ->
            val n = if (mode == "C") pe.compressedSets else pe.standardSets
            val presc = Ladders.prescribe(Ladder.parse(profile.value.progPolicy), Program.EX[pe.id]!!, date, sessions.value)
            pe.id to MutableList(n) { SetEntry(load = presc.load) }
        }?.toMutableMap() ?: mutableMapOf()
        repo.saveSession(date, SessionData(plan?.key ?: "off", mode, sets, LocalDateTime.now().toString()))
    }
    fun setMode(date: LocalDate, mode: String) = viewModelScope.launch {
        val s = sessions.value[date] ?: return@launch; val plan = Program.dayFor(date) ?: return@launch
        s.mode = mode
        plan.exercises.forEach { pe ->
            val n = if (mode == "C") pe.compressedSets else pe.standardSets
            val list = s.sets.getOrPut(pe.id) { mutableListOf() }
            while (list.size < n) list += SetEntry(load = list.lastOrNull()?.load)
            if (list.size > n) { val done = list.filter { it.done }; val trimmed = if (done.size > n) done else list.take(n); list.clear(); list += trimmed }
        }
        repo.saveSession(date, s)
    }
    fun updateSet(date: LocalDate, exId: String, idx: Int, load: Double?, reps: Int?, rir: Int?) = viewModelScope.launch {
        val s = sessions.value[date] ?: return@launch
        val set = s.sets[exId]?.getOrNull(idx) ?: return@launch
        set.load = load; set.reps = reps; set.rir = rir
        repo.saveSession(date, s)
    }
    /** Marks a set done (filling in the prescription if the fields were left blank) and starts the rest timer. */
    fun toggleDone(date: LocalDate, exId: String, idx: Int, prescLoad: Double?, prescReps: Int) = viewModelScope.launch {
        val s = sessions.value[date] ?: return@launch
        val set = s.sets[exId]?.getOrNull(idx) ?: return@launch
        if (!set.done) {
            if (set.load == null && prescLoad != null) set.load = prescLoad
            if (set.reps == null) set.reps = prescReps
            set.done = true
            Program.resolve(exId, s)?.restSec?.let { startTimer(it) }
        } else set.done = false
        repo.saveSession(date, s)
    }
    fun finishSession(date: LocalDate) = viewModelScope.launch {
        val s = sessions.value[date] ?: return@launch
        s.finishedAt = LocalDateTime.now().toString()
        s.prSummary = PersonalRecords.summary(PersonalRecords.detect(date, s, sessions.value, days.value, profile.value))
        repo.saveSession(date, s)
        stopTimer()
    }
    fun discardSession(date: LocalDate) = viewModelScope.launch { repo.deleteSession(date) }
    fun addSet(date: LocalDate, exId: String) = viewModelScope.launch {
        val s = sessions.value[date] ?: return@launch
        val list = s.sets.getOrPut(exId) { mutableListOf() }
        list += SetEntry(load = list.lastOrNull()?.load)
        repo.saveSession(date, s)
    }
    fun removeSet(date: LocalDate, exId: String) = viewModelScope.launch {
        val s = sessions.value[date] ?: return@launch
        val list = s.sets[exId] ?: return@launch
        if (list.isEmpty()) return@launch
        list.removeAt(list.lastIndex)
        repo.saveSession(date, s)
    }
    fun setFeel(date: LocalDate, exId: String, feel: String) = viewModelScope.launch {
        val s = sessions.value[date] ?: return@launch
        s.feel[exId] = feel
        repo.saveSession(date, s)
    }
    fun setNotes(date: LocalDate, notes: String) = viewModelScope.launch {
        val s = sessions.value[date] ?: return@launch
        s.notes = notes.ifBlank { null }
        repo.saveSession(date, s)
    }
    fun setVariant(date: LocalDate, exId: String, variant: String?) = viewModelScope.launch {
        val s = sessions.value[date] ?: return@launch
        if (variant == null) s.variant.remove(exId) else s.variant[exId] = variant
        repo.saveSession(date, s)
    }
    fun addCatalogExercise(date: LocalDate, exId: String) = viewModelScope.launch {
        val s = sessions.value[date] ?: return@launch
        if (s.sets.containsKey(exId)) return@launch
        val ex = Program.EX[exId] ?: return@launch
        val presc = Ladders.prescribe(Ladder.parse(profile.value.progPolicy), ex, date, sessions.value)
        s.sets[exId] = mutableListOf(SetEntry(load = presc.load))
        repo.saveSession(date, s)
    }
    fun addCustomExercise(date: LocalDate, extra: ExtraExercise) = viewModelScope.launch {
        val s = sessions.value[date] ?: return@launch
        s.extras[extra.id] = extra
        s.sets[extra.id] = mutableListOf(SetEntry())
        repo.saveSession(date, s)
    }
    fun removeExercise(date: LocalDate, exId: String) = viewModelScope.launch {
        val s = sessions.value[date] ?: return@launch
        s.sets.remove(exId)
        s.extras.remove(exId)
        s.feel.remove(exId)
        s.variant.remove(exId)
        s.supersets.remove(exId)
        repo.saveSession(date, s)
    }
    fun patchSet(date: LocalDate, exId: String, idx: Int, block: SetEntry.() -> Unit) = viewModelScope.launch {
        val s = sessions.value[date] ?: return@launch
        val set = s.sets[exId]?.getOrNull(idx) ?: return@launch
        set.block()
        repo.saveSession(date, s)
    }
    fun setSuperset(date: LocalDate, exId: String, group: String?) = viewModelScope.launch {
        val s = sessions.value[date] ?: return@launch
        if (group.isNullOrBlank()) s.supersets.remove(exId) else s.supersets[exId] = group.uppercase().take(1)
        repo.saveSession(date, s)
    }
    fun importWorkoutCsv(uri: Uri) = viewModelScope.launch {
        runCatching {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                ?: error("Could not open the CSV.")
            val parsed = WorkoutCsv.parse(text)
            parsed.sessions.forEach { (d, incoming) ->
                val cur = sessions.value[d]
                if (cur == null) repo.saveSession(d, incoming)
                else {
                    incoming.sets.forEach { (id, rows) ->
                        cur.sets[id] = rows
                    }
                    incoming.extras.forEach { (id, extra) -> cur.extras.putIfAbsent(id, extra) }
                    repo.saveSession(d, cur)
                }
            }
            backupMessage.value = "Imported ${parsed.sessions.size} sessions" +
                if (parsed.unmatched.isEmpty()) "." else ". Unmatched names kept as custom: ${parsed.unmatched.take(6).joinToString()}."
        }.onFailure { backupMessage.value = it.message ?: "CSV import failed." }
    }
    fun saveWeight(date: LocalDate, kg: Double?) = viewModelScope.launch {
        val cur = repo.getDay(date) ?: DayLogModel(date.toString())
        repo.saveDay(if (kg == null) cur.copy(weight = null, weightSrc = null) else cur.copy(weight = kg, weightSrc = "manual"))
    }

    /* ---- backup ---- */
    val backupMessage = MutableStateFlow("")
    fun suggestedBackupName(): String = "grit-backup-${LocalDate.now()}.json"
    fun exportTo(uri: Uri) = viewModelScope.launch {
        runCatching {
            val file = repo.snapshot()
            val bytes = BackupCodec.encode(file).toByteArray(Charsets.UTF_8)
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Could not open the destination file.")
            backupMessage.value = "Exported ${file.days.size} days and ${file.sessions.size} sessions."
        }.onFailure { backupMessage.value = it.message ?: "Export failed." }
    }
    fun importFrom(uri: Uri) = viewModelScope.launch {
        runCatching {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                ?: error("Could not open the backup file.")
            val file = BackupCodec.decode(text)
            repo.restore(file)
            backupMessage.value = "Restored ${file.days.size} days and ${file.sessions.size} sessions from ${file.exportedAt}."
        }.onFailure { backupMessage.value = it.message ?: "Import failed." }
    }

    /* ---- profile ---- */
    fun saveProfile(p: Profile) = viewModelScope.launch { repo.saveProfile(p) }
    fun markLever(n: Int) = viewModelScope.launch { repo.saveProfile(profile.value.copy(lever = n, leverDate = LocalDate.now().toString())) }

    /* ---- rest timer ---- */
    val timerLeft = MutableStateFlow(0)
    val timerTotal = MutableStateFlow(0)
    private var timerJob: Job? = null
    fun startTimer(sec: Int) {
        timerJob?.cancel(); timerLeft.value = sec; timerTotal.value = sec
        val ctx = getApplication<Application>()
        RestAlerts.tick(ctx, sec, sec)
        timerJob = viewModelScope.launch {
            while (timerLeft.value > 0) {
                delay(1000)
                timerLeft.value -= 1
                RestAlerts.tick(ctx, timerLeft.value, timerTotal.value)
            }
            vibrate()
            RestAlerts.tick(ctx, 0, timerTotal.value)
        }
    }
    fun addTimer(sec: Int) { if (timerJob?.isActive == true) { timerLeft.value += sec; timerTotal.value += sec } }
    fun stopTimer() {
        timerJob?.cancel(); timerLeft.value = 0; timerTotal.value = 0
        RestAlerts.clear(getApplication())
    }
    private fun vibrate() {
        val ctx = getApplication<Application>()
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                          else @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))
    }
}
