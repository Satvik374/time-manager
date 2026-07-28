package com.kairo.timemanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.repo.CategorySlice
import com.kairo.timemanager.data.repo.DaySnapshot
import com.kairo.timemanager.ui.theme.ChartPalette
import com.kairo.timemanager.ui.theme.parseHexColor
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Chart conventions used across this file:
 *  - marks are thin, with 4dp rounded data-ends anchored to the baseline
 *  - a 2dp surface gap separates adjacent fills so touching marks stay readable
 *  - grid and axis ink is recessive; values wear text colours, never the series colour
 *  - every colour-coded chart ships labels or a legend, so identity is never colour-alone
 */

private val AxisLabelSize = 11.sp

@Composable
private fun axisInk() = MaterialTheme.colorScheme.onSurfaceVariant

/**
 * Completed blocks per day. The planned count sits behind as a recessive track so the
 * shortfall is visible without a second series competing for attention.
 */
@Composable
fun DailyBarChart(
    days: List<DaySnapshot>,
    modifier: Modifier = Modifier,
    selectedDay: Long? = null,
    showValues: Boolean = true,
    onSelectDay: ((Long) -> Unit)? = null
) {
    if (days.isEmpty()) {
        ChartEmpty(modifier, "No days in this range yet")
        return
    }
    val ceiling = max(1, days.maxOf { max(it.planned, it.completed) })
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val barColor = MaterialTheme.colorScheme.primary
    val todayColor = MaterialTheme.colorScheme.tertiary
    val today = TimeUtils.todayEpochDay()

    Row(
        modifier = modifier.fillMaxWidth().height(168.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEach { day ->
            val isSelected = selectedDay == day.epochDay
            val fraction by animateFloatAsState(
                targetValue = day.completed.toFloat() / ceiling,
                animationSpec = tween(520),
                label = "bar"
            )
            val plannedFraction = day.planned.toFloat() / ceiling

            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onSelectDay != null) Modifier.clickable { onSelectDay(day.epochDay) }
                        else Modifier
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                if (showValues) {
                    Text(
                        text = if (day.completed > 0) day.completed.toString() else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    Spacer(Modifier.height(3.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Planned track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isSelected) 0.86f else 0.7f)
                            .fillMaxHeight(plannedFraction.coerceIn(0.02f, 1f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(trackColor)
                    )
                    // Completed fill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isSelected) 0.86f else 0.7f)
                            .fillMaxHeight(fraction.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (day.epochDay == today) todayColor else barColor)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = TimeUtils.dayOfWeek(day.epochDay).name.take(1),
                    fontSize = AxisLabelSize,
                    color = if (day.epochDay == today) todayColor else axisInk(),
                    fontWeight = if (day.epochDay == today) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Completion-rate trend. Thin 2dp stroke, a soft fill beneath, and a marker plus direct label
 * only on the final point — never a number on every point.
 */
@Composable
fun TrendLineChart(
    days: List<DaySnapshot>,
    modifier: Modifier = Modifier
) {
    val points = days.filter { it.planned > 0 }
    if (points.size < 2) {
        ChartEmpty(modifier, "Not enough days to draw a trend")
        return
    }
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.14f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val progress by animateFloatAsState(1f, tween(700), label = "trend")

    Column(modifier) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val stepX = size.width / (points.size - 1).coerceAtLeast(1)
            val usableHeight = size.height - 12.dp.toPx()

            // Recessive grid at 0/50/100%
            listOf(0f, 0.5f, 1f).forEach { level ->
                val y = 6.dp.toPx() + usableHeight * (1f - level)
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }

            val coords = points.mapIndexed { index, day ->
                Offset(
                    x = index * stepX,
                    y = 6.dp.toPx() + usableHeight * (1f - day.completionRate.coerceIn(0f, 1f))
                )
            }
            val visible = coords.take((coords.size * progress).roundToInt().coerceAtLeast(2))

            val fill = Path().apply {
                moveTo(visible.first().x, size.height)
                visible.forEach { lineTo(it.x, it.y) }
                lineTo(visible.last().x, size.height)
                close()
            }
            drawPath(fill, fillColor)

            val line = Path().apply {
                moveTo(visible.first().x, visible.first().y)
                visible.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

            // Endpoint marker with a 2dp surface ring so it reads on top of the fill
            val last = visible.last()
            drawCircle(surface, radius = 6.dp.toPx(), center = last)
            drawCircle(lineColor, radius = 4.dp.toPx(), center = last)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                TimeUtils.formatDayLabel(points.first().epochDay),
                fontSize = AxisLabelSize,
                color = axisInk()
            )
            Text(
                "${(points.last().completionRate * 100).roundToInt()}% on " +
                    TimeUtils.formatDayLabel(points.last().epochDay),
                fontSize = AxisLabelSize,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Category split. Slices use each category's own colour because the colour belongs to the
 * entity, and the legend always carries the name and value so nothing depends on hue alone.
 */
@Composable
fun CategoryDonut(
    slices: List<CategorySlice>,
    modifier: Modifier = Modifier,
    centerLabel: String = "",
    centerValue: String = ""
) {
    if (slices.isEmpty()) {
        ChartEmpty(modifier, "No completed blocks to break down yet")
        return
    }
    // Past six categories the tail folds into one slice rather than inventing new hues.
    val top = slices.take(6)
    val rest = slices.drop(6)
    val folded = if (rest.isEmpty()) top else top + CategorySlice(
        category = "Other",
        colorHex = "#8A8AA3",
        completed = rest.sumOf { it.completed },
        minutes = rest.sumOf { it.minutes }
    )
    val total = folded.sumOf { it.minutes }.coerceAtLeast(1)
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val sweepProgress by animateFloatAsState(1f, tween(700), label = "donut")

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(132.dp),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val stroke = 22.dp.toPx()
                val inset = stroke / 2
                var startAngle = -90f
                folded.forEach { slice ->
                    val sweep = 360f * (slice.minutes.toFloat() / total) * sweepProgress
                    // 2dp surface gap keeps neighbouring segments from bleeding together
                    val gap = if (folded.size > 1) 2.5f else 0f
                    drawArc(
                        color = parseHexColor(slice.colorHex),
                        startAngle = startAngle + gap / 2,
                        sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Butt)
                    )
                    startAngle += sweep
                }
                drawCircle(surface, radius = (size.minDimension / 2) - stroke - 2.dp.toPx())
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    centerValue,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    centerLabel,
                    fontSize = AxisLabelSize,
                    color = axisInk(),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            folded.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(parseHexColor(slice.colorHex))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        slice.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text(
                        TimeUtils.formatDuration(slice.minutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = axisInk()
                    )
                }
            }
        }
    }
}

/** Month view: one cell per day, sequential single-hue ramp for magnitude. */
@Composable
fun MonthHeatmap(
    days: List<DaySnapshot>,
    modifier: Modifier = Modifier,
    onSelectDay: ((Long) -> Unit)? = null,
    selectedDay: Long? = null
) {
    if (days.isEmpty()) {
        ChartEmpty(modifier, "No data for this month")
        return
    }
    val emptyCell = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.primary
    val leadingBlanks = (TimeUtils.dayOfWeek(days.first().epochDay).value - 1).coerceIn(0, 6)
    val cells = List(leadingBlanks) { null } + days

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = AxisLabelSize,
                    color = axisInk()
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (day == null) return@Box
                        val intensity = if (day.planned == 0) 0f else day.completionRate
                        val fill = if (day.planned == 0 || day.completed == 0) emptyCell
                        else ChartPalette.sequentialStep(intensity)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(7.dp))
                                .background(fill)
                                .then(
                                    if (selectedDay == day.epochDay)
                                        Modifier.border(2.dp, outline, RoundedCornerShape(7.dp))
                                    else Modifier
                                )
                                .then(
                                    if (onSelectDay != null)
                                        Modifier.clickable { onSelectDay(day.epochDay) }
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                TimeUtils.date(day.epochDay).dayOfMonth.toString(),
                                fontSize = 10.sp,
                                color = if (intensity > 0.6f) Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        // Scale legend — the heatmap's stand-in for a numeric axis.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text("less", fontSize = AxisLabelSize, color = axisInk())
            Spacer(Modifier.width(6.dp))
            listOf(0.05f, 0.3f, 0.55f, 0.8f, 1f).forEach { level ->
                Box(
                    Modifier
                        .size(11.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ChartPalette.sequentialStep(level))
                )
                Spacer(Modifier.width(3.dp))
            }
            Spacer(Modifier.width(3.dp))
            Text("more", fontSize = AxisLabelSize, color = axisInk())
        }
    }
}

/** Which part of the day the user actually delivers in. Horizontal bars — the labels are wide. */
@Composable
fun TimeBandChart(
    bands: List<Int>,
    modifier: Modifier = Modifier
) {
    val labels = listOf("00–04", "04–08", "08–12", "12–16", "16–20", "20–24")
    val ceiling = max(1, bands.maxOrNull() ?: 1)
    if (bands.sum() == 0) {
        ChartEmpty(modifier, "Complete some blocks to see when you deliver")
        return
    }
    val barColor = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val peak = bands.indexOf(bands.maxOrNull() ?: 0)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            val value = bands.getOrElse(index) { 0 }
            val fraction by animateFloatAsState(
                value.toFloat() / ceiling,
                tween(500 + index * 60),
                label = "band"
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    fontSize = AxisLabelSize,
                    color = if (index == peak) MaterialTheme.colorScheme.onSurface else axisInk(),
                    fontWeight = if (index == peak) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.width(48.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(track)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    value.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(22.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/** Weekday strength profile — where the week actually breaks down. */
@Composable
fun WeekdayProfile(
    profile: Map<Int, Float>,
    modifier: Modifier = Modifier
) {
    if (profile.isEmpty()) {
        ChartEmpty(modifier, "Not enough history for a weekday profile")
        return
    }
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { dow ->
            val rate = profile[dow] ?: return@forEach
            val color = ChartPalette.grade(rate)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    names[dow - 1],
                    fontSize = AxisLabelSize,
                    color = axisInk(),
                    modifier = Modifier.width(36.dp)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(rate.coerceIn(0.01f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${(rate * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(38.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun ChartEmpty(modifier: Modifier, message: String) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Shared by the ring gauges: draws a track plus a progress arc with rounded ends. */
internal fun DrawScope.drawGaugeArc(
    progress: Float,
    trackColor: Color,
    progressColor: Color,
    strokeWidthPx: Float
) {
    val inset = strokeWidthPx / 2
    drawArc(
        color = trackColor,
        startAngle = 135f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    )
    drawArc(
        color = progressColor,
        startAngle = 135f,
        sweepAngle = 270f * progress.coerceIn(0f, 1f),
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    )
}
