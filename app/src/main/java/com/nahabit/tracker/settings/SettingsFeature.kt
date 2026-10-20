package com.nahabit.tracker.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nahabit.tracker.R
import com.nahabit.tracker.core.backup.AutoBackupWorker
import com.nahabit.tracker.core.backup.BackupAndExportEngine
import com.nahabit.tracker.core.database.NahabitDao
import com.nahabit.tracker.core.datastore.AppThemeMode
import com.nahabit.tracker.core.datastore.FirstDayOfWeek
import com.nahabit.tracker.core.datastore.MeasurementUnitSystem
import com.nahabit.tracker.core.datastore.UserPreferences
import com.nahabit.tracker.core.datastore.UserPreferencesRepository
import com.nahabit.tracker.core.system.BatteryOptimizationHelper
import com.nahabit.tracker.core.system.LocalCrashLogger
import com.nahabit.tracker.core.ui.theme.NahabitCoral
import com.nahabit.tracker.core.ui.theme.NahabitCyan
import com.nahabit.tracker.core.ui.theme.NahabitEmerald
import com.nahabit.tracker.core.widget.NahabitGlanceWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefsRepo: UserPreferencesRepository,
    private val backupEngine: BackupAndExportEngine,
    private val dao: NahabitDao
) : ViewModel() {

    val prefs: StateFlow<UserPreferences> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val _autoBackupCount = MutableStateFlow(backupEngine.getLocalAutoBackupCount())
    val autoBackupCount: StateFlow<Int> = _autoBackupCount.asStateFlow()

    private val _statusBanner = MutableStateFlow<Boolean?>(null)
    val statusBanner: StateFlow<Boolean?> = _statusBanner.asStateFlow()

    fun updateDayResetHour(hour: Int) {
        viewModelScope.launch { prefsRepo.setDayResetHour(hour) }
    }

    fun setMeasurementSystem(system: MeasurementUnitSystem) {
        viewModelScope.launch { prefsRepo.setMeasurementSystem(system) }
    }

    fun setFirstDayOfWeek(day: FirstDayOfWeek) {
        viewModelScope.launch { prefsRepo.setFirstDayOfWeek(day) }
    }

    fun setAppLanguage(lang: String) {
        viewModelScope.launch { prefsRepo.setAppLanguage(lang) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun toggleDynamicColors(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setDynamicColors(enabled) }
    }

    fun toggleCompactMode(compact: Boolean) {
        viewModelScope.launch { prefsRepo.setCompactMode(compact) }
    }

    fun toggleHideInRecents(hide: Boolean) {
        viewModelScope.launch { prefsRepo.setHideInRecents(hide) }
    }

    fun updateAppLockPin(pin: String) {
        viewModelScope.launch { prefsRepo.setAppLockPinCode(pin) }
    }

    fun toggleAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAutoBackupEnabled(enabled)
            AutoBackupWorker.syncSchedule(appContext, enabled)
        }
    }

    fun runManualSnapshotNow() {
        viewModelScope.launch {
            _autoBackupCount.value = backupEngine.createLocalRotatingSnapshot()
            _statusBanner.value = true
        }
    }

    fun exportFullBackup(uri: Uri, password: String) {
        viewModelScope.launch {
            _statusBanner.value = backupEngine.exportMasterBackupToUri(uri, password)
        }
    }

    fun importFullBackup(uri: Uri, password: String) {
        viewModelScope.launch {
            val ok = backupEngine.importMasterBackupFromUri(uri, password)
            _statusBanner.value = ok
            if (ok) NahabitGlanceWidget.refreshAll(appContext)
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            _statusBanner.value = backupEngine.exportHabitsCsvToUri(uri)
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            val ok = backupEngine.importGenericHabitsCsvFromUri(uri)
            _statusBanner.value = ok
            if (ok) NahabitGlanceWidget.refreshAll(appContext)
        }
    }

    fun exportPdf(uri: Uri) {
        viewModelScope.launch {
            _statusBanner.value = backupEngine.exportPdfReportToUri(uri)
        }
    }

    fun wipeAllLocalData() {
        viewModelScope.launch {
            dao.wipeAllDataTransaction()
            NahabitGlanceWidget.refreshAll(appContext)
            _statusBanner.value = true
        }
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val autoCount by viewModel.autoBackupCount.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var backupPassword by rememberSaveable { mutableStateOf("") }
    var appPinInput by rememberSaveable(prefs.appLockPinCode) { mutableStateOf(prefs.appLockPinCode) }
    var showWipeConfirmDialog by rememberSaveable { mutableStateOf(false) }

    val isIgnoringBattery = remember { BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context) }
    val latestCrashLog = remember { LocalCrashLogger.readLatestCrash(context) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportFullBackup(it, backupPassword) } }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFullBackup(it, backupPassword) } }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.exportCsv(it) } }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importCsv(it) } }

    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { viewModel.exportPdf(it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )

        if (statusBanner != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (statusBanner == true)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        if (statusBanner == true) R.string.backup_status_ok else R.string.backup_status_error
                    ),
                    modifier = Modifier.padding(14.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Language, contentDescription = null, tint = NahabitEmerald)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_language_title), fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("es" to R.string.settings_lang_es, "en" to R.string.settings_lang_en).forEach { (code, labelRes) ->
                        FilterChip(
                            selected = prefs.appLanguage == code,
                            onClick = { viewModel.setAppLanguage(code) },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }

                HorizontalDivider()
                Text(stringResource(R.string.settings_units_title), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        MeasurementUnitSystem.METRIC to R.string.settings_unit_kg_cm,
                        MeasurementUnitSystem.IMPERIAL to R.string.settings_unit_lb_in
                    ).forEach { (sys, labelRes) ->
                        FilterChip(
                            selected = prefs.measurementSystem == sys,
                            onClick = { viewModel.setMeasurementSystem(sys) },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }

                HorizontalDivider()
                Text(stringResource(R.string.settings_first_day_week), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        FirstDayOfWeek.MONDAY to R.string.settings_day_monday,
                        FirstDayOfWeek.SUNDAY to R.string.settings_day_sunday
                    ).forEach { (day, labelRes) ->
                        FilterChip(
                            selected = prefs.firstDayOfWeek == day,
                            onClick = { viewModel.setFirstDayOfWeek(day) },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_theme_title), fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val modes = listOf(
                        AppThemeMode.SYSTEM to R.string.settings_theme_system,
                        AppThemeMode.LIGHT to R.string.settings_theme_light,
                        AppThemeMode.DARK to R.string.settings_theme_dark,
                        AppThemeMode.AMOLED to R.string.settings_theme_amoled
                    )
                    modes.forEach { (mode, labelRes) ->
                        FilterChip(
                            selected = prefs.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_dynamic_color))
                    Switch(checked = prefs.useDynamicColors, onCheckedChange = viewModel::toggleDynamicColors)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_compact_mode))
                    Switch(checked = prefs.isCompactMode, onCheckedChange = viewModel::toggleCompactMode)
                }

                Text(stringResource(R.string.settings_day_reset_hour, prefs.dayResetHour))
                Slider(
                    value = prefs.dayResetHour.toFloat(),
                    onValueChange = { viewModel.updateDayResetHour(it.toInt()) },
                    valueRange = 0f..6f,
                    steps = 5
                )
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = NahabitEmerald)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.battery_guide_title), fontWeight = FontWeight.Bold)
                }
                Text(
                    text = stringResource(
                        R.string.battery_guide_status,
                        if (isIgnoringBattery) stringResource(R.string.battery_status_ignored) else stringResource(R.string.battery_status_restricted)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.battery_manufacturer_tip, android.os.Build.MANUFACTURER),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isIgnoringBattery) {
                    Button(
                        onClick = { BatteryOptimizationHelper.requestDisableBatteryOptimization(context) },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(stringResource(R.string.battery_guide_btn))
                    }
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = NahabitEmerald)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.privacy_section_title), fontWeight = FontWeight.Bold)
                }
                Text(
                    text = stringResource(R.string.privacy_manifesto),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.privacy_hide_recents),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = prefs.hideInRecents,
                        onCheckedChange = viewModel::toggleHideInRecents
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = appPinInput,
                        onValueChange = { appPinInput = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.privacy_app_lock_pin)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { viewModel.updateAppLockPin(appPinInput) }) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.backup_section_title), fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.backup_auto_daily),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = prefs.autoBackupEnabled,
                        onCheckedChange = viewModel::toggleAutoBackup
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.backup_auto_count, autoCount),
                        style = MaterialTheme.typography.bodySmall
                    )
                    FilledTonalButton(onClick = viewModel::runManualSnapshotNow) {
                        Text(stringResource(R.string.backup_run_now_btn))
                    }
                }

                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it },
                    label = { Text(stringResource(R.string.backup_password_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { exportBackupLauncher.launch("nahabit_${LocalDate.now()}.nahabit") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_export_backup))
                    }
                    OutlinedButton(
                        onClick = { importBackupLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.settings_import_backup))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { exportCsvLauncher.launch("nahabit_habits_${LocalDate.now()}.csv") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.backup_export_csv))
                    }
                    OutlinedButton(
                        onClick = { importCsvLauncher.launch(arrayOf("text/*", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.backup_import_csv))
                    }
                }

                Button(
                    onClick = { exportPdfLauncher.launch("nahabit_reporte_${LocalDate.now()}.pdf") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.backup_export_pdf))
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = NahabitCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.about_section_title), fontWeight = FontWeight.Bold)
                }
                Text(stringResource(R.string.about_version_text), style = MaterialTheme.typography.bodySmall)
                Text(
                    text = latestCrashLog ?: stringResource(R.string.about_crash_log_empty),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.danger_zone_title),
                    color = NahabitCoral,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = { showWipeConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, tint = NahabitCoral)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.danger_wipe_all_btn), color = NahabitCoral)
                }
            }
        }
    }

    if (showWipeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmDialog = false },
            title = { Text(stringResource(R.string.danger_wipe_confirm_title)) },
            text = { Text(stringResource(R.string.danger_wipe_confirm_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.wipeAllLocalData()
                        showWipeConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NahabitCoral)
                ) {
                    Text(stringResource(R.string.danger_wipe_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}
