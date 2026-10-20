package com.nahabit.tracker.habits

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nahabit.tracker.R
import com.nahabit.tracker.core.ui.UiState
import com.nahabit.tracker.core.ui.components.CelebrationConfettiBurst
import com.nahabit.tracker.core.ui.components.DualProgressRings
import com.nahabit.tracker.core.ui.components.SwipeableHabitCard
import com.nahabit.tracker.core.ui.theme.LocalCardSpacing
import com.nahabit.tracker.core.ui.theme.NahabitCyan
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayDashboardScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: HabitsViewModel = hiltViewModel()
) {
    val habitsState by viewModel.uiState.collectAsStateWithLifecycle()
    val waterState by viewModel.waterState.collectAsStateWithLifecycle()
    val prefs by viewModel.userPreferences.collectAsStateWithLifecycle()

    val haptics = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 600
    val spacing = LocalCardSpacing.current

    val currentHour = remember { LocalTime.now().hour }
    val greetingRes = when (currentHour) {
        in 5..11 -> R.string.greeting_morning
        in 12..18 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }

    val quoteRes = remember {
        listOf(R.string.today_quote_1, R.string.today_quote_2, R.string.today_quote_3).random()
    }

    val habitsList = (habitsState as? UiState.Success)?.data.orEmpty()
    val completedHabitsCount = habitsList.count { it.completedToday }
    val habitsProgress = if (habitsList.isEmpty()) 0f else completedHabitsCount.toFloat() / habitsList.size.toFloat()
    val waterProgress = if (waterState.targetGlasses <= 0) 0f else waterState.glassesCount.toFloat() / waterState.targetGlasses.toFloat()
    val allDone = habitsList.isNotEmpty() && completedHabitsCount == habitsList.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(greetingRes),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = stringResource(R.string.today_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isWideScreen) {
            Row(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    SummaryHeroCard(
                        habitsProgress = habitsProgress,
                        waterProgress = waterProgress,
                        completedCount = completedHabitsCount,
                        totalCount = habitsList.size,
                        quoteRes = quoteRes
                    )
                    WaterQuickCard(
                        glasses = waterState.glassesCount,
                        target = waterState.targetGlasses,
                        onAdd = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.addWaterGlass(1)
                        },
                        onRemove = { viewModel.addWaterGlass(-1) }
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1.2f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    item {
                        CelebrationConfettiBurst(visible = allDone)
                    }
                    items(habitsList, key = { it.definition.id }) { item ->
                        SwipeableHabitCard(
                            item = item,
                            onIncrement = { viewModel.incrementToday(item) }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(spacing),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (!prefs.hasCompletedOnboarding && habitsList.isEmpty()) {
                    item {
                        OnboardingWelcomeCard(
                            onSetupStarterHabits = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.seedStarterExperience()
                            }
                        )
                    }
                }

                item {
                    SummaryHeroCard(
                        habitsProgress = habitsProgress,
                        waterProgress = waterProgress,
                        completedCount = completedHabitsCount,
                        totalCount = habitsList.size,
                        quoteRes = quoteRes
                    )
                }

                item {
                    AnimatedVisibility(visible = allDone) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CelebrationConfettiBurst(visible = true)
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.today_all_done_banner),
                                    modifier = Modifier.padding(14.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                item {
                    WaterQuickCard(
                        glasses = waterState.glassesCount,
                        target = waterState.targetGlasses,
                        onAdd = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.addWaterGlass(1)
                        },
                        onRemove = { viewModel.addWaterGlass(-1) }
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.today_swipe_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(habitsList, key = { it.definition.id }) { item ->
                    SwipeableHabitCard(
                        item = item,
                        onIncrement = { viewModel.incrementToday(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryHeroCard(
    habitsProgress: Float,
    waterProgress: Float,
    completedCount: Int,
    totalCount: Int,
    quoteRes: Int
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            DualProgressRings(
                habitsProgress = habitsProgress,
                waterProgress = waterProgress,
                ringSize = 128.dp
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${stringResource(R.string.today_ring_habits)}: $completedCount / $totalCount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.today_quote_title),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "“${stringResource(quoteRes)}”",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun WaterQuickCard(
    glasses: Int,
    target: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = null,
                    tint = NahabitCyan
                )
                Text(
                    text = stringResource(R.string.today_water_title, glasses, target),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRemove,
                    enabled = glasses > 0,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.today_water_remove))
                }
                Button(
                    onClick = onAdd,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.today_water_add))
                }
            }
        }
    }
}

@Composable
private fun OnboardingWelcomeCard(onSetupStarterHabits: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                text = stringResource(R.string.onboarding_desc),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = onSetupStarterHabits,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            ) {
                Text(stringResource(R.string.onboarding_cta))
            }
        }
    }
}
