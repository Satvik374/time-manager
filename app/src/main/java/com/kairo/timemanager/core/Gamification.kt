package com.kairo.timemanager.core

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Central place for every number that drives XP, coins, levels and streak bonuses so the
 * economy can be tuned in one file instead of scattered across screens.
 */
object Economy {
    const val XP_TASK_LOW = 6
    const val XP_TASK_NORMAL = 10
    const val XP_TASK_HIGH = 16
    const val XP_CHECKLIST_ITEM = 2
    const val XP_PERFECT_DAY = 45

    const val COINS_TASK_LOW = 3
    const val COINS_TASK_NORMAL = 5
    const val COINS_TASK_HIGH = 8
    const val COINS_PERFECT_DAY = 25

    /** A day only counts toward the streak once this share of planned work is done. */
    const val STREAK_COMPLETION_THRESHOLD = 0.6f

    const val MAX_FREEZE_TOKENS = 3
    const val FREEZE_TOKEN_COST = 120

    fun xpForTask(priority: Int): Int = when (priority) {
        0 -> XP_TASK_LOW
        2 -> XP_TASK_HIGH
        else -> XP_TASK_NORMAL
    }

    fun coinsForTask(priority: Int): Int = when (priority) {
        0 -> COINS_TASK_LOW
        2 -> COINS_TASK_HIGH
        else -> COINS_TASK_NORMAL
    }

    /** Streak milestones award a coin bonus, and every 7th day also hands back a freeze token. */
    fun streakBonusCoins(streak: Int): Int = when {
        streak <= 0 -> 0
        streak % 100 == 0 -> 500
        streak % 30 == 0 -> 200
        streak % 14 == 0 -> 90
        streak % 7 == 0 -> 40
        streak % 3 == 0 -> 12
        else -> 0
    }

    fun grantsFreezeToken(streak: Int): Boolean = streak > 0 && streak % 7 == 0
}

data class LevelInfo(
    val level: Int,
    val title: String,
    val xpIntoLevel: Int,
    val xpForNextLevel: Int,
    val progress: Float
)

/** Level curve: total XP needed for level L is 60 * L * (L - 1) / 2, i.e. gently super-linear. */
object Leveling {

    private val titles = listOf(
        "Drifter", "Starter", "Planner", "Builder", "Focused", "Consistent",
        "Disciplined", "Momentum", "Relentless", "Architect", "Master of Hours"
    )

    fun totalXpForLevel(level: Int): Int {
        if (level <= 1) return 0
        return 30 * (level - 1) * level
    }

    fun levelForXp(xp: Int): Int {
        if (xp <= 0) return 1
        // invert 30 * (L-1) * L  <=  xp
        val l = ((1 + sqrt(1.0 + 4.0 * xp / 30.0)) / 2.0).toInt()
        return l.coerceAtLeast(1)
    }

    fun titleFor(level: Int): String = titles[(level - 1).coerceIn(0, titles.lastIndex)]

    fun infoFor(xp: Int): LevelInfo {
        val level = levelForXp(xp)
        val floor = totalXpForLevel(level)
        val ceiling = totalXpForLevel(level + 1)
        val span = (ceiling - floor).coerceAtLeast(1)
        val into = (xp - floor).coerceAtLeast(0)
        return LevelInfo(
            level = level,
            title = titleFor(level),
            xpIntoLevel = into,
            xpForNextLevel = span,
            progress = (into.toFloat() / span).coerceIn(0f, 1f)
        )
    }
}

/** Turns a raw completion ratio into the copy shown on cards and used in coach prompts. */
object ScoreCopy {
    fun grade(rate: Float): String = when {
        rate >= 0.95f -> "Flawless"
        rate >= 0.8f -> "Strong"
        rate >= 0.6f -> "Solid"
        rate >= 0.35f -> "Patchy"
        rate > 0f -> "Rough"
        else -> "Untouched"
    }

    fun percent(rate: Float): String = "${(rate * 100).roundToInt()}%"

    /**
     * The verdict shown against a single day, judged only on the blocks the clock has already
     * asked for. An evening gym session is not a block you have failed at three in the afternoon,
     * so a day still running is graded on what is due — the same reason analytics only score
     * elapsed days. [due] equals [planned] for any day that is over.
     */
    fun dayGrade(completed: Int, due: Int, planned: Int): String = when {
        planned == 0 -> "Rest day"
        due == 0 -> if (completed > 0) "Ahead" else "Not due yet"
        // Say "so far" while blocks are still to come, so a verdict on half a day never reads
        // as a verdict on the whole one.
        due < planned -> "${grade(completed.toFloat() / due)} so far"
        else -> grade(completed.toFloat() / due)
    }

    /** Rate to colour a day's ring by — pairs with [dayGrade], not with raw progress. */
    fun dayGradeRate(completed: Int, due: Int, planned: Int): Float = when {
        planned == 0 || due == 0 -> 1f
        else -> completed.toFloat() / due
    }
}
