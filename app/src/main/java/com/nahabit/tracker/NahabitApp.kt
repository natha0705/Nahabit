package com.nahabit.tracker

import android.app.Application
import com.nahabit.tracker.core.system.LocalCrashLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NahabitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LocalCrashLogger.install(this)
    }
}
