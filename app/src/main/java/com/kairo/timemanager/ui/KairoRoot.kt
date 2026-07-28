package com.kairo.timemanager.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kairo.timemanager.ui.navigation.Routes
import com.kairo.timemanager.ui.navigation.bottomTabs
import com.kairo.timemanager.ui.screens.chat.CoachScreen
import com.kairo.timemanager.ui.screens.editor.TaskEditorScreen
import com.kairo.timemanager.ui.screens.insights.InsightsScreen
import com.kairo.timemanager.ui.screens.planner.PlannerScreen
import com.kairo.timemanager.ui.screens.rewards.RewardsScreen
import com.kairo.timemanager.ui.screens.settings.SettingsScreen
import com.kairo.timemanager.ui.screens.today.TodayScreen

@Composable
fun KairoRoot(startRoute: String? = null) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(startRoute) {
        if (startRoute != null && bottomTabs.any { it.route == startRoute }) {
            navController.navigate(startRoute) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    val showBottomBar = currentRoute in bottomTabs.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp
                ) {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        val scale by animateFloatAsState(
                            if (selected) 1.12f else 1f,
                            tween(220),
                            label = "tabScale"
                        )
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.scale(scale)
                                )
                            },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.TODAY,
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(140)) }
            ) {
                composable(Routes.TODAY) {
                    TodayScreen(
                        onOpenEditor = { taskId, day -> navController.navigate(Routes.editor(taskId, day)) },
                        onOpenCoach = { navController.navigate(Routes.COACH) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenRewards = { navController.navigate(Routes.REWARDS) }
                    )
                }
                composable(Routes.PLANNER) {
                    PlannerScreen(
                        onOpenEditor = { taskId, day -> navController.navigate(Routes.editor(taskId, day)) }
                    )
                }
                composable(Routes.COACH) {
                    CoachScreen(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
                }
                composable(Routes.INSIGHTS) { InsightsScreen() }
                composable(Routes.REWARDS) { RewardsScreen() }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = "${Routes.EDITOR}?taskId={taskId}&day={day}",
                    arguments = listOf(
                        navArgument("taskId") { type = NavType.LongType; defaultValue = -1L },
                        navArgument("day") { type = NavType.LongType; defaultValue = -1L }
                    )
                ) {
                    TaskEditorScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
