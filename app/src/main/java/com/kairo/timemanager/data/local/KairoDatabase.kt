package com.kairo.timemanager.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kairo.timemanager.data.local.dao.AchievementDao
import com.kairo.timemanager.data.local.dao.ChatDao
import com.kairo.timemanager.data.local.dao.ChecklistDao
import com.kairo.timemanager.data.local.dao.RecommendationDao
import com.kairo.timemanager.data.local.dao.RewardDao
import com.kairo.timemanager.data.local.dao.StatsDao
import com.kairo.timemanager.data.local.dao.TaskDao
import com.kairo.timemanager.data.local.dao.TaskLogDao
import com.kairo.timemanager.data.local.entity.AchievementEntity
import com.kairo.timemanager.data.local.entity.ChatMessageEntity
import com.kairo.timemanager.data.local.entity.ChecklistItemEntity
import com.kairo.timemanager.data.local.entity.ChecklistLogEntity
import com.kairo.timemanager.data.local.entity.RecommendationEntity
import com.kairo.timemanager.data.local.entity.RewardEntity
import com.kairo.timemanager.data.local.entity.TaskEntity
import com.kairo.timemanager.data.local.entity.TaskLogEntity
import com.kairo.timemanager.data.local.entity.UserStatsEntity

@Database(
    entities = [
        TaskEntity::class,
        TaskLogEntity::class,
        ChecklistItemEntity::class,
        ChecklistLogEntity::class,
        UserStatsEntity::class,
        AchievementEntity::class,
        RewardEntity::class,
        ChatMessageEntity::class,
        RecommendationEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class KairoDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskLogDao(): TaskLogDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun statsDao(): StatsDao
    abstract fun achievementDao(): AchievementDao
    abstract fun rewardDao(): RewardDao
    abstract fun chatDao(): ChatDao
    abstract fun recommendationDao(): RecommendationDao

    companion object {
        const val NAME = "kairo.db"
    }
}
