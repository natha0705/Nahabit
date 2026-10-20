package com.nahabit.tracker.habits

import androidx.compose.runtime.Immutable
import com.nahabit.tracker.core.database.DailyWaterEntity
import com.nahabit.tracker.core.database.HabitDefinitionEntity
import com.nahabit.tracker.core.database.HabitLogEntity
import com.nahabit.tracker.core.database.NahabitDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class HabitWithStreakUiModel(
    val definition: HabitDefinitionEntity,
    val completedToday: Boolean,
    val todayValue: Int,
    val todayNote: String = "",
    val currentStreak: Int,
    val bestStreak: Int,
    val last28DaysCompleted: List<Boolean> = emptyList()
)

@Singleton
class HabitsRepository @Inject constructor(
    private val dao: NahabitDao,
    private val calculateStreakUseCase: CalculateStreakUseCase
) {
    fun observeHabitsForDay(selectedEpochDay: Long): Flow<List<HabitWithStreakUiModel>> {
        return combine(
            dao.observeActiveHabitDefinitions(),
            dao.observeAllHabitLogs()
        ) { definitions, allLogs ->
            val logsByHabit = allLogs.groupBy { it.habitId }
            definitions.map { def ->
                val habitLogs = logsByHabit[def.id].orEmpty()
                val completedDaysSet = habitLogs
                    .filter { it.isCompleted }
                    .map { it.epochDay }
                    .toSet()

                val dayLog = habitLogs.firstOrNull { it.epochDay == selectedEpochDay }
                val streak = calculateStreakUseCase(
                    completedEpochDays = completedDaysSet,
                    todayEpochDay = selectedEpochDay,
                    graceDaysAllowed = def.graceDaysAllowed
                )

                val heatmap28 = (27 downTo 0).map { offset ->
                    completedDaysSet.contains(selectedEpochDay - offset)
                }

                HabitWithStreakUiModel(
                    definition = def,
                    completedToday = dayLog?.isCompleted == true,
                    todayValue = dayLog?.completedValue ?: 0,
                    todayNote = dayLog?.note.orEmpty(),
                    currentStreak = streak.currentStreak,
                    bestStreak = streak.bestStreak,
                    last28DaysCompleted = heatmap28
                )
            }
        }
    }

    fun observeWaterForDay(logicalEpochDay: Long): Flow<DailyWaterEntity> {
        return dao.observeWaterForDay(logicalEpochDay).map { entity ->
            entity ?: DailyWaterEntity(epochDay = logicalEpochDay, glassesCount = 0, targetGlasses = 8)
        }
    }

    suspend fun updateWaterGlasses(logicalEpochDay: Long, current: DailyWaterEntity, delta: Int) {
        val nextCount = (current.glassesCount + delta).coerceIn(0, 30)
        dao.upsertWaterForDay(
            current.copy(epochDay = logicalEpochDay, glassesCount = nextCount)
        )
    }

    suspend fun createHabit(title: String, targetValue: Int, createdEpochDay: Long) {
        dao.upsertHabitDefinition(
            HabitDefinitionEntity(
                title = title.trim(),
                targetValue = targetValue.coerceAtLeast(1),
                createdEpochDay = createdEpochDay
            )
        )
    }

    suspend fun createAdvancedHabit(definition: HabitDefinitionEntity): Long {
        return dao.upsertHabitDefinition(definition)
    }

    suspend fun incrementHabitOnDay(
        habit: HabitWithStreakUiModel,
        epochDay: Long,
        note: String = habit.todayNote
    ) {
        val existingLog = dao.getHabitLogForDaySync(habit.definition.id, epochDay)
        val nextVal = habit.todayValue + 1
        val completed = if (habit.definition.goalComparison == "AT_MOST") {
            nextVal <= habit.definition.targetValue
        } else {
            nextVal >= habit.definition.targetValue
        }

        dao.upsertHabitLog(
            HabitLogEntity(
                id = existingLog?.id ?: 0L,
                habitId = habit.definition.id,
                epochDay = epochDay,
                completedValue = nextVal,
                isCompleted = completed,
                note = note
            )
        )
    }

    suspend fun saveHabitNoteForDay(habit: HabitWithStreakUiModel, epochDay: Long, note: String) {
        val existingLog = dao.getHabitLogForDaySync(habit.definition.id, epochDay)
        dao.upsertHabitLog(
            HabitLogEntity(
                id = existingLog?.id ?: 0L,
                habitId = habit.definition.id,
                epochDay = epochDay,
                completedValue = habit.todayValue,
                isCompleted = habit.completedToday,
                note = note.trim()
            )
        )
    }

    suspend fun archiveHabit(definition: HabitDefinitionEntity) {
        dao.upsertHabitDefinition(definition.copy(isArchived = true))
    }

    suspend fun togglePauseHabit(definition: HabitDefinitionEntity) {
        dao.upsertHabitDefinition(definition.copy(isPaused = !definition.isPaused))
    }

    suspend fun moveHabitOrder(definition: HabitDefinitionEntity, delta: Int) {
        dao.upsertHabitDefinition(definition.copy(sortOrder = (definition.sortOrder + delta).coerceAtLeast(0)))
    }

    suspend fun deleteHabit(definition: HabitDefinitionEntity) {
        dao.deleteHabitDefinition(definition)
    }
}
