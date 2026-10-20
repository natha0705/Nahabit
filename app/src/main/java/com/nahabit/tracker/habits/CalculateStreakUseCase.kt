package com.nahabit.tracker.habits

import javax.inject.Inject

data class StreakResult(
    val currentStreak: Int,
    val bestStreak: Int
)

class CalculateStreakUseCase @Inject constructor() {

    operator fun invoke(
        completedEpochDays: Set<Long>,
        todayEpochDay: Long,
        graceDaysAllowed: Int = 0
    ): StreakResult {
        if (completedEpochDays.isEmpty()) return StreakResult(0, 0)

        val sortedDays = completedEpochDays.sorted()
        var bestStreak = 0
        var tempStreak = 0
        var previousDay: Long? = null

        for (day in sortedDays) {
            if (previousDay == null) {
                tempStreak = 1
            } else {
                val gap = (day - previousDay - 1).toInt()
                if (gap <= graceDaysAllowed) {
                    tempStreak += 1
                } else {
                    tempStreak = 1
                }
            }
            if (tempStreak > bestStreak) {
                bestStreak = tempStreak
            }
            previousDay = day
        }

        var currentStreak = 0
        var cursorDay = if (completedEpochDays.contains(todayEpochDay)) {
            todayEpochDay
        } else {
            todayEpochDay - 1L
        }
        var graceRemaining = graceDaysAllowed

        while (true) {
            if (completedEpochDays.contains(cursorDay)) {
                currentStreak += 1
                graceRemaining = graceDaysAllowed
                cursorDay -= 1L
            } else if (currentStreak > 0 && graceRemaining > 0) {
                graceRemaining -= 1
                cursorDay -= 1L
            } else {
                break
            }
        }

        return StreakResult(
            currentStreak = currentStreak,
            bestStreak = maxOf(bestStreak, currentStreak)
        )
    }
}
