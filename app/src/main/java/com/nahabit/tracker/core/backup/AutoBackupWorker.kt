package com.nahabit.tracker.core.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nahabit.tracker.core.di.SystemEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                SystemEntryPoint::class.java
            )
            val engine = BackupAndExportEngine(applicationContext, entryPoint.dao())
            engine.createLocalRotatingSnapshot()
            Result.success()
        }.getOrDefault(Result.retry())
    }

    companion object {
        private const val WORK_NAME = "nahabit_daily_auto_backup"

        fun syncSchedule(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (enabled) {
                val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS).build()
                workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            } else {
                workManager.cancelUniqueWork(WORK_NAME)
            }
        }
    }
}
