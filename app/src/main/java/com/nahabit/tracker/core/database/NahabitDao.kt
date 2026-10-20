package com.nahabit.tracker.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NahabitDao {
    @Query("SELECT * FROM habit_definitions WHERE isArchived = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeActiveHabitDefinitions(): Flow<List<HabitDefinitionEntity>>

    @Query("SELECT * FROM habit_logs")
    fun observeAllHabitLogs(): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_definitions WHERE isArchived = 0 ORDER BY sortOrder ASC, id ASC")
    suspend fun getActiveHabitDefinitionsSync(): List<HabitDefinitionEntity>

    @Query("SELECT * FROM habit_logs WHERE epochDay = :epochDay")
    suspend fun getHabitLogsForDaySync(epochDay: Long): List<HabitLogEntity>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND epochDay = :epochDay LIMIT 1")
    suspend fun getHabitLogForDaySync(habitId: Long, epochDay: Long): HabitLogEntity?

    @Query("SELECT * FROM daily_water_logs WHERE epochDay = :epochDay LIMIT 1")
    suspend fun getWaterForDaySync(epochDay: Long): DailyWaterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabitDefinition(habit: HabitDefinitionEntity): Long

    @Delete
    suspend fun deleteHabitDefinition(habit: HabitDefinitionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabitLog(log: HabitLogEntity)

    @Query("SELECT * FROM daily_water_logs WHERE epochDay = :epochDay LIMIT 1")
    fun observeWaterForDay(epochDay: Long): Flow<DailyWaterEntity?>

    @Query("SELECT * FROM daily_water_logs")
    fun observeAllWaterLogs(): Flow<List<DailyWaterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWaterForDay(water: DailyWaterEntity)

    @Query("SELECT * FROM workout_routines ORDER BY id DESC")
    fun observeRoutines(): Flow<List<WorkoutRoutineEntity>>

    @Query("SELECT * FROM workout_sessions ORDER BY dateEpochDay DESC")
    fun observeWorkoutSessions(): Flow<List<WorkoutSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutine(routine: WorkoutRoutineEntity)

    @Delete
    suspend fun deleteRoutine(routine: WorkoutRoutineEntity)

    @Query("SELECT * FROM exercise_library ORDER BY name ASC")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: ExerciseEntity)

    @Query("SELECT * FROM body_metrics ORDER BY dateEpochDay DESC")
    fun observeBodyMetrics(): Flow<List<BodyMetricEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyMetric(metric: BodyMetricEntity)

    @Query("SELECT * FROM body_composition_extended ORDER BY dateEpochDay DESC")
    fun observeBodyComposition(): Flow<List<BodyCompositionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyComposition(entity: BodyCompositionEntity)

    @Query("SELECT * FROM meal_logs WHERE dateEpochDay = :epochDay ORDER BY id DESC")
    fun observeMealsForDay(epochDay: Long): Flow<List<MealLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(entity: MealLogEntity)

    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC")
    fun observeJournalEntries(): Flow<List<JournalEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntry(entry: JournalEntryEntity)

    @Delete
    suspend fun deleteJournalEntry(entry: JournalEntryEntity)

    @Query("SELECT * FROM mindfulness_sessions ORDER BY timestamp DESC")
    fun observeMindfulnessSessions(): Flow<List<MindfulnessSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMindfulnessSession(session: MindfulnessSessionEntity)

    @Query("SELECT * FROM bad_habits ORDER BY id ASC")
    fun observeBadHabits(): Flow<List<BadHabitCounterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBadHabit(counter: BadHabitCounterEntity)

    @Delete
    suspend fun deleteBadHabit(counter: BadHabitCounterEntity)

    @Query("SELECT * FROM bad_habit_relapses ORDER BY timestamp DESC")
    fun observeBadHabitRelapses(): Flow<List<BadHabitRelapseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBadHabitRelapse(relapse: BadHabitRelapseEntity)

    @Query("SELECT * FROM challenges ORDER BY startEpochDay DESC")
    fun observeChallenges(): Flow<List<ChallengeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChallenge(challenge: ChallengeEntity)

    @Delete
    suspend fun deleteChallenge(challenge: ChallengeEntity)

    @Query("SELECT * FROM long_term_goals ORDER BY isCompleted ASC, deadlineEpochDay ASC")
    fun observeLongTermGoals(): Flow<List<LongTermGoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLongTermGoal(goal: LongTermGoalEntity)

    @Delete
    suspend fun deleteLongTermGoal(goal: LongTermGoalEntity)

    @Query("SELECT * FROM todo_tasks ORDER BY isCompleted ASC, priority ASC, dueEpochDay ASC")
    fun observeTodoTasks(): Flow<List<TodoTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTodoTask(task: TodoTaskEntity)

    @Delete
    suspend fun deleteTodoTask(task: TodoTaskEntity)

    @Query("SELECT * FROM focus_sessions ORDER BY completedTimestamp DESC")
    fun observeFocusSessions(): Flow<List<FocusSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFocusSession(session: FocusSessionEntity)

    @Query("SELECT * FROM chained_routines ORDER BY id ASC")
    fun observeChainedRoutines(): Flow<List<ChainedRoutineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChainedRoutine(routine: ChainedRoutineEntity)

    @Query("SELECT * FROM habit_definitions") suspend fun dumpHabitDefinitions(): List<HabitDefinitionEntity>
    @Query("SELECT * FROM habit_logs") suspend fun dumpHabitLogs(): List<HabitLogEntity>
    @Query("SELECT * FROM journal_entries") suspend fun dumpJournalEntries(): List<JournalEntryEntity>
    @Query("SELECT * FROM mindfulness_sessions") suspend fun dumpMindfulnessSessions(): List<MindfulnessSessionEntity>
    @Query("SELECT * FROM bad_habits") suspend fun dumpBadHabits(): List<BadHabitCounterEntity>
    @Query("SELECT * FROM workout_routines") suspend fun dumpWorkoutRoutines(): List<WorkoutRoutineEntity>
    @Query("SELECT * FROM workout_sessions") suspend fun dumpWorkoutSessions(): List<WorkoutSessionEntity>
    @Query("SELECT * FROM daily_water_logs") suspend fun dumpDailyWater(): List<DailyWaterEntity>
    @Query("SELECT * FROM challenges") suspend fun dumpChallenges(): List<ChallengeEntity>
    @Query("SELECT * FROM body_composition_extended") suspend fun dumpBodyComposition(): List<BodyCompositionEntity>
    @Query("SELECT * FROM long_term_goals") suspend fun dumpLongTermGoals(): List<LongTermGoalEntity>
    @Query("SELECT * FROM todo_tasks") suspend fun dumpTodoTasks(): List<TodoTaskEntity>
    @Query("SELECT * FROM focus_sessions") suspend fun dumpFocusSessions(): List<FocusSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllHabitDefs(items: List<HabitDefinitionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllHabitLogs(items: List<HabitLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllJournals(items: List<JournalEntryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllMindfulness(items: List<MindfulnessSessionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllBadHabits(items: List<BadHabitCounterEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllRoutines(items: List<WorkoutRoutineEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllWorkoutSessions(items: List<WorkoutSessionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllWater(items: List<DailyWaterEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllChallenges(items: List<ChallengeEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllBodyComp(items: List<BodyCompositionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllGoals(items: List<LongTermGoalEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllTasks(items: List<TodoTaskEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAllFocusSessions(items: List<FocusSessionEntity>)

    @Query("DELETE FROM habit_logs") suspend fun clearHabitLogs()
    @Query("DELETE FROM habit_definitions") suspend fun clearHabitDefinitions()
    @Query("DELETE FROM journal_entries") suspend fun clearJournalEntries()
    @Query("DELETE FROM mindfulness_sessions") suspend fun clearMindfulnessSessions()
    @Query("DELETE FROM bad_habit_relapses") suspend fun clearBadHabitRelapses()
    @Query("DELETE FROM bad_habits") suspend fun clearBadHabits()
    @Query("DELETE FROM workout_set_logs") suspend fun clearWorkoutSetLogs()
    @Query("DELETE FROM workout_sessions") suspend fun clearWorkoutSessions()
    @Query("DELETE FROM workout_routines") suspend fun clearWorkoutRoutines()
    @Query("DELETE FROM daily_water_logs") suspend fun clearDailyWater()
    @Query("DELETE FROM challenges") suspend fun clearChallenges()
    @Query("DELETE FROM body_composition_extended") suspend fun clearBodyComposition()
    @Query("DELETE FROM long_term_goals") suspend fun clearLongTermGoals()
    @Query("DELETE FROM todo_tasks") suspend fun clearTodoTasks()
    @Query("DELETE FROM focus_sessions") suspend fun clearFocusSessions()

    @Transaction
    suspend fun wipeAllDataTransaction() {
        clearHabitLogs()
        clearHabitDefinitions()
        clearJournalEntries()
        clearMindfulnessSessions()
        clearBadHabitRelapses()
        clearBadHabits()
        clearWorkoutSetLogs()
        clearWorkoutSessions()
        clearWorkoutRoutines()
        clearDailyWater()
        clearChallenges()
        clearBodyComposition()
        clearLongTermGoals()
        clearTodoTasks()
        clearFocusSessions()
    }
}
