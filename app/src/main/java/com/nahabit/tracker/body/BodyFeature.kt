package com.nahabit.tracker.body

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nahabit.tracker.R
import com.nahabit.tracker.core.database.BodyCompositionEntity
import com.nahabit.tracker.core.database.NahabitDao
import com.nahabit.tracker.core.health.HealthConnectManager
import com.nahabit.tracker.core.ui.UiState
import com.nahabit.tracker.core.ui.theme.LocalCardSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class BodyViewModel @Inject constructor(
    private val dao: NahabitDao,
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    val uiState: StateFlow<UiState<List<BodyCompositionEntity>>> = dao.observeBodyComposition()
        .map { if (it.isEmpty()) UiState.Empty else UiState.Success(it) }
        .catch { emit(UiState.Error(R.string.state_error_generic)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    fun saveBodyMeasurement(weight: Float, height: Float, waist: Float) {
        viewModelScope.launch {
            val today = LocalDate.now().toEpochDay()
            dao.insertBodyComposition(
                BodyCompositionEntity(
                    dateEpochDay = today,
                    weightKg = weight,
                    heightCm = height,
                    neckCm = 38f,
                    shouldersCm = 110f,
                    chestCm = 100f,
                    waistCm = waist,
                    hipsCm = 95f,
                    thighCm = 55f,
                    bicepCm = 35f
                )
            )
            if (healthConnectManager.isAvailable()) {
                healthConnectManager.writeWeight(weight.toDouble(), Instant.now())
            }
        }
    }
}

@Composable
fun BodyScreen(viewModel: BodyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalCardSpacing.current

    var weightInput by rememberSaveable { mutableStateOf("70") }
    var heightInput by rememberSaveable { mutableStateOf("175") }
    var waistInput by rememberSaveable { mutableStateOf("76") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        item {
            Text(
                text = stringResource(R.string.body_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { weightInput = it },
                            label = { Text("Peso (kg)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = heightInput,
                            onValueChange = { heightInput = it },
                            label = { Text("Altura (cm)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = waistInput,
                        onValueChange = { waistInput = it },
                        label = { Text("Cintura (cm)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            viewModel.saveBodyMeasurement(
                                weightInput.toFloatOrNull() ?: 70f,
                                heightInput.toFloatOrNull() ?: 175f,
                                waistInput.toFloatOrNull() ?: 76f
                            )
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }

        if (state is UiState.Success) {
            items((state as UiState.Success<List<BodyCompositionEntity>>).data, key = { it.id }) { m ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Text(
                        text = "${m.weightKg} kg • ${m.heightCm} cm • Cintura: ${m.waistCm} cm",
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
