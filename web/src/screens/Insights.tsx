import { useMemo, useState } from 'react'
import { gradeFor } from '../core/rules'
import { formatDayLabel, formatDuration, formatFullDate } from '../core/time'
import {
  CategoryDonut,
  DayBars,
  MonthHeatmap,
  ProgressRing,
  TimeBands,
  TrendLine,
  WeekdayProfile,
  gradeColor,
} from '../components/Charts'
import {
  IconChevronLeft,
  IconChevronRight,
  IconFire,
  IconTimer,
  IconTrendDown,
  IconTrendFlat,
  IconTrendUp,
} from '../components/Icons'
import { Card, Segmented, StatTile } from '../components/Ui'
import {
  checklistCompletedIn,
  hourBands,
  rangeForPeriod,
  reportFor,
  shiftPeriod,
  weekdayProfile,
} from '../data/selectors'
import type { Period } from '../data/types'
import { completionRate } from '../data/types'
import { useAppState } from '../state/AppState'

export function InsightsScreen({
  anchorDay,
  onAnchorChange,
}: {
  anchorDay: number
  onAnchorChange: (epochDay: number) => void
}) {
  const { data } = useAppState()
  const [period, setPeriod] = useState<Period>('weekly')
  const [selectedDay, setSelectedDay] = useState<number | null>(null)

  const report = useMemo(() => reportFor(data, period, anchorDay), [data, period, anchorDay])
  const previous = useMemo(
    () => reportFor(data, period, shiftPeriod(period, anchorDay, -1)),
    [data, period, anchorDay],
  )
  const [from, to] = rangeForPeriod(period, anchorDay)
  const bands = useMemo(() => hourBands(data, from, to), [data, from, to])
  const profile = useMemo(
    // A single day is too thin for a weekday profile, so widen to the last eight weeks.
    () => weekdayProfile(data, period === 'daily' ? anchorDay - 55 : from, to),
    [data, period, anchorDay, from, to],
  )
  const checklistDone = useMemo(() => checklistCompletedIn(data, from, to), [data, from, to])

  const deltaPoints =
    previous.planned === 0 ? null : Math.round((report.completionRate - previous.completionRate) * 100)
  const selected = selectedDay != null ? report.days.find((day) => day.epochDay === selectedDay) : null

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <h1 className="page-title">Insights</h1>
          <p className="page-subtitle">{report.label}</p>
        </div>
        <div className="page-actions">
          <Segmented
            value={period}
            onChange={(next) => {
              setPeriod(next)
              setSelectedDay(null)
            }}
            options={[
              { value: 'daily', label: 'Daily' },
              { value: 'weekly', label: 'Weekly' },
              { value: 'monthly', label: 'Monthly' },
            ]}
          />
          <div className="row">
            <button
              type="button"
              className="btn ghost icon-only"
              aria-label="Previous period"
              onClick={() => {
                onAnchorChange(shiftPeriod(period, anchorDay, -1))
                setSelectedDay(null)
              }}
            >
              <IconChevronLeft size={17} />
            </button>
            <button
              type="button"
              className="btn ghost"
              onClick={() => {
                onAnchorChange(todayAnchor())
                setSelectedDay(null)
              }}
            >
              Now
            </button>
            <button
              type="button"
              className="btn ghost icon-only"
              aria-label="Next period"
              onClick={() => {
                onAnchorChange(shiftPeriod(period, anchorDay, 1))
                setSelectedDay(null)
              }}
            >
              <IconChevronRight size={17} />
            </button>
          </div>
        </div>
      </header>

      <div className="split">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <Card
            title={period === 'monthly' ? 'Every day this month' : 'Blocks completed per day'}
            hint={
              period === 'monthly'
                ? 'Darker means a higher share of that day’s blocks got done.'
                : 'The pale bar is what you planned; the solid bar is what you finished.'
            }
          >
            {period === 'monthly' ? (
              <MonthHeatmap
                days={report.days}
                selectedDay={selectedDay}
                onSelectDay={(day) => setSelectedDay(day === selectedDay ? null : day)}
              />
            ) : (
              <DayBars
                days={report.days}
                selectedDay={selectedDay}
                onSelectDay={(day) => setSelectedDay(day === selectedDay ? null : day)}
              />
            )}
            {selected ? (
              <div className="selected-day">
                <div>
                  <strong>{formatFullDate(selected.epochDay)}</strong>
                  <div className="muted small">
                    {selected.completed}/{selected.planned} blocks ·{' '}
                    {formatDuration(selected.completedMinutes)} focused
                  </div>
                </div>
                <span
                  className="rate-badge"
                  style={{
                    background: `color-mix(in srgb, ${gradeColor(completionRate(selected))} 18%, transparent)`,
                  }}
                >
                  {Math.round(completionRate(selected) * 100)}%
                </span>
              </div>
            ) : null}
          </Card>

          {period !== 'daily' ? (
            <Card title="Completion trend" hint="Share of planned blocks finished, day by day.">
              <TrendLine days={report.elapsedDays} />
            </Card>
          ) : null}

          <div className="grid cols-2">
            <Card title="Where your time went">
              <CategoryDonut
                slices={report.categories}
                centerValue={formatDuration(report.focusMinutes)}
                centerLabel="focused"
              />
            </Card>
            <Card
              title="When you actually deliver"
              hint="Completed blocks grouped by the hour they were scheduled to start."
            >
              <TimeBands bands={bands} />
            </Card>
          </div>

          <Card title="Day by day" hint="The numbers behind every chart above.">
            <DayTable report={report} />
          </Card>
        </div>

        <aside style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <Card>
            <div className="ring-row">
              <ProgressRing
                progress={report.completionRate}
                size={128}
                color={gradeColor(report.completionRate)}
              >
                <strong style={{ fontSize: 22, fontWeight: 700 }}>
                  {Math.round(report.completionRate * 100)}%
                </strong>
                <span className="muted small">{gradeFor(report.completionRate)}</span>
              </ProgressRing>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 16, fontWeight: 650 }}>
                  {report.completed} of {report.planned} blocks
                </div>
                <DeltaLine points={deltaPoints} period={period} />
                {report.bestDay ? (
                  <div className="muted small" style={{ marginTop: 4 }}>
                    Best: {formatDayLabel(report.bestDay.epochDay)} ·{' '}
                    {Math.round(completionRate(report.bestDay) * 100)}%
                  </div>
                ) : null}
                {report.worstDay && report.elapsedDays.length > 1 ? (
                  <div className="muted small">
                    Weakest: {formatDayLabel(report.worstDay.epochDay)} ·{' '}
                    {Math.round(completionRate(report.worstDay) * 100)}%
                  </div>
                ) : null}
              </div>
            </div>
          </Card>

          <div className="grid cols-2">
            <StatTile
              label="Focused"
              value={formatDuration(report.focusMinutes)}
              caption={`of ${formatDuration(report.plannedMinutes)} planned`}
              icon={<IconTimer size={13} />}
            />
            <StatTile
              label="Perfect days"
              value={String(report.perfectDays)}
              caption={`${report.activeDays}/${report.elapsedDays.length} days so far`}
              icon={<IconFire size={13} />}
              accent="var(--accent)"
            />
            <StatTile
              label="Consistency"
              value={`${Math.round(report.consistency * 100)}%`}
              caption="days you showed up at all"
            />
            <StatTile
              label="Checklist steps"
              value={String(checklistDone)}
              caption="ticked this period"
              accent="var(--secondary)"
            />
          </div>

          <Card title="Strength by weekday">
            <WeekdayPanel profile={profile} />
          </Card>
        </aside>
      </div>
    </div>
  )
}

function todayAnchor(): number {
  const now = new Date()
  return Math.floor(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()) / 86_400_000)
}

function DeltaLine({ points, period }: { points: number | null; period: Period }) {
  const word = period === 'daily' ? 'day' : period === 'weekly' ? 'week' : 'month'
  if (points == null) {
    return <div className="muted small">No comparable {word} to measure against yet</div>
  }
  const Icon = points > 2 ? IconTrendUp : points < -2 ? IconTrendDown : IconTrendFlat
  const color =
    points > 2 ? 'var(--status-good)' : points < -2 ? 'var(--status-critical)' : 'var(--text-muted)'
  return (
    <div className="row small" style={{ color: 'var(--text-secondary)', marginTop: 3 }}>
      <Icon size={15} className="delta-icon" />
      <span style={{ color }}>
        {Math.abs(points) <= 2
          ? `About the same as last ${word}`
          : `${Math.abs(points)} points ${points > 0 ? 'up' : 'down'} on last ${word}`}
      </span>
    </div>
  )
}

function WeekdayPanel({ profile }: { profile: Map<number, number> }) {
  const names = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']
  const entries = Array.from(profile.entries())
  let line: string | null = null
  if (entries.length >= 3) {
    const strongest = entries.reduce((best, item) => (item[1] > best[1] ? item : best))
    const weakest = entries.reduce((worst, item) => (item[1] < worst[1] ? item : worst))
    if (strongest[0] !== weakest[0]) {
      line = `${names[strongest[0] - 1]}s are your strongest at ${Math.round(strongest[1] * 100)}%; ${names[weakest[0] - 1]}s trail at ${Math.round(weakest[1] * 100)}%.`
    }
  }
  return (
    <>
      <WeekdayProfile profile={profile} />
      {line ? (
        <p className="muted small" style={{ marginTop: 'var(--space-3)' }}>
          {line}
        </p>
      ) : null}
    </>
  )
}

function DayTable({ report }: { report: ReturnType<typeof reportFor> }) {
  const rows = report.elapsedDays.filter((day) => day.planned > 0 || day.completed > 0)
  if (rows.length === 0) {
    return <p className="muted small">Nothing scheduled in this period.</p>
  }
  return (
    <div style={{ overflowX: 'auto' }}>
      <table className="data-table">
        <thead>
          <tr>
            <th>Day</th>
            <th className="num">Done</th>
            <th className="num">Focus</th>
            <th className="num">Rate</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((day) => (
            <tr key={day.epochDay}>
              <td>{formatDayLabel(day.epochDay)}</td>
              <td className="num">
                {day.completed}/{day.planned}
              </td>
              <td className="num">{formatDuration(day.completedMinutes)}</td>
              <td className="num">{Math.round(completionRate(day) * 100)}%</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
