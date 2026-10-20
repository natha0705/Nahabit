package com.nahabit.tracker.habits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahabit.tracker.R
import com.nahabit.tracker.core.ui.UiState
import com.nahabit.tracker.core.ui.theme.LocalCardSpacing
import com.nahabit.tracker.core.ui.theme.NahabitEmerald
import java.time.LocalDate

@Composable
fun HabitsScreen(
    viewModel: HabitsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedEpochDay by viewModel.selectedEpochDay.collectAsStateWithLifecycle()
    val dateOffset by viewModel.selectedDateOffsetDays.collectAsStateWithLifecycle()
    val spacing = LocalCardSpacing.current

    var showCreatorSheet by rememberSaveable { mutableStateOf(false) }

    val templates = remember {
        listOf(
            Triple("💧", "Beber 8 vasos de agua", 8),
            Triple("📚", "Leer 20 páginas", 20),
            Triple("🧘", "Meditar 10 minutos", 10),
            Triple("💪", "Dominadas y fondos", 1),
            Triple("📵", "Máximo 30m en redes", 30)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.habits_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Button(onClick = { showCreatorSheet = !showCreatorSheet }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.habits_add_button))
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { viewModel.shiftSelectedDate(-1L) }) {
                    Text("< -1d")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = LocalDate.ofEpochDay(selectedEpochDay).toString(),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (dateOffset < 0L) {
                        TextButton(onClick = viewModel::resetToToday) {
                            Text("Volver a Hoy")
                        }
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.shiftSelectedDate(1L) },
                    enabled = dateOffset < 0L
                ) {
                    Text("+1d >")
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(templates) { (emoji, title, target) ->
                AssistChip(
                    onClick = {
                        viewModel.createProHabit(
                            title = title,
                            emoji = emoji,
                            habitType = if (target > 1) "QUANTITY" else "YES_NO",
                            goalComparison = if (title.contains("Máximo")) "AT_MOST" else "AT_LEAST",
                            targetValue = target,
                            unit = "",
                            frequencyType = "DAILY",
                            frequencyParam = 7,
                            timeOfDayGroup = "ANYTIME",
                            graceDays = 1,
                            reminderHour = -1
                        )
                    },
                    label = { Text("$emoji $title") }
                )
            }
        }

        AnimatedVisibility(visible = showCreatorSheet) {
            ProHabitCreatorCard(
                onCancel = { showCreatorSheet = false },
                onSave = { title, emoji, type, comp, target, unit, freqType, freqParam, group, grace, remHour ->
                    viewModel.createProHabit(
                        title = title,
                        emoji = emoji,
                        habitType = type,
                        goalComparison = comp,
                        targetValue = target,
                        unit = unit,
                        frequencyType = freqType,
                        frequencyParam = freqParam,
                        timeOfDayGroup = group,
                        graceDays = grace,
                        reminderHour = remHour
                    )
                    showCreatorSheet = false
                }
            )
        }

        when (val currentState = state) {
            is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Empty -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.state_empty_habits))
                }
            }
            is UiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(currentState.messageResId))
                }
            }
            is UiState.Success -> {
                val grouped = remember(currentState.data) {
                    currentState.data.groupBy { it.definition.timeOfDayGroup }
                }
                val groupOrder = listOf(
                    "MORNING" to R.string.habits_group_morning,
                    "AFTERNOON" to R.string.habits_group_afternoon,
                    "EVENING" to R.string.habits_group_evening,
                    "ANYTIME" to R.string.habits_group_anytime
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    groupOrder.forEach { (groupKey, groupTitleRes) ->
                        val itemsInGroup = grouped[groupKey].orEmpty()
                        if (itemsInGroup.isNotEmpty()) {
                            item(key = "header_$groupKey") {
                                Text(
                                    text = stringResource(groupTitleRes),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(itemsInGroup, key = { it.definition.id }) { item ->
                                ProHabitItemCard(
                                    item = item,
                                    onIncrement = { viewModel.incrementToday(item) },
                                    onSaveNote = { note -> viewModel.saveNoteForSelectedDay(item, note) },
                                    onMoveUp = { viewModel.reorderHabit(item, -1) },
                                    onMoveDown = { viewModel.reorderHabit(item, 1) },
                                    onPause = { viewModel.togglePauseHabit(item) },
                                    onArchive = { viewModel.archiveHabit(item) },
                                    onDelete = { viewModel.deleteHabit(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProHabitCreatorCard(
    onCancel: () -> Unit,
    onSave: (String, String, String, String, Int, String, String, Int, String, Int, Int) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("🔥") }
    var habitType by rememberSaveable { mutableStateOf("YES_NO") }
    var goalComparison by rememberSaveable { mutableStateOf("AT_LEAST") }
    var targetText by rememberSaveable { mutableStateOf("1") }
    var unitText by rememberSaveable { mutableStateOf("") }
    var freqType by rememberSaveable { mutableStateOf("DAILY") }
    var freqParam by rememberSaveable { mutableIntStateOf(5) }
    var timeGroup by rememberSaveable { mutableStateOf("MORNING") }
    var graceDays by rememberSaveable { mutableIntStateOf(1) }
    var reminderHourText by rememberSaveable { mutableStateOf("8") }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(2) },
                    label = { Text("Emoji") },
                    singleLine = true,
                    modifier = Modifier.width(78.dp)
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.habits_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "YES_NO" to R.string.habits_type_yesno,
                    "QUANTITY" to R.string.habits_type_quantity,
                    "DURATION" to R.string.habits_type_duration
                ).forEach { (code, labelRes) ->
                    FilterChip(
                        selected = habitType == code,
                        onClick = { habitType = code },
                        label = { Text(stringResource(labelRes)) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "AT_LEAST" to R.string.habits_goal_at_least,
                    "AT_MOST" to R.string.habits_goal_at_most
                ).forEach { (code, labelRes) ->
                    FilterChip(
                        selected = goalComparison == code,
                        onClick = { goalComparison = code },
                        label = { Text(stringResource(labelRes)) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it.filter(Char::isDigit) },
                    label = { Text("Meta") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = unitText,
                    onValueChange = { unitText = it },
                    label = { Text(stringResource(R.string.habits_unit_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = reminderHourText,
                    onValueChange = { reminderHourText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Hora (0-23)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "MORNING" to "Mañana",
                    "AFTERNOON" to "Tarde",
                    "EVENING" to "Noche",
                    "ANYTIME" to "Libre"
                ).forEach { (code, label) ->
                    FilterChip(
                        selected = timeGroup == code,
                        onClick = { timeGroup = code },
                        label = { Text(label) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = freqType == "DAILY",
                    onClick = { freqType = "DAILY" },
                    label = { Text(stringResource(R.string.habits_freq_daily)) }
                )
                FilterChip(
                    selected = freqType == "TIMES_PER_WEEK",
                    onClick = { freqType = "TIMES_PER_WEEK"; freqParam = 4 },
                    label = { Text(stringResource(R.string.habits_freq_times_week, 4)) }
                )
                FilterChip(
                    selected = freqType == "EVERY_N_DAYS",
                    onClick = { freqType = "EVERY_N_DAYS"; freqParam = 2 },
                    label = { Text(stringResource(R.string.habits_freq_every_n, 2)) }
                )
            }

            Text(stringResource(R.string.habits_Grace_days, graceDays), style = MaterialTheme.typography.labelMedium)
            Slider(
                value = graceDays.toFloat(),
                onValueChange = { graceDays = it.toInt() },
                valueRange = 0f..3f,
                steps = 2
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        onSave(
                            title,
                            emoji,
                            habitType,
                            goalComparison,
                            targetText.toIntOrNull() ?: 1,
                            unitText,
                            freqType,
                            freqParam,
                            timeGroup,
                            graceDays,
                            reminderHourText.toIntOrNull() ?: -1
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@Composable
private fun ProHabitItemCard(
    item: HabitWithStreakUiModel,
    onIncrement: () -> Unit,
    onSaveNote: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPause: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var expandedDetails by rememberSaveable { mutableStateOf(false) }
    var noteInput by rememberSaveable(item.todayNote) { mutableStateOf(item.todayNote) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.definition.iconEmoji} ${item.definition.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val modePrefix = if (item.definition.goalComparison == "AT_MOST") "Máx" else "Meta"
                    val unitSuffix = if (item.definition.unit.isNotBlank()) " ${item.definition.unit}" else ""
                    Text(
                        text = stringResource(R.string.habits_streak_label, item.currentStreak, item.bestStreak) +
                            " • $modePrefix: ${item.todayValue}/${item.definition.targetValue}$unitSuffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.completedToday) NahabitEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = onIncrement,
                        enabled = !item.definition.isPaused
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+1")
                    }
                    IconButton(onClick = { expandedDetails = !expandedDetails }) {
                        Icon(
                            imageVector = if (expandedDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
            }

            HabitMiniHeatmap28Days(completedFlags = item.last28DaysCompleted)

            AnimatedVisibility(visible = expandedDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = noteInput,
                            onValueChange = { noteInput = it },
                            label = { Text(stringResource(R.string.habits_note_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { onSaveNote(noteInput) }) {
                            Text(stringResource(R.string.action_save))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row {
                            IconButton(onClick = onMoveUp) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) }
                            IconButton(onClick = onMoveDown) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AssistChip(
                                onClick = onPause,
                                label = { Text(if (item.definition.isPaused) "Reanudar" else stringResource(R.string.habits_pause_action)) }
                            )
                            AssistChip(
                                onClick = onArchive,
                                label = { Text(stringResource(R.string.habits_archive_action)) }
                            )
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitMiniHeatmap28Days(completedFlags: List<Boolean>) {
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
    ) {
        if (completedFlags.isEmpty()) return@Canvas
        val count = completedFlags.size
        val gap = 3.dp.toPx()
        val boxWidth = (size.width - (gap * (count - 1))) / count

        completedFlags.forEachIndexed { index, done ->
            drawRoundRect(
                color = if (done) NahabitEmerald else emptyColor,
                topLeft = Offset(index * (boxWidth + gap), 0f),
                size = Size(boxWidth, size.height),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}
