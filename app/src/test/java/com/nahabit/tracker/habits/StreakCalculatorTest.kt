package com.nahabit.tracker.habits

import com.nahabit.tracker.core.database.*
import com.nahabit.tracker.core.time.TimeProvider
import com.nahabit.tracker.stats.CalculateStatsAndGamificationUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class StreakCalculatorTest {

    private val calculateStreak = CalculateStreakUseCase()
    private val timeProvider = TimeProvider()
    private val calculateStats = CalculateStatsAndGamificationUseCase(calculateStreak)

    @Test
    fun `racha consecutiva sin dias perdidos devuelve conteo exacto`() {
        val completedDays = setOf(100L, 101L, 102L, 103L)
        val result = calculateStreak(completedEpochDays = completedDays, todayEpochDay = 103L)
        assertEquals(4, result.currentStreak)
        assertEquals(4, result.bestStreak)
    }

    @Test
    fun `racha se mantiene viva si hoy aun no se ha completado pero ayer si`() {
        val completedDays = setOf(100L, 101L, 102L)
        val result = calculateStreak(completedEpochDays = completedDays, todayEpochDay = 103L)
        assertEquals(3, result.currentStreak)
        assertEquals(3, result.bestStreak)
    }

    @Test
    fun `dias de gracia permiten saltar un dia sin romper la racha`() {
        val completedDays = setOf(100L, 102L, 103L)
        val result = calculateStreak(
            completedEpochDays = completedDays,
            todayEpochDay = 103L,
            graceDaysAllowed = 1
        )
        assertEquals(3, result.currentStreak)
        assertEquals(3, result.bestStreak)
    }

    @Test
    fun `cambio de dia a las 4 AM mantiene las 2 AM dentro del dia anterior`() {
        val zone = ZoneId.of("America/Mexico_City")
        val instant = Instant.parse("2026-10-18T08:30:00Z")
        val logicalDayWithResetAt4Am = timeProvider.currentLogicalEpochDay(
            dayResetHour = 4,
            nowInstant = instant,
            zoneId = zone
        )
        val logicalDayStandardMidnight = timeProvider.currentLogicalEpochDay(
            dayResetHour = 0,
            nowInstant = instant,
            zoneId = zone
        )
        assertEquals(logicalDayStandardMidnight - 1L, logicalDayWithResetAt4Am)
    }

    @Test
    fun `calculo de nivel XP y puntaje de bienestar diario es exacto`() {
        val today = 200L
        val habit = HabitDefinitionEntity(id = 1L, title = "Leer", createdEpochDay = 190L)
        val log = HabitLogEntity(id = 1L, habitId = 1L, epochDay = today, completedValue = 1, isCompleted = true)
        val water = DailyWaterEntity(epochDay = today, glassesCount = 8, targetGlasses = 8)
        val workout = WorkoutSessionEntity(id = 1L, routineName = "Calistenia", dateEpochDay = today, durationSeconds = 1800, totalVolumeKg = 500f)

        val stats = calculateStats(
            todayEpochDay = today,
            habits = listOf(habit),
            logs = listOf(log),
            workouts = listOf(workout),
            journals = emptyList(),
            waters = listOf(water),
            challenges = emptyList()
        )

        assertTrue(stats.totalXp > 0)
        assertEquals(90, stats.wellnessScoreToday)
    }
}
