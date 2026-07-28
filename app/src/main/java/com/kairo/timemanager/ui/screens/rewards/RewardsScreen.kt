package com.kairo.timemanager.ui.screens.rewards

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairo.timemanager.core.Economy
import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.local.entity.AchievementEntity
import com.kairo.timemanager.data.local.entity.RewardEntity
import com.kairo.timemanager.ui.components.GradientHeader
import com.kairo.timemanager.ui.components.KairoCard
import com.kairo.timemanager.ui.components.LabeledProgress
import com.kairo.timemanager.ui.components.SectionHeader
import com.kairo.timemanager.ui.components.StatTile
import androidx.compose.foundation.text.KeyboardOptions

@Composable
fun RewardsScreen(viewModel: RewardsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messageFlow.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            item { RewardsHeader(state) }

            item {
                Column(Modifier.padding(16.dp)) {
                    StreakCard(state, onBuyFreeze = viewModel::buyFreezeToken)

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            label = "Blocks done",
                            value = state.stats.totalTasksCompleted.toString(),
                            caption = "all time",
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = "Time focused",
                            value = TimeUtils.formatDuration(state.stats.totalFocusMinutes),
                            caption = "${state.stats.perfectDays} perfect days",
                            accent = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    SectionHeader(
                        title = "Reward shop",
                        action = "Add your own",
                        onAction = { showAddDialog = true }
                    )
                }
            }

            items(state.rewards, key = { "reward_${it.id}" }) { reward ->
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    RewardRow(
                        reward = reward,
                        coins = state.stats.coins,
                        onRedeem = { viewModel.redeem(reward) },
                        onDelete = if (reward.isCustom) ({ viewModel.deleteReward(reward) }) else null
                    )
                }
            }

            item {
                Column(Modifier.padding(16.dp)) {
                    Spacer(Modifier.height(10.dp))
                    SectionHeader("Achievements · ${state.unlockedCount}/${state.achievements.size}")
                }
            }

            items(state.achievements.chunked(2), key = { it.first().key }) { pair ->
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { achievement ->
                        AchievementCard(achievement, Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    if (showAddDialog) {
        AddRewardDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, description, emoji, cost ->
                viewModel.addCustomReward(title, description, emoji, cost)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun RewardsHeader(state: RewardsUiState) {
    val statusPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    GradientHeader {
        Spacer(Modifier.height(statusPadding))
        Text(
            "Level ${state.level.level}",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.85f)
        )
        Text(
            state.level.title,
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(9.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(state.level.progress)
                            .height(9.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "${state.level.xpIntoLevel} / ${state.level.xpForNextLevel} XP to level ${state.level.level + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Spacer(Modifier.width(14.dp))
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f)) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🪙", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        state.stats.coins.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun StreakCard(state: RewardsUiState, onBuyFreeze: () -> Unit) {
    val streak = state.streak
    KairoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(
                        if (streak.atRisk) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.tertiaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (streak.atRisk) "⚠️" else "🔥", style = MaterialTheme.typography.titleLarge)
                    Text(
                        streak.current.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (streak.current == 0) "No streak yet"
                    else "${streak.current}-day streak",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    when {
                        streak.atRisk -> "Finish ${(Economy.STREAK_COMPLETION_THRESHOLD * 100).toInt()}% of today's blocks to keep it alive."
                        streak.current == 0 -> "Complete ${(Economy.STREAK_COMPLETION_THRESHOLD * 100).toInt()}% of a day's blocks to start one."
                        else -> "${streak.daysToMilestone} day(s) to the ${streak.nextMilestone}-day milestone."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(9.dp))
                LabeledProgress(
                    label = "Longest ${streak.longest} days",
                    progress = if (streak.nextMilestone == 0) 0f
                    else streak.current.toFloat() / streak.nextMilestone,
                    trailing = "${streak.current}/${streak.nextMilestone}",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                repeat(Economy.MAX_FREEZE_TOKENS) { index ->
                    Text(
                        "🧊",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .alpha(if (index < state.stats.freezeTokens) 1f else 0.22f)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Streak freezes cover one missed day automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                shape = CircleShape,
                color = if (state.canBuyFreeze) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable(enabled = state.canBuyFreeze, onClick = onBuyFreeze)
            ) {
                Text(
                    "Buy · ${state.freezeCost}🪙",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.canBuyFreeze) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun RewardRow(
    reward: RewardEntity,
    coins: Int,
    onRedeem: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val affordable = coins >= reward.cost
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(reward.emoji, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    reward.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (reward.description.isNotBlank()) {
                    Text(
                        reward.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                if (reward.timesRedeemed > 0) {
                    Text(
                        "Claimed ${reward.timesRedeemed}×" +
                            (reward.lastRedeemedAt?.let { ", last ${TimeUtils.relativeTime(it)}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete reward",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Surface(
                shape = CircleShape,
                color = if (affordable) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable(enabled = affordable, onClick = onRedeem)
            ) {
                Text(
                    "${reward.cost}🪙",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (affordable) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun AchievementCard(achievement: AchievementEntity, modifier: Modifier = Modifier) {
    val unlocked = achievement.unlockedAt != null
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (unlocked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                achievement.emoji,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.alpha(if (unlocked) 1f else 0.3f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                achievement.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(Modifier.height(3.dp))
            Text(
                achievement.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Spacer(Modifier.height(9.dp))
            if (unlocked) {
                Text(
                    "Unlocked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(
                                (achievement.progress.toFloat() / achievement.target).coerceIn(0f, 1f)
                            )
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${achievement.progress}/${achievement.target}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddRewardDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🎁") }
    var cost by remember { mutableStateOf("100") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a reward") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Rewards work best when they're things you actually want and would otherwise feel guilty about.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Reward") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it.take(2) },
                        label = { Text("Icon") },
                        singleLine = true,
                        modifier = Modifier.width(90.dp)
                    )
                    OutlinedTextField(
                        value = cost,
                        onValueChange = { input -> cost = input.filter { it.isDigit() }.take(4) },
                        label = { Text("Coins") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, description, emoji, cost.toIntOrNull() ?: 100) },
                enabled = title.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
