/*
 * Chart components.
 *
 * Conventions, applied consistently across the file:
 *  - thin marks with rounded data-ends anchored to the baseline
 *  - a 2px surface gap between adjacent fills so touching marks stay readable
 *  - recessive grid and axis ink; values wear text colours, never the series colour
 *  - every colour-coded chart ships a legend or labels, so identity is never colour-alone
 *  - an interactive layer by default: hovering any mark explains it
 */

import { useState, type ReactNode } from 'react'
import {
  dayOfWeek,
  formatDayLabel,
  formatDuration,
  fromEpochDay,
  todayEpochDay,
} from '../core/time'
import type { CategorySlice, DaySnapshot } from '../data/types'
import { completionRate } from '../data/types'

const HEAT_STEPS = ['--heat-1', '--heat-2', '--heat-3', '--heat-4', '--heat-5', '--heat-6']

export function gradeColor(rate: number): string {
  if (rate >= 0.8) return 'var(--status-good)'
  if (rate >= 0.5) return 'var(--status-warning)'
  return 'var(--status-critical)'
}

/* ---- tooltip ----------------------------------------------------------- */

function Tooltip({ children, visible, x }: { children: ReactNode; visible: boolean; x: number }) {
  if (!visible) return null
  return (
    <div className="chart-tooltip" style={{ left: `${x}%` }}>
      {children}
    </div>
  )
}

/* ---- day bars ---------------------------------------------------------- */

export function DayBars({
  days,
  selectedDay,
  onSelectDay,
  height = 190,
}: {
  days: DaySnapshot[]
  selectedDay?: number | null
  onSelectDay?: (epochDay: number) => void
  height?: number
}) {
  const [hover, setHover] = useState<number | null>(null)
  if (days.length === 0) return <ChartEmpty message="No days in this range yet" />

  const ceiling = Math.max(1, ...days.map((day) => Math.max(day.planned, day.completed)))
  const today = todayEpochDay()
  const dense = days.length > 12

  return (
    <div className="chart" style={{ position: 'relative' }}>
      <div className="bars" style={{ height }}>
        {days.map((day, index) => {
          const isToday = day.epochDay === today
          const isSelected = selectedDay === day.epochDay
          const isHovered = hover === index
          return (
            <button
              key={day.epochDay}
              type="button"
              className={`bar-col${isSelected ? ' selected' : ''}`}
              onMouseEnter={() => setHover(index)}
              onMouseLeave={() => setHover(null)}
              onFocus={() => setHover(index)}
              onBlur={() => setHover(null)}
              onClick={() => onSelectDay?.(day.epochDay)}
              aria-label={`${formatDayLabel(day.epochDay)}: ${day.completed} of ${day.planned} blocks done`}
            >
              {!dense ? (
                <span className="bar-value">{day.completed > 0 ? day.completed : ''}</span>
              ) : null}
              <span className="bar-track">
                <span
                  className="bar-planned"
                  style={{ height: `${Math.max(2, (day.planned / ceiling) * 100)}%` }}
                />
                <span
                  className="bar-fill"
                  style={{
                    height: `${(day.completed / ceiling) * 100}%`,
                    background: isToday ? 'var(--accent)' : 'var(--primary)',
                    opacity: isHovered || isSelected ? 1 : 0.92,
                  }}
                />
              </span>
              <span className={`bar-label${isToday ? ' today' : ''}`}>
                {dense
                  ? fromEpochDay(day.epochDay).getDate()
                  : ['M', 'T', 'W', 'T', 'F', 'S', 'S'][dayOfWeek(day.epochDay) - 1]}
              </span>
            </button>
          )
        })}
      </div>
      <Tooltip
        visible={hover != null}
        x={hover != null ? ((hover + 0.5) / days.length) * 100 : 0}
      >
        {hover != null ? (
          <>
            <strong>{formatDayLabel(days[hover].epochDay)}</strong>
            <span>
              {days[hover].completed}/{days[hover].planned} blocks ·{' '}
              {formatDuration(days[hover].completedMinutes)}
            </span>
          </>
        ) : null}
      </Tooltip>
    </div>
  )
}

/* ---- trend line -------------------------------------------------------- */

export function TrendLine({ days, height = 170 }: { days: DaySnapshot[]; height?: number }) {
  const [hover, setHover] = useState<number | null>(null)
  const points = days.filter((day) => day.planned > 0)
  if (points.length < 2) return <ChartEmpty message="Not enough days to draw a trend" />

  const width = 100
  const padY = 8
  const coords = points.map((day, index) => ({
    x: (index / (points.length - 1)) * width,
    y: padY + (1 - completionRate(day)) * (100 - padY * 2),
    day,
  }))
  const linePath = coords.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`).join(' ')
  const areaPath = `${linePath} L${coords[coords.length - 1].x},100 L${coords[0].x},100 Z`
  const last = coords[coords.length - 1]
  const active = hover != null ? coords[hover] : null

  return (
    <div className="chart" style={{ position: 'relative' }}>
      <svg
        viewBox="0 0 100 100"
        preserveAspectRatio="none"
        style={{ width: '100%', height, display: 'block' }}
        onMouseLeave={() => setHover(null)}
        role="img"
        aria-label="Completion rate trend"
      >
        {[0, 0.5, 1].map((level) => (
          <line
            key={level}
            x1="0"
            x2="100"
            y1={padY + (1 - level) * (100 - padY * 2)}
            y2={padY + (1 - level) * (100 - padY * 2)}
            stroke="var(--grid)"
            strokeWidth="0.4"
            vectorEffect="non-scaling-stroke"
          />
        ))}
        <path d={areaPath} fill="var(--primary)" opacity="0.13" />
        <path
          d={linePath}
          fill="none"
          stroke="var(--primary)"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          vectorEffect="non-scaling-stroke"
        />
        {active ? (
          <line
            x1={active.x}
            x2={active.x}
            y1="0"
            y2="100"
            stroke="var(--border-strong)"
            strokeWidth="1"
            vectorEffect="non-scaling-stroke"
          />
        ) : null}
        {coords.map((point, index) => (
          <rect
            key={index}
            x={point.x - width / points.length / 2}
            y="0"
            width={width / points.length}
            height="100"
            fill="transparent"
            onMouseEnter={() => setHover(index)}
          />
        ))}
      </svg>
      {/* Markers sit outside the stretched viewBox so they stay circular. */}
      <span
        className="trend-marker"
        style={{ left: `${(active ?? last).x}%`, top: `${((active ?? last).y / 100) * height}px` }}
      />
      <div className="chart-axis">
        <span>{formatDayLabel(points[0].epochDay)}</span>
        <span className="strong">
          {Math.round(completionRate((active ?? last).day) * 100)}% on{' '}
          {formatDayLabel((active ?? last).day.epochDay)}
        </span>
      </div>
    </div>
  )
}

/* ---- donut ------------------------------------------------------------- */

export function CategoryDonut({
  slices,
  centerValue,
  centerLabel,
}: {
  slices: CategorySlice[]
  centerValue: string
  centerLabel: string
}) {
  const [hover, setHover] = useState<number | null>(null)
  if (slices.length === 0) return <ChartEmpty message="No completed blocks to break down yet" />

  // Past six categories the tail folds into one slice rather than inventing new hues.
  const top = slices.slice(0, 6)
  const rest = slices.slice(6)
  const folded =
    rest.length === 0
      ? top
      : [
          ...top,
          {
            category: 'Other',
            colorHex: '#8A8AA3',
            completed: rest.reduce((sum, slice) => sum + slice.completed, 0),
            minutes: rest.reduce((sum, slice) => sum + slice.minutes, 0),
          },
        ]
  const total = Math.max(
    1,
    folded.reduce((sum, slice) => sum + slice.minutes, 0),
  )

  const radius = 42
  const circumference = 2 * Math.PI * radius
  let offset = 0

  return (
    <div className="donut-wrap">
      <div className="donut">
        <svg viewBox="0 0 100 100" role="img" aria-label="Time by category">
          {folded.map((slice, index) => {
            const fraction = slice.minutes / total
            // A 2px gap between segments keeps neighbours from bleeding together.
            const gap = folded.length > 1 ? 1.2 : 0
            const length = Math.max(0.5, fraction * circumference - gap)
            const dash = `${length} ${circumference - length}`
            const rotation = (offset / circumference) * 360 - 90
            offset += fraction * circumference
            return (
              <circle
                key={slice.category}
                cx="50"
                cy="50"
                r={radius}
                fill="none"
                stroke={slice.colorHex}
                strokeWidth={hover === index ? 15 : 13}
                strokeDasharray={dash}
                transform={`rotate(${rotation} 50 50)`}
                onMouseEnter={() => setHover(index)}
                onMouseLeave={() => setHover(null)}
                style={{ transition: 'stroke-width 140ms ease' }}
              />
            )
          })}
        </svg>
        <div className="donut-center">
          <strong>{hover != null ? formatDuration(folded[hover].minutes) : centerValue}</strong>
          <span>{hover != null ? folded[hover].category : centerLabel}</span>
        </div>
      </div>
      <ul className="legend">
        {folded.map((slice, index) => (
          <li
            key={slice.category}
            onMouseEnter={() => setHover(index)}
            onMouseLeave={() => setHover(null)}
            className={hover === index ? 'active' : undefined}
          >
            <span className="dot" style={{ background: slice.colorHex }} />
            <span className="legend-name">{slice.category}</span>
            <span className="legend-value">{formatDuration(slice.minutes)}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/* ---- month heatmap ----------------------------------------------------- */

export function MonthHeatmap({
  days,
  selectedDay,
  onSelectDay,
}: {
  days: DaySnapshot[]
  selectedDay?: number | null
  onSelectDay?: (epochDay: number) => void
}) {
  if (days.length === 0) return <ChartEmpty message="No data for this month" />
  const leadingBlanks = dayOfWeek(days[0].epochDay) - 1
  const today = todayEpochDay()

  return (
    <div className="heatmap">
      <div className="heat-head">
        {['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((label, index) => (
          <span key={index}>{label}</span>
        ))}
      </div>
      <div className="heat-grid">
        {Array.from({ length: leadingBlanks }, (_, index) => (
          <span key={`blank-${index}`} className="heat-cell blank" />
        ))}
        {days.map((day) => {
          const rate = completionRate(day)
          const empty = day.planned === 0 || day.completed === 0
          const step = empty ? null : HEAT_STEPS[Math.min(HEAT_STEPS.length - 1, Math.floor(rate * (HEAT_STEPS.length - 1)))]
          return (
            <button
              key={day.epochDay}
              type="button"
              className={`heat-cell${selectedDay === day.epochDay ? ' selected' : ''}${day.epochDay === today ? ' today' : ''}`}
              style={{ background: step ? `var(${step})` : 'var(--heat-0)' }}
              onClick={() => onSelectDay?.(day.epochDay)}
              title={`${formatDayLabel(day.epochDay)} — ${day.completed}/${day.planned} blocks`}
              aria-label={`${formatDayLabel(day.epochDay)}: ${day.completed} of ${day.planned} blocks done`}
            >
              <span className={rate > 0.6 ? 'bright' : undefined}>
                {fromEpochDay(day.epochDay).getDate()}
              </span>
            </button>
          )
        })}
      </div>
      <div className="heat-legend">
        <span className="muted small">less</span>
        {['--heat-0', ...HEAT_STEPS].map((step) => (
          <span key={step} className="heat-swatch" style={{ background: `var(${step})` }} />
        ))}
        <span className="muted small">more</span>
      </div>
    </div>
  )
}

/* ---- horizontal bars --------------------------------------------------- */

export function TimeBands({ bands }: { bands: number[] }) {
  const labels = ['00–04', '04–08', '08–12', '12–16', '16–20', '20–24']
  const total = bands.reduce((sum, value) => sum + value, 0)
  if (total === 0) return <ChartEmpty message="Complete some blocks to see when you deliver" />
  const ceiling = Math.max(1, ...bands)
  const peak = bands.indexOf(ceiling)

  return (
    <div className="hbars">
      {labels.map((label, index) => (
        <div key={label} className={`hbar${index === peak ? ' peak' : ''}`}>
          <span className="hbar-label">{label}</span>
          <span className="hbar-track">
            <span
              className="hbar-fill"
              style={{
                width: `${((bands[index] ?? 0) / ceiling) * 100}%`,
                background: 'var(--secondary)',
              }}
            />
          </span>
          <span className="hbar-value">{bands[index] ?? 0}</span>
        </div>
      ))}
    </div>
  )
}

export function WeekdayProfile({ profile }: { profile: Map<number, number> }) {
  if (profile.size === 0) return <ChartEmpty message="Not enough history for a weekday profile" />
  const names = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

  return (
    <div className="hbars">
      {[1, 2, 3, 4, 5, 6, 7].map((dow) => {
        const rate = profile.get(dow)
        if (rate == null) return null
        return (
          <div key={dow} className="hbar">
            <span className="hbar-label">{names[dow - 1]}</span>
            <span className="hbar-track">
              <span
                className="hbar-fill"
                style={{ width: `${Math.max(2, rate * 100)}%`, background: gradeColor(rate) }}
              />
            </span>
            <span className="hbar-value">{Math.round(rate * 100)}%</span>
          </div>
        )
      })}
    </div>
  )
}

/* ---- gauge ------------------------------------------------------------- */

export function ProgressRing({
  progress,
  size = 132,
  stroke = 12,
  color = 'var(--primary)',
  children,
}: {
  progress: number
  size?: number
  stroke?: number
  color?: string
  children?: ReactNode
}) {
  const clamped = Math.min(1, Math.max(0, progress))
  const radius = (100 - stroke) / 2
  const circumference = 2 * Math.PI * radius
  // A 270° gauge reads as a dial rather than a pie, and leaves room for the label underneath.
  const sweep = 0.75
  const arc = circumference * sweep

  return (
    <div className="ring" style={{ width: size, height: size }}>
      <svg viewBox="0 0 100 100" role="img" aria-label={`${Math.round(clamped * 100)} percent`}>
        <circle
          cx="50"
          cy="50"
          r={radius}
          fill="none"
          stroke="var(--track)"
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={`${arc} ${circumference}`}
          transform="rotate(135 50 50)"
        />
        <circle
          cx="50"
          cy="50"
          r={radius}
          fill="none"
          stroke={color}
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={`${arc * clamped} ${circumference}`}
          transform="rotate(135 50 50)"
          style={{ transition: 'stroke-dasharray 600ms cubic-bezier(0.22, 1, 0.36, 1)' }}
        />
      </svg>
      <div className="ring-center">{children}</div>
    </div>
  )
}

function ChartEmpty({ message }: { message: string }) {
  return <div className="chart-empty">{message}</div>
}
