package com.kairo.timemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimeUtilsTest {

    @Test
    fun `parses the time formats a language model actually emits`() {
        assertEquals(7 * 60 + 30, TimeUtils.parseTimeToMinuteOrNull("07:30"))
        assertEquals(7 * 60 + 30, TimeUtils.parseTimeToMinuteOrNull("7:30"))
        assertEquals(7 * 60 + 30, TimeUtils.parseTimeToMinuteOrNull("7:30 am"))
        assertEquals(19 * 60 + 5, TimeUtils.parseTimeToMinuteOrNull("19:05"))
        assertEquals(19 * 60, TimeUtils.parseTimeToMinuteOrNull("7pm"))
        assertEquals(19 * 60 + 30, TimeUtils.parseTimeToMinuteOrNull("7:30 PM"))
        assertEquals(7 * 60 + 30, TimeUtils.parseTimeToMinuteOrNull("0730"))
        assertEquals(0, TimeUtils.parseTimeToMinuteOrNull("12:00 am"))
        assertEquals(12 * 60, TimeUtils.parseTimeToMinuteOrNull("12:00 pm"))
        assertEquals(9 * 60, TimeUtils.parseTimeToMinuteOrNull(" 9 "))
    }

    @Test
    fun `rejects nonsense rather than guessing`() {
        assertNull(TimeUtils.parseTimeToMinuteOrNull(null))
        assertNull(TimeUtils.parseTimeToMinuteOrNull(""))
        assertNull(TimeUtils.parseTimeToMinuteOrNull("later"))
        assertNull(TimeUtils.parseTimeToMinuteOrNull("25:00"))
        assertNull(TimeUtils.parseTimeToMinuteOrNull("10:75"))
    }

    @Test
    fun `parses ISO dates and rejects malformed ones`() {
        assertEquals(LocalDate.of(2026, 3, 14).toEpochDay(), TimeUtils.parseIsoDateOrNull("2026-03-14"))
        assertEquals(LocalDate.of(2026, 3, 14).toEpochDay(), TimeUtils.parseIsoDateOrNull(" 2026-03-14 "))
        assertNull(TimeUtils.parseIsoDateOrNull("14/03/2026"))
        assertNull(TimeUtils.parseIsoDateOrNull("tomorrow"))
        assertNull(TimeUtils.parseIsoDateOrNull(null))
    }

    @Test
    fun `formats times in both clock styles`() {
        assertEquals("07:30", TimeUtils.formatTime(7 * 60 + 30, use24h = true))
        assertEquals("7:30 AM", TimeUtils.formatTime(7 * 60 + 30).uppercase())
        assertEquals("00:00", TimeUtils.formatTime(0, use24h = true))
        assertEquals("23:59", TimeUtils.formatTime(23 * 60 + 59, use24h = true))
    }

    @Test
    fun `formats durations without redundant units`() {
        assertEquals("45m", TimeUtils.formatDuration(45))
        assertEquals("1h", TimeUtils.formatDuration(60))
        assertEquals("1h 30m", TimeUtils.formatDuration(90))
        assertEquals("0m", TimeUtils.formatDuration(0))
    }

    @Test
    fun `week boundaries run Monday to Sunday`() {
        val wednesday = LocalDate.of(2026, 3, 11).toEpochDay()
        assertEquals(LocalDate.of(2026, 3, 9).toEpochDay(), TimeUtils.startOfWeek(wednesday))
        assertEquals(LocalDate.of(2026, 3, 15).toEpochDay(), TimeUtils.endOfWeek(wednesday))
        val monday = LocalDate.of(2026, 3, 9).toEpochDay()
        assertEquals(monday, TimeUtils.startOfWeek(monday))
    }

    @Test
    fun `month boundaries cover the whole month`() {
        val mid = LocalDate.of(2026, 2, 14).toEpochDay()
        assertEquals(LocalDate.of(2026, 2, 1).toEpochDay(), TimeUtils.startOfMonth(mid))
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), TimeUtils.endOfMonth(mid))
    }

    @Test
    fun `day bit maps Monday to bit zero`() {
        val monday = LocalDate.of(2026, 3, 9).toEpochDay()
        assertEquals(1, TimeUtils.dayBit(monday))
        assertEquals(1 shl 6, TimeUtils.dayBit(monday + 6))
    }

    @Test
    fun `greeting changes across the day`() {
        assertTrue(TimeUtils.greeting(8 * 60).contains("morning"))
        assertTrue(TimeUtils.greeting(14 * 60).contains("afternoon"))
        assertTrue(TimeUtils.greeting(19 * 60).contains("evening"))
    }
}
