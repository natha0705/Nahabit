package com.nahabit.tracker.workout

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nahabit.tracker.core.notifications.NahabitNotificationHelper

class NahabitWorkoutService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val exerciseName = intent?.getStringExtra("EXTRA_EXERCISE") ?: "Entrenamiento activo"
        val seconds = intent?.getIntExtra("EXTRA_SECONDS", 60) ?: 60

        NahabitNotificationHelper.createNotificationChannels(this)
        val targetWhenMs = System.currentTimeMillis() + (seconds * 1000L)

        val builder = NotificationCompat.Builder(this, NahabitNotificationHelper.CHANNEL_WORKOUT)
            .setContentTitle("Descanso: $exerciseName")
            .setContentText("Tiempo objetivo: ${seconds}s")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setWhen(targetWhenMs)
            .setUsesChronometer(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setChronometerCountDown(true)
        }

        startForeground(101, builder.build())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context, exerciseName: String, restSeconds: Int) {
            val intent = Intent(context, NahabitWorkoutService::class.java).apply {
                putExtra("EXTRA_EXERCISE", exerciseName)
                putExtra("EXTRA_SECONDS", restSeconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, NahabitWorkoutService::class.java)
            context.stopService(intent)
        }
    }
}
