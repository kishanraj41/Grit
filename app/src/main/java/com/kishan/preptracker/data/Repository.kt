package com.kishan.preptracker.data

import android.content.Context
import androidx.room.withTransaction
import com.kishan.preptracker.domain.DayLogModel
import com.kishan.preptracker.domain.Profile
import com.kishan.preptracker.domain.SessionData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime

class Repository(context: Context) {
    private val db = AppDatabase.get(context)
    private val profileStore = ProfileStore(context)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val profile: Flow<Profile> = profileStore.profile
    suspend fun saveProfile(p: Profile) = profileStore.save(p)

    val days: Flow<Map<LocalDate, DayLogModel>> = db.dayLogs().observeAll().map { list ->
        list.associate { LocalDate.parse(it.date) to it.toModel() }
    }
    val sessions: Flow<Map<LocalDate, SessionData>> = db.sessions().observeAll().map { list ->
        list.associate { LocalDate.parse(it.date) to json.decodeFromString<SessionData>(it.json) }
    }

    suspend fun getDay(date: LocalDate): DayLogModel? = db.dayLogs().get(date.toString())?.toModel()
    suspend fun saveDay(m: DayLogModel) = db.dayLogs().upsert(m.toEntity())
    suspend fun deleteDay(date: LocalDate) = db.dayLogs().delete(date.toString())

    suspend fun saveSession(date: LocalDate, s: SessionData) = db.sessions().upsert(SessionEntity(date.toString(), json.encodeToString(s)))
    suspend fun deleteSession(date: LocalDate) = db.sessions().delete(date.toString())

    suspend fun snapshot(): BackupFile {
        val days = db.dayLogs().getAll().map { it.toModel() }
        val sessions = db.sessions().getAll().map { BackupSession(it.date, json.decodeFromString(it.json)) }
        return BackupFile(
            format = BackupFile.FORMAT,
            version = BackupFile.VERSION,
            exportedAt = LocalDateTime.now().toString(),
            profile = profileStore.profile.first(),
            days = days,
            sessions = sessions,
        )
    }

    /** Replaces all local days, sessions, and profile with the backup. */
    suspend fun restore(file: BackupFile) {
        db.withTransaction {
            db.dayLogs().deleteAll()
            db.sessions().deleteAll()
            if (file.days.isNotEmpty()) db.dayLogs().upsertAll(file.days.map { it.toEntity() })
            if (file.sessions.isNotEmpty()) {
                db.sessions().upsertAll(file.sessions.map { SessionEntity(it.date, json.encodeToString(it.session)) })
            }
        }
        profileStore.save(file.profile)
    }

    private fun DayLogEntity.toModel() = DayLogModel(
        date, weight, weightSrc, sleepH, sleepSrc, steps, stepsSrc, kcal, kcalSrc, protein, proteinSrc,
        rhr, rhrSrc, hrvMs, hrvSrc, waistCm, armCm, note, gymAt,
    )
    private fun DayLogModel.toEntity() = DayLogEntity(
        date, weight, weightSrc, sleepH, sleepSrc, steps, stepsSrc, kcal, kcalSrc, protein, proteinSrc,
        rhr, rhrSrc, hrvMs, hrvSrc, waistCm, armCm, note, gymAt,
    )
}
