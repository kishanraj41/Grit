package com.kishan.preptracker.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "day_logs")
data class DayLogEntity(
    @PrimaryKey val date: String,
    val weight: Double? = null, val weightSrc: String? = null,
    val sleepH: Double? = null, val sleepSrc: String? = null,
    val steps: Int? = null, val stepsSrc: String? = null,
    val kcal: Int? = null, val kcalSrc: String? = null,
    val protein: Int? = null, val proteinSrc: String? = null,
    val rhr: Int? = null, val rhrSrc: String? = null,
    val hrvMs: Double? = null, val hrvSrc: String? = null,
    val waistCm: Double? = null,
    val armCm: Double? = null,
    val note: String? = null,
    val gymAt: String? = null,
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val date: String,
    val json: String,   // SessionData serialised with kotlinx.serialization
)

@Dao
interface DayLogDao {
    @Query("SELECT * FROM day_logs") fun observeAll(): Flow<List<DayLogEntity>>
    @Query("SELECT * FROM day_logs") suspend fun getAll(): List<DayLogEntity>
    @Query("SELECT * FROM day_logs WHERE date = :date") suspend fun get(date: String): DayLogEntity?
    @Upsert suspend fun upsert(e: DayLogEntity)
    @Upsert suspend fun upsertAll(rows: List<DayLogEntity>)
    @Query("DELETE FROM day_logs WHERE date = :date") suspend fun delete(date: String)
    @Query("DELETE FROM day_logs") suspend fun deleteAll()
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions") fun observeAll(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM sessions") suspend fun getAll(): List<SessionEntity>
    @Upsert suspend fun upsert(e: SessionEntity)
    @Upsert suspend fun upsertAll(rows: List<SessionEntity>)
    @Query("DELETE FROM sessions WHERE date = :date") suspend fun delete(date: String)
    @Query("DELETE FROM sessions") suspend fun deleteAll()
}

/**
 * Additive migrations only. Never fall back to destructive — Train sessions and
 * day logs live only in this database.
 */
@Database(entities = [DayLogEntity::class, SessionEntity::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dayLogs(): DayLogDao
    abstract fun sessions(): SessionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE day_logs ADD COLUMN rhrSrc TEXT")
                db.execSQL("ALTER TABLE day_logs ADD COLUMN hrvSrc TEXT")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE day_logs ADD COLUMN gymAt TEXT")
            }
        }
        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "prep.db")
                .addMigrations(*MIGRATIONS)
                .build()
                .also { INSTANCE = it }
        }
    }
}
