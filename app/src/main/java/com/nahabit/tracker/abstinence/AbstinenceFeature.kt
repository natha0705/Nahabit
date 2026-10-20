package com.nahabit.tracker.abstinence

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nahabit.tracker.R
import com.nahabit.tracker.core.database.BadHabitCounterEntity
import com.nahabit.tracker.core.database.BadHabitRelapseEntity
import com.nahabit.tracker.core.database.NahabitDao
import com.nahabit.tracker.core.ui.theme.LocalCardSpacing
import com.nahabit.tracker.core.ui.theme.NahabitAmber
import com.nahabit.tracker.core.ui.theme.NahabitCoral
import com.nahabit.tracker.core.ui.theme.NahabitCyan
import com.nahabit.tracker.core.ui.theme.NahabitEmerald
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class AbstinenceViewModel @Inject constructor(
    private val dao: NahabitDao
) : ViewModel() {

    val counters: StateFlow<List<BadHabitCounterEntity>> = dao.observeBadHabits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val relapses: StateFlow<List<BadHabitRelapseEntity>> = dao.observeBadHabitRelapses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addBadHabitPro(
        name: String,
        whyReason: String,
        dailyCost: Float,
        dailyMinutes: Int
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.upsertBadHabit(
                BadHabitCounterEntity(
                    habitName = name.trim(),
                    lastRelapseTimestamp = System.currentTimeMillis(),
                    dailyCostMoney = dailyCost.coerceAtLeast(0f),
                    dailyMinutesWasted = dailyMinutes.coerceAtLeast(0),
                    whyIStartedReason = whyReason.trim()
                )
            )
        }
    }

    fun registerRelapseWithTrigger(counter: BadHabitCounterEntity, trigger: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.insertBadHabitRelapse(
                BadHabitRelapseEntity(
                    badHabitId = counter.id,
                    timestamp = now,
                    triggerReason = trigger.trim().ifEmpty { "Impulso momentáneo" }
                )
            )
            dao.upsertBadHabit(counter.copy(lastRelapseTimestamp = now))
        }
    }

    fun deleteCounter(counter: BadHabitCounterEntity) {
        viewModelScope.launch {
            dao.deleteBadHabit(counter)
        }
    }
}

@Composable
fun AbstinenceScreen(viewModel: AbstinenceViewModel = hiltViewModel()) {
    val counters by viewModel.counters.collectAsStateWithLifecycle()
    val relapses by viewModel.relapses.collectAsStateWithLifecycle()
    val spacing = LocalCardSpacing.current

    var nameInput by rememberSaveable { mutableStateOf("") }
    var whyInput by rememberSaveable { mutableStateOf("") }
    var costInput by rememberSaveable { mutableStateOf("50") }
    var minutesInput by rememberSaveable { mutableStateOf("45") }

    var showEmergencyDialog by rememberSaveable { mutableStateOf(false) }
    var relapseDialogTarget by remember { mutableStateOf<BadHabitCounterEntity?>(null) }

    val nowMs = System.currentTimeMillis()
    val relapsesByHabit = remember(relapses) { relapses.groupBy { it.badHabitId } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        contentPadding = PaddingValues(vertical = 14.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.abstinence_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
        }

        item {
            Button(
                onClick = { showEmergencyDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = NahabitCoral),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
            ) {
                Icon(Icons.Default.HealthAndSafety, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.abstinence_sos_button), fontWeight = FontWeight.ExtraBold)
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Hábito a dejar (ej. Azúcar, Cigarro, Apuestas)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = whyInput,
                        onValueChange = { whyInput = it },
                        label = { Text(stringResource(R.string.abstinence_why_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = costInput,
                            onValueChange = { costInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                            label = { Text(stringResource(R.string.abstinence_cost_hint)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minutesInput,
                            onValueChange = { minutesInput = it.filter(Char::isDigit) },
                            label = { Text(stringResource(R.string.abstinence_minutes_hint)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.addBadHabitPro(
                                name = nameInput,
                                whyReason = whyInput,
                                dailyCost = costInput.toFloatOrNull() ?: 0f,
                                dailyMinutes = minutesInput.toIntOrNull() ?: 0
                            )
                            nameInput = ""
                            whyInput = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }

        items(counters, key = { it.id }) { item ->
            val elapsedMs = (nowMs - item.lastRelapseTimestamp).coerceAtLeast(0L)
            val daysClean = TimeUnit.MILLISECONDS.toDays(elapsedMs)
            val hoursClean = TimeUnit.MILLISECONDS.toHours(elapsedMs) % 24
            val exactDaysFloat = elapsedMs.toFloat() / 86_400_000f

            val moneySaved = exactDaysFloat * item.dailyCostMoney
            val hoursReclaimed = (exactDaysFloat * item.dailyMinutesWasted) / 60f
            val milestoneBadge = when {
                daysClean >= 365 -> "1 Año Libre 🏆"
                daysClean >= 100 -> "100 Días Libre 💎"
                daysClean >= 30 -> "1 Mes Libre 🥇"
                daysClean >= 7 -> "1 Semana Libre 🔥"
                daysClean >= 3 -> "3 Días Libre ⚡"
                daysClean >= 1 -> "1 Día Libre 🌱"
                else -> "Primeras 24h en curso"
            }

            val historyList = relapsesByHabit[item.id].orEmpty()

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.habitName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        IconButton(onClick = { viewModel.deleteCounter(item) }) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    }

                    Text(
                        text = "$daysClean días y $hoursClean horas libre",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = NahabitEmerald
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = NahabitAmber)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.abstinence_milestone_reached, milestoneBadge),
                            fontWeight = FontWeight.SemiBold,
                            color = NahabitAmber
                        )
                    }

                    Text(
                        text = stringResource(R.string.abstinence_saved_summary, moneySaved, hoursReclaimed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = NahabitCyan
                    )

                    if (item.whyIStartedReason.isNotBlank()) {
                        Text(
                            text = "Motivo: “${item.whyIStartedReason}”",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (historyList.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.abstinence_relapse_history, historyList.size) +
                                " (Último disparador: ${historyList.first().triggerReason})",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    OutlinedButton(
                        onClick = { relapseDialogTarget = item },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.History, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.abstinence_relapse_btn))
                    }
                }
            }
        }
    }

    if (showEmergencyDialog) {
        EmergencySosDialog(onDismiss = { showEmergencyDialog = false })
    }

    relapseDialogTarget?.let { target ->
        var triggerText by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { relapseDialogTarget = null },
            title = { Text(stringResource(R.string.abstinence_relapse_btn)) },
            text = {
                OutlinedTextField(
                    value = triggerText,
                    onValueChange = { triggerText = it },
                    label = { Text(stringResource(R.string.abstinence_relapse_trigger_hint)) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.registerRelapseWithTrigger(target, triggerText)
                    relapseDialogTarget = null
                }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { relapseDialogTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun EmergencySosDialog(onDismiss: () -> Unit) {
    val endRealtime = remember { SystemClock.elapsedRealtime() + (10 * 60 * 1000L) }
    var secondsLeft by remember { mutableIntStateOf(600) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            val diff = endRealtime - SystemClock.elapsedRealtime()
            secondsLeft = ((diff + 999L) / 1000L).toInt().coerceAtLeast(0)
            delay(250L)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.abstinence_sos_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.abstinence_sos_desc))
                Text(
                    text = "${secondsLeft / 60}m : ${"%02d".format(secondsLeft % 60)}s",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = NahabitEmerald
                )
                Text("1. Inhala 4s, mantén 4s, exhala 4s.\n2. Bebe un vaso de agua fría.\n3. Cambia de habitación durante 5 minutos.")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Superé el impulso")
            }
        }
    )
}
