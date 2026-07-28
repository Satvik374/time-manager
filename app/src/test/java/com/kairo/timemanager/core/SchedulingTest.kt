package com.kairo.timemanager.core

import com.kairo.timemanager.data.local.entity.Recurrence
import com.kairo.timemanager.data.local.entity.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceRulesTest {

    private val monday = LocalDate.of(2026, 3, 9).toEpochDay()
    private val wednesday = monday + 2
    private val saturday = monday + 5

    private fun task(
        recurrence: Int,
        daysMask: Int = 0,
        specificDate: Long? = null,
        startEpochDay: Long = monday,
        endEpochDay: Long? = null,
        archived: Boolean = false
    ) = TaskEntity(
        id = 1,
        title = "Block",
        startMinute = 9 * 60,
        durationMinutes = 60,
        recurrence = recurrence,
        daysMask = daysMask,
        specificDate = specificDate,
        startEpochDay = startEpochDay,
        endEpochDay = endEpochDay,
        archived = archived
    )

    @Test
    fun `a one-off block occurs only on its own date`() {
        val subject = task(Recurrence.ONCE, specificDate = wednesday, startEpochDay = wednesday)
        assertTrue(RecurrenceRules.occursOn(subject, wednesday))
        assertFalse(RecurrenceRules.occursOn(subject, wednesday + 1))
        assertFalse(RecurrenceRules.occursOn(subject, wednesday - 1))
    }

    @Test
    fun `a daily block occurs every day from its start date onward`() {
        val subject = task(Recurrence.DAILY, startEpochDay = wednesday)
        assertFalse(RecurrenceRules.occursOn(subject, wednesday - 1))
        assertTrue(RecurrenceRules.occursOn(subject, wednesday))
        assertTrue(RecurrenceRules.occursOn(subject, wednesday + 30))
    }

    @Test
    fun `a weekly block honours its day mask`() {
        val monWedFri = DayMask.fromNames(listOf("mon", "wed", "fri"))
        val subject = task(Recurrence.WEEKLY, daysMask = monWedFri)
        assertTrue(RecurrenceRules.occursOn(subject, monday))
        assertFalse(RecurrenceRules.occursOn(subject, monday + 1))
        assertTrue(RecurrenceRules.occursOn(subject, wednesday))
        assertFalse(RecurrenceRules.occursOn(subject, saturday))
    }

    @Test
    fun `an end date closes the block out`() {
        val subject = task(Recurrence.DAILY, endEpochDay = wednesday)
        assertTrue(RecurrenceRules.occursOn(subject, wednesday))
        assertFalse(RecurrenceRules.occursOn(subject, wednesday + 1))
    }

    @Test
    fun `an archived block never occurs`() {
        val subject = task(Recurrence.DAILY, archived = true)
        assertFalse(RecurrenceRules.occursOn(subject, monday))
    }

    @Test
    fun `next occurrence finds the following matching weekday`() {
        val subject = task(Recurrence.WEEKLY, daysMask = DayMask.fromNames(listOf("fri")))
        assertEquals(monday + 4, RecurrenceRules.nextOccurrence(subject, monday))
    }

    @Test
    fun `next occurrence is null when the block has already ended`() {
        val subject = task(Recurrence.DAILY, endEpochDay = monday - 1)
        assertNull(RecurrenceRules.nextOccurrence(subject, monday))
    }
}

class DayMaskTest {

    @Test
    fun `parses the vocabulary the AI is told to use`() {
        assertEquals(DayMask.ALL, DayMask.fromNames(listOf("daily")))
        assertEquals(DayMask.WEEKDAYS, DayMask.fromNames(listOf("weekdays")))
        assertEquals(DayMask.WEEKENDS, DayMask.fromNames(listOf("weekends")))
        assertEquals(DayMask.NONE, DayMask.fromNames(emptyList()))
        assertEquals(DayMask.NONE, DayMask.fromNames(null))
    }

    @Test
    fun `parses individual day names and abbreviations`() {
        assertEquals(0b0000001, DayMask.fromNames(listOf("monday")))
        assertEquals(0b0000001, DayMask.fromNames(listOf("Mon")))
        assertEquals(0b1000000, DayMask.fromNames(listOf("sun")))
        assertEquals(0b0010101, DayMask.fromNames(listOf("mon", "wed", "fri")))
    }

    @Test
    fun `ignores names it cannot understand instead of failing`() {
        assertEquals(0b0000001, DayMask.fromNames(listOf("mon", "someday")))
    }

    @Test
    fun `describes masks in human terms`() {
        assertEquals("Every day", DayMask.describe(DayMask.ALL))
        assertEquals("Weekdays", DayMask.describe(DayMask.WEEKDAYS))
        assertEquals("Weekends", DayMask.describe(DayMask.WEEKENDS))
        assertEquals("Mon, Wed, Fri", DayMask.describe(DayMask.fromNames(listOf("mon", "wed", "fri"))))
    }

    @Test
    fun `toggle is its own inverse`() {
        val start = DayMask.fromNames(listOf("tue"))
        assertEquals(start, DayMask.toggle(DayMask.toggle(start, 3), 3))
    }
}

class LevelingTest {

    @Test
    fun `a new user starts at level one`() {
        assertEquals(1, Leveling.levelForXp(0))
        assertEquals(1, Leveling.levelForXp(-50))
    }

    @Test
    fun `level thresholds and inverse agree with each other`() {
        for (level in 1..30) {
            val floor = Leveling.totalXpForLevel(level)
            val ceiling = Leveling.totalXpForLevel(level + 1)
            assertEquals("floor of level $level", level, Leveling.levelForXp(floor))
            assertEquals("just below level ${level + 1}", level, Leveling.levelForXp(ceiling - 1))
            assertEquals("ceiling of level $level", level + 1, Leveling.levelForXp(ceiling))
        }
    }

    @Test
    fun `progress within a level stays normalised`() {
        for (xp in 0..5000 step 37) {
            val info = Leveling.infoFor(xp)
            assertTrue("progress out of range at $xp", info.progress in 0f..1f)
            assertTrue("xpIntoLevel negative at $xp", info.xpIntoLevel >= 0)
            assertTrue("span not positive at $xp", info.xpForNextLevel > 0)
            assertTrue("overflowed the level at $xp", info.xpIntoLevel <= info.xpForNextLevel)
        }
    }

    @Test
    fun `titles are stable and never index out of bounds`() {
        assertEquals(Leveling.titleFor(1), Leveling.titleFor(1))
        assertTrue(Leveling.titleFor(999).isNotBlank())
    }
}

class EconomyTest {

    @Test
    fun `higher priority work is worth more`() {
        assertTrue(Economy.xpForTask(2) > Economy.xpForTask(1))
        assertTrue(Economy.xpForTask(1) > Economy.xpForTask(0))
        assertTrue(Economy.coinsForTask(2) > Economy.coinsForTask(0))
    }

    @Test
    fun `unknown priorities fall back to normal`() {
        assertEquals(Economy.xpForTask(1), Economy.xpForTask(99))
    }

    @Test
    fun `milestone bonuses only pay on milestone days`() {
        assertEquals(0, Economy.streakBonusCoins(0))
        assertEquals(0, Economy.streakBonusCoins(1))
        assertEquals(0, Economy.streakBonusCoins(2))
        assertTrue(Economy.streakBonusCoins(3) > 0)
        assertTrue(Economy.streakBonusCoins(7) > Economy.streakBonusCoins(3))
        assertTrue(Economy.streakBonusCoins(30) > Economy.streakBonusCoins(14))
        assertEquals(0, Economy.streakBonusCoins(8))
    }

    @Test
    fun `freeze tokens are granted weekly`() {
        assertTrue(Economy.grantsFreezeToken(7))
        assertTrue(Economy.grantsFreezeToken(14))
        assertFalse(Economy.grantsFreezeToken(0))
        assertFalse(Economy.grantsFreezeToken(8))
    }
}

class ScoreCopyTest {

    @Test
    fun `grades descend with the completion rate`() {
        assertEquals("Untouched", ScoreCopy.grade(0f))
        assertEquals("Flawless", ScoreCopy.grade(1f))
        assertEquals("Rough", ScoreCopy.grade(0.1f))
        assertEquals("Strong", ScoreCopy.grade(0.85f))
    }

    @Test
    fun `percentages round rather than truncate`() {
        assertEquals("67%", ScoreCopy.percent(2f / 3f))
        assertEquals("100%", ScoreCopy.percent(1f))
    }
}
