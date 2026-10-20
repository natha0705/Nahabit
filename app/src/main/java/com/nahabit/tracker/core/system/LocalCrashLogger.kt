package com.nahabit.tracker.core.system

import android.content.Context
import java.io.File
import java.time.Instant

object LocalCrashLogger {
    private const val FILE_NAME = "nahabit_local_crash_log.txt"

    fun install(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        val appContext = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val logFile = File(appContext.filesDir, FILE_NAME)
                val report = buildString {
                    appendLine("=== Crash @ ${Instant.now()} (${thread.name}) ===")
                    appendLine(throwable.stackTraceToString().take(2000))
                }
                logFile.writeText(report, Charsets.UTF_8)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun readLatestCrash(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) file.readText(Charsets.UTF_8).takeIf { it.isNotBlank() } else null
    }
}
