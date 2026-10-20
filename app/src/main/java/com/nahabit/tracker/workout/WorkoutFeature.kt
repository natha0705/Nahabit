package com.nahabit.tracker.workout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nahabit.tracker.R
import com.nahabit.tracker.core.database.ExerciseEntity
import com.nahabit.tracker.core.database.NahabitDao
import com.nahabit.tracker.core.database.WorkoutRoutineEntity
import com.nahabit.tracker.core.ui.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val dao: NahabitDao
) : ViewModel() {

    val routinesUiState: StateFlow<UiState<List<WorkoutRoutineEntity>>> = dao.observeRoutines()
        .map { if (it.isEmpty()) UiState.Empty else UiState.Success(it) }
        .catch { emit(UiState.Error(R.string.state_error_generic)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    val exercisesLibrary: StateFlow<List<ExerciseEntity>> = dao.observeExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createRoutine(name: String, jsonExercises: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.upsertRoutine(WorkoutRoutineEntity(routineName = name.trim(), exercisesJson = jsonExercises))
        }
    }

    fun deleteRoutine(routine: WorkoutRoutineEntity) {
        viewModelScope.launch {
            dao.deleteRoutine(routine)
        }
    }
}

@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.routinesUiState.collectAsStateWithLifecycle()
    val exercises by viewModel.exercisesLibrary.collectAsStateWithLifecycle()
    var routineName by rememberSaveable { mutableStateOf("") }
    var showCreator by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.workout_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Button(onClick = { showCreator = !showCreator }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(if (showCreator) "Cerrar" else "Nueva Rutina")
            }
        }

        if (showCreator) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Crear Rutina Pro (${exercises.size} ejercicios en biblioteca)", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = routineName,
                        onValueChange = { routineName = it },
                        label = { Text("Nombre de la rutina (ej. Calistenia Tirón / Empuje)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            viewModel.createRoutine(routineName, "[]")
                            routineName = ""
                            showCreator = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }

        when (val currentState = state) {
            is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Empty -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.state_empty_workouts))
                }
            }
            is UiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(currentState.messageResId))
                }
            }
            is UiState.Success -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(currentState.data, key = { it.id }) { routine ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = routine.routineName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Temporizador en pantalla de bloqueo listo",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalButton(
                                        onClick = {
                                            NahabitWorkoutService.start(context, routine.routineName, 90)
                                        }
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("90s")
                                    }
                                    IconButton(onClick = { viewModel.deleteRoutine(routine) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.action_delete)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
