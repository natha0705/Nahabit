package com.nahabit.tracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        val database = NahabitDatabase.getInstance(this)
        val healthBridge = SafeHealthConnectBridge(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                NotificationPermissionGate()
                NahabitMainRoot(
                    dao = database.dao(),
                    healthBridge = healthBridge,
                    onTriggerNotification = ::sendLocalNotification
                )
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "NAHABIT_CHANNEL",
            "Temporizadores y Recordatorios Nahabit",
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun sendLocalNotification(title: String, message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, "NAHABIT_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

@Composable
fun NotificationPermissionGate() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun NahabitMainRoot(
    dao: NahabitDao,
    healthBridge: SafeHealthConnectBridge,
    onTriggerNotification: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentTab by rememberSaveable { mutableIntStateOf(0) }

    val habits by dao.observeHabits().collectAsState(initial = emptyList())
    val badHabits by dao.observeBadHabits().collectAsState(initial = emptyList())
    val routines by dao.observeRoutines().collectAsState(initial = emptyList())
    val journalEntries by dao.observeJournal().collectAsState(initial = emptyList())
    val bodyMetrics by dao.observeBodyMetrics().collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        if (dao.getAllHabitsSync().isEmpty() && dao.getAllRoutinesSync().isEmpty()) {
            val today = LocalDate.now().toEpochDay()
            dao.upsertHabit(HabitTask(title = "Entrenar fuerza / Calistenia", type = "HABIT", targetCount = 1, dateEpochDay = today))
            dao.upsertHabit(HabitTask(title = "Leer 20 páginas", type = "GOAL", targetCount = 20, dateEpochDay = today))
            dao.upsertBadHabit(BadHabitCounter(habitName = "Comida ultraprocesada", lastRelapseTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)))
            dao.insertBodyMetric(BodyMetric(dateEpochDay = today, weightKg = 70f, heightCm = 175f, waistCm = 76f, chestCm = 98f, armCm = 35f, caloriesConsumed = 2200))
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { targetUri ->
            scope.launch {
                val backup = NahabitMasterBackup(
                    version = 1,
                    exportTimestamp = System.currentTimeMillis(),
                    habitsTasks = dao.getAllHabitsSync(),
                    journal = dao.getAllJournalSync(),
                    badHabits = dao.getAllBadHabitsSync(),
                    routines = dao.getAllRoutinesSync(),
                    bodyMetrics = dao.getAllBodyMetricsSync()
                )
                val ok = BackupManager.writeJsonToUri(context, targetUri, BackupManager.serializeMasterBackup(backup))
                if (ok) onTriggerNotification("Backup Exportado", "Tu archivo .nahabit se guardó correctamente.")
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { sourceUri ->
            scope.launch {
                BackupManager.readJsonFromUri(context, sourceUri)?.let { raw ->
                    runCatching {
                        val restored = BackupManager.deserializeMasterBackup(raw)
                        dao.restoreFullBackup(restored)
                        onTriggerNotification("Backup Restaurado", "Todos tus datos fueron importados con éxito.")
                    }.onFailure {
                        onTriggerNotification("Error al Importar", "El archivo no corresponde a un backup válido de Nahabit.")
                    }
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    "Metas/Cal" to Icons.Default.CheckCircle,
                    "Abstinencia" to Icons.Default.Shield,
                    "Ejercicio" to Icons.Default.FitnessCenter,
                    "Diario" to Icons.Default.MenuBook,
                    "Físico/Datos" to Icons.Default.MonitorWeight
                )
                items.forEachIndexed { index, pair ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        icon = { Icon(pair.second, contentDescription = pair.first) },
                        label = { Text(pair.first) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                text = "Nahabit - Tracker de hábitos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))

            when (currentTab) {
                0 -> HabitsCalendarAndFocusSection(
                    habits = habits,
                    onUpsert = { scope.launch { dao.upsertHabit(it) } },
                    onDelete = { scope.launch { dao.deleteHabit(it) } },
                    onNotify = onTriggerNotification
                )
                1 -> BadHabitsSection(
                    counters = badHabits,
                    onUpsert = { scope.launch { dao.upsertBadHabit(it) } },
                    onDelete = { scope.launch { dao.deleteBadHabit(it) } }
                )
                2 -> WorkoutRoutineManager(
                    routines = routines,
                    onUpsertRoutine = { scope.launch { dao.upsertRoutine(it) } },
                    onDeleteRoutine = { scope.launch { dao.deleteRoutine(it) } },
                    onNotify = onTriggerNotification
                )
                3 -> JournalSection(
                    entries = journalEntries,
                    onAddEntry = { scope.launch { dao.insertJournal(it) } }
                )
                4 -> BodyMetricsAndBackupSection(
                    latestMetric = bodyMetrics.firstOrNull(),
                    onSaveMetric = { metric ->
                        scope.launch {
                            dao.insertBodyMetric(metric)
                            healthBridge.syncMetricsLocally(metric.weightKg.toDouble(), metric.caloriesConsumed.toDouble())
                            onTriggerNotification("Medidas Guardadas", "Estado físico actualizado localmente.")
                        }
                    },
                    onExportAll = { exportBackupLauncher.launch("nahabit_backup_${LocalDate.now()}.nahabit") },
                    onImportAll = { importBackupLauncher.launch(arrayOf("*/*")) }
                )
            }
        }
    }
}

@Composable
fun HabitsCalendarAndFocusSection(
    habits: List<HabitTask>,
    onUpsert: (HabitTask) -> Unit,
    onDelete: (HabitTask) -> Unit,
    onNotify: (String, String) -> Unit
) {
    var selectedDateEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var newTitle by rememberSaveable { mutableStateOf("") }
    var newTarget by rememberSaveable { mutableStateOf("1") }
    var selectedType by rememberSaveable { mutableStateOf("HABIT") }

    var focusTargetRealtime by rememberSaveable { mutableLongStateOf(0L) }
    var focusRemainingSecs by rememberSaveable { mutableIntStateOf(25 * 60) }
    var isFocusRunning by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isFocusRunning, focusTargetRealtime) {
        while (isFocusRunning) {
            val diff = focusTargetRealtime - SystemClock.elapsedRealtime()
            if (diff <= 0L) {
                focusRemainingSecs = 0
                isFocusRunning = false
                onNotify("¡Concentración Completada!", "Has terminado tu bloque de enfoque.")
                break
            } else {
                focusRemainingSecs = ((diff + 999L) / 1000L).toInt()
            }
            delay(250L)
        }
    }

    val dayHabits = remember(habits, selectedDateEpoch) {
        habits.filter { it.dateEpochDay == selectedDateEpoch }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Temporizador de Concentración", fontWeight = FontWeight.Bold)
                    Text(
                        text = "${focusRemainingSecs / 60}m : ${"%02d".format(focusRemainingSecs % 60)}s",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (!isFocusRunning) {
                                focusTargetRealtime = SystemClock.elapsedRealtime() + (focusRemainingSecs * 1000L)
                                isFocusRunning = true
                            } else {
                                isFocusRunning = false
                            }
                        }) { Text(if (isFocusRunning) "Pausar" else "Iniciar Enfoque") }
                        OutlinedButton(onClick = {
                            isFocusRunning = false
                            focusRemainingSecs = 25 * 60
                        }) { Text("25m") }
                        OutlinedButton(onClick = {
                            isFocusRunning = false
                            focusRemainingSecs = 50 * 60
                        }) { Text("50m") }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { selectedDateEpoch -= 1 }) { Text("<") }
                Text("Fecha: ${LocalDate.ofEpochDay(selectedDateEpoch)}", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { selectedDateEpoch += 1 }) { Text(">") }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("HABIT" to "Hábito", "TASK" to "Tarea", "GOAL" to "Meta").forEach { (code, label) ->
                            FilterChip(
                                selected = selectedType == code,
                                onClick = { selectedType = code },
                                label = { Text(label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            label = { Text("Nuevo hábito, tarea o meta") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = newTarget,
                            onValueChange = { newTarget = it.filter(Char::isDigit) },
                            label = { Text("Meta") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            if (newTitle.isNotBlank()) {
                                onUpsert(
                                    HabitTask(
                                        title = newTitle.trim(),
                                        type = selectedType,
                                        targetCount = (newTarget.toIntOrNull() ?: 1).coerceAtLeast(1),
                                        dateEpochDay = selectedDateEpoch
                                    )
                                )
                                newTitle = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Agregar al Calendario") }
                }
            }
        }

        items(dayHabits, key = { it.id }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("[${item.type}] ${item.title}", fontWeight = FontWeight.Bold)
                        Text("Avance: ${item.currentCount} / ${item.targetCount}")
                    }
                    Row {
                        FilledTonalButton(onClick = {
                            val next = item.currentCount + 1
                            onUpsert(item.copy(currentCount = next, isCompleted = next >= item.targetCount))
                        }) { Text("+1") }
                        IconButton(onClick = { onDelete(item) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BadHabitsSection(
    counters: List<BadHabitCounter>,
    onUpsert: (BadHabitCounter) -> Unit,
    onDelete: (BadHabitCounter) -> Unit
) {
    var newBadHabit by rememberSaveable { mutableStateOf("") }
    val now = System.currentTimeMillis()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newBadHabit,
                onValueChange = { newBadHabit = it },
                label = { Text("Mal hábito a dejar (ej. Fumar, Refresco)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = {
                if (newBadHabit.isNotBlank()) {
                    onUpsert(BadHabitCounter(habitName = newBadHabit.trim(), lastRelapseTimestamp = System.currentTimeMillis()))
                    newBadHabit = ""
                }
            }) { Text("Iniciar") }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            items(counters, key = { it.id }) { counter ->
                val elapsedMs = (now - counter.lastRelapseTimestamp).coerceAtLeast(0L)
                val days = TimeUnit.MILLISECONDS.toDays(elapsedMs)
                val hours = TimeUnit.MILLISECONDS.toHours(elapsedMs) % 24

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(counter.habitName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { onDelete(counter) }) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        }
                        Text(
                            text = "Llevas $days días y $hours horas libre",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(onClick = {
                            onUpsert(counter.copy(lastRelapseTimestamp = System.currentTimeMillis()))
                        }) { Text("Reiniciar contador por recaída") }
                    }
                }
            }
        }
    }
}

@Composable
fun JournalSection(entries: List<JournalEntry>, onAddEntry: (JournalEntry) -> Unit) {
    var content by rememberSaveable { mutableStateOf("") }
    var mood by rememberSaveable { mutableStateOf("Enfocado") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Enfocado", "Motivado", "Cansado", "Tranquilo").forEach { m ->
                FilterChip(selected = mood == m, onClick = { mood = m }, label = { Text(m) })
            }
        }
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("Escribe en tu diario personal...") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Button(
            onClick = {
                if (content.isNotBlank()) {
                    onAddEntry(JournalEntry(timestamp = System.currentTimeMillis(), mood = mood, content = content.trim()))
                    content = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Guardar Entrada") }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(entries, key = { it.id }) { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Estado: ${entry.mood}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(entry.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun BodyMetricsAndBackupSection(
    latestMetric: BodyMetric?,
    onSaveMetric: (BodyMetric) -> Unit,
    onExportAll: () -> Unit,
    onImportAll: () -> Unit
) {
    var weight by rememberSaveable(latestMetric) { mutableStateOf((latestMetric?.weightKg ?: 70f).toString()) }
    var height by rememberSaveable(latestMetric) { mutableStateOf((latestMetric?.heightCm ?: 175f).toString()) }
    var waist by rememberSaveable(latestMetric) { mutableStateOf((latestMetric?.waistCm ?: 76f).toString()) }
    var chest by rememberSaveable(latestMetric) { mutableStateOf((latestMetric?.chestCm ?: 98f).toString()) }
    var arm by rememberSaveable(latestMetric) { mutableStateOf((latestMetric?.armCm ?: 35f).toString()) }
    var calories by rememberSaveable(latestMetric) { mutableStateOf((latestMetric?.caloriesConsumed ?: 2200).toString()) }

    val evaluation = PhysicalStateCalculator.evaluate(
        weightKg = weight.toFloatOrNull() ?: 0f,
        heightCm = height.toFloatOrNull() ?: 0f,
        waistCm = waist.toFloatOrNull() ?: 0f
    )

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("IMC y Estado Físico Actual", fontWeight = FontWeight.Bold)
                    Text("IMC: ${"%.1f".format(evaluation.bmi)} | Índice Cintura-Altura: ${"%.2f".format(evaluation.whtr)}")
                    Text("Estado: ${evaluation.status}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Peso (kg)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = height, onValueChange = { height = it }, label = { Text("Altura (cm)") }, modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = waist, onValueChange = { waist = it }, label = { Text("Cintura (cm)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = chest, onValueChange = { chest = it }, label = { Text("Pecho (cm)") }, modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = arm, onValueChange = { arm = it }, label = { Text("Brazo (cm)") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = calories, onValueChange = { calories = it }, label = { Text("Calorías (Kcal)") }, modifier = Modifier.weight(1f))
            }
        }
        item {
            Button(
                onClick = {
                    onSaveMetric(
                        BodyMetric(
                            dateEpochDay = LocalDate.now().toEpochDay(),
                            weightKg = weight.toFloatOrNull() ?: 70f,
                            heightCm = height.toFloatOrNull() ?: 175f,
                            waistCm = waist.toFloatOrNull() ?: 76f,
                            chestCm = chest.toFloatOrNull() ?: 98f,
                            armCm = arm.toFloatOrNull() ?: 35f,
                            caloriesConsumed = calories.toIntOrNull() ?: 2000
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar Medidas y Sincronizar Health Connect") }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Copia de Seguridad Total (.nahabit)", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExportAll, modifier = Modifier.weight(1f)) { Text("Exportar Todo") }
                OutlinedButton(onClick = onImportAll, modifier = Modifier.weight(1f)) { Text("Importar Todo") }
            }
        }
    }
}
