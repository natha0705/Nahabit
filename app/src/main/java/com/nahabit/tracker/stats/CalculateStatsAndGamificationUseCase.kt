package com.nahabit.tracker.stats

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.nahabit.tracker.R
import com.nahabit.tracker.core.database.*
import com.nahabit.tracker.habits.CalculateStreakUseCase
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@Immutable
data class BadgeItem(
    val id: String,
    @StringRes val titleRes: Int,
    val isUnlocked: Boolean
)

@Immutable
data class ChallengeProgressModel(
    val entity: ChallengeEntity,
    val elapsedDays: Int,
    val progressFraction: Float
)

@Immutable
data class StatsDashboardModel(
    val level: Int,
    val totalXp: Int,
    val xpForNextLevel: Int,
    val levelProgress: Float,
    val wellnessScoreToday: Int,
    val weeklyRatePercent: Int,
    val monthlyRatePercent: Int,
    val yearlyRatePercent: Int,
    val monthOverMonthDeltaPercent: Int,
    val bestDayName: String,
    val worstDayName: String,
    val mostConsistentHabit: String,
    val leastConsistentHabit: String,
    val dayOfWeekRates: List<Float>,
    val last90DaysHeatmap: List<Float>,
    val badges: List<BadgeItem>,
    val activeChallenges: List<ChallengeProgressModel>,
    val showCompassionMessage: Boolean,
    val globalBestStreak: Int,
    val totalCompletedCheckins: Int
)

class CalculateStatsAndGamificationUseCase @Inject constructor(
    private val calculateStreakUseCase: CalculateStreakUseCase
) {
    operator fun invoke(
        todayEpochDay: Long,
        habits: List<HabitDefinitionEntity>,
        logs: List<HabitLogEntity>,
        workouts: List<WorkoutSessionEntity>,
        journals: List<JournalEntryEntity>,
        waters: List<DailyWaterEntity>,
        challenges: List<ChallengeEntity>,
        locale: Locale = Locale.getDefault()
    ): StatsDashboardModel {
        val completedLogs = logs.filter { it.isCompleted }
        val activeHabitsCount = habits.size.coerceAtLeast(1)

        val totalGlasses = waters.sumOf { it.glassesCount }
        val totalXp = (completedLogs.size * 15) + (workouts.size * 50) + (journals.size * 20) + (totalGlasses * 5)
        val level = (totalXp / 200) + 1
        val xpInCurrentLevel = totalXp % 200
        val xpForNextLevel = 200 - xpInCurrentLevel
        val levelProgress = xpInCurrentLevel / 200f

        val completedTodayCount = completedLogs.count { it.epochDay == todayEpochDay }
        val habitScore = if (habits.isEmpty()) 0 else ((completedTodayCount.toFloat() / activeHabitsCount) * 50f).toInt().coerceAtMost(50)
        val todayWater = waters.firstOrNull { it.epochDay == todayEpochDay }
        val waterScore = if (todayWater == null || todayWater.targetGlasses <= 0) 0 else ((todayWater.glassesCount.toFloat() / todayWater.targetGlasses) * 20f).toInt().coerceAtMost(20)
        val workoutTodayScore = if (workouts.any { it.dateEpochDay == todayEpochDay }) 20 else 0
        val journalTodayScore = if (journals.any { (it.timestamp / 86_400_000L) == todayEpochDay }) 10 else 0
        val wellnessScoreToday = (habitScore + waterScore + workoutTodayScore + journalTodayScore).coerceIn(0, 100)

        fun rateForRange(startDay: Long, endDay: Long): Int {
            if (habits.isEmpty() || endDay < startDay) return 0
            val daysCount = (endDay - startDay + 1).toInt()
            val maxPossible = daysCount * habits.size
            val actual = completedLogs.count { it.epochDay in startDay..endDay }
            return ((actual.toFloat() / maxPossible.toFloat()) * 100f).toInt().coerceIn(0, 100)
        }

        val weeklyRate = rateForRange(todayEpochDay - 6, todayEpochDay)
        val monthlyRate = rateForRange(todayEpochDay - 29, todayEpochDay)
        val previousMonthRate = rateForRange(todayEpochDay - 59, todayEpochDay - 30)
        val yearlyRate = rateForRange(todayEpochDay - 364, todayEpochDay)
        val monthDelta = monthlyRate - previousMonthRate

        val countsByDow = IntArray(7)
        completedLogs.filter { it.epochDay >= todayEpochDay - 89 }.forEach { log ->
            val dowIndex = LocalDate.ofEpochDay(log.epochDay).dayOfWeek.value - 1
            countsByDow[dowIndex]++
        }
        val maxDowCount = (countsByDow.maxOrNull() ?: 1).coerceAtLeast(1)
        val dowRates = countsByDow.map { it.toFloat() / maxDowCount.toFloat() }

        val bestDowIdx = countsByDow.indices.maxByOrNull { countsByDow[it] } ?: 0
        val worstDowIdx = countsByDow.indices.minByOrNull { countsByDow[it] } ?: 6
        val bestDayName = DayOfWeek.of(bestDowIdx + 1).getDisplayName(TextStyle.SHORT, locale)
        val worstDayName = DayOfWeek.of(worstDowIdx + 1).getDisplayName(TextStyle.SHORT, locale)

        val logsLast30ByHabit = completedLogs
            .filter { it.epochDay in (todayEpochDay - 29)..todayEpochDay }
            .groupBy { it.habitId }

        val mostConsistent = habits.maxByOrNull { logsLast30ByHabit[it.id]?.size ?: 0 }?.title ?: "-"
        val leastConsistent = habits.minByOrNull { logsLast30ByHabit[it.id]?.size ?: 0 }?.title ?: "-"

        val logsByDay = completedLogs.groupBy { it.epochDay }
        val heatmap90 = (89 downTo 0).map { offset ->
            val day = todayEpochDay - offset
            val count = logsByDay[day]?.size ?: 0
            if (habits.isEmpty()) 0f else (count.toFloat() / habits.size.toFloat()).coerceIn(0f, 1f)
        }

        val allLogsByHabit = completedLogs.groupBy { it.habitId }
        var globalBestStreak = 0
        var anyStreakBrokenYesterday = false

        habits.forEach { def ->
            val set = allLogsByHabit[def.id]?.map { it.epochDay }?.toSet().orEmpty()
            val streak = calculateStreakUseCase(set, todayEpochDay, def.graceDaysAllowed)
            if (streak.bestStreak > globalBestStreak) globalBestStreak = streak.bestStreak
            if (streak.bestStreak >= 3 && streak.currentStreak == 0) {
                anyStreakBrokenYesterday = true
            }
        }

        val badges = listOf(
            BadgeItem("first_step", R.string.badge_first_step, completedLogs.isNotEmpty()),
            BadgeItem("streak_7", R.string.badge_streak_7, globalBestStreak >= 7),
            BadgeItem("streak_30", R.string.badge_streak_30, globalBestStreak >= 30),
            BadgeItem("centurion", R.string.badge_centurion, completedLogs.size >= 100),
            BadgeItem("athlete", R.string.badge_athlete, workouts.isNotEmpty()),
            BadgeItem("mindful", R.string.badge_mindful, journals.size >= 3)
        )

        val activeChallenges = challenges.map { ch ->
            val elapsed = (todayEpochDay - ch.startEpochDay + 1).toInt().coerceIn(0, ch.durationDays)
            val fraction = (elapsed.toFloat() / ch.durationDays.toFloat()).coerceIn(0f, 1f)
            ChallengeProgressModel(ch, elapsed, fraction)
        }

        return StatsDashboardModel(
            level = level,
            totalXp = totalXp,
            xpForNextLevel = xpForNextLevel,
            levelProgress = levelProgress,
            wellnessScoreToday = wellnessScoreToday,
            weeklyRatePercent = weeklyRate,
            monthlyRatePercent = monthlyRate,
            yearlyRatePercent = yearlyRate,
            monthOverMonthDeltaPercent = monthDelta,
            bestDayName = bestDayName,
            worstDayName = worstDayName,
            mostConsistentHabit = mostConsistent,
            leastConsistentHabit = leastConsistent,
            dayOfWeekRates = dowRates,
            last90DaysHeatmap = heatmap90,
            badges = badges,
            activeChallenges = activeChallenges,
            showCompassionMessage = anyStreakBrokenYesterday,
            globalBestStreak = globalBestStreak,
            totalCompletedCheckins = completedLogs.size
        )
    }
}
