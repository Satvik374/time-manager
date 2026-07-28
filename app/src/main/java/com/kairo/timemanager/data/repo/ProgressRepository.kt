package com.kairo.timemanager.data.repo

import com.kairo.timemanager.core.DayOutcome
import com.kairo.timemanager.core.Economy
import com.kairo.timemanager.core.Leveling
import com.kairo.timemanager.core.StreakEngine
import com.kairo.timemanager.core.StreakEvent
import com.kairo.timemanager.core.StreakState
import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.local.AchievementCatalog
import com.kairo.timemanager.data.local.RewardCatalog
import com.kairo.timemanager.data.local.dao.AchievementDao
import com.kairo.timemanager.data.local.dao.RewardDao
import com.kairo.timemanager.data.local.dao.StatsDao
import com.kairo.timemanager.data.local.dao.TaskLogDao
import com.kairo.timemanager.data.local.entity.AchievementEntity
import com.kairo.timemanager.data.local.entity.RewardEntity
import com.kairo.timemanager.data.local.entity.UserStatsEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Things worth celebrating in the UI right after an action. */
sealed interface RewardEvent {
    data class Xp(val amount: Int, val reason: String) : RewardEvent
    data class Coins(val amount: Int, val reason: String) : RewardEvent
    data class LevelUp(val level: Int, val title: String) : RewardEvent
    data class Unlocked(val achievement: AchievementEntity) : RewardEvent
    data class StreakExtended(val streak: Int, val bonusCoins: Int) : RewardEvent
    data class StreakFrozen(val streak: Int, val tokensLeft: Int) : RewardEvent
    data class StreakLost(val previous: Int) : RewardEvent
    data class PerfectDay(val xp: Int, val coins: Int) : RewardEvent
}

/**
 * Owns XP, coins, levels, streaks, achievements and the reward shop.
 * Every mutation funnels through [mutex] so concurrent taps can't double-spend or double-award.
 */
@Singleton
class ProgressRepository @Inject constructor(
    private val statsDao: StatsDao,
    private val achievementDao: AchievementDao,
    private val rewardDao: RewardDao,
    private val logDao: TaskLogDao,
    private val taskRepository: TaskRepository
) {

    private val mutex = Mutex()

    fun observeStats(): Flow<UserStatsEntity> = statsDao.observe().map { it ?: UserStatsEntity() }

    fun observeAchievements(): Flow<List<AchievementEntity>> = achievementDao.observeAll()

    fun observeRewards(): Flow<List<RewardEntity>> = rewardDao.observeAll()

    fun observeStreak(): Flow<StreakSnapshot> = statsDao.observe().map { stats ->
        streakSnapshot(stats ?: UserStatsEntity())
    }

    suspend fun stats(): UserStatsEntity = statsDao.getOrCreate()

    suspend fun rewardsSnapshot(): List<RewardEntity> = rewardDao.getAll()

    suspend fun unlockedAchievementTitles(): List<String> =
        achievementDao.getAll().filter { it.unlockedAt != null }.map { it.title }

    private fun streakSnapshot(stats: UserStatsEntity): StreakSnapshot {
        val today = TimeUtils.todayEpochDay()
        return StreakSnapshot(
            current = stats.currentStreak,
            longest = stats.longestStreak,
            freezeTokens = stats.freezeTokens,
            lastStreakDay = stats.lastStreakDay,
            atRisk = stats.currentStreak > 0 && stats.lastStreakDay < today,
            nextMilestone = nextMilestone(stats.currentStreak)
        )
    }

    private fun nextMilestone(streak: Int): Int =
        listOf(3, 7, 14, 30, 60, 100, 180, 365).firstOrNull { it > streak } ?: (streak + 100)

    /** Populates the achievement catalogue and the starter reward shop exactly once. */
    suspend fun ensureSeeded() = mutex.withLock {
        statsDao.getOrCreate()
        val existing = achievementDao.getAll().associateBy { it.key }
        val missing = AchievementCatalog.all.filter { it.key !in existing }
        if (missing.isNotEmpty()) achievementDao.upsertAll(missing)
        if (rewardDao.count() == 0) rewardDao.insertAll(RewardCatalog.defaults)
    }

    // ---- earning ---------------------------------------------------------

    /**
     * Called after a task genuinely flips to completed. Returns the celebration events so the
     * caller can surface them; all persistence has already happened when this returns.
     */
    suspend fun onTaskCompleted(priority: Int, durationMinutes: Int, startMinute: Int, epochDay: Long): List<RewardEvent> {
        val snapshot = taskRepository.snapshotFor(epochDay)
        return mutex.withLock {
            val events = mutableListOf<RewardEvent>()
            var stats = statsDao.getOrCreate()
            val beforeLevel = Leveling.levelForXp(stats.xp)

            val xp = Economy.xpForTask(priority)
            val coins = Economy.coinsForTask(priority)
            stats = stats.copy(
                xp = stats.xp + xp,
                coins = stats.coins + coins,
                totalTasksCompleted = stats.totalTasksCompleted + 1,
                totalFocusMinutes = stats.totalFocusMinutes + durationMinutes
            )
            events += RewardEvent.Xp(xp, "Block completed")
            events += RewardEvent.Coins(coins, "Block completed")

            if (snapshot.isPerfect) {
                stats = stats.copy(
                    xp = stats.xp + Economy.XP_PERFECT_DAY,
                    coins = stats.coins + Economy.COINS_PERFECT_DAY,
                    perfectDays = stats.perfectDays + 1
                )
                events += RewardEvent.PerfectDay(Economy.XP_PERFECT_DAY, Economy.COINS_PERFECT_DAY)
            }

            val afterLevel = Leveling.levelForXp(stats.xp)
            if (afterLevel > beforeLevel) {
                events += RewardEvent.LevelUp(afterLevel, Leveling.titleFor(afterLevel))
            }

            statsDao.upsert(stats)
            events += refreshStreakLocked(TimeUtils.todayEpochDay())
            events += refreshAchievementsLocked(earlyBirdCandidate = startMinute < 7 * 60)
            events
        }
    }

    suspend fun onChecklistItemChecked(): List<RewardEvent> = mutex.withLock {
        val stats = statsDao.getOrCreate()
        statsDao.upsert(stats.copy(xp = stats.xp + Economy.XP_CHECKLIST_ITEM))
        listOf(RewardEvent.Xp(Economy.XP_CHECKLIST_ITEM, "Checklist item"))
    }

    suspend fun onCoachConversation(): List<RewardEvent> = mutex.withLock {
        val achievement = achievementDao.get(AchievementCatalog.COACH_TALK)
        if (achievement == null || achievement.unlockedAt != null) return@withLock emptyList()
        achievementDao.upsert(achievement.copy(progress = 1, unlockedAt = System.currentTimeMillis()))
        grantAchievementRewards(achievement)
        listOf(RewardEvent.Unlocked(achievement))
    }

    // ---- streaks ---------------------------------------------------------

    /** Safe to call on every app start; it replays only the days it has not scored yet. */
    suspend fun refreshStreak(today: Long = TimeUtils.todayEpochDay()): List<RewardEvent> =
        mutex.withLock { refreshStreakLocked(today) }

    private suspend fun refreshStreakLocked(today: Long): List<RewardEvent> {
        val stats = statsDao.getOrCreate()

        // Snapshots are resolved up front because the engine itself is deliberately pure.
        val firstDay = maxOf(
            (if (stats.lastStreakDay < 0) today - 1 else minOf(stats.lastStreakDay, today)) + 1,
            today - StreakEngine.MAX_REPLAY_DAYS
        )
        val outcomes = HashMap<Long, DayOutcome>()
        var day = firstDay
        while (day <= today) {
            val snapshot = taskRepository.snapshotFor(day)
            outcomes[day] = DayOutcome(snapshot.planned, snapshot.completionRate)
            day++
        }

        val result = StreakEngine.evaluate(
            initial = StreakState(
                streak = stats.currentStreak,
                longest = stats.longestStreak,
                freezeTokens = stats.freezeTokens,
                lastStreakDay = stats.lastStreakDay
            ),
            today = today,
            outcomeFor = { outcomes[it] ?: DayOutcome(0, 0f) }
        )

        statsDao.upsert(
            stats.copy(
                xp = stats.xp,
                coins = stats.coins + result.bonusCoins,
                currentStreak = result.state.streak,
                longestStreak = result.state.longest,
                freezeTokens = result.state.freezeTokens,
                lastStreakDay = result.state.lastStreakDay,
                lastRolloverDay = today
            )
        )

        return result.events.map { event ->
            when (event) {
                is StreakEvent.Extended -> RewardEvent.StreakExtended(event.streak, event.bonusCoins)
                is StreakEvent.Frozen -> RewardEvent.StreakFrozen(event.streak, event.tokensLeft)
                is StreakEvent.Lost -> RewardEvent.StreakLost(event.previous)
            }
        }
    }

    // ---- shop ------------------------------------------------------------

    sealed interface RedeemResult {
        data class Success(val reward: RewardEntity, val coinsLeft: Int) : RedeemResult
        data class NotEnoughCoins(val missing: Int) : RedeemResult
        data object NotFound : RedeemResult
    }

    suspend fun redeemReward(rewardId: Long): RedeemResult = mutex.withLock {
        val reward = rewardDao.get(rewardId) ?: return@withLock RedeemResult.NotFound
        val stats = statsDao.getOrCreate()
        if (stats.coins < reward.cost) {
            return@withLock RedeemResult.NotEnoughCoins(reward.cost - stats.coins)
        }
        statsDao.upsert(stats.copy(coins = stats.coins - reward.cost))
        rewardDao.update(
            reward.copy(
                timesRedeemed = reward.timesRedeemed + 1,
                lastRedeemedAt = System.currentTimeMillis()
            )
        )
        RedeemResult.Success(reward, stats.coins - reward.cost)
    }

    suspend fun buyFreezeToken(): RedeemResult = mutex.withLock {
        val stats = statsDao.getOrCreate()
        if (stats.freezeTokens >= Economy.MAX_FREEZE_TOKENS) {
            return@withLock RedeemResult.NotEnoughCoins(0)
        }
        if (stats.coins < Economy.FREEZE_TOKEN_COST) {
            return@withLock RedeemResult.NotEnoughCoins(Economy.FREEZE_TOKEN_COST - stats.coins)
        }
        statsDao.upsert(
            stats.copy(
                coins = stats.coins - Economy.FREEZE_TOKEN_COST,
                freezeTokens = stats.freezeTokens + 1
            )
        )
        RedeemResult.Success(
            RewardEntity(title = "Streak freeze", emoji = "🧊", cost = Economy.FREEZE_TOKEN_COST),
            stats.coins - Economy.FREEZE_TOKEN_COST
        )
    }

    suspend fun addCustomReward(title: String, description: String, emoji: String, cost: Int): Long =
        rewardDao.insert(
            RewardEntity(
                title = title.trim(),
                description = description.trim(),
                emoji = emoji.ifBlank { "🎁" },
                cost = cost.coerceIn(10, 5000),
                isCustom = true
            )
        )

    suspend fun deleteReward(id: Long) = rewardDao.delete(id)

    // ---- achievements ----------------------------------------------------

    suspend fun refreshAchievements(): List<RewardEvent> =
        mutex.withLock { refreshAchievementsLocked(earlyBirdCandidate = false) }

    private suspend fun refreshAchievementsLocked(earlyBirdCandidate: Boolean): List<RewardEvent> {
        val stats = statsDao.getOrCreate()
        val completedCount = logDao.countCompleted()
        val level = Leveling.levelForXp(stats.xp)
        val unlocked = mutableListOf<RewardEvent>()

        val progressFor: (String) -> Int = { key ->
            when (key) {
                AchievementCatalog.TASKS_10, AchievementCatalog.TASKS_50, AchievementCatalog.TASKS_250 -> completedCount
                AchievementCatalog.STREAK_3, AchievementCatalog.STREAK_7,
                AchievementCatalog.STREAK_30, AchievementCatalog.STREAK_100 -> stats.longestStreak
                AchievementCatalog.PERFECT_1, AchievementCatalog.PERFECT_10 -> stats.perfectDays
                AchievementCatalog.FOCUS_600, AchievementCatalog.FOCUS_3000 -> stats.totalFocusMinutes
                AchievementCatalog.LEVEL_5, AchievementCatalog.LEVEL_10 -> level
                AchievementCatalog.EARLY_BIRD -> if (earlyBirdCandidate) 1 else 0
                else -> -1
            }
        }

        achievementDao.getAll().forEach { achievement ->
            val computed = progressFor(achievement.key)
            if (computed < 0) return@forEach
            val progress = maxOf(achievement.progress, computed)
            val justUnlocked = achievement.unlockedAt == null && progress >= achievement.target
            if (progress != achievement.progress || justUnlocked) {
                val updated = achievement.copy(
                    progress = progress.coerceAtMost(achievement.target),
                    unlockedAt = if (justUnlocked) System.currentTimeMillis() else achievement.unlockedAt
                )
                achievementDao.upsert(updated)
                if (justUnlocked) {
                    grantAchievementRewards(updated)
                    unlocked += RewardEvent.Unlocked(updated)
                }
            }
        }
        return unlocked
    }

    private suspend fun grantAchievementRewards(achievement: AchievementEntity) {
        if (achievement.rewardXp == 0 && achievement.rewardCoins == 0) return
        val stats = statsDao.getOrCreate()
        statsDao.upsert(
            stats.copy(
                xp = stats.xp + achievement.rewardXp,
                coins = stats.coins + achievement.rewardCoins
            )
        )
    }
}
