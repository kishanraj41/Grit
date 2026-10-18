package com.kishan.preptracker.data

import com.kishan.preptracker.domain.DayLogModel
import com.kishan.preptracker.domain.Profile
import com.kishan.preptracker.domain.SessionData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One Train session inside a backup, keyed by the calendar date it was logged on. */
@Serializable
data class BackupSession(val date: String, val session: SessionData)

/**
 * Portable snapshot of everything Grit stores locally.
 * [format] and [version] are required so a random JSON file cannot silently restore as empty.
 */
@Serializable
data class BackupFile(
    val format: String,
    val version: Int,
    val exportedAt: String,
    val profile: Profile,
    val days: List<DayLogModel> = emptyList(),
    val sessions: List<BackupSession> = emptyList(),
) {
    companion object {
        const val FORMAT = "grit-backup"
        const val VERSION = 1
    }
}

object BackupCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile {
        val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { throw IllegalArgumentException("Could not read backup file.", it) }
        if (file.format != BackupFile.FORMAT)
            throw IllegalArgumentException("Not a Grit backup (format=${file.format}).")
        if (file.version < 1)
            throw IllegalArgumentException("Backup version ${file.version} is invalid.")
        if (file.version > BackupFile.VERSION)
            throw IllegalArgumentException("Backup version ${file.version} is newer than this app (${BackupFile.VERSION}). Update Grit first.")
        return file
    }
}
