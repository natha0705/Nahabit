package com.nahabit.tracker.core.database

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Immutable
@Serializable
@Entity(
    tableName = "habit_definitions",
    indices = [Index(value = ["isArchived", "sortOrder"])]
)
data class HabitDefinitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String = "General",
    val targetValue: Int = 1,
    val unit: String = "",
    val frequencyDaysMask: Int = 0b1111111,
    val graceDaysAllowed: Int = 0,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdEpochDay: Long,
    val habitType: String = "YES_NO",
    val goalComparison: String = "AT_LEAST",
    val frequencyType: String = "DAILY",
    val frequencyParam: Int = 7,
    val timeOfDayGroup: String = "ANYTIME",
    val iconEmoji: String = "✨",
    val colorHex: String = "#38BDF8",
    val isPaused: Boolean = false,
    val endEpochDay: Long? = null,
    val subtasksCsv: String = "",
    val reminderHour: Int = -1,
    val reminderMinute: Int = 0
)

@Immutable
@Serializable
@Entity(
    tableName = "habit_logs",
    foreignKeys = [
        ForeignKey(
            entity = HabitDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["habitId", "epochDay"], unique = true),
        Index(value = ["epochDay"])
    ]
)
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val epochDay: Long,
    val completedValue: Int,
    val isCompleted: Boolean,
    val note: String = ""
)

@Immutable
@Serializable
@Entity(
    tableName = "journal_entries",
    indices = [Index(value = ["timestamp"]), Index(value = ["isFavorite"])]
)
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val mood: String,
    val content: String,
    val moodScore: Int = 4,
    val tags: String = "",
    val templateType: String = "FREE",
    val isFavorite: Boolean = false,
    val photoUri: String = ""
)

@Immutable
@Serializable
@Entity(tableName = "mindfulness_sessions")
data class MindfulnessSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val sessionType: String,
    val durationMinutes: Int
)

@Immutable
@Serializable
@Entity(tableName = "bad_habits")
data class BadHabitCounterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitName: String,
    val lastRelapseTimestamp: Long,
    val dailyCostMoney: Float = 0f,
    val dailyMinutesWasted: Int = 0,
    val whyIStartedReason: String = ""
)

@Immutable
@Serializable
@Entity(
    tableName = "bad_habit_relapses",
    foreignKeys = [
        ForeignKey(
            entity = BadHabitCounterEntity::class,
            parentColumns = ["id"],
            childColumns = ["badHabitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["badHabitId"])]
)
data class BadHabitRelapseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val badHabitId: Long,
    val timestamp: Long,
    val triggerReason: String,
    val note: String = ""
)

@Immutable
@Serializable
@Entity(tableName = "workout_routines")
data class WorkoutRoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineName: String,
    val exercisesJson: String
)

@Immutable
@Serializable
@Entity(
    tableName = "body_metrics",
    indices = [Index(value = ["dateEpochDay"])]
)
data class BodyMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val weightKg: Float,
    val heightCm: Float,
    val waistCm: Float,
    val chestCm: Float,
    val armCm: Float,
    val caloriesConsumed: Int
)

@Immutable
@Serializable
@Entity(tableName = "exercise_library")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val primaryMuscle: String,
    val secondaryMuscle: String = "",
    val equipment: String,
    val difficulty: String = "Intermedio",
    val isCustom: Boolean = false,
    val description: String = ""
)

@Immutable
@Serializable
@Entity(
    tableName = "workout_sessions",
    indices = [Index(value = ["dateEpochDay"])]
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long = 0,
    val routineName: String,
    val dateEpochDay: Long,
    val durationSeconds: Long,
    val totalVolumeKg: Float,
    val notes: String = ""
)

@Immutable
@Serializable
@Entity(
    tableName = "workout_set_logs",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"]), Index(value = ["exerciseId"])]
)
data class WorkoutSetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val setNumber: Int,
    val weightKg: Float,
    val repsOrSeconds: Int,
    val isTimeBased: Boolean,
    val rpe: Float = 8.0f
)

@Immutable
@Serializable
@Entity(tableName = "daily_water_logs")
data class DailyWaterEntity(
    @PrimaryKey val epochDay: Long,
    val glassesCount: Int = 0,
    val targetGlasses: Int = 8
)

@Immutable
@Serializable
@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val durationDays: Int,
    val startEpochDay: Long,
    val isCompleted: Boolean = false
)

@Immutable
@Serializable
@Entity(
    tableName = "body_composition_extended",
    indices = [Index(value = ["dateEpochDay"])]
)
data class BodyCompositionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val weightKg: Float,
    val heightCm: Float,
    val neckCm: Float,
    val shouldersCm: Float,
    val chestCm: Float,
    val waistCm: Float,
    val hipsCm: Float,
    val thighCm: Float,
    val bicepCm: Float,
    val bodyFatPercent: Float = 0f,
    val muscleMassKg: Float = 0f
)

@Immutable
@Serializable
@Entity(tableName = "sleep_logs")
data class SleepLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val durationMinutes: Int,
    val sleepQuality: String,
    val notes: String = ""
)

@Immutable
@Serializable
@Entity(tableName = "meal_logs")
data class MealLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val mealType: String,
    val foodName: String,
    val caloriesKcal: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float
)

@Immutable
@Serializable
@Entity(tableName = "fasting_sessions")
data class FastingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimestamp: Long,
    val targetHours: Int,
    val endTimestamp: Long? = null,
    val isCompleted: Boolean = false
)

@Immutable
@Serializable
@Entity(tableName = "medication_reminders")
data class MedicationReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineName: String,
    val dosage: String,
    val timeOfDay: String,
    val isEnabled: Boolean = true
)

@Immutable
@Serializable
@Entity(tableName = "long_term_goals")
data class LongTermGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val deadlineEpochDay: Long,
    val linkedHabitId: Long? = null,
    val milestonesCsv: String = "",
    val completedMilestonesCount: Int = 0,
    val totalMilestonesCount: Int = 1,
    val isCompleted: Boolean = false
)

@Immutable
@Serializable
@Entity(
    tableName = "todo_tasks",
    indices = [Index(value = ["isCompleted", "priority"])]
)
data class TodoTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val priority: Int = 2,
    val dueEpochDay: Long,
    val subtasksText: String = "",
    val isRecurringDaily: Boolean = false,
    val isCompleted: Boolean = false
)

@Immutable
@Serializable
@Entity(
    tableName = "focus_sessions",
    indices = [Index(value = ["dateEpochDay"])]
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val durationMinutes: Int,
    val projectTag: String,
    val completedTimestamp: Long
)

@Immutable
@Serializable
@Entity(tableName = "chained_routines")
data class ChainedRoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val timeOfDay: String,
    val stepsCsv: String
)
