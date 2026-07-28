package com.kairo.timemanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.kairo.timemanager.data.local.entity.AchievementEntity
import com.kairo.timemanager.data.local.entity.ChatMessageEntity
import com.kairo.timemanager.data.local.entity.ChecklistItemEntity
import com.kairo.timemanager.data.local.entity.ChecklistLogEntity
import com.kairo.timemanager.data.local.entity.RecommendationEntity
import com.kairo.timemanager.data.local.entity.RewardEntity
import com.kairo.timemanager.data.local.entity.TaskEntity
import com.kairo.timemanager.data.local.entity.TaskLogEntity
import com.kairo.timemanager.data.local.entity.UserStatsEntity
import kotlinx.coroutines.flow.Flow

/** Aggregate row used by every analytics screen. */
data class DayAggregate(
    val epochDay: Long,
    val completed: Int,
    val focusMinutes: Int
)

data class CategoryAggregate(
    val category: String,
    val colorHex: String,
    val completed: Int,
    val minutes: Int
)

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE archived = 0 ORDER BY startMinute ASC")
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY startMinute ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE archived = 0 ORDER BY startMinute ASC")
    suspend fun getActive(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query(
        "SELECT * FROM tasks WHERE archived = 0 AND " +
            "(LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(note) LIKE '%' || LOWER(:query) || '%' " +
            "OR LOWER(category) LIKE '%' || LOWER(:query) || '%') ORDER BY startMinute ASC LIMIT :limit"
    )
    suspend fun search(query: String, limit: Int = 25): List<TaskEntity>

    @Query("SELECT DISTINCT category FROM tasks WHERE archived = 0 ORDER BY category ASC")
    fun observeCategories(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE tasks SET archived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM tasks WHERE archived = 0")
    suspend fun countActive(): Int
}

@Dao
interface TaskLogDao {

    @Query("SELECT * FROM task_logs WHERE epochDay = :epochDay")
    fun observeForDay(epochDay: Long): Flow<List<TaskLogEntity>>

    @Query("SELECT * FROM task_logs WHERE epochDay BETWEEN :from AND :to")
    fun observeForRange(from: Long, to: Long): Flow<List<TaskLogEntity>>

    @Query("SELECT * FROM task_logs WHERE epochDay BETWEEN :from AND :to")
    suspend fun getForRange(from: Long, to: Long): List<TaskLogEntity>

    @Query("SELECT * FROM task_logs WHERE epochDay = :epochDay")
    suspend fun getForDay(epochDay: Long): List<TaskLogEntity>

    @Query("SELECT * FROM task_logs WHERE taskId = :taskId AND epochDay = :epochDay")
    suspend fun get(taskId: Long, epochDay: Long): TaskLogEntity?

    @Upsert
    suspend fun upsert(log: TaskLogEntity)

    @Query("DELETE FROM task_logs WHERE taskId = :taskId AND epochDay = :epochDay")
    suspend fun clear(taskId: Long, epochDay: Long)

    @Query(
        "SELECT epochDay, COUNT(*) AS completed, COALESCE(SUM(focusMinutes), 0) AS focusMinutes " +
            "FROM task_logs WHERE completed = 1 AND epochDay BETWEEN :from AND :to GROUP BY epochDay"
    )
    fun observeDayAggregates(from: Long, to: Long): Flow<List<DayAggregate>>

    @Query(
        "SELECT t.category AS category, t.colorHex AS colorHex, COUNT(*) AS completed, " +
            "COALESCE(SUM(t.durationMinutes), 0) AS minutes " +
            "FROM task_logs l JOIN tasks t ON t.id = l.taskId " +
            "WHERE l.completed = 1 AND l.epochDay BETWEEN :from AND :to " +
            "GROUP BY t.category ORDER BY minutes DESC"
    )
    fun observeCategoryAggregates(from: Long, to: Long): Flow<List<CategoryAggregate>>

    @Query("SELECT COUNT(*) FROM task_logs WHERE completed = 1")
    suspend fun countCompleted(): Int
}

@Dao
interface ChecklistDao {

    @Query("SELECT * FROM checklist_items WHERE taskId = :taskId ORDER BY position ASC")
    fun observeForTask(taskId: Long): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items ORDER BY taskId, position ASC")
    fun observeAll(): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE taskId = :taskId ORDER BY position ASC")
    suspend fun getForTask(taskId: Long): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE id = :id")
    suspend fun getItem(id: Long): ChecklistItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ChecklistItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChecklistItemEntity>)

    @Update
    suspend fun update(item: ChecklistItemEntity)

    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM checklist_items WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM checklist_items WHERE taskId = :taskId")
    suspend fun maxPosition(taskId: Long): Int

    @Query("SELECT * FROM checklist_logs WHERE epochDay = :epochDay")
    fun observeLogsForDay(epochDay: Long): Flow<List<ChecklistLogEntity>>

    @Query("SELECT * FROM checklist_logs WHERE epochDay = :epochDay AND taskId = :taskId")
    suspend fun getLogsForTaskDay(taskId: Long, epochDay: Long): List<ChecklistLogEntity>

    @Query("SELECT * FROM checklist_logs WHERE itemId = :itemId AND epochDay = :epochDay")
    suspend fun getLog(itemId: Long, epochDay: Long): ChecklistLogEntity?

    @Upsert
    suspend fun upsertLog(log: ChecklistLogEntity)

    @Query("SELECT COUNT(*) FROM checklist_logs WHERE checked = 1 AND epochDay BETWEEN :from AND :to")
    suspend fun countChecked(from: Long, to: Long): Int
}

@Dao
interface StatsDao {

    @Query("SELECT * FROM user_stats WHERE id = 1")
    fun observe(): Flow<UserStatsEntity?>

    @Query("SELECT * FROM user_stats WHERE id = 1")
    suspend fun get(): UserStatsEntity?

    @Upsert
    suspend fun upsert(stats: UserStatsEntity)

    @Transaction
    suspend fun getOrCreate(): UserStatsEntity {
        val existing = get()
        if (existing != null) return existing
        val fresh = UserStatsEntity()
        upsert(fresh)
        return fresh
    }
}

@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievements ORDER BY unlockedAt IS NULL, unlockedAt DESC, tier ASC")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements")
    suspend fun getAll(): List<AchievementEntity>

    @Query("SELECT * FROM achievements WHERE `key` = :key")
    suspend fun get(key: String): AchievementEntity?

    @Upsert
    suspend fun upsert(achievement: AchievementEntity)

    @Upsert
    suspend fun upsertAll(achievements: List<AchievementEntity>)

    @Query("SELECT COUNT(*) FROM achievements")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM achievements WHERE unlockedAt IS NOT NULL")
    suspend fun countUnlocked(): Int
}

@Dao
interface RewardDao {

    @Query("SELECT * FROM rewards ORDER BY cost ASC")
    fun observeAll(): Flow<List<RewardEntity>>

    @Query("SELECT * FROM rewards ORDER BY cost ASC")
    suspend fun getAll(): List<RewardEntity>

    @Query("SELECT * FROM rewards WHERE id = :id")
    suspend fun get(id: Long): RewardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reward: RewardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rewards: List<RewardEntity>)

    @Update
    suspend fun update(reward: RewardEntity)

    @Query("DELETE FROM rewards WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM rewards")
    suspend fun count(): Int
}

@Dao
interface ChatDao {

    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}

@Dao
interface RecommendationDao {

    @Query("SELECT * FROM recommendations ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<RecommendationEntity>>

    @Query("SELECT * FROM recommendations WHERE status = 'new' ORDER BY createdAt DESC")
    fun observeOpen(): Flow<List<RecommendationEntity>>

    @Query("SELECT COUNT(*) FROM recommendations WHERE createdAt > :since")
    suspend fun countSince(since: Long): Int

    @Insert
    suspend fun insert(recommendation: RecommendationEntity): Long

    @Query("UPDATE recommendations SET status = :status, respondedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM recommendations WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
