package com.kairo.timemanager.core

/** The streak fields as they exist before and after an evaluation pass. */
data class StreakState(
    val streak: Int,
    val longest: Int,
    val freezeTokens: Int,
    val lastStreakDay: Long
)

sealed interface StreakEvent {
    data class Extended(val streak: Int, val bonusCoins: Int, val grantedToken: Boolean) : StreakEvent
    data class Frozen(val streak: Int, val tokensLeft: Int) : StreakEvent
    data class Lost(val previous: Int) : StreakEvent
}

data class StreakOutcome(
    val state: StreakState,
    val events: List<StreakEvent>
) {
    val bonusCoins: Int
        get() = events.filterIsInstance<StreakEvent.Extended>().sumOf { it.bonusCoins }
}

/** What a given day looked like, as far as the streak is concerned. */
data class DayOutcome(val planned: Int, val completionRate: Float)

/**
 * Pure streak arithmetic, separated from storage so the rules can be tested directly.
 *
 * Rules, in order of precedence for each unscored day from `lastStreakDay + 1` through `today`:
 *  1. A day with nothing scheduled is neutral — a genuine rest day never breaks a streak, and
 *     never extends one either.
 *  2. A day that clears [Economy.STREAK_COMPLETION_THRESHOLD] extends the streak and may pay a
 *     milestone bonus.
 *  3. A missed day in the past burns a freeze token if one is available, preserving the streak.
 *  4. Otherwise a missed past day resets the streak to zero.
 *  5. Today is never judged as missed — it is still winnable until it becomes the past.
 */
object StreakEngine {

    /** Ceiling on how many days a single pass will replay, in case the device clock jumps. */
    const val MAX_REPLAY_DAYS = 400

    fun evaluate(
        initial: StreakState,
        today: Long,
        outcomeFor: (Long) -> DayOutcome
    ): StreakOutcome {
        var streak = initial.streak
        var longest = initial.longest
        var tokens = initial.freezeTokens
        var lastDay = if (initial.lastStreakDay < 0) today - 1 else initial.lastStreakDay
        if (lastDay > today) lastDay = today

        val events = mutableListOf<StreakEvent>()
        var day = maxOf(lastDay + 1, today - MAX_REPLAY_DAYS)

        while (day <= today) {
            val outcome = outcomeFor(day)
            when {
                outcome.planned == 0 -> Unit

                outcome.completionRate >= Economy.STREAK_COMPLETION_THRESHOLD -> {
                    streak += 1
                    lastDay = day
                    longest = maxOf(longest, streak)
                    val bonus = Economy.streakBonusCoins(streak)
                    val token = Economy.grantsFreezeToken(streak)
                    if (token) tokens = (tokens + 1).coerceAtMost(Economy.MAX_FREEZE_TOKENS)
                    events += StreakEvent.Extended(streak, bonus, token)
                }

                day < today && streak > 0 && tokens > 0 -> {
                    tokens -= 1
                    lastDay = day
                    events += StreakEvent.Frozen(streak, tokens)
                }

                day < today -> {
                    if (streak > 0) events += StreakEvent.Lost(streak)
                    streak = 0
                    lastDay = day
                }

                else -> Unit
            }
            day++
        }

        return StreakOutcome(StreakState(streak, longest, tokens, lastDay), events)
    }
}
