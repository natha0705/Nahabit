package com.nahabit.tracker.journal

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.nahabit.tracker.core.database.JournalEntryEntity
import com.nahabit.tracker.core.database.MindfulnessSessionEntity
import com.nahabit.tracker.core.database.NahabitDao
import com.nahabit.tracker.core.datastore.UserPreferences
import com.nahabit.tracker.core.datastore.UserPreferencesRepository
import com.nahabit.tracker.core.time.TimeProvider
import com.nahabit.tracker.core.ui.theme.LocalCardSpacing
import com.nahabit.tracker.core.ui.theme.NahabitCyan
import com.nahabit.tracker.core.ui.theme.NahabitEmerald
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeeklyReflectionSummary(
    val habitsCompletedLast7Days: Int,
    val workoutsLast7Days: Int,
    val averageMood: Float
)

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val dao: NahabitDao,
    private val prefsRepo: UserPreferencesRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    val entries: StateFlow<List<JournalEntryEntity>> = dao.observeJournalEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mindfulnessSessions: StateFlow<List<MindfulnessSessionEntity>> = dao.observeMindfulnessSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weeklySummary: StateFlow<WeeklyReflectionSummary> = combine(
        dao.observeAllHabitLogs(),
        dao.observeWorkoutSessions(),
        dao.observeJournalEntries()
    ) { habitLogs, workouts, journals ->
        val today = timeProvider.currentLogicalEpochDay()
        val startDay = today - 6L
        val habits7d = habitLogs.count { it.isCompleted && it.epochDay in startDay..today }
        val workouts7d = workouts.count { it.dateEpochDay in startDay..today }
        val recentMoods = journals.take(7).map { it.moodScore }
        val avgMood = if (recentMoods.isEmpty()) 4.0f else recentMoods.average().toFloat()

        WeeklyReflectionSummary(habits7d, workouts7d, avgMood)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WeeklyReflectionSummary(0, 0, 4.0f)
    )

    fun saveEntry(
        content: String,
        moodScore: Int,
        tags: String,
        templateType: String
    ) {
        if (content.isBlank()) return
        val moodEmoji = when (moodScore) {
            1 -> "😞 Muy bajo"
            2 -> "😕 Bajo"
            3 -> "😐 Neutral"
            4 -> "🙂 Bien"
            else -> "🤩 Excelente"
        }
        viewModelScope.launch {
            dao.insertJournalEntry(
                JournalEntryEntity(
                    timestamp = System.currentTimeMillis(),
                    mood = moodEmoji,
                    content = content.trim(),
                    moodScore = moodScore.coerceIn(1, 5),
                    tags = tags.trim(),
                    templateType = templateType
                )
            )
        }
    }

    fun toggleFavorite(entry: JournalEntryEntity) {
        viewModelScope.launch {
            dao.insertJournalEntry(entry.copy(isFavorite = !entry.isFavorite))
        }
    }

    fun deleteEntry(entry: JournalEntryEntity) {
        viewModelScope.launch {
            dao.deleteJournalEntry(entry)
        }
    }

    fun logMindfulnessSession(type: String, minutes: Int) {
        viewModelScope.launch {
            dao.insertMindfulnessSession(
                MindfulnessSessionEntity(
                    timestamp = System.currentTimeMillis(),
                    sessionType = type,
                    durationMinutes = minutes
                )
            )
        }
    }

    fun updatePin(pin: String) {
        viewModelScope.launch {
            prefsRepo.setJournalPinCode(pin)
        }
    }
}

@Composable
fun JournalScreen(viewModel: JournalViewModel = hiltViewModel()) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val allEntries by viewModel.entries.collectAsStateWithLifecycle()
    val mindfulSessions by viewModel.mindfulnessSessions.collectAsStateWithLifecycle()
    val weeklySummary by viewModel.weeklySummary.collectAsStateWithLifecycle()
    val spacing = LocalCardSpacing.current

    var isUnlocked by rememberSaveable { mutableStateOf(false) }
    var pinAttempt by rememberSaveable { mutableStateOf("") }
    var showPinSetupDialog by rememberSaveable { mutableStateOf(false) }

    if (prefs.journalPinCode.isNotEmpty() && !isUnlocked) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = NahabitCyan)
                    Text(
                        text = stringResource(R.string.journal_pin_lock_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = pinAttempt,
                        onValueChange = { pinAttempt = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.journal_pin_enter)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (pinAttempt == prefs.journalPinCode) {
                                isUnlocked = true
                                pinAttempt = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(stringResource(R.string.journal_pin_unlock))
                    }
                }
            }
        }
        return
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }

    var selectedMoodScore by rememberSaveable { mutableIntStateOf(4) }
    var selectedTemplate by rememberSaveable { mutableStateOf("FREE") }
    var tagsInput by rememberSaveable { mutableStateOf("") }
    var contentInput by rememberSaveable { mutableStateOf("") }

    val dailyPromptRes = remember {
        listOf(R.string.journal_prompt_1, R.string.journal_prompt_2, R.string.journal_prompt_3).random()
    }

    val showLowMoodSupport = remember(allEntries) {
        allEntries.size >= 3 && allEntries.take(3).all { it.moodScore <= 2 }
    }

    val filteredEntries = remember(allEntries, searchQuery, onlyFavorites) {
        allEntries.filter { entry ->
            val matchesFav = !onlyFavorites || entry.isFavorite
            val matchesQuery = searchQuery.isBlank() ||
                entry.content.contains(searchQuery, ignoreCase = true) ||
                entry.tags.contains(searchQuery, ignoreCase = true)
            matchesFav && matchesQuery
        }
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
                text = stringResource(R.string.journal_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            IconButton(
                onClick = { showPinSetupDialog = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (prefs.journalPinCode.isEmpty()) Icons.Default.LockOpen else Icons.Default.Lock,
                    contentDescription = stringResource(R.string.journal_pin_setup)
                )
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.journal_tab_entries)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.journal_tab_breathing)) }
            )
        }

        if (selectedTab == 1) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item {
                    BreathingAndMeditationSection(
                        completedSessionsCount = mindfulSessions.size,
                        totalMindfulMinutes = mindfulSessions.sumOf { it.durationMinutes },
                        onSessionCompleted = viewModel::logMindfulnessSession
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(spacing),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (showLowMoodSupport) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = NahabitEmerald)
                                Text(
                                    text = stringResource(R.string.journal_low_mood_support),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.journal_weekly_review_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(
                                    R.string.journal_weekly_review_desc,
                                    weeklySummary.habitsCompletedLast7Days,
                                    weeklySummary.workoutsLast7Days,
                                    weeklySummary.averageMood
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "${stringResource(R.string.journal_prompt_title)}: “${stringResource(dailyPromptRes)}”",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val moods = listOf(1 to "😞", 2 to "😕", 3 to "😐", 4 to "🙂", 5 to "🤩")
                                moods.forEach { (score, emoji) ->
                                    FilterChip(
                                        selected = selectedMoodScore == score,
                                        onClick = { selectedMoodScore = score },
                                        label = { Text("$emoji $score") }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val templates = listOf(
                                    "FREE" to R.string.journal_template_free,
                                    "GRATITUDE" to R.string.journal_template_gratitude,
                                    "REVIEW" to R.string.journal_template_review
                                )
                                templates.forEach { (code, labelRes) ->
                                    AssistChip(
                                        onClick = {
                                            selectedTemplate = code
                                            if (contentInput.isBlank()) {
                                                contentInput = when (code) {
                                                    "GRATITUDE" -> "1. Hoy agradezco por:\n2. Me hizo sonreír:\n3. Valoro de mí:"
                                                    "REVIEW" -> "• Lo que salió bien hoy:\n• Lo que aprendí:\n• Mi prioridad para mañana:"
                                                    else -> ""
                                                }
                                            }
                                        },
                                        label = { Text(stringResource(labelRes)) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = contentInput,
                                onValueChange = { contentInput = it },
                                label = { Text(stringResource(R.string.journal_write_hint)) },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = tagsInput,
                                onValueChange = { tagsInput = it },
                                label = { Text(stringResource(R.string.journal_tags_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                onClick = {
                                    viewModel.saveEntry(
                                        content = contentInput,
                                        moodScore = selectedMoodScore,
                                        tags = tagsInput,
                                        templateType = selectedTemplate
                                    )
                                    contentInput = ""
                                    tagsInput = ""
                                },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text(stringResource(R.string.action_save))
                            }
                        }
                    }
                }

                if (allEntries.size >= 2) {
                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = stringResource(R.string.journal_mood_chart_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                MoodTrendChart(
                                    scores = allEntries.take(14).map { it.moodScore }.reversed()
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text(stringResource(R.string.journal_search_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = onlyFavorites,
                            onClick = { onlyFavorites = !onlyFavorites },
                            label = { Icon(Icons.Default.Star, contentDescription = null) }
                        )
                    }
                }

                items(filteredEntries, key = { it.id }) { entry ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${entry.mood} • [${entry.templateType}]",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Row {
                                    IconButton(onClick = { viewModel.toggleFavorite(entry) }) {
                                        Icon(
                                            imageVector = if (entry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = null,
                                            tint = if (entry.isFavorite) NahabitCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteEntry(entry) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.action_delete)
                                        )
                                    }
                                }
                            }
                            Text(text = entry.content, style = MaterialTheme.typography.bodyMedium)
                            if (entry.tags.isNotBlank()) {
                                Text(
                                    text = entry.tags,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NahabitEmerald
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPinSetupDialog) {
        var newPin by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            title = { Text(stringResource(R.string.journal_pin_setup)) },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.journal_pin_enter)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updatePin(newPin)
                    isUnlocked = true
                    showPinSetupDialog = false
                }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.updatePin("")
                    showPinSetupDialog = false
                }) {
                    Text(stringResource(R.string.journal_pin_remove))
                }
            }
        )
    }
}

@Composable
private fun MoodTrendChart(scores: List<Int>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        if (scores.size < 2) return@Canvas
        val stepX = size.width / (scores.size - 1).coerceAtLeast(1)
        val path = Path()

        scores.forEachIndexed { index, score ->
            val normalizedY = 1f - ((score.coerceIn(1, 5) - 1) / 4f)
            val x = index * stepX
            val y = normalizedY * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(color = NahabitEmerald, radius = 5.dp.toPx(), center = Offset(x, y))
        }

        drawPath(
            path = path,
            color = NahabitCyan,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
