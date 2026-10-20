package com.nahabit.tracker.core.datastore

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Immutable
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "nahabit_preferences")

enum class AppThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class MeasurementUnitSystem { METRIC, IMPERIAL }
enum class FirstDayOfWeek { MONDAY, SUNDAY }

@Immutable
data class UserPreferences(
    val dayResetHour: Int = 0,
    val useMetricUnits: Boolean = true,
    val measurementSystem: MeasurementUnitSystem = MeasurementUnitSystem.METRIC,
    val firstDayOfWeek: FirstDayOfWeek = FirstDayOfWeek.MONDAY,
    val appLanguage: String = "es",
    val themeMode: AppThemeMode = AppThemeMode.AMOLED,
    val useDynamicColors: Boolean = true,
    val isCompactMode: Boolean = false,
    val hasCompletedOnboarding: Boolean = false,
    val journalPinCode: String = "",
    val appLockPinCode: String = "",
    val hideInRecents: Boolean = false,
    val autoBackupEnabled: Boolean = true
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val DAY_RESET_HOUR = intPreferencesKey("day_reset_hour")
        val USE_METRIC_UNITS = booleanPreferencesKey("use_metric_units")
        val MEASUREMENT_SYSTEM = stringPreferencesKey("measurement_system")
        val FIRST_DAY_OF_WEEK = stringPreferencesKey("first_day_of_week")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val COMPACT_MODE = booleanPreferencesKey("compact_mode")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val JOURNAL_PIN = stringPreferencesKey("journal_pin")
        val APP_LOCK_PIN = stringPreferencesKey("app_lock_pin")
        val HIDE_IN_RECENTS = booleanPreferencesKey("hide_in_recents")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val modeString = prefs[Keys.THEME_MODE] ?: AppThemeMode.AMOLED.name
        val parsedMode = runCatching { AppThemeMode.valueOf(modeString) }.getOrDefault(AppThemeMode.AMOLED)

        val unitSystemString = prefs[Keys.MEASUREMENT_SYSTEM] ?: MeasurementUnitSystem.METRIC.name
        val parsedUnitSystem = runCatching { MeasurementUnitSystem.valueOf(unitSystemString) }.getOrDefault(MeasurementUnitSystem.METRIC)

        val firstDayString = prefs[Keys.FIRST_DAY_OF_WEEK] ?: FirstDayOfWeek.MONDAY.name
        val parsedFirstDay = runCatching { FirstDayOfWeek.valueOf(firstDayString) }.getOrDefault(FirstDayOfWeek.MONDAY)

        UserPreferences(
            dayResetHour = prefs[Keys.DAY_RESET_HOUR] ?: 0,
            useMetricUnits = prefs[Keys.USE_METRIC_UNITS] ?: true,
            measurementSystem = parsedUnitSystem,
            firstDayOfWeek = parsedFirstDay,
            appLanguage = prefs[Keys.APP_LANGUAGE] ?: "es",
            themeMode = parsedMode,
            useDynamicColors = prefs[Keys.DYNAMIC_COLORS] ?: true,
            isCompactMode = prefs[Keys.COMPACT_MODE] ?: false,
            hasCompletedOnboarding = prefs[Keys.ONBOARDING_DONE] ?: false,
            journalPinCode = prefs[Keys.JOURNAL_PIN] ?: "",
            appLockPinCode = prefs[Keys.APP_LOCK_PIN] ?: "",
            hideInRecents = prefs[Keys.HIDE_IN_RECENTS] ?: false,
            autoBackupEnabled = prefs[Keys.AUTO_BACKUP_ENABLED] ?: true
        )
    }

    suspend fun setDayResetHour(hour: Int) {
        context.dataStore.edit { it[Keys.DAY_RESET_HOUR] = hour.coerceIn(0, 6) }
    }

    suspend fun setMeasurementSystem(system: MeasurementUnitSystem) {
        context.dataStore.edit {
            it[Keys.MEASUREMENT_SYSTEM] = system.name
            it[Keys.USE_METRIC_UNITS] = (system == MeasurementUnitSystem.METRIC)
        }
    }

    suspend fun setFirstDayOfWeek(day: FirstDayOfWeek) {
        context.dataStore.edit { it[Keys.FIRST_DAY_OF_WEEK] = day.name }
    }

    suspend fun setAppLanguage(languageCode: String) {
        context.dataStore.edit { it[Keys.APP_LANGUAGE] = languageCode }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageCode))
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColors(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLORS] = enabled }
    }

    suspend fun setCompactMode(compact: Boolean) {
        context.dataStore.edit { it[Keys.COMPACT_MODE] = compact }
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    suspend fun setJournalPinCode(pin: String) {
        context.dataStore.edit { it[Keys.JOURNAL_PIN] = pin.trim() }
    }

    suspend fun setAppLockPinCode(pin: String) {
        context.dataStore.edit { it[Keys.APP_LOCK_PIN] = pin.trim() }
    }

    suspend fun setHideInRecents(hide: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_IN_RECENTS] = hide }
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP_ENABLED] = enabled }
    }
}
