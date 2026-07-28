package com.kairo.timemanager.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Everything in the app keys off `epochDay` for a calendar day and `minuteOfDay` for a clock time. */
object TimeUtils {

    val zone: ZoneId get() = ZoneId.systemDefault()

    fun today(): LocalDate = LocalDate.now(zone)

    fun todayEpochDay(): Long = today().toEpochDay()

    fun nowMinuteOfDay(): Int = LocalTime.now(zone).let { it.hour * 60 + it.minute }

    fun date(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    fun epochMillisOf(epochDay: Long, minuteOfDay: Int): Long =
        LocalDateTime.of(date(epochDay), minuteToLocalTime(minuteOfDay))
            .atZone(zone).toInstant().toEpochMilli()

    fun minuteToLocalTime(minuteOfDay: Int): LocalTime =
        LocalTime.of((minuteOfDay / 60).coerceIn(0, 23), minuteOfDay % 60)

    /** "7:30 AM" style, respecting nothing fancy — stable across locales for a compact schedule grid. */
    fun formatTime(minuteOfDay: Int, use24h: Boolean = false): String {
        val t = minuteToLocalTime(minuteOfDay.coerceIn(0, 24 * 60 - 1))
        return if (use24h) String.format("%02d:%02d", t.hour, t.minute)
        else t.format(DateTimeFormatter.ofPattern("h:mm a"))
    }

    fun formatRange(startMinute: Int, durationMinutes: Int, use24h: Boolean = false): String =
        "${formatTime(startMinute, use24h)} – ${formatTime((startMinute + durationMinutes) % (24 * 60), use24h)}"

    fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    fun formatDayLabel(epochDay: Long): String {
        val d = date(epochDay)
        return when (epochDay) {
            todayEpochDay() -> "Today"
            todayEpochDay() - 1 -> "Yesterday"
            todayEpochDay() + 1 -> "Tomorrow"
            else -> d.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        }
    }

    fun formatFullDate(epochDay: Long): String =
        date(epochDay).format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))

    fun formatMonth(epochDay: Long): String =
        date(epochDay).format(DateTimeFormatter.ofPattern("MMMM yyyy"))

    fun formatIso(epochDay: Long): String = date(epochDay).toString()

    fun parseIsoDateOrNull(value: String?): Long? = runCatching {
        LocalDate.parse(value?.trim()).toEpochDay()
    }.getOrNull()

    /**
     * Accepts "07:30", "7:30", "7:30 am", "19:05", "7am", "0730".
     * Returns minute-of-day or null.
     */
    fun parseTimeToMinuteOrNull(raw: String?): Int? {
        val s = raw?.trim()?.lowercase() ?: return null
        if (s.isEmpty()) return null
        val pm = s.endsWith("pm") || s.endsWith("p.m.")
        val am = s.endsWith("am") || s.endsWith("a.m.")
        val digits = s.removeSuffix("p.m.").removeSuffix("a.m.")
            .removeSuffix("pm").removeSuffix("am").trim()
        val parts = when {
            digits.contains(':') -> digits.split(':')
            digits.contains('.') -> digits.split('.')
            digits.length == 4 && digits.all { it.isDigit() } ->
                listOf(digits.substring(0, 2), digits.substring(2))
            else -> listOf(digits)
        }
        val hRaw = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        if (m !in 0..59) return null
        var h = hRaw
        if (pm && h < 12) h += 12
        if (am && h == 12) h = 0
        if (h !in 0..23) return null
        return h * 60 + m
    }

    fun startOfWeek(epochDay: Long): Long =
        date(epochDay).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()

    fun endOfWeek(epochDay: Long): Long = startOfWeek(epochDay) + 6

    fun startOfMonth(epochDay: Long): Long = date(epochDay).withDayOfMonth(1).toEpochDay()

    fun endOfMonth(epochDay: Long): Long =
        date(epochDay).with(TemporalAdjusters.lastDayOfMonth()).toEpochDay()

    fun dayOfWeek(epochDay: Long): DayOfWeek = date(epochDay).dayOfWeek

    /** bit 0 = Monday … bit 6 = Sunday */
    fun dayBit(epochDay: Long): Int = 1 shl (dayOfWeek(epochDay).value - 1)

    fun daysBetween(from: Long, to: Long): Long = ChronoUnit.DAYS.between(date(from), date(to))

    fun relativeTime(epochMillis: Long): String {
        val diff = System.currentTimeMillis() - epochMillis
        val mins = diff / 60_000
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 60 * 24 -> "${mins / 60}h ago"
            mins < 60 * 24 * 7 -> "${mins / (60 * 24)}d ago"
            else -> LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zone)
                .format(DateTimeFormatter.ofPattern("MMM d"))
        }
    }

    fun greeting(minuteOfDay: Int = nowMinuteOfDay()): String = when (minuteOfDay / 60) {
        in 0..4 -> "Still up"
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
}

/** Mon..Sun bitmask helpers shared by the planner UI and the AI tools. */
object DayMask {
    const val NONE = 0
    const val ALL = 0b1111111
    const val WEEKDAYS = 0b0011111
    const val WEEKENDS = 0b1100000

    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    val fullNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    fun contains(mask: Int, dayOfWeekValue: Int): Boolean = mask and (1 shl (dayOfWeekValue - 1)) != 0

    fun toggle(mask: Int, index: Int): Int = mask xor (1 shl index)

    fun describe(mask: Int): String = when (mask) {
        ALL -> "Every day"
        WEEKDAYS -> "Weekdays"
        WEEKENDS -> "Weekends"
        NONE -> "No days"
        else -> (0..6).filter { mask and (1 shl it) != 0 }
            .joinToString(", ") { fullNames[it].take(3) }
    }

    /** Parses ["mon","tue"], ["weekdays"], ["daily"] etc. into a mask. */
    fun fromNames(names: List<String>?): Int {
        if (names.isNullOrEmpty()) return NONE
        var mask = NONE
        names.forEach { raw ->
            when (val n = raw.trim().lowercase()) {
                "daily", "everyday", "every day", "all" -> mask = mask or ALL
                "weekdays", "weekday" -> mask = mask or WEEKDAYS
                "weekends", "weekend" -> mask = mask or WEEKENDS
                else -> {
                    val idx = fullNames.indexOfFirst { it.lowercase().startsWith(n.take(3)) }
                    if (idx >= 0) mask = mask or (1 shl idx)
                }
            }
        }
        return mask
    }
}
