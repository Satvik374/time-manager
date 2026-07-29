package com.kairo.timemanager.ui.screens.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.kairo.timemanager.data.local.entity.RecommendationEntity
import com.kairo.timemanager.data.repo.RewardEvent
import com.kairo.timemanager.ui.components.CoinPill
import com.kairo.timemanager.ui.components.EmptyState
import com.kairo.timemanager.ui.components.GradientHeader
import com.kairo.timemanager.ui.components.KairoCard
import com.kairo.timemanager.ui.components.LabeledProgress
import com.kairo.timemanager.ui.components.MarkdownText
import com.kairo.timemanager.ui.components.ProgressRing
import com.kairo.timemanager.ui.components.SectionHeader
import com.kairo.timemanager.ui.components.StreakBadge
import com.kairo.timemanager.ui.theme.ChartPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(
    onOpenEditor: (Long?, Long?) -> Unit,
    onOpenCoach: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRewards: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requesting by viewModel.requestingSuggestion.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Keeps "now" markers honest without a full recomposition storm.
    var nowMinute by remember { mutableIntStateOf(TimeUtils.nowMinuteOfDay()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMinute = TimeUtils.nowMinuteOfDay()
            delay(30_000)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.rewardEvents.collect { event ->
            describeReward(event)?.let {
                snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onOpenEditor(null, state.epochDay) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New block")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item { TodayHeader(state, nowMinute, onOpenSettings, onOpenRewards, viewModel::shiftDay, viewModel::goToToday) }

            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(16.dp))
                    DaySummaryCard(state)
                }
            }

            if (state.recommendations.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(14.dp))
                        SectionHeader("From your coach")
                    }
                }
                items(state.recommendations, key = { "rec_${it.id}" }) { recommendation ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        RecommendationCard(
                            recommendation = recommendation,
                            onAccept = { viewModel.acceptRecommendation(recommendation.id) },
                            onDismiss = { viewModel.dismissRecommendation(recommendation.id) },
                            onOpenCoach = onOpenCoach
                        )
                    }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(18.dp))
                    SectionHeader(
                        title = if (state.isToday) "Today's blocks" else "Blocks",
                        action = if (state.agenda.isEmpty()) null else "Ask coach",
                        onAction = if (state.agenda.isEmpty()) null else onOpenCoach
                    )
                }
            }

            if (state.agenda.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        icon = Icons.Filled.EventAvailable,
                        title = "Nothing scheduled",
                        body = "Add a block yourself, or ask the coach to build the day for you.",
                        actionLabel = "Ask the coach",
                        onAction = onOpenCoach
                    )
                }
            }

            items(state.agenda, key = { "task_${it.id}" }) { task ->
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    TaskRow(
                        task = task,
                        use24h = state.prefs.use24HourClock,
                        nowMinute = nowMinute,
                        onToggle = { viewModel.toggleTask(task) },
                        onToggleChecklistItem = { itemId, checked ->
                            viewModel.toggleChecklistItem(task, itemId, checked)
                        },
                        onOpen = { onOpenEditor(task.id, task.epochDay) },
                        onSkip = { viewModel.skipTask(task) }
                    )
                }
            }

            item {
                Column(Modifier.padding(16.dp)) {
                    Spacer(Modifier.height(8.dp))
                    CoachNudgeCard(
                        loading = requesting,
                        onAsk = {
                            viewModel.requestSuggestion { posted ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (posted) "New suggestion added above."
                                        else "No suggestion this time — either the coach had nothing specific to add, or it isn't configured yet."
                                    )
                                }
                            }
                        },
                        onOpenCoach = onOpenCoach
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayHeader(
    state: TodayUiState,
    nowMinute: Int,
    onOpenSettings: () -> Unit,
    onOpenRewards: () -> Unit,
    onShiftDay: (Long) -> Unit,
    onToday: () -> Unit
) {
    val statusPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    GradientHeader {
        Spacer(Modifier.height(statusPadding))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        !state.isToday -> TimeUtils.formatDayLabel(state.epochDay)
                        state.profileName.isBlank() -> TimeUtils.greeting(nowMinute)
                        else -> "${TimeUtils.greeting(nowMinute)}, ${state.profileName}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Text(
                    TimeUtils.formatFullDate(state.epochDay),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            StreakBadge(
                streak = state.streak.current,
                atRisk = state.streak.atRisk,
                onClick = onOpenRewards
            )
            Spacer(Modifier.width(8.dp))
            CoinPill(state.coins, onClick = onOpenRewards)
            Spacer(Modifier.weight(1f))
            DayStepper(state, onShiftDay, onToday)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun DayStepper(state: TodayUiState, onShiftDay: (Long) -> Unit, onToday: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShiftDay(-1) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day", tint = Color.White)
        }
        if (!state.isToday) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.clickable(onClick = onToday)
            ) {
                Text(
                    "Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
        IconButton(onClick = { onShiftDay(1) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next day", tint = Color.White)
        }
    }
}

@Composable
private fun DaySummaryCard(state: TodayUiState) {
    val snapshot = state.snapshot
    KairoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = snapshot.completionRate,
                size = 104.dp,
                strokeWidth = 11.dp,
                color = ChartPalette.grade(snapshot.completionRate)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${snapshot.completed}/${snapshot.planned}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "blocks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    ScoreCopy.grade(snapshot.completionRate),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    when {
                        snapshot.planned == 0 -> "No blocks scheduled — a clean slate."
                        snapshot.isPerfect -> "Every block done. That's a perfect day."
                        snapshot.remaining == 1 -> "One block left."
                        else -> "${snapshot.remaining} blocks left · ${TimeUtils.formatDuration(snapshot.completedMinutes)} focused"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LabeledProgress(
                    label = "Level ${state.level.level} · ${state.level.title}",
                    progress = state.level.progress,
                    trailing = "${state.level.xpIntoLevel}/${state.level.xpForNextLevel} XP"
                )
            }
        }

        if (snapshot.checklistTotal > 0) {
            Spacer(Modifier.height(14.dp))
            LabeledProgress(
                label = "Checklist steps",
                progress = snapshot.checklistDone.toFloat() / snapshot.checklistTotal,
                trailing = "${snapshot.checklistDone}/${snapshot.checklistTotal}",
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: RecommendationEntity,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onOpenCoach: () -> Unit
) {
    var visible by remember { androidx.compose.runtime.mutableStateOf(true) }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut()
    ) {
        KairoCard(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        recommendation.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    MarkdownText(
                        markdown = recommendation.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallAction("Do it", filled = true) {
                            onAccept()
                            onOpenCoach()
                        }
                        SmallAction("Keep for later") { onAccept() }
                    }
                }
                IconButton(
                    onClick = {
                        visible = false
                        onDismiss()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Dismiss suggestion",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallAction(text: String, filled: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (filled) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = if (filled) null
        else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun CoachNudgeCard(loading: Boolean, onAsk: () -> Unit, onOpenCoach: () -> Unit) {
    KairoCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Want a second opinion?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "The coach reads your real numbers and suggests one small change. Entirely optional.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                SmallAction("Ask", filled = true, onClick = onAsk)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Open the Coach tab →",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onOpenCoach)
        )
    }
}

private fun describeReward(event: RewardEvent): String? = when (event) {
    is RewardEvent.Xp -> null // too frequent to interrupt for on its own
    is RewardEvent.Coins -> "+${event.amount} 🪙"
    is RewardEvent.LevelUp -> "⭐ Level ${event.level} — ${event.title}"
    is RewardEvent.Unlocked -> "${event.achievement.emoji} ${event.achievement.title} unlocked"
    is RewardEvent.StreakExtended ->
        if (event.bonusCoins > 0) "🔥 ${event.streak}-day streak · +${event.bonusCoins} 🪙"
        else "🔥 ${event.streak}-day streak"
    is RewardEvent.StreakFrozen -> "🧊 Streak freeze used — ${event.tokensLeft} left"
    is RewardEvent.StreakLost -> "Streak reset from ${event.previous}. Fresh start today."
    is RewardEvent.PerfectDay -> "🌟 Perfect day! +${event.xp} XP, +${event.coins} 🪙"
}
