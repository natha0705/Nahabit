package com.nahabit.tracker.core.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    fun isAvailable(): Boolean {
        return runCatching {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        }.getOrDefault(false)
    }

    suspend fun readTodaySteps(startOfDay: Instant, now: Instant): Long {
        if (!isAvailable()) return 0L
        return runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                )
            )
            response.records.sumOf { it.count }
        }.getOrDefault(0L)
    }

    suspend fun writeWeight(weightKg: Double, time: Instant) {
        if (!isAvailable()) return
        runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            val record = WeightRecord(
                weight = androidx.health.connect.client.units.Mass.kilograms(weightKg),
                time = time,
                zoneOffset = ZoneOffset.UTC
            )
            client.insertRecords(listOf(record))
        }
    }
}
