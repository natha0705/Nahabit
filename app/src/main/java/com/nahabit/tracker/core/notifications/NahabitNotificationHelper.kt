package com.nahabit.tracker.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.nahabit.tracker.R
import com.nahabit.tracker.core.database.HabitLogEntity
import com.nahabit.tracker.core.di.SystemEntryPoint
import com.nahabit.tracker.core.widget.NahabitGlanceWidget
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object NahabitNotificationHelper {
    const val CHANNEL_HABITS = "NAHABIT_CHANNEL_HABITS"
    const val CHANNEL_WORKOUT = "NAHABIT_WORKOUT_SERVICE_CHANNEL"
    const val CHANNEL_HYDRATION = "NAHABIT_CHANNEL_HYDRATION"
    const val CHANNEL_ACHIEVEMENTS = "NAHABIT_CHANNEL_ACHIEVEMENTS"

    const val GROUP_HABITS = "com.nahabit.tracker.GROUP_HABITS"
    const val EXTRA_HABIT_ID = "extra_notif_habit_id"
    const val EXTRA_NOTIFICATION_ID = "extra_notif_id"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channels = listOf(
            NotificationChannel(
                CHANNEL_HABITS,
                context.getString(R.string.channel_habits_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ),
            NotificationChannel(
                CHANNEL_WORKOUT,
                context.getString(R.string.channel_workout_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            },
            NotificationChannel(
                CHANNEL_HYDRATION,
                context.getString(R.string.channel_hydration_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ),
            NotificationChannel(
                CHANNEL_ACHIEVEMENTS,
                context.getString(R.string.channel_achievements_name),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        manager.createNotificationChannels(channels)
    }

    fun sendHabitActionableNotification(
        context: Context,
        habitId: Long,
        habitTitle: String
    ) {
        val notifId = habitId.toInt() + 2000
        val actionIntent = Intent(context, HabitNotificationActionReceiver::class.java).apply {
            putExtra(EXTRA_HABIT_ID, habitId)
            putExtra(EXTRA_NOTIFICATION_ID, notifId)
        }
        val actionPendingIntent = PendingIntent.getBroadcast(
            context,
            notifId,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_HABITS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(habitTitle)
            .setContentText(context.getString(R.string.today_subtitle))
            .setGroup(GROUP_HABITS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.checkbox_on_background,
                context.getString(R.string.notif_action_complete),
                actionPendingIntent
            )
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notifId, notification)
    }
}

class HabitNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val habitId = intent?.getLongExtra(NahabitNotificationHelper.EXTRA_HABIT_ID, -1L) ?: -1L
        val notifId = intent?.getIntExtra(NahabitNotificationHelper.EXTRA_NOTIFICATION_ID, -1) ?: -1
        if (habitId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    SystemEntryPoint::class.java
                )
                val dao = entryPoint.dao()
                val today = entryPoint.timeProvider().currentLogicalEpochDay()
                val def = dao.getActiveHabitDefinitionsSync().firstOrNull { it.id == habitId }
                if (def != null) {
                    val existing = dao.getHabitLogForDaySync(habitId, today)
                    dao.upsertHabitLog(
                        HabitLogEntity(
                            id = existing?.id ?: 0L,
                            habitId = habitId,
                            epochDay = today,
                            completedValue = def.targetValue,
                            isCompleted = true
                        )
                    )
                    NahabitGlanceWidget.refreshAll(context)
                }
                if (notifId != -1) {
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.cancel(notifId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
