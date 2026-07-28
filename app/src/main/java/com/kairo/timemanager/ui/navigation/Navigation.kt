package com.kairo.timemanager.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val TODAY = "today"
    const val PLANNER = "planner"
    const val COACH = "coach"
    const val INSIGHTS = "insights"
    const val REWARDS = "rewards"
    const val SETTINGS = "settings"
    const val EDITOR = "editor"

    fun editor(taskId: Long? = null, epochDay: Long? = null): String =
        "$EDITOR?taskId=${taskId ?: -1L}&day=${epochDay ?: -1L}"
}

data class TabDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector
)

/** The five tabs in the bottom bar, in order. */
val bottomTabs = listOf(
    TabDestination(Routes.TODAY, "Today", Icons.Filled.Today, Icons.Outlined.Today),
    TabDestination(Routes.PLANNER, "Planner", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    TabDestination(Routes.COACH, "Coach", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    TabDestination(Routes.INSIGHTS, "Insights", Icons.Filled.Insights, Icons.Outlined.Insights),
    TabDestination(Routes.REWARDS, "Rewards", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents)
)
