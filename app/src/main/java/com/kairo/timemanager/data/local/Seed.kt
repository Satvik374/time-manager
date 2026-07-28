package com.kairo.timemanager.data.local

import com.kairo.timemanager.data.local.entity.AchievementEntity
import com.kairo.timemanager.data.local.entity.RewardEntity

/** Catalogue of achievements. `key` is stable — progress is recomputed against these definitions. */
object AchievementCatalog {

    const val TASKS_10 = "tasks_10"
    const val TASKS_50 = "tasks_50"
    const val TASKS_250 = "tasks_250"
    const val STREAK_3 = "streak_3"
    const val STREAK_7 = "streak_7"
    const val STREAK_30 = "streak_30"
    const val STREAK_100 = "streak_100"
    const val PERFECT_1 = "perfect_1"
    const val PERFECT_10 = "perfect_10"
    const val FOCUS_600 = "focus_600"
    const val FOCUS_3000 = "focus_3000"
    const val LEVEL_5 = "level_5"
    const val LEVEL_10 = "level_10"
    const val COACH_TALK = "coach_talk"
    const val EARLY_BIRD = "early_bird"

    val all: List<AchievementEntity> = listOf(
        AchievementEntity(TASKS_10, "First Ten", "Complete 10 scheduled blocks", "✅", 10, rewardXp = 30, rewardCoins = 20, tier = 0),
        AchievementEntity(TASKS_50, "Half Century", "Complete 50 scheduled blocks", "🏅", 50, rewardXp = 90, rewardCoins = 60, tier = 1),
        AchievementEntity(TASKS_250, "Two Fifty", "Complete 250 scheduled blocks", "🏆", 250, rewardXp = 300, rewardCoins = 200, tier = 2),
        AchievementEntity(STREAK_3, "Warming Up", "Hold a 3-day streak", "🔥", 3, rewardXp = 25, rewardCoins = 15, tier = 0),
        AchievementEntity(STREAK_7, "Week Solid", "Hold a 7-day streak", "🔥", 7, rewardXp = 70, rewardCoins = 50, tier = 1),
        AchievementEntity(STREAK_30, "Month Strong", "Hold a 30-day streak", "💎", 30, rewardXp = 250, rewardCoins = 180, tier = 2),
        AchievementEntity(STREAK_100, "Unbreakable", "Hold a 100-day streak", "👑", 100, rewardXp = 800, rewardCoins = 600, tier = 3),
        AchievementEntity(PERFECT_1, "Clean Sweep", "Finish every block in a day", "🌟", 1, rewardXp = 40, rewardCoins = 25, tier = 0),
        AchievementEntity(PERFECT_10, "Ten Clean Days", "Finish every block on 10 days", "✨", 10, rewardXp = 200, rewardCoins = 150, tier = 2),
        AchievementEntity(FOCUS_600, "Ten Hours Deep", "Log 600 focused minutes", "⏳", 600, rewardXp = 80, rewardCoins = 50, tier = 1),
        AchievementEntity(FOCUS_3000, "Fifty Hours Deep", "Log 3000 focused minutes", "🧠", 3000, rewardXp = 350, rewardCoins = 250, tier = 3),
        AchievementEntity(LEVEL_5, "Level Five", "Reach level 5", "⭐", 5, rewardXp = 0, rewardCoins = 60, tier = 1),
        AchievementEntity(LEVEL_10, "Level Ten", "Reach level 10", "🌠", 10, rewardXp = 0, rewardCoins = 200, tier = 2),
        AchievementEntity(COACH_TALK, "Ask the Coach", "Have your first conversation with the AI coach", "🤖", 1, rewardXp = 20, rewardCoins = 15, tier = 0),
        AchievementEntity(EARLY_BIRD, "Early Bird", "Complete a block that starts before 7:00 AM", "🌅", 1, rewardXp = 30, rewardCoins = 20, tier = 0)
    )
}

object RewardCatalog {
    val defaults: List<RewardEntity> = listOf(
        RewardEntity(title = "Coffee break", description = "A proper unhurried one", emoji = "☕", cost = 40),
        RewardEntity(title = "One episode", description = "Guilt-free screen time", emoji = "📺", cost = 90),
        RewardEntity(title = "Gaming hour", description = "60 minutes, no timer anxiety", emoji = "🎮", cost = 150),
        RewardEntity(title = "Takeout night", description = "Someone else cooks", emoji = "🍜", cost = 260),
        RewardEntity(title = "Guilt-free lie-in", description = "Skip the alarm tomorrow", emoji = "😴", cost = 320),
        RewardEntity(title = "Day off", description = "A full planned rest day", emoji = "🏝️", cost = 600)
    )
}
