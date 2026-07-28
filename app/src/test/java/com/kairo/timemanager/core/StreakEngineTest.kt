package com.kairo.timemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakEngineTest {

    private val today = 20_000L

    private fun state(
        streak: Int = 0,
        longest: Int = 0,
        tokens: Int = 0,
        lastDay: Long = -1L
    ) = StreakState(streak, longest, tokens, lastDay)

    private fun outcomes(vararg pairs: Pair<Long, DayOutcome>): (Long) -> DayOutcome {
        val map = pairs.toMap()
        return { map[it] ?: DayOutcome(0, 0f) }
    }

    private fun done(rate: Float = 1f, planned: Int = 4) = DayOutcome(planned, rate)

    private val nothingScheduled = DayOutcome(0, 0f)

    @Test
    fun `a fully completed today starts a streak at one`() {
        val result = StreakEngine.evaluate(
            initial = state(),
            today = today,
            outcomeFor = outcomes(today to done())
        )
        assertEquals(1, result.state.streak)
        assertEquals(1, result.state.longest)
        assertEquals(today, result.state.lastStreakDay)
    }

    @Test
    fun `an unfinished today never breaks the streak because it is still winnable`() {
        val result = StreakEngine.evaluate(
            initial = state(streak = 5, longest = 5, lastDay = today - 1),
            today = today,
            outcomeFor = outcomes(today to done(rate = 0.1f))
        )
        assertEquals(5, result.state.streak)
        assertTrue(result.events.isEmpty())
    }

    @Test
    fun `a day scoring below the threshold in the past resets the streak`() {
        val result = StreakEngine.evaluate(
            initial = state(streak = 9, longest = 9, lastDay = today - 2),
            today = today,
            outcomeFor = outcomes(
                today - 1 to done(rate = 0.25f),
                today to nothingScheduled
            )
        )
        assertEquals(0, result.state.streak)
        assertEquals(9, result.state.longest)
        assertEquals(listOf(StreakEvent.Lost(9)), result.events)
    }

    @Test
    fun `exactly meeting the threshold counts as a qualifying day`() {
        val result = StreakEngine.evaluate(
            initial = state(lastDay = today - 1),
            today = today,
            outcomeFor = outcomes(today to DayOutcome(5, Economy.STREAK_COMPLETION_THRESHOLD))
        )
        assertEquals(1, result.state.streak)
    }

    @Test
    fun `a rest day with nothing scheduled is neutral`() {
        val result = StreakEngine.evaluate(
            initial = state(streak = 4, longest = 4, lastDay = today - 3),
            today = today,
            outcomeFor = outcomes(
                today - 2 to nothingScheduled,
                today - 1 to nothingScheduled,
                today to done()
            )
        )
        assertEquals(5, result.state.streak)
        assertTrue(result.events.none { it is StreakEvent.Lost })
    }

    @Test
    fun `a freeze token absorbs one missed day and is consumed`() {
        val result = StreakEngine.evaluate(
            initial = state(streak = 6, longest = 6, tokens = 1, lastDay = today - 2),
            today = today,
            outcomeFor = outcomes(
                today - 1 to done(rate = 0f),
                today to nothingScheduled
            )
        )
        assertEquals(6, result.state.streak)
        assertEquals(0, result.state.freezeTokens)
        assertEquals(listOf(StreakEvent.Frozen(6, 0)), result.events)
    }

    @Test
    fun `two missed days with one token still breaks the streak`() {
        val result = StreakEngine.evaluate(
            initial = state(streak = 6, longest = 6, tokens = 1, lastDay = today - 3),
            today = today,
            outcomeFor = outcomes(
                today - 2 to done(rate = 0f),
                today - 1 to done(rate = 0f),
                today to nothingScheduled
            )
        )
        assertEquals(0, result.state.streak)
        assertEquals(0, result.state.freezeTokens)
        assertTrue(result.events.any { it is StreakEvent.Frozen })
        assertTrue(result.events.any { it is StreakEvent.Lost })
    }

    @Test
    fun `a seventh day pays a milestone bonus and hands back a freeze token`() {
        val result = StreakEngine.evaluate(
            initial = state(streak = 6, longest = 6, tokens = 0, lastDay = today - 1),
            today = today,
            outcomeFor = outcomes(today to done())
        )
        val extended = result.events.filterIsInstance<StreakEvent.Extended>().single()
        assertEquals(7, extended.streak)
        assertEquals(Economy.streakBonusCoins(7), extended.bonusCoins)
        assertTrue(extended.grantedToken)
        assertEquals(1, result.state.freezeTokens)
        assertEquals(Economy.streakBonusCoins(7), result.bonusCoins)
    }

    @Test
    fun `freeze tokens never exceed the cap`() {
        val result = StreakEngine.evaluate(
            initial = state(streak = 6, longest = 6, tokens = Economy.MAX_FREEZE_TOKENS, lastDay = today - 1),
            today = today,
            outcomeFor = outcomes(today to done())
        )
        assertEquals(Economy.MAX_FREEZE_TOKENS, result.state.freezeTokens)
    }

    @Test
    fun `re-running the same evaluation is idempotent`() {
        val first = StreakEngine.evaluate(
            initial = state(streak = 2, longest = 2, lastDay = today - 1),
            today = today,
            outcomeFor = outcomes(today to done())
        )
        val second = StreakEngine.evaluate(
            initial = first.state,
            today = today,
            outcomeFor = outcomes(today to done())
        )
        assertEquals(3, first.state.streak)
        assertEquals(first.state, second.state)
        assertTrue(second.events.isEmpty())
    }

    @Test
    fun `a clock jump into the far future replays at most the capped window`() {
        var reads = 0
        val result = StreakEngine.evaluate(
            initial = state(streak = 1, longest = 1, lastDay = 0L),
            today = today,
            outcomeFor = {
                reads++
                nothingScheduled
            }
        )
        assertTrue("replayed $reads days", reads <= StreakEngine.MAX_REPLAY_DAYS + 1)
        assertEquals(1, result.state.streak)
    }

    @Test
    fun `a first run with no history does not punish the user for the past`() {
        val result = StreakEngine.evaluate(
            initial = state(lastDay = -1L),
            today = today,
            outcomeFor = outcomes(today to done(rate = 0f))
        )
        assertEquals(0, result.state.streak)
        assertTrue(result.events.isEmpty())
    }
}
