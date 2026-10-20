package com.nahabit.tracker.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nahabit.tracker.core.database.HabitDefinitionEntity
import com.nahabit.tracker.core.di.SystemEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class HabitReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val entryPoint = EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            SystemEntryPoint::class.java
                        )
                        val habits = entryPoint.dao().getActiveHabitDefinitionsSync()
                        habits.filter { it.reminderHour in 0..23 && !it.isPaused }.forEach { habit ->
                            scheduleDailyHabitReminder(context, habit)
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_TRIGGER_HABIT_REMINDER -> {
                val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
                val habitTitle = intent.getStringExtra(EXTRA_HABIT_TITLE) ?: "Hábito pendiente"
                if (habitId > 0L) {
                    NahabitNotificationHelper.sendHabitActionableNotification(
                        context = context,
                        habitId = habitId,
                        habitTitle = habitTitle
                    )
                }
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER_HABIT_REMINDER = "com.nahabit.tracker.ACTION_TRIGGER_HABIT_REMINDER"
        const val EXTRA_HABIT_ID = "extra_rem_habit_id"
        const val EXTRA_HABIT_TITLE = "extra_rem_habit_title"

        fun scheduleDailyHabitReminder(context: Context, habit: HabitDefinitionEntity) {
            if (habit.reminderHour !in 0..23) return
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, HabitReminderReceiver::class.java).apply {
                action = ACTION_TRIGGER_HABIT_REMINDER
                putExtra(EXTRA_HABIT_ID, habit.id)
                putExtra(EXTRA_HABIT_TITLE, "${habit.iconEmoji} ${habit.title}")
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                habit.id.toInt() + 5000,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, habit.reminderHour)
                set(Calendar.MINUTE, habit.reminderMinute)
                set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }
}
