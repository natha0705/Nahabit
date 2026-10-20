package com.nahabit.tracker.habits

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nahabit.tracker.R
import com.nahabit.tracker.core.database.DailyWaterEntity
import com.nahabit.tracker.core.database.HabitDefinitionEntity
import com.nahabit.tracker.core.datastore.UserPreferences
import com.nahabit.tracker.core.datastore.UserPreferencesRepository
import com.nahabit.tracker.core.notifications.HabitReminderReceiver
import com.nahabit.tracker.core.time.TimeProvider
import com.nahabit.tracker.core.ui.UiState
import com.nahabit.tracker.core.widget.NahabitGlanceWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HabitsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: HabitsRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val _selectedDateOffsetDays = MutableStateFlow(0L)
    val selectedDateOffsetDays: StateFlow<Long> = _selectedDateOffsetDays.asStateFlow()

    val selectedEpochDay: StateFlow<Long> = combine(
        userPreferences,
        _selectedDateOffsetDays
    ) { prefs, offset ->
        timeProvider.currentLogicalEpochDay(prefs.dayResetHour) + offset
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        timeProvider.currentLogicalEpochDay()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState<List<HabitWithStreakUiModel>>> =
        selectedEpochDay
            .flatMapLatest { epochDay ->
                repository.observeHabitsForDay(epochDay)
            }
            .map { list ->
                if (list.isEmpty()) UiState.Empty else UiState.Success(list)
            }
            .catch { emit(UiState.Error(R.string.state_error_generic, it.localizedMessage)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UiState.Loading
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val waterState: StateFlow<DailyWaterEntity> =
        preferencesRepository.preferencesFlow
            .flatMapLatest { prefs ->
                val logicalToday = timeProvider.currentLogicalEpochDay(prefs.dayResetHour)
                repository.observeWaterForDay(logicalToday)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = DailyWaterEntity(epochDay = 0L, glassesCount = 0, targetGlasses = 8)
            )

    fun shiftSelectedDate(daysDelta: Long) {
        _selectedDateOffsetDays.value = (_selectedDateOffsetDays.value + daysDelta).coerceAtMost(0L)
    }

    fun resetToToday() {
        _selectedDateOffsetDays.value = 0L
    }

    fun createProHabit(
        title: String,
        emoji: String,
        habitType: String,
        goalComparison: String,
        targetValue: Int,
        unit: String,
        frequencyType: String,
        frequencyParam: Int,
        timeOfDayGroup: String,
        graceDays: Int,
        reminderHour: Int
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val today = timeProvider.currentLogicalEpochDay(userPreferences.value.dayResetHour)
            val entity = HabitDefinitionEntity(
                title = title.trim(),
                iconEmoji = emoji.ifBlank { "✨" },
                habitType = habitType,
                goalComparison = goalComparison,
                targetValue = targetValue.coerceAtLeast(1),
                unit = unit.trim(),
                frequencyType = frequencyType,
                frequencyParam = frequencyParam.coerceAtLeast(1),
                timeOfDayGroup = timeOfDayGroup,
                graceDaysAllowed = graceDays.coerceIn(0, 5),
                reminderHour = reminderHour,
                createdEpochDay = today
            )
            val newId = repository.createAdvancedHabit(entity)
            if (reminderHour in 0..23) {
                HabitReminderReceiver.scheduleDailyHabitReminder(appContext, entity.copy(id = newId))
            }
            NahabitGlanceWidget.refreshAll(appContext)
        }
    }

    fun incrementToday(item: HabitWithStreakUiModel) {
        viewModelScope.launch {
            repository.incrementHabitOnDay(item, selectedEpochDay.value)
            NahabitGlanceWidget.refreshAll(appContext)
        }
    }

    fun saveNoteForSelectedDay(item: HabitWithStreakUiModel, note: String) {
        viewModelScope.launch {
            repository.saveHabitNoteForDay(item, selectedEpochDay.value, note)
        }
    }

    fun archiveHabit(item: HabitWithStreakUiModel) {
        viewModelScope.launch {
            repository.archiveHabit(item.definition)
            NahabitGlanceWidget.refreshAll(appContext)
        }
    }

    fun togglePauseHabit(item: HabitWithStreakUiModel) {
        viewModelScope.launch {
            repository.togglePauseHabit(item.definition)
        }
    }

    fun reorderHabit(item: HabitWithStreakUiModel, delta: Int) {
        viewModelScope.launch {
            repository.moveHabitOrder(item.definition, delta)
        }
    }

    fun deleteHabit(item: HabitWithStreakUiModel) {
        viewModelScope.launch {
            repository.deleteHabit(item.definition)
            NahabitGlanceWidget.refreshAll(appContext)
        }
    }

    fun addWaterGlass(delta: Int) {
        viewModelScope.launch {
            val today = timeProvider.currentLogicalEpochDay(userPreferences.value.dayResetHour)
            repository.updateWaterGlasses(today, waterState.value, delta)
            NahabitGlanceWidget.refreshAll(appContext)
        }
    }

    fun seedStarterExperience() {
        viewModelScope.launch {
            val today = timeProvider.currentLogicalEpochDay(userPreferences.value.dayResetHour)
            repository.createAdvancedHabit(
                HabitDefinitionEntity(
                    title = "Entrenamiento de fuerza / Calistenia",
                    iconEmoji = "💪",
                    habitType = "YES_NO",
                    targetValue = 1,
                    timeOfDayGroup = "MORNING",
                    createdEpochDay = today
                )
            )
            repository.createAdvancedHabit(
                HabitDefinitionEntity(
                    title = "Lectura enfocada",
                    iconEmoji = "📚",
                    habitType = "QUANTITY",
                    targetValue = 20,
                    unit = "páginas",
                    timeOfDayGroup = "EVENING",
                    createdEpochDay = today
                )
            )
            repository.createAdvancedHabit(
                HabitDefinitionEntity(
                    title = "Redes sociales (Límite)",
                    iconEmoji = "📵",
                    habitType = "DURATION",
                    goalComparison = "AT_MOST",
                    targetValue = 30,
                    unit = "min",
                    timeOfDayGroup = "ANYTIME",
                    createdEpochDay = today
                )
            )
            preferencesRepository.completeOnboarding()
            NahabitGlanceWidget.refreshAll(appContext)
        }
    }
}
