package com.nahabit.tracker.core.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.*
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.nahabit.tracker.MainActivity
import com.nahabit.tracker.R
import com.nahabit.tracker.core.database.DailyWaterEntity
import com.nahabit.tracker.core.database.HabitDefinitionEntity
import com.nahabit.tracker.core.database.HabitLogEntity
import com.nahabit.tracker.core.di.SystemEntryPoint
import dagger.hilt.android.EntryPointAccessors

private val HabitIdParamKey = ActionParameters.Key<Long>("habit_id")

class NahabitGlanceWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    companion object {
        private val SMALL_BOX = DpSize(140.dp, 110.dp)
        private val MEDIUM_BOX = DpSize(240.dp, 140.dp)
        private val LARGE_BOX = DpSize(280.dp, 220.dp)

        suspend fun refreshAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(NahabitGlanceWidget::class.java)
            glanceIds.forEach { id ->
                try {
                    NahabitGlanceWidget().update(context, id)
                } catch (_: Exception) {
                }
            }
        }
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL_BOX, MEDIUM_BOX, LARGE_BOX)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SystemEntryPoint::class.java
        )
        val dao = entryPoint.dao()
        val today = entryPoint.timeProvider().currentLogicalEpochDay()

        val definitions = runCatching { dao.getActiveHabitDefinitionsSync() }.getOrDefault(emptyList())
        val todayLogs = runCatching { dao.getHabitLogsForDaySync(today) }.getOrDefault(emptyList())
        val water = runCatching { dao.getWaterForDaySync(today) }.getOrNull()
            ?: DailyWaterEntity(epochDay = today, glassesCount = 0, targetGlasses = 8)

        val logsMap = todayLogs.associateBy { it.habitId }
        val completedCount = definitions.count { logsMap[it.id]?.isCompleted == true }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    context = context,
                    definitions = definitions,
                    logsMap = logsMap,
                    completedCount = completedCount,
                    water = water
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        definitions: List<HabitDefinitionEntity>,
        logsMap: Map<Long, HabitLogEntity>,
        completedCount: Int,
        water: DailyWaterEntity
    ) {
        val size = LocalSize.current
        val isMediumOrLarger = size.width >= MEDIUM_BOX.width
        val isLarge = size.height >= LARGE_BOX.height

        val openTodayIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("extra_shortcut_dest", "TODAY")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openWorkoutIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("extra_shortcut_dest", "WORKOUT")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .appWidgetBackground()
                .padding(12.dp)
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(openTodayIntent)),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = context.getString(R.string.widget_title),
                        style = TextStyle(
                            color = GlanceTheme.colors.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                    Text(
                        text = "${context.getString(R.string.today_ring_habits)}: $completedCount/${definitions.size} • 💧 ${water.glassesCount}/${water.targetGlasses}",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 12.sp
                        )
                    )
                }

                if (isMediumOrLarger) {
                    Button(
                        text = context.getString(R.string.widget_water_btn),
                        onClick = actionRunCallback<IncrementWaterWidgetAction>()
                    )
                }
            }

            Spacer(GlanceModifier.height(8.dp))

            if (definitions.isEmpty()) {
                Text(
                    text = context.getString(R.string.state_empty_habits),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
                )
            } else {
                val maxVisible = if (isLarge) 5 else 2
                LazyColumn(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                    items(definitions.take(maxVisible), itemId = { it.id }) { habit ->
                        val log = logsMap[habit.id]
                        val done = log?.isCompleted == true
                        val currentVal = log?.completedValue ?: 0
                        val prefix = if (done) "☑" else "☐"

                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(
                                    actionRunCallback<ToggleHabitWidgetAction>(
                                        actionParametersOf(HabitIdParamKey to habit.id)
                                    )
                                ),
                            verticalAlignment = Alignment.Vertical.CenterVertically
                        ) {
                            Text(
                                text = "$prefix ${habit.iconEmoji} ${habit.title}",
                                style = TextStyle(
                                    color = if (done) GlanceTheme.colors.primary else GlanceTheme.colors.onSurface,
                                    fontWeight = if (done) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                ),
                                modifier = GlanceModifier.defaultWeight()
                            )
                            Text(
                                text = "$currentVal/${habit.targetValue}",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            if (isLarge) {
                Spacer(GlanceModifier.height(6.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Horizontal.End
                ) {
                    Button(
                        text = context.getString(R.string.widget_workout_btn),
                        onClick = actionStartActivity(openWorkoutIntent)
                    )
                }
            }
        }
    }
}

class ToggleHabitWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val habitId = parameters[HabitIdParamKey] ?: return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SystemEntryPoint::class.java
        )
        val dao = entryPoint.dao()
        val today = entryPoint.timeProvider().currentLogicalEpochDay()

        val definitions = dao.getActiveHabitDefinitionsSync()
        val targetDef = definitions.firstOrNull { it.id == habitId } ?: return
        val existingLog = dao.getHabitLogForDaySync(habitId, today)

        val nextValue = if (existingLog?.isCompleted == true) {
            0
        } else {
            (existingLog?.completedValue ?: 0) + 1
        }
        val completed = nextValue >= targetDef.targetValue

        dao.upsertHabitLog(
            HabitLogEntity(
                id = existingLog?.id ?: 0L,
                habitId = habitId,
                epochDay = today,
                completedValue = nextValue,
                isCompleted = completed
            )
        )
        NahabitGlanceWidget.refreshAll(context)
    }
}

class IncrementWaterWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SystemEntryPoint::class.java
        )
        val dao = entryPoint.dao()
        val today = entryPoint.timeProvider().currentLogicalEpochDay()
        val current = dao.getWaterForDaySync(today)
            ?: DailyWaterEntity(epochDay = today, glassesCount = 0, targetGlasses = 8)

        dao.upsertWaterForDay(
            current.copy(glassesCount = (current.glassesCount + 1).coerceAtMost(30))
        )
        NahabitGlanceWidget.refreshAll(context)
    }
}

class NahabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NahabitGlanceWidget()
}
