package com.kairo.timemanager.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairo.timemanager.core.DayMask
import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.local.entity.Priority
import com.kairo.timemanager.data.local.entity.Recurrence
import com.kairo.timemanager.ui.components.KairoCard
import com.kairo.timemanager.ui.components.KairoChip
import com.kairo.timemanager.ui.components.SectionHeader
import com.kairo.timemanager.ui.theme.TaskPalette
import com.kairo.timemanager.ui.theme.parseHexColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(
    onBack: () -> Unit,
    viewModel: TaskEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var newItem by remember { mutableStateOf("") }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New block" else "Edit block") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Delete block",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .imePadding()
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text("What is this block?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
                SectionHeader("When")
                KairoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier
                                .weight(1f)
                                .clickable { showTimePicker = true }
                        ) {
                            Text(
                                "Starts",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                TimeUtils.formatTime(state.startMinute, state.use24h),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Ends",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                TimeUtils.formatTime(state.endMinute % (24 * 60), state.use24h),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Length · ${TimeUtils.formatDuration(state.durationMinutes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 25, 30, 45, 60, 90, 120, 180).forEach { minutes ->
                            KairoChip(
                                text = TimeUtils.formatDuration(minutes),
                                selected = state.durationMinutes == minutes,
                                onClick = { viewModel.setDuration(minutes) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                SectionHeader("Repeat")
                KairoCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KairoChip(
                            text = "Once",
                            selected = state.recurrence == Recurrence.ONCE,
                            onClick = { viewModel.setRecurrence(Recurrence.ONCE) }
                        )
                        KairoChip(
                            text = "Every day",
                            selected = state.recurrence == Recurrence.DAILY,
                            onClick = { viewModel.setRecurrence(Recurrence.DAILY) }
                        )
                        KairoChip(
                            text = "Certain days",
                            selected = state.recurrence == Recurrence.WEEKLY,
                            onClick = { viewModel.setRecurrence(Recurrence.WEEKLY) }
                        )
                    }

                    when (state.recurrence) {
                        Recurrence.ONCE -> {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "On ${TimeUtils.formatFullDate(state.epochDay)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Recurrence.WEEKLY -> {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DayMask.labels.forEachIndexed { index, label ->
                                    val selected = state.daysMask and (1 shl index) != 0
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable { viewModel.toggleDay(index) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                KairoChip(
                                    text = "Weekdays",
                                    selected = state.daysMask == DayMask.WEEKDAYS,
                                    onClick = { viewModel.setDaysMask(DayMask.WEEKDAYS) }
                                )
                                KairoChip(
                                    text = "Weekends",
                                    selected = state.daysMask == DayMask.WEEKENDS,
                                    onClick = { viewModel.setDaysMask(DayMask.WEEKENDS) }
                                )
                            }
                        }
                        else -> Unit
                    }
                }

                if (state.conflicts.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    ConflictWarning(state)
                }

                Spacer(Modifier.height(16.dp))
                SectionHeader("Checklist")
                KairoCard {
                    Text(
                        "Break the block into steps you can tick off. The coach can add these too.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    state.checklist.forEachIndexed { index, line ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = line,
                                onValueChange = { viewModel.updateChecklistLine(index, it) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                            Column {
                                IconButton(
                                    onClick = { viewModel.moveChecklistLine(index, -1) },
                                    modifier = Modifier.size(24.dp),
                                    enabled = index > 0
                                ) {
                                    Icon(
                                        Icons.Filled.ArrowUpward,
                                        contentDescription = "Move up",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.moveChecklistLine(index, 1) },
                                    modifier = Modifier.size(24.dp),
                                    enabled = index < state.checklist.lastIndex
                                ) {
                                    Icon(
                                        Icons.Filled.ArrowDownward,
                                        contentDescription = "Move down",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.removeChecklistLine(index) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove step",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newItem,
                            onValueChange = { newItem = it },
                            placeholder = { Text("Add a step…") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                viewModel.addChecklistLine(newItem)
                                newItem = ""
                            })
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                viewModel.addChecklistLine(newItem)
                                newItem = ""
                            },
                            enabled = newItem.isNotBlank()
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add step")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                SectionHeader("Details")
                KairoCard {
                    OutlinedTextField(
                        value = state.category,
                        onValueChange = viewModel::setCategory,
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.knownCategories.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.knownCategories.forEach { category ->
                                KairoChip(
                                    text = category,
                                    selected = state.category == category,
                                    onClick = { viewModel.setCategory(category) }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Priority",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Priority.LOW, Priority.NORMAL, Priority.HIGH).forEach { priority ->
                            KairoChip(
                                text = Priority.label(priority),
                                selected = state.priority == priority,
                                onClick = { viewModel.setPriority(priority) }
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Colour",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TaskPalette.forEach { hex ->
                            val selected = state.colorHex.equals(hex, ignoreCase = true)
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(hex))
                                    .then(
                                        if (selected) Modifier.border(
                                            3.dp,
                                            MaterialTheme.colorScheme.onSurface,
                                            CircleShape
                                        ) else Modifier
                                    )
                                    .clickable { viewModel.setColor(hex) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Reminder",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(-1 to "None", 0 to "At start", 5 to "5m before", 15 to "15m before", 30 to "30m before")
                            .forEach { (minutes, label) ->
                                KairoChip(
                                    text = label,
                                    selected = state.reminderMinutes == minutes,
                                    onClick = { viewModel.setReminder(minutes) }
                                )
                            }
                    }

                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = viewModel::setNote,
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth().height(110.dp)
                    )
                }

                if (!state.isNew) {
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = viewModel::archive, modifier = Modifier.fillMaxWidth()) {
                        Text("Archive instead (keeps past history)")
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(Modifier.padding(16.dp).navigationBarsPadding()) {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = CircleShape
                    ) {
                        Text(if (state.isNew) "Add to timetable" else "Save changes")
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        StartTimeDialog(
            initialMinute = state.startMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { minute ->
                viewModel.setStart(minute)
                showTimePicker = false
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this block?") },
            text = {
                Text(
                    "This removes the block and every completion it recorded. " +
                        "Archiving keeps the history and just takes it off your schedule."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ConflictWarning(state: EditorUiState) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "Overlaps ${state.conflicts.size} existing block(s)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    state.conflicts.joinToString(", ") { "${it.title} (${it.timeLabel})" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTimeDialog(
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start time") },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour * 60 + pickerState.minute) }) {
                Text("Set")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
