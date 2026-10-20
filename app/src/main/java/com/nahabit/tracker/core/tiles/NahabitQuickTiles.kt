package com.nahabit.tracker.core.tiles

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.nahabit.tracker.R
import com.nahabit.tracker.core.database.DailyWaterEntity
import com.nahabit.tracker.core.database.HabitLogEntity
import com.nahabit.tracker.core.di.SystemEntryPoint
import com.nahabit.tracker.core.widget.NahabitGlanceWidget
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WaterQuickTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        refreshTileSubtitle()
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                SystemEntryPoint::class.java
            )
            val dao = entryPoint.dao()
            val today = entryPoint.timeProvider().currentLogicalEpochDay()
            val current = dao.getWaterForDaySync(today)
                ?: DailyWaterEntity(epochDay = today, glassesCount = 0, targetGlasses = 8)

            val updated = current.copy(glassesCount = (current.glassesCount + 1).coerceAtMost(30))
            dao.upsertWaterForDay(updated)
            NahabitGlanceWidget.refreshAll(applicationContext)
            updateTileUi(updated.glassesCount, updated.targetGlasses)
        }
    }

    private fun refreshTileSubtitle() {
        serviceScope.launch {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                SystemEntryPoint::class.java
            )
            val today = entryPoint.timeProvider().currentLogicalEpochDay()
            val current = entryPoint.dao().getWaterForDaySync(today)
            updateTileUi(current?.glassesCount ?: 0, current?.targetGlasses ?: 8)
        }
    }

    private fun updateTileUi(glasses: Int, target: Int) {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = getString(R.string.tile_water_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = "$glasses / $target"
        }
        tile.updateTile()
    }
}

class QuickHabitTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        refreshHabitTileState()
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                SystemEntryPoint::class.java
            )
            val dao = entryPoint.dao()
            val today = entryPoint.timeProvider().currentLogicalEpochDay()

            val defs = dao.getActiveHabitDefinitionsSync()
            val logs = dao.getHabitLogsForDaySync(today).associateBy { it.habitId }
            val nextPending = defs.firstOrNull { logs[it.id]?.isCompleted != true }

            if (nextPending != null) {
                val currentLog = logs[nextPending.id]
                val nextVal = (currentLog?.completedValue ?: 0) + 1
                dao.upsertHabitLog(
                    HabitLogEntity(
                        id = currentLog?.id ?: 0L,
                        habitId = nextPending.id,
                        epochDay = today,
                        completedValue = nextVal,
                        isCompleted = nextVal >= nextPending.targetValue
                    )
                )
                NahabitGlanceWidget.refreshAll(applicationContext)
            }
            refreshHabitTileState()
        }
    }

    private fun refreshHabitTileState() {
        serviceScope.launch {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                SystemEntryPoint::class.java
            )
            val dao = entryPoint.dao()
            val today = entryPoint.timeProvider().currentLogicalEpochDay()
            val defs = dao.getActiveHabitDefinitionsSync()
            val logs = dao.getHabitLogsForDaySync(today).associateBy { it.habitId }
            val pending = defs.firstOrNull { logs[it.id]?.isCompleted != true }

            val tile = qsTile ?: return@launch
            tile.state = if (pending != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_habit_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = pending?.title ?: getString(R.string.widget_all_completed)
            }
            tile.updateTile()
        }
    }
}
