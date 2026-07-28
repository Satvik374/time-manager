package com.kairo.timemanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object Recurrence {
    const val ONCE = 0
    const val DAILY = 1
    const val WEEKLY = 2
}

object Priority {
    const val LOW = 0
    const val NORMAL = 1
    const val HIGH = 2

    fun label(value: Int) = when (value) {
        LOW -> "Low"
        HIGH -> "High"
        else -> "Normal"
    }
}

/**
 * A block on the timetable. Recurring blocks are stored once; the concrete per-day state
 * lives in [TaskLogEntity] so history survives edits to the definition.
 */
@Entity(
    tableName = "tasks",
    indices = [Index("specificDate"), Index("archived")]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val category: String = "General",
    val colorHex: String = "#6C5CE7",
    val priority: Int = Priority.NORMAL,
    val startMinute: Int,
    val durationMinutes: Int,
    val recurrence: Int = Recurrence.ONCE,
    /** Only meaningful when [recurrence] is WEEKLY. bit0 = Monday. */
    val daysMask: Int = 0,
    /** Only meaningful when [recurrence] is ONCE. */
    val specificDate: Long? = null,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    /** Minutes before start to fire a reminder, or -1 for none. */
    val reminderMinutes: Int = -1,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val endMinute: Int get() = startMinute + durationMinutes
}

/** Per-day completion state of a task. */
@Entity(
    tableName = "task_logs",
    primaryKeys = ["taskId", "epochDay"],
    indices = [Index("epochDay")],
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class TaskLogEntity(
    val taskId: Long,
    val epochDay: Long,
    val completed: Boolean = false,
    val skipped: Boolean = false,
    val completedAt: Long? = null,
    val focusMinutes: Int = 0
)

@Entity(
    tableName = "checklist_items",
    indices = [Index("taskId")],
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val text: String,
    val position: Int = 0
)

/** Per-day tick state of a checklist item. */
@Entity(
    tableName = "checklist_logs",
    primaryKeys = ["itemId", "epochDay"],
    indices = [Index("epochDay"), Index("taskId")],
    foreignKeys = [ForeignKey(
        entity = ChecklistItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ChecklistLogEntity(
    val itemId: Long,
    val epochDay: Long,
    val taskId: Long,
    val checked: Boolean = false,
    val checkedAt: Long? = null
)

/** Single-row table (id = 1) holding the player state. */
@Entity(tableName = "user_stats")
data class UserStatsEntity(
    @PrimaryKey val id: Int = 1,
    val xp: Int = 0,
    val coins: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastStreakDay: Long = -1L,
    val freezeTokens: Int = 1,
    val lastRolloverDay: Long = -1L,
    val totalTasksCompleted: Int = 0,
    val totalFocusMinutes: Int = 0,
    val perfectDays: Int = 0
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val key: String,
    val title: String,
    val description: String,
    val emoji: String,
    val target: Int,
    val progress: Int = 0,
    val unlockedAt: Long? = null,
    val rewardXp: Int = 0,
    val rewardCoins: Int = 0,
    val tier: Int = 0
)

/** Something the user buys with coins — real-life treats they define themselves. */
@Entity(tableName = "rewards")
data class RewardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val emoji: String = "🎁",
    val cost: Int = 50,
    val isCustom: Boolean = false,
    val timesRedeemed: Int = 0,
    val lastRedeemedAt: Long? = null
)

object ChatRole {
    const val USER = "user"
    const val ASSISTANT = "assistant"
    const val TOOL = "tool"
    const val SYSTEM = "system"
}

@Entity(tableName = "chat_messages", indices = [Index("createdAt")])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val content: String,
    /** Raw provider tool-call payload, replayed on the next turn so the model keeps context. */
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val isError: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

object RecommendationStatus {
    const val NEW = "new"
    const val ACCEPTED = "accepted"
    const val DISMISSED = "dismissed"
}

/** An optional nudge from the coach. Never applied automatically — the user decides. */
@Entity(tableName = "recommendations", indices = [Index("status"), Index("createdAt")])
data class RecommendationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val kind: String = "general",
    val status: String = RecommendationStatus.NEW,
    val createdAt: Long = System.currentTimeMillis(),
    val respondedAt: Long? = null
)
