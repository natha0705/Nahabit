package com.nahabit.tracker.core.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nahabit.tracker.R
import com.nahabit.tracker.abstinence.AbstinenceScreen
import com.nahabit.tracker.body.BodyScreen
import com.nahabit.tracker.habits.HabitsScreen
import com.nahabit.tracker.habits.TodayDashboardScreen
import com.nahabit.tracker.journal.JournalScreen
import com.nahabit.tracker.productivity.ProductivityScreen
import com.nahabit.tracker.settings.SettingsScreen
import com.nahabit.tracker.stats.StatsScreen
import com.nahabit.tracker.workout.WorkoutScreen
import kotlinx.serialization.Serializable

@Serializable object TodayRoute
@Serializable object HabitsRoute
@Serializable object FocusRoute
@Serializable object WorkoutRoute
@Serializable object BodyRoute
@Serializable object JournalRoute
@Serializable object AbstinenceRoute
@Serializable object StatsRoute
@Serializable object SettingsRoute

data class TopLevelDestination<T : Any>(
    val route: T,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NahabitScaffold(shortcutDestination: String? = null) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    LaunchedEffect(shortcutDestination) {
        when (shortcutDestination) {
            "WORKOUT" -> navController.navigate(WorkoutRoute) { launchSingleTop = true }
            "STATS" -> navController.navigate(StatsRoute) { launchSingleTop = true }
            "TODAY" -> navController.navigate(TodayRoute) { launchSingleTop = true }
        }
    }

    val bottomDestinations = listOf(
        TopLevelDestination(TodayRoute, R.string.nav_today, Icons.Default.WbSunny),
        TopLevelDestination(HabitsRoute, R.string.nav_habits, Icons.Default.CheckCircle),
        TopLevelDestination(FocusRoute, R.string.nav_focus, Icons.Default.Timer),
        TopLevelDestination(WorkoutRoute, R.string.nav_workout, Icons.Default.FitnessCenter),
        TopLevelDestination(StatsRoute, R.string.nav_stats, Icons.Default.BarChart)
    )

    val secondaryDestinations = listOf(
        TopLevelDestination(JournalRoute, R.string.nav_journal, Icons.Default.MenuBook),
        TopLevelDestination(AbstinenceRoute, R.string.nav_abstinence, Icons.Default.Shield),
        TopLevelDestination(BodyRoute, R.string.nav_body, Icons.Default.MonitorWeight),
        TopLevelDestination(SettingsRoute, R.string.nav_settings, Icons.Default.Settings)
    )

    Scaffold(
        topBar = {
            ScrollableTabRow(selectedTabIndex = 0) {
                secondaryDestinations.forEach { dest ->
                    val selected = currentDestination?.hasRoute(dest.route::class) == true
                    Tab(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                launchSingleTop = true
                            }
                        },
                        text = { Text(stringResource(dest.labelRes)) },
                        icon = { Icon(dest.icon, contentDescription = null) }
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                bottomDestinations.forEach { dest ->
                    val selected = currentDestination?.hasRoute(dest.route::class) == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                        label = { Text(stringResource(dest.labelRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = TodayRoute,
                enterTransition = { fadeIn(animationSpec = tween(220)) },
                exitTransition = { fadeOut(animationSpec = tween(180)) }
            ) {
                composable<TodayRoute> {
                    TodayDashboardScreen(
                        onNavigateToSettings = { navController.navigate(SettingsRoute) }
                    )
                }
                composable<HabitsRoute> { HabitsScreen() }
                composable<FocusRoute> { ProductivityScreen() }
                composable<WorkoutRoute> { WorkoutScreen() }
                composable<BodyRoute> { BodyScreen() }
                composable<JournalRoute> { JournalScreen() }
                composable<AbstinenceRoute> { AbstinenceScreen() }
                composable<StatsRoute> { StatsScreen() }
                composable<SettingsRoute> { SettingsScreen() }
            }
        }
    }
}
