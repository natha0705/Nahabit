package com.nahabit.tracker.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.nahabit.tracker.core.database.ChallengeEntity
import com.nahabit.tracker.core.database.NahabitDao
import com.nahabit.tracker.core.datastore.UserPreferencesRepository
import com.nahabit.tracker.core.time.TimeProvider
import com.nahabit.tracker.core.ui.UiState
import com.nahabit.tracker.core.ui.theme.LocalCardSpacing
import com.nahabit.tracker.core.ui.theme.NahabitAmber
import com.nahabit.tracker.core.ui.theme.NahabitEmerald
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val dao: NahabitDao,
    private val prefsRepo: UserPreferencesRepository,
    private val timeProvider: TimeProvider,
    private val calculateStatsUseCase: CalculateStatsAndGamificationUseCase
) : ViewModel() {

    val uiState: StateFlow<UiState<StatsDashboardModel>> = combine(
        prefsRepo.preferencesFlow,
        dao.observeActiveHabitDefinitions(),
        dao.observeAllHabitLogs(),
        dao.observeWorkoutSessions(),
        combine(
            dao.observeJournalEntries(),
            dao.observeAllWaterLogs(),
            dao.observeChallenges(),
            ::Triple
        )
    ) { prefs, habits, logs, workouts, extraTriple ->
        val (journals, waters, challenges) = extraTriple
        val today = timeProvider.currentLogicalEpochDay(prefs.dayResetHour)
        val model = calculateStatsUseCase(
            todayEpochDay = today,
            habits = habits,
            logs = logs,
            workouts = workouts,
            journals = journals,
            waters = waters,
            challenges = challenges
        )
        UiState.Success(model) as UiState<StatsDashboardModel>
    }.catch {
        emit(UiState.Error(R.string.state_error_generic, it.localizedMessage))
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState.Loading
    )

    fun startChallenge(days: Int) {
        viewModelScope.launch {
            val today = timeProvider.currentLogicalEpochDay()
            dao.upsertChallenge(
                ChallengeEntity(
                    title = "Reto $days Días de Disciplina",
                    durationDays = days,
                    startEpochDay = today
                )
            )
        }
    }
}

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val spacing = LocalCardSpacing.current

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
            val data = currentState.data
            val unlockedCount = data.badges.count { it.isUnlocked }
            val deltaPrefix = if (data.monthOverMonthDeltaPercent >= 0) "+" else ""

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(spacing),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.stats_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold
                        )
                        FilledTonalButton(
                            onClick = { ShareableSummaryCardExporter.shareSummaryCard(context, data) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.stats_share_button))
                        }
                    }
                }

                if (data.showCompassionMessage) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = NahabitEmerald)
                                Text(
                                    text = stringResource(R.string.stats_compassion_msg),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.stats_level_format, data.level, data.totalXp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Text(stringResource(R.string.stats_wellness_score, data.wellnessScoreToday))
                                    }
                                )
                            }
                            LinearProgressIndicator(
                                progress = { data.levelProgress },
                                modifier = Modifier.fillMaxWidth().height(10.dp)
                            )
                            Text(
                                text = stringResource(R.string.stats_xp_next, data.xpForNextLevel, data.level + 1),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.stats_period_week, data.weeklyRatePercent), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.stats_period_month, data.monthlyRatePercent), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.stats_period_year, data.yearlyRatePercent), fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider()
                            Text(
                                text = stringResource(R.string.stats_month_comparison, deltaPrefix, data.monthOverMonthDeltaPercent),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(stringResource(R.string.stats_best_worst_days, data.bestDayName, data.worstDayName))
                            Text(stringResource(R.string.stats_most_consistent, data.mostConsistentHabit))
                            Text(stringResource(R.string.stats_least_consistent, data.leastConsistentHabit))
                        }
                    }
                }

                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.stats_weekly_bars_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            AnimatedWeeklyBarChart(rates = data.dayOfWeekRates)
                        }
                    }
                }

                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.stats_heatmap_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            ContributionHeatmap90Days(intensities = data.last90DaysHeatmap)
                        }
                    }
                }

                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.stats_badges_title, unlockedCount, data.badges.size),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(data.badges, key = { it.id }) { badge ->
                                    FilterChip(
                                        selected = badge.isUnlocked,
                                        onClick = {},
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (badge.isUnlocked) Icons.Default.EmojiEvents else Icons.Default.WorkspacePremium,
                                                contentDescription = null,
                                                tint = if (badge.isUnlocked) NahabitAmber else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        label = { Text(stringResource(badge.titleRes)) }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.stats_challenges_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(listOf(7, 21, 30, 66, 100)) { days ->
                                    AssistChip(
                                        onClick = { viewModel.startChallenge(days) },
                                        label = { Text(stringResource(R.string.stats_start_challenge, days)) }
                                    )
                                }
                            }

                            data.activeChallenges.forEach { ch ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "${ch.entity.title}: ${ch.elapsedDays}/${ch.entity.durationDays}d",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    LinearProgressIndicator(
                                        progress = { ch.progressFraction },
                                        modifier = Modifier.fillMaxWidth()
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
