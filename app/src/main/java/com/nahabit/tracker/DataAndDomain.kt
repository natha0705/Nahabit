package com.nahabit.tracker

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.pow

@Immutable
@Serializable
@Entity(tableName = "habits_tasks")
data class HabitTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String,
    val targetCount: Int = 1,
    val currentCount: Int = 0,
    val isCompleted: Boolean = false,
    val dateEpochDay: Long
)

@Immutable
@Serializable
@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val mood: String,
    val content: String
)

@Immutable
@Serializable
@Entity(tableName = "bad_habits")
data class BadHabitCounter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitName: String,
    val lastRelapseTimestamp: Long
)

@Immutable
@Serializable
data class ExerciseItem(
    val name: String,
    val sets: Int,
    val repsOrSeconds: Int,
    val isTimeBased: Boolean,
    val restSeconds: Int
)

@Immutable
@Serializable
@Entity(tableName = "workout_routines")
data class WorkoutRoutine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineName: String,
    val exercisesJson: String
)

@Immutable
@Serializable
@Entity(tableName = "body_metrics")
data class BodyMetric(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val weightKg: Float,
    val heightCm: Float,
    val waistCm: Float,
    val chestCm: Float,
    val armCm: Float,
    val caloriesConsumed: Int
)

@Serializable
data class NahabitMasterBackup(
    val version: Int = 1,
    val exportTimestamp: Long,
    val habitsTasks: List<HabitTask>,
    val journal: List<JournalEntry>,
    val badHabits: List<BadHabitCounter>,
    val routines: List<WorkoutRoutine>,
    val bodyMetrics: List<BodyMetric>
)

@Dao
interface NahabitDao {
    @Query("SELECT * FROM habits_tasks ORDER BY id DESC")
    fun observeHabits(): Flow<List<HabitTask>>

    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC")
    fun observeJournal(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM bad_habits ORDER BY id ASC")
    fun observeBadHabits(): Flow<List<BadHabitCounter>>

    @Query("SELECT * FROM workout_routines ORDER BY id DESC")
    fun observeRoutines(): Flow<List<WorkoutRoutine>>

    @Query("SELECT * FROM body_metrics ORDER BY dateEpochDay DESC")
    fun observeBodyMetrics(): Flow<List<BodyMetric>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabit(item: HabitTask)

    @Delete
    suspend fun deleteHabit(item: HabitTask)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournal(entry: JournalEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBadHabit(counter: BadHabitCounter)

    @Delete
    suspend fun deleteBadHabit(counter: BadHabitCounter)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutine(routine: WorkoutRoutine)

    @Delete
    suspend fun deleteRoutine(routine: WorkoutRoutine)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyMetric(metric: BodyMetric)

    @Query("SELECT * FROM habits_tasks") suspend fun getAllHabitsSync(): List<HabitTask>
    @Query("SELECT * FROM journal_entries") suspend fun getAllJournalSync(): List<JournalEntry>
    @Query("SELECT * FROM bad_habits") suspend fun getAllBadHabitsSync(): List<BadHabitCounter>
    @Query("SELECT * FROM workout_routines") suspend fun getAllRoutinesSync(): List<WorkoutRoutine>
    @Query("SELECT * FROM body_metrics") suspend fun getAllBodyMetricsSync(): List<BodyMetric>

    @Query("DELETE FROM habits_tasks") suspend fun clearHabits()
    @Query("DELETE FROM journal_entries") suspend fun clearJournal()
    @Query("DELETE FROM bad_habits") suspend fun clearBadHabits()
    @Query("DELETE FROM workout_routines") suspend fun clearRoutines()
    @Query("DELETE FROM body_metrics") suspend fun clearBodyMetrics()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllHabits(list: List<HabitTask>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllJournal(list: List<JournalEntry>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllBadHabits(list: List<BadHabitCounter>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllRoutines(list: List<WorkoutRoutine>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllBodyMetrics(list: List<BodyMetric>)

    @Transaction
    suspend fun restoreFullBackup(backup: NahabitMasterBackup) {
        clearHabits()
        clearJournal()
        clearBadHabits()
        clearRoutines()
        clearBodyMetrics()
        insertAllHabits(backup.habitsTasks)
        insertAllJournal(backup.journal)
        insertAllBadHabits(backup.badHabits)
        insertAllRoutines(backup.routines)
        insertAllBodyMetrics(backup.bodyMetrics)
    }
}

@Database(
    entities = [HabitTask::class, JournalEntry::class, BadHabitCounter::class, WorkoutRoutine::class, BodyMetric::class],
    version = 1,
    exportSchema = false
)
abstract class NahabitDatabase : RoomDatabase() {
    abstract fun dao(): NahabitDao

    companion object {
        @Volatile
        private var INSTANCE: NahabitDatabase? = null

        fun getInstance(context: Context): NahabitDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    NahabitDatabase::class.java,
                    "nahabit_local.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}

object PhysicalStateCalculator {
    @Immutable
    data class HealthEvaluation(val bmi: Float, val whtr: Float, val status: String)

    fun evaluate(weightKg: Float, heightCm: Float, waistCm: Float): HealthEvaluation {
        if (heightCm <= 0f || weightKg <= 0f) return HealthEvaluation(0f, 0f, "Datos incompletos")
        val heightM = heightCm / 100f
        val bmi = weightKg / (heightM.pow(2))
        val whtr = if (waistCm > 0f) waistCm / heightCm else 0f

        val status = when {
            bmi < 18.5f -> "Bajo peso"
            bmi in 18.5f..24.9f && (whtr == 0f || whtr < 0.5f) -> "Estado físico óptimo"
            bmi >= 25.0f && whtr in 0.01f..0.49f -> "Atlético / Alta masa muscular"
            bmi in 25.0f..29.9f -> "Sobrepeso moderado"
            else -> "Requiere atención metabólica"
        }
        return HealthEvaluation(bmi, whtr, status)
    }
}

object BackupManager {
    val jsonFormat = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun writeJsonToUri(context: Context, uri: Uri, jsonContent: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(jsonContent.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun readJsonFromUri(context: Context, uri: Uri): String? {
        return try {
            val sb = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        sb.append(line)
                        line = reader.readLine()
                    }
                }
            }
            sb.toString()
        } catch (e: Exception) {
            null
        }
    }

    fun serializeMasterBackup(backup: NahabitMasterBackup): String = jsonFormat.encodeToString(backup)
    fun deserializeMasterBackup(raw: String): NahabitMasterBackup = jsonFormat.decodeFromString(raw)

    fun serializeRoutine(routine: WorkoutRoutine): String = jsonFormat.encodeToString(routine)
    fun deserializeRoutine(raw: String): WorkoutRoutine = jsonFormat.decodeFromString(raw)
}

class SafeHealthConnectBridge(private val context: Context) {
    fun isAvailable(): Boolean {
        return runCatching {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        }.getOrDefault(false)
    }

    suspend fun syncMetricsLocally(weightKg: Double, caloriesKcal: Double) {
        if (!isAvailable()) return
        runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            val now = Instant.now()
            val weightRecord = WeightRecord(
                weight = Mass.kilograms(weightKg),
                time = now,
                zoneOffset = ZoneOffset.UTC
            )
            val nutritionRecord = NutritionRecord(
                energy = Energy.kilocalories(caloriesKcal),
                startTime = now.minusSeconds(60),
                endTime = now,
                startZoneOffset = ZoneOffset.UTC,
                endZoneOffset = ZoneOffset.UTC
            )
            client.insertRecords(listOf(weightRecord, nutritionRecord))
        }
    }
}

class NahabitWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = NahabitDatabase.getInstance(context)
        val habits = runCatching { db.dao().getAllHabitsSync() }.getOrDefault(emptyList())
        val completed = habits.count { it.isCompleted }

        provideContent {
            Column(modifier = GlanceModifier.padding(12.dp)) {
                Text(text = "Nahabit • 100% Local")
                Text(text = "Progreso: $completed / ${habits.size} completados")
            }
        }
    }
}

class NahabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NahabitWidget()
}
