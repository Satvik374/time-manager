package com.kairo.timemanager.ui.screens.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairo.timemanager.core.ScoreCopy
import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.repo.DaySnapshot
import com.kairo.timemanager.data.repo.Period
import com.kairo.timemanager.data.repo.PeriodReport
import com.kairo.timemanager.ui.components.CategoryDonut
import com.kairo.timemanager.ui.components.DailyBarChart
import com.kairo.timemanager.ui.components.GradientHeader
import com.kairo.timemanager.ui.components.KairoCard
import com.kairo.timemanager.ui.components.MonthHeatmap
import com.kairo.timemanager.ui.components.ProgressRing
import com.kairo.timemanager.ui.components.SectionHeader
import com.kairo.timemanager.ui.components.StatTile
import com.kairo.timemanager.ui.components.TimeBandChart
import com.kairo.timemanager.ui.components.TrendLineChart
import com.kairo.timemanager.ui.components.WeekdayProfile
import com.kairo.timemanager.ui.theme.ChartPalette
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(viewModel: InsightsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val report = state.report

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        InsightsHeader(state, viewModel::setPeriod, viewModel::shiftPeriod, viewModel::goToNow)

        Column(Modifier.padding(16.dp)) {
            if (report == null) {
                Spacer(Modifier.height(40.dp))
                Text(
                    "Crunching your numbers…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            HeadlineCard(state, report)

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = "Focused",
                    value = TimeUtils.formatDuration(report.focusMinutes),
                    caption = "of ${TimeUtils.formatDuration(report.plannedMinutes)} planned",
                    icon = Icons.Filled.Timer,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Perfect days",
                    value = report.perfectDays.toString(),
                    caption = "${report.activeDays}/${report.days.size} days active",
                    accent = MaterialTheme.colorScheme.tertiary,
                    icon = Icons.Filled.LocalFireDepartment,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = "Consistency",
                    value = "${(report.consistency * 100).roundToInt()}%",
                    caption = "days you showed up at all",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "Checklist steps",
                    value = state.checklistCompleted.toString(),
                    caption = "ticked this period",
                    accent = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(22.dp))
            when (state.period) {
                Period.MONTHLY -> {
                    SectionHeader("Every day this month")
                    KairoCard {
                        MonthHeatmap(
                            days = report.days,
                            selectedDay = state.selectedDay,
                            onSelectDay = viewModel::selectDay
                        )
                    }
                }
                Period.WEEKLY -> {
                    SectionHeader("Blocks completed per day")
                    KairoCard {
                        DailyBarChart(
                            days = report.days,
                            selectedDay = state.selectedDay,
                            onSelectDay = viewModel::selectDay
                        )
                    }
                }
                Period.DAILY -> {
                    SectionHeader("How the day went")
                    KairoCard {
                        DailyBarChart(days = report.days, showValues = true)
                    }
                }
            }

            state.selectedDay?.let { day ->
                report.days.firstOrNull { it.epochDay == day }?.let { snapshot ->
                    Spacer(Modifier.height(10.dp))
                    SelectedDayCard(snapshot)
                }
            }

            if (state.period != Period.DAILY) {
                Spacer(Modifier.height(22.dp))
                SectionHeader("Completion trend")
                KairoCard { TrendLineChart(report.days) }
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader("Where your time went")
            KairoCard {
                CategoryDonut(
                    slices = report.categories,
                    centerValue = TimeUtils.formatDuration(report.focusMinutes),
                    centerLabel = "focused"
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader("When you actually deliver")
            KairoCard {
                TimeBandChart(state.hourBands)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Completed blocks grouped by the hour they were scheduled to start.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader("Strength by weekday")
            KairoCard {
                WeekdayProfile(state.weekdayProfile)
                Spacer(Modifier.height(8.dp))
                weakestWeekdayLine(state.weekdayProfile)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionHeader("Day by day")
            DayTable(report)

            Spacer(Modifier.height(90.dp))
        }
    }
}

@Composable
private fun InsightsHeader(
    state: InsightsUiState,
    onSetPeriod: (Period) -> Unit,
    onShift: (Int) -> Unit,
    onNow: () -> Unit
) {
    val statusPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    GradientHeader {
        Spacer(Modifier.height(statusPadding))
        Text(
            "Insights",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.85f)
        )
        Text(
            state.report?.label ?: "…",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f)) {
                Row(Modifier.padding(3.dp)) {
                    Period.entries.forEach { period ->
                        val selected = state.period == period
                        Surface(
                            shape = CircleShape,
                            color = if (selected) Color.White else Color.Transparent,
                            modifier = Modifier.clickable { onSetPeriod(period) }
                        ) {
                            Text(
                                period.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onShift(-1) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous period", tint = Color.White)
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.clickable(onClick = onNow)
            ) {
                Text(
                    "Now",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            IconButton(onClick = { onShift(1) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next period", tint = Color.White)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun HeadlineCard(state: InsightsUiState, report: PeriodReport) {
    KairoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = report.completionRate,
                size = 112.dp,
                strokeWidth = 12.dp,
                color = ChartPalette.grade(report.completionRate)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(report.completionRate * 100).roundToInt()}%",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        ScoreCopy.grade(report.completionRate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${report.completed} of ${report.planned} blocks",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                DeltaLine(state.deltaPoints, state.period)
                report.bestDay?.let {
                    Text(
                        "Best: ${TimeUtils.formatDayLabel(it.epochDay)} · ${(it.completionRate * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                report.worstDay?.takeIf { report.days.size > 1 }?.let {
                    Text(
                        "Weakest: ${TimeUtils.formatDayLabel(it.epochDay)} · ${(it.completionRate * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DeltaLine(deltaPoints: Int?, period: Period) {
    if (deltaPoints == null) {
        Text(
            "No comparable ${period.label.lowercase()} period yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val (icon, color, word) = when {
        deltaPoints > 2 -> Triple(Icons.Filled.TrendingUp, ChartPalette.good, "up")
        deltaPoints < -2 -> Triple(Icons.Filled.TrendingDown, ChartPalette.critical, "down")
        else -> Triple(Icons.Filled.TrendingFlat, MaterialTheme.colorScheme.onSurfaceVariant, "flat")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            if (word == "flat") "About the same as last ${periodWord(period)}"
            else "${abs(deltaPoints)} points $word on last ${periodWord(period)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun periodWord(period: Period): String = when (period) {
    Period.DAILY -> "day"
    Period.WEEKLY -> "week"
    Period.MONTHLY -> "month"
}

@Composable
private fun SelectedDayCard(snapshot: DaySnapshot) {
    KairoCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    TimeUtils.formatFullDate(snapshot.epochDay),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "${snapshot.completed}/${snapshot.planned} blocks · " +
                        TimeUtils.formatDuration(snapshot.completedMinutes) + " focused",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(ChartPalette.grade(snapshot.completionRate).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${(snapshot.completionRate * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** The table view that backs up every chart above — required so nothing depends on colour alone. */
@Composable
private fun DayTable(report: PeriodReport) {
    val rows = report.days.filter { it.planned > 0 || it.completed > 0 }
    if (rows.isEmpty()) {
        Text(
            "Nothing scheduled in this period.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    KairoCard {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            TableCell("Day", Modifier.weight(1.4f), header = true)
            TableCell("Done", Modifier.weight(1f), header = true)
            TableCell("Focus", Modifier.weight(1f), header = true)
            TableCell("Rate", Modifier.weight(0.9f), header = true)
        }
        rows.forEach { day ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableCell(TimeUtils.formatDayLabel(day.epochDay), Modifier.weight(1.4f))
                TableCell("${day.completed}/${day.planned}", Modifier.weight(1f))
                TableCell(TimeUtils.formatDuration(day.completedMinutes), Modifier.weight(1f))
                TableCell("${(day.completionRate * 100).roundToInt()}%", Modifier.weight(0.9f))
            }
        }
    }
}

@Composable
private fun TableCell(text: String, modifier: Modifier = Modifier, header: Boolean = false) {
    Text(
        text,
        modifier = modifier,
        style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface,
        maxLines = 1
    )
}

private fun weakestWeekdayLine(profile: Map<Int, Float>): String? {
    if (profile.size < 3) return null
    val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val weakest = profile.minByOrNull { it.value } ?: return null
    val strongest = profile.maxByOrNull { it.value } ?: return null
    if (weakest.key == strongest.key) return null
    return "${names[strongest.key - 1]}s are your strongest at ${(strongest.value * 100).roundToInt()}%; " +
        "${names[weakest.key - 1]}s trail at ${(weakest.value * 100).roundToInt()}%."
}
