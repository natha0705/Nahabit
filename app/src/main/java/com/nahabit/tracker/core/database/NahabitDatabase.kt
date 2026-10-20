package com.nahabit.tracker.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HabitDefinitionEntity::class,
        HabitLogEntity::class,
        JournalEntryEntity::class,
        MindfulnessSessionEntity::class,
        BadHabitCounterEntity::class,
        BadHabitRelapseEntity::class,
        WorkoutRoutineEntity::class,
        BodyMetricEntity::class,
        ExerciseEntity::class,
        WorkoutSessionEntity::class,
        WorkoutSetLogEntity::class,
        DailyWaterEntity::class,
        ChallengeEntity::class,
        BodyCompositionEntity::class,
        SleepLogEntity::class,
        MealLogEntity::class,
        FastingSessionEntity::class,
        MedicationReminderEntity::class,
        LongTermGoalEntity::class,
        TodoTaskEntity::class,
        FocusSessionEntity::class,
        ChainedRoutineEntity::class
    ],
    version = 9,
    exportSchema = true
)
abstract class NahabitDatabase : RoomDatabase() {
    abstract fun dao(): NahabitDao
}
