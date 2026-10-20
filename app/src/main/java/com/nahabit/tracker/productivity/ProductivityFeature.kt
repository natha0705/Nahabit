package com.nahabit.tracker.productivity

import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nahabit.tracker.R
import com.nahabit.tracker.core.database.*
import com.nahabit.tracker.core.notifications.NahabitNotificationHelper
import com.nahabit.tracker.core.time.TimeProvider
import com.nahabit.tracker.core.ui.theme.LocalCardSpacing
import com.nahabit.tracker.core.ui.theme.NahabitAmber
import com.nahabit.tracker.core.ui.theme.NahabitCoral
import com.nahabit.tracker.core.ui.theme.NahabitCyan
import com.nahabit.tracker.core.ui.theme.NahabitEmerald
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductivityViewModel @Inject constructor(
    private val dao: NahabitDao,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val soundGenerator = AmbientSoundGenerator()

    val goals: StateFlow<List<LongTermGoalEntity>> = dao.observeLongTermGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tasks: StateFlow<List<TodoTaskEntity>> = dao.observeTodoTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val focusSessions: StateFlow<List<FocusSessionEntity>> = dao.observeFocusSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val chainedRoutines: StateFlow<List<ChainedRoutineEntity>> = dao.observeChainedRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayEpochDay: Long
        get() = timeProvider.currentLogicalEpochDay()

    fun setAmbientSound(mode: AmbientSoundMode, isTimerRunning: Boolean) {
        if (isTimerRunning && mode != AmbientSoundMode.OFF) {
            soundGenerator.start(mode)
        } else {
            soundGenerator.stop()
        }
    }

    fun recordFocusSession(minutes: Int, tag: String) {
        viewModelScope.launch {
            dao.insertFocusSession(
                FocusSessionEntity(
                    dateEpochDay = todayEpochDay,
                    durationMinutes = minutes,
                    projectTag = tag.trim().ifEmpty { "#General" },
                    completedTimestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun addGoal(title: String, milestonesCsv: String) {
        if (title.isBlank()) return
        val milestonesList = milestonesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val total = milestonesList.size.coerceAtLeast(1)
        viewModelScope.launch {
            dao.upsertLongTermGoal(
                LongTermGoalEntity(
                    title = title.trim(),
                    deadlineEpochDay = todayEpochDay + 30L,
                    milestonesCsv = milestonesList.joinToString(","),
                    completedMilestonesCount = 0,
                    totalMilestonesCount = total
                )
            )
        }
    }

    fun advanceGoalMilestone(goal: LongTermGoalEntity) {
        val next = (goal.completedMilestonesCount + 1).coerceAtMost(goal.totalMilestonesCount)
        viewModelScope.launch {
            dao.upsertLongTermGoal(
                goal.copy(
                    completedMilestonesCount = next,
                    isCompleted = next >= goal.totalMilestonesCount
                )
            )
        }
    }

    fun deleteGoal(goal: LongTermGoalEntity) {
        viewModelScope.launch { dao.deleteLongTermGoal(goal) }
    }

    fun addTask(title: String, subtasks: String, priority: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            dao.upsertTodoTask(
                TodoTaskEntity(
                    title = title.trim(),
                    priority = priority.coerceIn(1, 3),
                    dueEpochDay = todayEpochDay,
                    subtasksText = subtasks.trim()
                )
            )
        }
    }

    fun toggleTaskCompleted(task: TodoTaskEntity) {
        viewModelScope.launch {
            dao.upsertTodoTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteTask(task: TodoTaskEntity) {
        viewModelScope.launch { dao.deleteTodoTask(task) }
    }

    override fun onCleared() {
        soundGenerator.stop()
        super.onCleared()
    }
}

@Composable
fun ProductivityScreen(viewModel: ProductivityViewModel = hiltViewModel()) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val focusSessions by viewModel.focusSessions.collectAsStateWithLifecycle()
    val chains by viewModel.chainedRoutines.collectAsStateWithLifecycle()
    val spacing = LocalCardSpacing.current

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.focus_screen_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold
        )

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.focus_tab_pomodoro)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.focus_tab_goals_tasks)) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(stringResource(R.string.focus_tab_chains_review)) }
            )
        }

        when (selectedTab) {
            0 -> PomodoroFocusTab(
                sessions = focusSessions,
                todayEpochDay = viewModel.todayEpochDay,
                onSoundChange = viewModel::setAmbientSound,
                onSessionCompleted = viewModel::recordFocusSession
            )
            1 -> GoalsAndTasksTab(
                goals = goals,
                tasks = tasks,
                spacing = spacing,
                onAddGoal = viewModel::addGoal,
                onAdvanceGoal = viewModel::advanceGoalMilestone,
                onDeleteGoal = viewModel::deleteGoal,
                onAddTask = viewModel::addTask,
                onToggleTask = viewModel::toggleTaskCompleted,
                onDeleteTask = viewModel::deleteTask
            )
            2 -> ChainedRoutinesAndReviewTab(
                chains = chains,
                spacing = spacing
            )
        }
    }
}

@Composable
private fun PomodoroFocusTab(
    sessions: List<FocusSessionEntity>,
    todayEpochDay: Long,
    onSoundChange: (AmbientSoundMode, Boolean) -> Unit,
    onSessionCompleted: (Int, String) -> Unit
) {
    val context = LocalContext.current
    var selectedMinutes by rememberSaveable { mutableIntStateOf(25) }
    var remainingSeconds by rememberSaveable { mutableIntStateOf(25 * 60) }
    var targetEndRealtime by rememberSaveable { mutableLongStateOf(0L) }
    var isRunning by rememberSaveable { mutableStateOf(false) }
    var projectTag by rememberSaveable { mutableStateOf("#Estudio") }
    var soundMode by rememberSaveable { mutableStateOf(AmbientSoundMode.OFF) }

    val todaySessions = remember(sessions, todayEpochDay) {
        sessions.filter { it.dateEpochDay == todayEpochDay }
    }
    val todayTotalMinutes = remember(todaySessions) {
        todaySessions.sumOf { it.durationMinutes }
    }

    LaunchedEffect(soundMode, isRunning) {
        onSoundChange(soundMode, isRunning)
    }

    LaunchedEffect(isRunning, targetEndRealtime) {
        while (isRunning) {
            val diff = targetEndRealtime - SystemClock.elapsedRealtime()
            if (diff <= 0L) {
                remainingSeconds = 0
                isRunning = false
                onSoundChange(AmbientSoundMode.OFF, false)
                onSessionCompleted(selectedMinutes, projectTag)
                sendFocusCompletedNotification(context, selectedMinutes, projectTag)
                break
            } else {
                remainingSeconds = ((diff + 999L) / 1000L).toInt()
            }
            delay(250L)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.focus_today_minutes, todayTotalMinutes, todaySessions.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "${remainingSeconds / 60}m : ${"%02d".format(remainingSeconds % 60)}s",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 25, 50, 90).forEach { mins ->
                            FilterChip(
                                selected = selectedMinutes == mins,
                                onClick = {
                                    isRunning = false
                                    selectedMinutes = mins
                                    remainingSeconds = mins * 60
                                },
                                label = { Text("${mins}m") }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = projectTag,
                        onValueChange = { projectTag = it },
                        label = { Text(stringResource(R.string.focus_tag_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            AmbientSoundMode.OFF to R.string.focus_sound_off,
                            AmbientSoundMode.RAIN to R.string.focus_sound_rain,
                            AmbientSoundMode.WHITE_NOISE to R.string.focus_sound_white
                        )
                        modes.forEach { (mode, labelRes) ->
                            FilterChip(
                                selected = soundMode == mode,
                                onClick = { soundMode = mode },
                                label = { Text(stringResource(labelRes)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (!isRunning) {
                                val baseSecs = if (remainingSeconds > 0) remainingSeconds else selectedMinutes * 60
                                targetEndRealtime = SystemClock.elapsedRealtime() + (baseSecs * 1000L)
                                isRunning = true
                            } else {
                                isRunning = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(
                            if (isRunning) stringResource(R.string.focus_pause_btn)
                            else stringResource(R.string.focus_start_btn)
                        )
                    }
                }
            }
        }

        items(todaySessions, key = { it.id }) { session ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(session.projectTag, fontWeight = FontWeight.Bold, color = NahabitCyan)
                    Text("+${session.durationMinutes} min", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun GoalsAndTasksTab(
    goals: List<LongTermGoalEntity>,
    tasks: List<TodoTaskEntity>,
    spacing: androidx.compose.ui.unit.Dp,
    onAddGoal: (String, String) -> Unit,
    onAdvanceGoal: (LongTermGoalEntity) -> Unit,
    onDeleteGoal: (LongTermGoalEntity) -> Unit,
    onAddTask: (String, String, Int) -> Unit,
    onToggleTask: (TodoTaskEntity) -> Unit,
    onDeleteTask: (TodoTaskEntity) -> Unit
) {
    var goalTitle by rememberSaveable { mutableStateOf("") }
    var goalMilestones by rememberSaveable { mutableStateOf("") }

    var taskTitle by rememberSaveable { mutableStateOf("") }
    var taskSubtasks by rememberSaveable { mutableStateOf("") }
    var taskPriority by rememberSaveable { mutableIntStateOf(1) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.goals_section_title), fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = goalTitle,
                        onValueChange = { goalTitle = it },
                        label = { Text(stringResource(R.string.goals_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = goalMilestones,
                        onValueChange = { goalMilestones = it },
                        label = { Text(stringResource(R.string.goals_milestones_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onAddGoal(goalTitle, goalMilestones)
                            goalTitle = ""
                            goalMilestones = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.goals_add_btn))
                    }
                }
            }
        }

        items(goals, key = { "goal_${it.id}" }) { goal ->
            val fraction = (goal.completedMilestonesCount.toFloat() / goal.totalMilestonesCount.coerceAtLeast(1)).coerceIn(0f, 1f)
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(goal.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        FilledTonalButton(
                            onClick = { onAdvanceGoal(goal) },
                            enabled = !goal.isCompleted
                        ) {
                            Text("${goal.completedMilestonesCount}/${goal.totalMilestonesCount}")
                        }
                        IconButton(onClick = { onDeleteGoal(goal) }) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    }
                    if (goal.milestonesCsv.isNotBlank()) {
                        Text(
                            text = "Hitos: ${goal.milestonesCsv}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tasks_section_title), fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            1 to R.string.priority_high,
                            2 to R.string.priority_medium,
                            3 to R.string.priority_low
                        ).forEach { (prio, labelRes) ->
                            FilterChip(
                                selected = taskPriority == prio,
                                onClick = { taskPriority = prio },
                                label = { Text(stringResource(labelRes)) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = taskTitle,
                        onValueChange = { taskTitle = it },
                        label = { Text(stringResource(R.string.tasks_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = taskSubtasks,
                        onValueChange = { taskSubtasks = it },
                        label = { Text(stringResource(R.string.tasks_subtasks_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onAddTask(taskTitle, taskSubtasks, taskPriority)
                            taskTitle = ""
                            taskSubtasks = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }

        items(tasks, key = { "task_${it.id}" }) { task ->
            val prioColor = when (task.priority) {
                1 -> NahabitCoral
                2 -> NahabitAmber
                else -> NahabitCyan
            }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = task.isCompleted,
                        onCheckedChange = { onToggleTask(task) }
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(
                            text = task.title,
                            fontWeight = FontWeight.Bold,
                            color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else prioColor
                        )
                        if (task.subtasksText.isNotBlank()) {
                            Text(task.subtasksText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    IconButton(onClick = { onDeleteTask(task) }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChainedRoutinesAndReviewTab(
    chains: List<ChainedRoutineEntity>,
    spacing: androidx.compose.ui.unit.Dp
) {
    var reviewChecks by rememberSaveable { mutableStateOf(listOf(false, false, false, false)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.chains_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(chains, key = { it.id }) { chain ->
            val steps = remember(chain.stepsCsv) {
                chain.stepsCsv.split("|").filter { it.isNotBlank() }
            }
            var currentStepIndex by rememberSaveable(chain.id) { mutableIntStateOf(0) }

            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = chain.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    steps.forEachIndexed { idx, stepText ->
                        val done = idx < currentStepIndex
                        Text(
                            text = if (done) "☑ $stepText" else "☐ $stepText",
                            fontWeight = if (idx == currentStepIndex) FontWeight.Bold else FontWeight.Normal,
                            color = if (done) NahabitEmerald else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { if (currentStepIndex < steps.size) currentStepIndex++ },
                            enabled = currentStepIndex < steps.size,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.chains_step_done))
                        }
                        OutlinedButton(onClick = { currentStepIndex = 0 }) {
                            Text(stringResource(R.string.chains_reset))
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.weekly_guided_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val labels = listOf(
                        R.string.weekly_check_1,
                        R.string.weekly_check_2,
                        R.string.weekly_check_3,
                        R.string.weekly_check_4
                    )
                    labels.forEachIndexed { index, resId ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = reviewChecks[index],
                                onCheckedChange = { checked ->
                                    reviewChecks = reviewChecks.toMutableList().also { it[index] = checked }
                                }
                            )
                            Text(stringResource(resId), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

private fun sendFocusCompletedNotification(context: Context, minutes: Int, tag: String) {
    NahabitNotificationHelper.createNotificationChannels(context)
    val notification = NotificationCompat.Builder(context, NahabitNotificationHelper.CHANNEL_ACHIEVEMENTS)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(context.getString(R.string.focus_complete_notif_title))
        .setContentText(context.getString(R.string.focus_complete_notif_msg, minutes, tag))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(3050, notification)
}
