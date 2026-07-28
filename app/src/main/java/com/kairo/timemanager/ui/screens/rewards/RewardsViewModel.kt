package com.kairo.timemanager.ui.screens.rewards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.timemanager.core.Economy
import com.kairo.timemanager.core.LevelInfo
import com.kairo.timemanager.core.Leveling
import com.kairo.timemanager.data.local.entity.AchievementEntity
import com.kairo.timemanager.data.local.entity.RewardEntity
import com.kairo.timemanager.data.local.entity.UserStatsEntity
import com.kairo.timemanager.data.repo.ProgressRepository
import com.kairo.timemanager.data.repo.StreakSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RewardsUiState(
    val stats: UserStatsEntity = UserStatsEntity(),
    val level: LevelInfo = Leveling.infoFor(0),
    val streak: StreakSnapshot = StreakSnapshot(0, 0, 0, -1, false, 3),
    val rewards: List<RewardEntity> = emptyList(),
    val achievements: List<AchievementEntity> = emptyList()
) {
    val unlockedCount: Int get() = achievements.count { it.unlockedAt != null }
    val freezeCost: Int get() = Economy.FREEZE_TOKEN_COST
    val canBuyFreeze: Boolean
        get() = stats.freezeTokens < Economy.MAX_FREEZE_TOKENS && stats.coins >= Economy.FREEZE_TOKEN_COST
}

@HiltViewModel
class RewardsViewModel @Inject constructor(
    private val progressRepository: ProgressRepository
) : ViewModel() {

    private val messages = Channel<String>(Channel.BUFFERED)
    val messageFlow: Flow<String> = messages.receiveAsFlow()

    val state: StateFlow<RewardsUiState> = combine(
        progressRepository.observeStats(),
        progressRepository.observeRewards(),
        progressRepository.observeAchievements()
    ) { stats, rewards, achievements ->
        RewardsUiState(
            stats = stats,
            level = Leveling.infoFor(stats.xp),
            streak = StreakSnapshot(
                current = stats.currentStreak,
                longest = stats.longestStreak,
                freezeTokens = stats.freezeTokens,
                lastStreakDay = stats.lastStreakDay,
                atRisk = stats.currentStreak > 0 &&
                    stats.lastStreakDay < com.kairo.timemanager.core.TimeUtils.todayEpochDay(),
                nextMilestone = listOf(3, 7, 14, 30, 60, 100, 180, 365)
                    .firstOrNull { it > stats.currentStreak } ?: (stats.currentStreak + 100)
            ),
            rewards = rewards,
            achievements = achievements
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RewardsUiState())

    init {
        viewModelScope.launch {
            progressRepository.ensureSeeded()
            progressRepository.refreshAchievements()
        }
    }

    fun redeem(reward: RewardEntity) {
        viewModelScope.launch {
            when (val result = progressRepository.redeemReward(reward.id)) {
                is ProgressRepository.RedeemResult.Success ->
                    messages.send("${reward.emoji} ${reward.title} redeemed — enjoy it, you earned it.")
                is ProgressRepository.RedeemResult.NotEnoughCoins ->
                    messages.send("${result.missing} more coins needed for ${reward.title}.")
                ProgressRepository.RedeemResult.NotFound ->
                    messages.send("That reward is no longer available.")
            }
        }
    }

    fun buyFreezeToken() {
        viewModelScope.launch {
            when (val result = progressRepository.buyFreezeToken()) {
                is ProgressRepository.RedeemResult.Success ->
                    messages.send("🧊 Streak freeze bought. It covers one missed day automatically.")
                is ProgressRepository.RedeemResult.NotEnoughCoins ->
                    if (result.missing == 0) messages.send("You're already carrying the maximum freezes.")
                    else messages.send("${result.missing} more coins needed for a streak freeze.")
                ProgressRepository.RedeemResult.NotFound -> Unit
            }
        }
    }

    fun addCustomReward(title: String, description: String, emoji: String, cost: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            progressRepository.addCustomReward(title, description, emoji, cost)
            messages.send("Added \"$title\" to your reward shop.")
        }
    }

    fun deleteReward(reward: RewardEntity) {
        viewModelScope.launch {
            progressRepository.deleteReward(reward.id)
            messages.send("Removed \"${reward.title}\".")
        }
    }
}
