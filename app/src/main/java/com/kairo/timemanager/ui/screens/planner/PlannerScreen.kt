package com.kairo.timemanager.ui.screens.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairo.timemanager.core.DayMask
import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.local.entity.Recurrence
import com.kairo.timemanager.data.local.entity.TaskEntity
import com.kairo.timemanager.ui.components.EmptyState
import com.kairo.timemanager.ui.components.GradientHeader
import com.kairo.timemanager.ui.components.KairoChip
import com.kairo.timemanager.ui.components.SectionHeader
import com.kairo.timemanager.ui.screens.today.TaskRow
import com.kairo.timemanager.ui.theme.parseHexColor

@Composable
fun PlannerScreen(
    onOpenEditor: (Long?, Long?) -> Unit,
    viewModel: PlannerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onOpenEditor(null, state.selectedDay) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New block")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                PlannerHeader(
                    state = state,
                    onShiftWeek = viewModel::shiftWeek,
                    onSelectDay = viewModel::selectDay,
                    onToday = viewModel::goToToday,
                    onSetView = viewModel::setView
                )
            }

            if (state.categories.size > 1) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KairoChip(
                            text = "All",
                            selected = state.categoryFilter == null,
                            onClick = { viewModel.setCategoryFilter(null) }
                        )
                        state.categories.forEach { category ->
                            KairoChip(
                                text = category,
                                selected = state.categoryFilter == category,
                                onClick = { viewModel.setCategoryFilter(category) }
                            )
                        }
                    }
                }
            }

            when (state.view) {
                PlannerView.TIMELINE -> {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Spacer(Modifier.height(4.dp))
                            SectionHeader(TimeUtils.formatFullDate(state.selectedDay))
                            DayTimeline(
                                state = state,
                                onOpen = { taskId -> onOpenEditor(taskId, state.selectedDay) }
                            )
                            Spacer(Modifier.height(18.dp))
                            SectionHeader("Blocks on this day")
                        }
                    }
                    if (state.filteredAgenda.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Filled.CalendarMonth,
                                title = "This day is open",
                                body = "Tap + to schedule something, or switch to the list view to see every recurring block.",
                                actionLabel = "Add a block",
                                onAction = { onOpenEditor(null, state.selectedDay) }
                            )
                        }
                    }
                    items(state.filteredAgenda, key = { "p_${it.id}" }) { task ->
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            TaskRow(
                                task = task,
                                use24h = state.prefs.use24HourClock,
                                onToggle = { viewModel.toggleTask(task) },
                                onToggleChecklistItem = { itemId, checked ->
                                    viewModel.toggleChecklistItem(task, itemId, checked)
                                },
                                onOpen = { onOpenEditor(task.id, state.selectedDay) }
                            )
                        }
                    }
                }

                PlannerView.LIST -> {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Spacer(Modifier.height(4.dp))
                            SectionHeader("Every block you've defined")
                        }
                    }
                    if (state.filteredTasks.isEmpty()) {
                        item {
                            EmptyState(
                                icon = Icons.Filled.FormatListBulleted,
                                title = "No blocks yet",
                                body = "Blocks you create — one-off or recurring — all live here.",
                                actionLabel = "Create your first",
                                onAction = { onOpenEditor(null, state.selectedDay) }
                            )
                        }
                    }
                    items(state.filteredTasks, key = { "def_${it.id}" }) { task ->
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            TaskDefinitionRow(
                                task = task,
                                use24h = state.prefs.use24HourClock,
                                onOpen = { onOpenEditor(task.id, state.selectedDay) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannerHeader(
    state: PlannerUiState,
    onShiftWeek: (Long) -> Unit,
    onSelectDay: (Long) -> Unit,
    onToday: () -> Unit,
    onSetView: (PlannerView) -> Unit
) {
    val statusPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    GradientHeader {
        Spacer(Modifier.height(statusPadding))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Planner",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Text(
                    TimeUtils.formatMonth(state.selectedDay),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }
            ViewToggle(state.view, onSetView)
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShiftWeek(-1) }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous week", tint = Color.White)
            }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                state.week.forEach { cell ->
                    WeekDayChip(
                        cell = cell,
                        selected = cell.epochDay == state.selectedDay,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectDay(cell.epochDay) }
                    )
                }
            }
            IconButton(onClick = { onShiftWeek(1) }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next week", tint = Color.White)
            }
        }

        if (state.selectedDay != TimeUtils.todayEpochDay()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.clickable(onClick = onToday)
            ) {
                Text(
                    "Jump to today",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ViewToggle(current: PlannerView, onSetView: (PlannerView) -> Unit) {
    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f)) {
        Row(Modifier.padding(3.dp)) {
            listOf(
                PlannerView.TIMELINE to Icons.Filled.ViewTimeline,
                PlannerView.LIST to Icons.Filled.FormatListBulleted
            ).forEach { (view, icon) ->
                val selected = current == view
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color.White.copy(alpha = 0.9f) else Color.Transparent)
                        .clickable { onSetView(view) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = view.name.lowercase(),
                        tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekDayChip(
    cell: WeekDayCell,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isToday = cell.epochDay == TimeUtils.todayEpochDay()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    selected -> Color.White.copy(alpha = 0.95f)
                    isToday -> Color.White.copy(alpha = 0.24f)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            DayMask.labels[(TimeUtils.dayOfWeek(cell.epochDay).value - 1)],
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            TimeUtils.date(cell.epochDay).dayOfMonth.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else Color.White,
            fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(Modifier.height(5.dp))
        // Tiny completion pip — presence of work and how much of it landed
        Box(
            Modifier
                .size(width = 16.dp, height = 3.dp)
                .clip(CircleShape)
                .background(
                    when {
                        cell.planned == 0 -> Color.Transparent
                        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f + cell.rate * 0.75f)
                        else -> Color.White.copy(alpha = 0.3f + cell.rate * 0.7f)
                    }
                )
        )
    }
}

/**
 * A proportional day timeline: each block is drawn at its real position between the earliest
 * and latest scheduled minute, so gaps and clashes are visible at a glance.
 */
@Composable
private fun DayTimeline(state: PlannerUiState, onOpen: (Long) -> Unit) {
    val agenda = state.filteredAgenda
    if (agenda.isEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No blocks scheduled",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val start = (agenda.minOf { it.startMinute } / 60) * 60
    val end = (((agenda.maxOf { it.endMinute } + 59) / 60) * 60).coerceAtMost(24 * 60)
    val span = (end - start).coerceAtLeast(60)
    val pixelsPerMinute = 1.15f
    val totalHeight = (span * pixelsPerMinute).dp
    val nowMinute = TimeUtils.nowMinuteOfDay()
    val showNow = state.selectedDay == TimeUtils.todayEpochDay() && nowMinute in start..end

    Row(Modifier.fillMaxWidth().height(totalHeight)) {
        // Hour gutter
        Column(Modifier.width(44.dp)) {
            var hour = start / 60
            while (hour * 60 <= end) {
                Box(Modifier.height((60 * pixelsPerMinute).dp)) {
                    Text(
                        TimeUtils.formatTime(hour * 60, state.prefs.use24HourClock)
                            .replace(":00", ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 0.dp),
                        textAlign = TextAlign.End
                    )
                }
                hour++
            }
        }

        Box(Modifier.weight(1f).fillMaxSize()) {
            // Hour rules
            Column {
                var hour = start / 60
                while (hour * 60 <= end) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height((60 * pixelsPerMinute).dp)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    hour++
                }
            }

            agenda.forEach { task ->
                val top = ((task.startMinute - start) * pixelsPerMinute).dp
                val blockHeight = (task.task.durationMinutes * pixelsPerMinute).dp
                val accent = parseHexColor(task.task.colorHex)
                Box(
                    Modifier
                        .padding(top = top, start = 6.dp, end = 2.dp)
                        .fillMaxWidth()
                        .height(blockHeight.coerceAtLeast(26.dp))
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = if (task.completed) 0.25f else 0.85f))
                        .clickable { onOpen(task.id) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Column {
                        Text(
                            task.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (task.completed) MaterialTheme.colorScheme.onSurface else Color.White,
                            maxLines = 1,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (blockHeight > 40.dp) {
                            Text(
                                TimeUtils.formatRange(
                                    task.startMinute,
                                    task.task.durationMinutes,
                                    state.prefs.use24HourClock
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant
                                else Color.White.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (showNow) {
                Box(
                    Modifier
                        .padding(top = ((nowMinute - start) * pixelsPerMinute).dp)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.error)
                )
            }
        }
    }
}

@Composable
private fun TaskDefinitionRow(task: TaskEntity, use24h: Boolean, onOpen: () -> Unit) {
    val accent = parseHexColor(task.colorHex)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    task.title.take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = accent
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${TimeUtils.formatRange(task.startMinute, task.durationMinutes, use24h)} · " +
                        repeatLabel(task),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                task.category,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun repeatLabel(task: TaskEntity): String = when (task.recurrence) {
    Recurrence.DAILY -> "Every day"
    Recurrence.WEEKLY -> DayMask.describe(task.daysMask)
    else -> task.specificDate?.let { TimeUtils.formatDayLabel(it) } ?: "One-off"
}
