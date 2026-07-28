package com.kairo.timemanager.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.kairo.timemanager.data.local.KairoDatabase
import com.kairo.timemanager.data.local.dao.AchievementDao
import com.kairo.timemanager.data.local.dao.ChatDao
import com.kairo.timemanager.data.local.dao.ChecklistDao
import com.kairo.timemanager.data.local.dao.RecommendationDao
import com.kairo.timemanager.data.local.dao.RewardDao
import com.kairo.timemanager.data.local.dao.StatsDao
import com.kairo.timemanager.data.local.dao.TaskDao
import com.kairo.timemanager.data.local.dao.TaskLogDao
import com.kairo.timemanager.data.prefs.settingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KairoDatabase =
        Room.databaseBuilder(context, KairoDatabase::class.java, KairoDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideTaskDao(db: KairoDatabase): TaskDao = db.taskDao()
    @Provides fun provideTaskLogDao(db: KairoDatabase): TaskLogDao = db.taskLogDao()
    @Provides fun provideChecklistDao(db: KairoDatabase): ChecklistDao = db.checklistDao()
    @Provides fun provideStatsDao(db: KairoDatabase): StatsDao = db.statsDao()
    @Provides fun provideAchievementDao(db: KairoDatabase): AchievementDao = db.achievementDao()
    @Provides fun provideRewardDao(db: KairoDatabase): RewardDao = db.rewardDao()
    @Provides fun provideChatDao(db: KairoDatabase): ChatDao = db.chatDao()
    @Provides fun provideRecommendationDao(db: KairoDatabase): RecommendationDao = db.recommendationDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        // Tool loops can involve slow models, so read timeouts are generous by design.
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}
