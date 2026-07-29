/*
 * Time helpers. Mirrors core/TimeUtils.kt in the Android app so both clients agree on what a
 * "day" is: an epoch-day integer, plus a minute-of-day for the clock. Keeping the two in step
 * matters because they are meant to describe the same schedule.
 */

const MS_PER_DAY = 86_400_000

/** Local calendar day as a day number, matching Java's LocalDate.toEpochDay(). */
export function toEpochDay(date: Date): number {
  return Math.floor(
    Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()) / MS_PER_DAY,
  )
}

export function fromEpochDay(epochDay: number): Date {
  const utc = new Date(epochDay * MS_PER_DAY)
  return new Date(utc.getUTCFullYear(), utc.getUTCMonth(), utc.getUTCDate())
}

export function todayEpochDay(): number {
  return toEpochDay(new Date())
}

export function nowMinuteOfDay(): number {
  const now = new Date()
  return now.getHours() * 60 + now.getMinutes()
}

export function epochMillisOf(epochDay: number, minuteOfDay: number): number {
  const date = fromEpochDay(epochDay)
  date.setHours(Math.floor(minuteOfDay / 60), minuteOfDay % 60, 0, 0)
  return date.getTime()
}

/** Monday = 1 … Sunday = 7, matching java.time.DayOfWeek. */
export function dayOfWeek(epochDay: number): number {
  const jsDay = fromEpochDay(epochDay).getDay() // Sunday = 0
  return jsDay === 0 ? 7 : jsDay
}

/** bit 0 = Monday … bit 6 = Sunday */
export function dayBit(epochDay: number): number {
  return 1 << (dayOfWeek(epochDay) - 1)
}

export function startOfWeek(epochDay: number): number {
  return epochDay - (dayOfWeek(epochDay) - 1)
}

export function endOfWeek(epochDay: number): number {
  return startOfWeek(epochDay) + 6
}

export function startOfMonth(epochDay: number): number {
  const date = fromEpochDay(epochDay)
  return toEpochDay(new Date(date.getFullYear(), date.getMonth(), 1))
}

export function endOfMonth(epochDay: number): number {
  const date = fromEpochDay(epochDay)
  return toEpochDay(new Date(date.getFullYear(), date.getMonth() + 1, 0))
}

export function addMonths(epochDay: number, delta: number): number {
  const date = fromEpochDay(epochDay)
  return toEpochDay(new Date(date.getFullYear(), date.getMonth() + delta, 1))
}

export function rangeOf(from: number, to: number): number[] {
  const days: number[] = []
  for (let day = from; day <= to; day++) days.push(day)
  return days
}

export function formatTime(minuteOfDay: number, use24h = false): string {
  const clamped = Math.max(0, Math.min(24 * 60 - 1, Math.round(minuteOfDay)))
  const hour = Math.floor(clamped / 60)
  const minute = clamped % 60
  if (use24h) return `${pad(hour)}:${pad(minute)}`
  const suffix = hour < 12 ? 'AM' : 'PM'
  const display = hour % 12 === 0 ? 12 : hour % 12
  return `${display}:${pad(minute)} ${suffix}`
}

export function formatRange(startMinute: number, durationMinutes: number, use24h = false): string {
  const end = (startMinute + durationMinutes) % (24 * 60)
  return `${formatTime(startMinute, use24h)} – ${formatTime(end, use24h)}`
}

export function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (hours > 0 && rest > 0) return `${hours}h ${rest}m`
  if (hours > 0) return `${hours}h`
  return `${rest}m`
}

export function formatDayLabel(epochDay: number): string {
  const today = todayEpochDay()
  if (epochDay === today) return 'Today'
  if (epochDay === today - 1) return 'Yesterday'
  if (epochDay === today + 1) return 'Tomorrow'
  return fromEpochDay(epochDay).toLocaleDateString(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  })
}

export function formatFullDate(epochDay: number): string {
  return fromEpochDay(epochDay).toLocaleDateString(undefined, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  })
}

export function formatMonth(epochDay: number): string {
  return fromEpochDay(epochDay).toLocaleDateString(undefined, {
    month: 'long',
    year: 'numeric',
  })
}

/** ISO yyyy-mm-dd in local time — the format every AI tool argument uses. */
export function formatIso(epochDay: number): string {
  const date = fromEpochDay(epochDay)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function parseIsoDate(value: string | null | undefined): number | null {
  if (!value) return null
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value.trim())
  if (!match) return null
  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  if (month < 1 || month > 12 || day < 1 || day > 31) return null
  const date = new Date(year, month - 1, day)
  if (date.getMonth() !== month - 1 || date.getDate() !== day) return null
  return toEpochDay(date)
}

/**
 * Accepts "07:30", "7:30", "7:30 am", "19:05", "7pm", "0730" — the shapes models actually emit.
 * Returns minute-of-day, or null when the input isn't a time at all.
 */
export function parseTimeToMinute(raw: string | null | undefined): number | null {
  if (raw == null) return null
  const value = raw.trim().toLowerCase()
  if (!value) return null

  const isPm = value.endsWith('pm') || value.endsWith('p.m.')
  const isAm = value.endsWith('am') || value.endsWith('a.m.')
  const digits = value
    .replace(/p\.m\.$/, '')
    .replace(/a\.m\.$/, '')
    .replace(/pm$/, '')
    .replace(/am$/, '')
    .trim()

  let parts: string[]
  if (digits.includes(':')) parts = digits.split(':')
  else if (digits.includes('.')) parts = digits.split('.')
  else if (/^\d{4}$/.test(digits)) parts = [digits.slice(0, 2), digits.slice(2)]
  else parts = [digits]

  const rawHour = Number(parts[0]?.trim())
  const minute = parts.length > 1 ? Number(parts[1]?.trim()) : 0
  if (!Number.isInteger(rawHour) || !Number.isInteger(minute)) return null
  if (minute < 0 || minute > 59) return null

  let hour = rawHour
  if (isPm && hour < 12) hour += 12
  if (isAm && hour === 12) hour = 0
  if (hour < 0 || hour > 23) return null
  return hour * 60 + minute
}

export function relativeTime(epochMillis: number): string {
  const minutes = Math.floor((Date.now() - epochMillis) / 60_000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes}m ago`
  if (minutes < 60 * 24) return `${Math.floor(minutes / 60)}h ago`
  if (minutes < 60 * 24 * 7) return `${Math.floor(minutes / (60 * 24))}d ago`
  return new Date(epochMillis).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

export function greeting(minuteOfDay = nowMinuteOfDay()): string {
  const hour = Math.floor(minuteOfDay / 60)
  if (hour < 5) return 'Still up'
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  if (hour < 22) return 'Good evening'
  return 'Good night'
}

export function weekdayName(epochDay: number, style: 'long' | 'short' = 'long'): string {
  return fromEpochDay(epochDay).toLocaleDateString(undefined, { weekday: style })
}

function pad(value: number): string {
  return value < 10 ? `0${value}` : String(value)
}

// ---- day-of-week bitmask -------------------------------------------------

export const DayMask = {
  NONE: 0,
  ALL: 0b1111111,
  WEEKDAYS: 0b0011111,
  WEEKENDS: 0b1100000,

  labels: ['M', 'T', 'W', 'T', 'F', 'S', 'S'] as const,
  fullNames: [
    'Monday',
    'Tuesday',
    'Wednesday',
    'Thursday',
    'Friday',
    'Saturday',
    'Sunday',
  ] as const,

  contains(mask: number, index: number): boolean {
    return (mask & (1 << index)) !== 0
  },

  toggle(mask: number, index: number): number {
    return mask ^ (1 << index)
  },

  describe(mask: number): string {
    if (mask === DayMask.ALL) return 'Every day'
    if (mask === DayMask.WEEKDAYS) return 'Weekdays'
    if (mask === DayMask.WEEKENDS) return 'Weekends'
    if (mask === DayMask.NONE) return 'No days'
    return DayMask.fullNames
      .filter((_, index) => (mask & (1 << index)) !== 0)
      .map((name) => name.slice(0, 3))
      .join(', ')
  },

  /** Parses ["mon","tue"], ["weekdays"], ["daily"] — the vocabulary the AI is told to use. */
  fromNames(names: string[] | null | undefined): number {
    if (!names || names.length === 0) return DayMask.NONE
    let mask = DayMask.NONE
    for (const raw of names) {
      const name = raw.trim().toLowerCase()
      if (['daily', 'everyday', 'every day', 'all'].includes(name)) mask |= DayMask.ALL
      else if (name === 'weekdays' || name === 'weekday') mask |= DayMask.WEEKDAYS
      else if (name === 'weekends' || name === 'weekend') mask |= DayMask.WEEKENDS
      else {
        const index = DayMask.fullNames.findIndex((full) =>
          full.toLowerCase().startsWith(name.slice(0, 3)),
        )
        if (index >= 0) mask |= 1 << index
      }
    }
    return mask
  },
}
