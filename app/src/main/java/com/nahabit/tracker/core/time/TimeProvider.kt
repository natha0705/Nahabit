package com.nahabit.tracker.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeProvider @Inject constructor() {
    fun currentLogicalEpochDay(
        dayResetHour: Int = 0,
        nowInstant: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val safeResetHour = dayResetHour.coerceIn(0, 12)
        val zonedDateTime: ZonedDateTime = nowInstant.atZone(zoneId)
        val effectiveDate: LocalDate = if (zonedDateTime.hour < safeResetHour) {
            zonedDateTime.toLocalDate().minusDays(1)
        } else {
            zonedDateTime.toLocalDate()
        }
        return effectiveDate.toEpochDay()
    }

    fun epochDayToLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
}
