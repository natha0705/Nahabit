package com.nahabit.tracker

import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import kotlinx.coroutines.delay
import kotlinx.serialization.encodeToString

@Composable
fun WorkoutRoutineManager(
    routines: List<WorkoutRoutine>,
    onUpsertRoutine: (WorkoutRoutine) -> Unit,
    onDeleteRoutine: (WorkoutRoutine) -> Unit,
    onNotify: (String, String) -> Unit
) {
    val context = LocalContext.current
    var editingRoutine by remember { mutableStateOf<WorkoutRoutine?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var activeWorkoutRoutine by remember { mutableStateOf<WorkoutRoutine?>(null) }
    var routineToExport by remember { mutableStateOf<WorkoutRoutine?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { targetUri ->
            routineToExport?.let { routine ->
                if (BackupManager.writeJsonToUri(context, targetUri, BackupManager.serializeRoutine(routine))) {
                    onNotify("Rutina Exportada", "Se guardó '${routine.routineName}' en tu dispositivo.")
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { sourceUri ->
            BackupManager.readJsonFromUri(context, sourceUri)?.let { rawJson ->
                runCatching {
                    val imported = BackupManager.deserializeRoutine(rawJson)
                    onUpsertRoutine(imported.copy(id = 0))
                    onNotify("Rutina Importada", "Se añadió '${imported.routineName}' con éxito.")
                }.onFailure {
                    onNotify("Archivo inválido", "No se pudo leer la rutina seleccionada.")
                }
            }
        }
    }

    when {
        activeWorkoutRoutine != null -> {
            ActiveWorkoutPlayer(
                routine = activeWorkoutRoutine!!,
                onNotify = onNotify,
                onFinish = { activeWorkoutRoutine = null }
            )
        }
        isCreatingNew || editingRoutine != null -> {
            RoutineEditorScreen(
                initialRoutine = editingRoutine,
                onCancel = {
                    isCreatingNew = false
                    editingRoutine = null
                },
                onSave = { savedRoutine ->
                    onUpsertRoutine(savedRoutine)
                    isCreatingNew = false
                    editingRoutine = null
                }
            )
        }
        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { isCreatingNew = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Crear Rutina")
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.FileOpen, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Importar")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(routines, key = { _, item -> item.id }) { _, routine ->
                        val exercises = remember(routine.exercisesJson) {
                            runCatching {
                                BackupManager.jsonFormat.decodeFromString<List<ExerciseItem>>(routine.exercisesJson)
                            }.getOrDefault(emptyList())
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(routine.routineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("${exercises.size} ejercicios", style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = {
                                        routineToExport = routine
                                        exportLauncher.launch("${routine.routineName.replace(" ", "_")}.nhroutine")
                                    }) { Icon(Icons.Default.Share, contentDescription = "Exportar") }
                                    IconButton(onClick = { editingRoutine = routine }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar")
                                    }
                                    IconButton(onClick = { onDeleteRoutine(routine) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Borrar")
                                    }
                                }

                                Spacer(Modifier.height(6.dp))
                                exercises.forEachIndexed { i, ex ->
                                    val unit = if (ex.isTimeBased) "${ex.repsOrSeconds}s" else "${ex.repsOrSeconds} reps"
                                    Text("${i + 1}. ${ex.name}: ${ex.sets} x $unit (Descanso: ${ex.restSeconds}s)", style = MaterialTheme.typography.bodySmall)
                                }

                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = { activeWorkoutRoutine = routine },
                                    enabled = exercises.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Iniciar Rutina y Temporizador")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoutineEditorScreen(
    initialRoutine: WorkoutRoutine?,
    onCancel: () -> Unit,
    onSave: (WorkoutRoutine) -> Unit
) {
    var routineName by rememberSaveable { mutableStateOf(initialRoutine?.routineName ?: "") }
    var exercises by remember {
        mutableStateOf(
            initialRoutine?.exercisesJson?.let {
                runCatching { BackupManager.jsonFormat.decodeFromString<List<ExerciseItem>>(it) }.getOrNull()
            } ?: listOf(
                ExerciseItem("Dominadas", 4, 8, false, 90),
                ExerciseItem("Fondos en paralelas", 4, 10, false, 90),
                ExerciseItem("Plancha abdominal", 3, 45, true, 60)
            )
        )
    }

    var showDialog by remember { mutableStateOf(false) }
    var editIdx by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = routineName,
            onValueChange = { routineName = it },
            label = { Text("Nombre de la Rutina") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ejercicios (${exercises.size})", fontWeight = FontWeight.Bold)
            FilledTonalButton(onClick = { editIdx = null; showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Añadir")
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(exercises) { idx, ex ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${idx + 1}. ${ex.name}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { editIdx = idx; showDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                            IconButton(onClick = { exercises = exercises.filterIndexed { i, _ -> i != idx } }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StepperControl("Series", "${ex.sets}",
                                onDec = { if (ex.sets > 1) exercises = exercises.toMutableList().also { it[idx] = ex.copy(sets = ex.sets - 1) } },
                                onInc = { exercises = exercises.toMutableList().also { it[idx] = ex.copy(sets = ex.sets + 1) } }
                            )
                            StepperControl(if (ex.isTimeBased) "Segundos" else "Reps", "${ex.repsOrSeconds}",
                                onDec = {
                                    val step = if (ex.isTimeBased) 5 else 1
                                    exercises = exercises.toMutableList().also { it[idx] = ex.copy(repsOrSeconds = (ex.repsOrSeconds - step).coerceAtLeast(1)) }
                                },
                                onInc = {
                                    val step = if (ex.isTimeBased) 5 else 1
                                    exercises = exercises.toMutableList().also { it[idx] = ex.copy(repsOrSeconds = ex.repsOrSeconds + step) }
                                }
                            )
                            StepperControl("Descanso", "${ex.restSeconds}s",
                                onDec = { exercises = exercises.toMutableList().also { it[idx] = ex.copy(restSeconds = (ex.restSeconds - 15).coerceAtLeast(0)) } },
                                onInc = { exercises = exercises.toMutableList().also { it[idx] = ex.copy(restSeconds = ex.restSeconds + 15) } }
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
            Button(
                onClick = {
                    onSave(
                        WorkoutRoutine(
                            id = initialRoutine?.id ?: 0L,
                            routineName = routineName.trim().ifEmpty { "Rutina Personalizada" },
                            exercisesJson = BackupManager.jsonFormat.encodeToString(exercises)
                        )
                    )
                },
                enabled = exercises.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) { Text("Guardar") }
        }
    }

    if (showDialog) {
        val current = editIdx?.let { exercises.getOrNull(it) }
        var name by rememberSaveable { mutableStateOf(current?.name ?: "") }
        var sets by rememberSaveable { mutableStateOf((current?.sets ?: 4).toString()) }
        var reps by rememberSaveable { mutableStateOf((current?.repsOrSeconds ?: 10).toString()) }
        var isTime by rememberSaveable { mutableStateOf(current?.isTimeBased ?: false) }
        var rest by rememberSaveable { mutableStateOf((current?.restSeconds ?: 90).toString()) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (current == null) "Nuevo Ejercicio" else "Editar Ejercicio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isTime, onClick = { isTime = false }, label = { Text("Repeticiones") })
                        FilterChip(selected = isTime, onClick = { isTime = true }, label = { Text("Segundos") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = sets,
                            onValueChange = { sets = it.filter(Char::isDigit) },
                            label = { Text("Series") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = reps,
                            onValueChange = { reps = it.filter(Char::isDigit) },
                            label = { Text(if (isTime) "Segs" else "Reps") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = rest,
                        onValueChange = { rest = it.filter(Char::isDigit) },
                        label = { Text("Descanso (segundos)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val item = ExerciseItem(
                        name = name.trim().ifEmpty { "Ejercicio" },
                        sets = (sets.toIntOrNull() ?: 4).coerceAtLeast(1),
                        repsOrSeconds = (reps.toIntOrNull() ?: 10).coerceAtLeast(1),
                        isTimeBased = isTime,
                        restSeconds = (rest.toIntOrNull() ?: 60).coerceAtLeast(0)
                    )
                    exercises = if (editIdx != null) {
                        exercises.toMutableList().also { it[editIdx!!] = item }
                    } else {
                        exercises + item
                    }
                    showDialog = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun StepperControl(label: String, value: String, onDec: () -> Unit, onInc: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onDec, modifier = Modifier.size(28.dp)) { Text("-") }
            Text(value, modifier = Modifier.padding(horizontal = 6.dp), fontWeight = FontWeight.SemiBold)
            FilledTonalIconButton(onClick = onInc, modifier = Modifier.size(28.dp)) { Text("+") }
        }
    }
}

@Composable
fun ActiveWorkoutPlayer(
    routine: WorkoutRoutine,
    onNotify: (String, String) -> Unit,
    onFinish: () -> Unit
) {
    val exercises = remember(routine.exercisesJson) {
        runCatching {
            BackupManager.jsonFormat.decodeFromString<List<ExerciseItem>>(routine.exercisesJson)
        }.getOrDefault(emptyList())
    }

    var exIdx by rememberSaveable { mutableIntStateOf(0) }
    var setNum by rememberSaveable { mutableIntStateOf(1) }
    var isResting by rememberSaveable { mutableStateOf(false) }

    var targetEndRealtime by rememberSaveable { mutableLongStateOf(0L) }
    var remainingSeconds by rememberSaveable { mutableIntStateOf(0) }
    var isRunning by rememberSaveable { mutableStateOf(false) }

    val currentEx = exercises.getOrNull(exIdx)

    LaunchedEffect(isRunning, targetEndRealtime) {
        while (isRunning) {
            val diffMs = targetEndRealtime - SystemClock.elapsedRealtime()
            if (diffMs <= 0L) {
                remainingSeconds = 0
                isRunning = false
                if (isResting) {
                    isResting = false
                    onNotify("¡Descanso Terminado!", "Continúa con la serie $setNum de ${currentEx?.name.orEmpty()}")
                } else {
                    onNotify("¡Serie Completada!", "Tiempo cumplido para ${currentEx?.name.orEmpty()}")
                }
                break
            } else {
                remainingSeconds = ((diffMs + 999L) / 1000L).toInt()
            }
            delay(250L)
        }
    }

    if (currentEx == null) {
        Button(onClick = onFinish) { Text("Volver") }
        return
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(routine.routineName, style = MaterialTheme.typography.labelLarge)
            Text(currentEx.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Serie $setNum de ${currentEx.sets} • Ejercicio ${exIdx + 1}/${exercises.size}", color = MaterialTheme.colorScheme.primary)

            AnimatedVisibility(visible = isResting) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("TIEMPO DE DESCANSO")
                    Text("${remainingSeconds / 60}m : ${"%02d".format(remainingSeconds % 60)}s", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            targetEndRealtime += 15_000L
                            remainingSeconds += 15
                        }) { Text("+15s") }
                        Button(onClick = { isResting = false; isRunning = false }) { Text("Saltar Descanso") }
                    }
                }
            }

            AnimatedVisibility(visible = !isResting) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (currentEx.isTimeBased) {
                        val shown = if (isRunning || remainingSeconds > 0) remainingSeconds else currentEx.repsOrSeconds
                        Text("Duración: ${shown}s", style = MaterialTheme.typography.headlineMedium)
                        Button(onClick = {
                            if (!isRunning) {
                                val baseSecs = if (remainingSeconds > 0) remainingSeconds else currentEx.repsOrSeconds
                                targetEndRealtime = SystemClock.elapsedRealtime() + (baseSecs * 1000L)
                                isRunning = true
                            } else {
                                isRunning = false
                            }
                        }) { Text(if (isRunning) "Pausar" else "Iniciar Serie por Tiempo") }
                    } else {
                        Text("Objetivo: ${currentEx.repsOrSeconds} repeticiones", style = MaterialTheme.typography.headlineMedium)
                    }

                    Button(
                        onClick = {
                            isRunning = false
                            if (setNum < currentEx.sets) {
                                setNum += 1
                                if (currentEx.restSeconds > 0) {
                                    isResting = true
                                    remainingSeconds = currentEx.restSeconds
                                    targetEndRealtime = SystemClock.elapsedRealtime() + (currentEx.restSeconds * 1000L)
                                    isRunning = true
                                }
                            } else if (exIdx < exercises.lastIndex) {
                                exIdx += 1
                                setNum = 1
                                if (currentEx.restSeconds > 0) {
                                    isResting = true
                                    remainingSeconds = currentEx.restSeconds
                                    targetEndRealtime = SystemClock.elapsedRealtime() + (currentEx.restSeconds * 1000L)
                                    isRunning = true
                                }
                            } else {
                                onNotify("¡Entrenamiento Terminado!", "Completaste ${routine.routineName} al 100%.")
                                onFinish()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (setNum < currentEx.sets) "Terminar Serie y Descansar" else if (exIdx < exercises.lastIndex) "Siguiente Ejercicio" else "Finalizar Entrenamiento")
                    }
                }
            }
            TextButton(onClick = onFinish) { Text("Salir") }
        }
    }
}
