package com.kairo.timemanager.ui.screens.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.local.entity.Priority
import com.kairo.timemanager.data.repo.ScheduledTask
import com.kairo.timemanager.ui.theme.parseHexColor

/**
 * One block on the agenda: a colour spine, the time, a tick target, and an expandable
 * checklist. The checklist is the point of the app, so it gets a real progress affordance.
 */
@Composable
fun TaskRow(
    task: ScheduledTask,
    use24h: Boolean,
    modifier: Modifier = Modifier,
    nowMinute: Int = TimeUtils.nowMinuteOfDay(),
    onToggle: () -> Unit = {},
    onToggleChecklistItem: (Long, Boolean) -> Unit = { _, _ -> },
    onOpen: () -> Unit = {},
    onSkip: () -> Unit = {}
) {
    var expanded by remember(task.id) { mutableStateOf(false) }
    val accent = parseHexColor(task.task.colorHex)
    val isNow = task.isNow(nowMinute)
    val overdue = task.isOverdue(nowMinute)

    val container = when {
        task.completed -> MaterialTheme.colorScheme.surfaceContainer
        isNow -> accent.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = container,
        border = if (isNow) BorderStroke(1.5.dp, accent.copy(alpha = 0.55f)) else null
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen() }
                    .padding(start = 0.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Colour spine doubles as the category identity marker
                Box(
                    Modifier
                        .padding(start = 10.dp)
                        .width(4.dp)
                        .height(if (task.hasChecklist) 54.dp else 40.dp)
                        .clip(CircleShape)
                        .background(if (task.completed) accent.copy(alpha = 0.4f) else accent)
                )
                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            task.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .alpha(if (task.completed || task.skipped) 0.55f else 1f),
                            maxLines = 1
                        )
                        if (task.task.priority == Priority.HIGH && !task.completed) {
                            Spacer(Modifier.width(6.dp))
                            Text("★", color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            TimeUtils.formatRange(task.startMinute, task.task.durationMinutes, use24h),
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                isNow -> accent
                                overdue -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Text(
                            "  ·  ${task.task.category}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        if (isNow) {
                            Spacer(Modifier.width(6.dp))
                            LiveDot(accent)
                        }
                    }
                    if (task.hasChecklist) {
                        Spacer(Modifier.height(7.dp))
                        ChecklistSummary(
                            done = task.checklistDone,
                            total = task.checklistTotal,
                            accent = accent,
                            expanded = expanded,
                            onClick = { expanded = !expanded }
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))
                CompletionTick(
                    completed = task.completed,
                    skipped = task.skipped,
                    accent = accent,
                    onClick = onToggle,
                    onLongClick = onSkip
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(200)) + fadeIn(),
                exit = shrinkVertically(tween(160)) + fadeOut()
            ) {
                Column(Modifier.padding(start = 26.dp, end = 14.dp, bottom = 14.dp)) {
                    task.checklist.forEach { line ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggleChecklistItem(line.id, line.checked) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(19.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (line.checked) accent
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (line.checked) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                line.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (line.checked) TextDecoration.LineThrough else null,
                                modifier = Modifier.alpha(if (line.checked) 0.55f else 1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChecklistSummary(
    done: Int,
    total: Int,
    accent: Color,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val fraction by animateFloatAsState(
        if (total == 0) 0f else done.toFloat() / total,
        tween(400),
        label = "checklist"
    )
    Row(
        Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(64.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "$done/$total steps",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Hide steps" else "Show steps",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompletionTick(
    completed: Boolean,
    skipped: Boolean,
    accent: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val scale by animateFloatAsState(if (completed) 1f else 0.92f, tween(180), label = "tick")
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                when {
                    completed -> accent
                    skipped -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                }
            )
            .then(
                if (!completed && !skipped)
                    Modifier.border(2.dp, accent.copy(alpha = 0.5f), CircleShape)
                else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            completed -> Icon(
                Icons.Filled.Check,
                contentDescription = "Mark as not done",
                tint = Color.White,
                modifier = Modifier.size(19.dp).alpha(scale)
            )
            skipped -> Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Unit
        }
    }
}

@Composable
private fun LiveDot(color: Color) {
    val alpha by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveAlpha"
    )
    Box(
        Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}
