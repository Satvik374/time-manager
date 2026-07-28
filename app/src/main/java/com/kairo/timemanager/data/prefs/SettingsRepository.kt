package com.kairo.timemanager.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kairo.timemanager.ai.ProviderFormat
import com.kairo.timemanager.ai.SystemPrompts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "kairo_settings")

/** Everything the user can configure about the AI provider and the coach's behaviour. */
data class AiConfig(
    val enabled: Boolean = true,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val format: ProviderFormat = ProviderFormat.OPENAI,
    val systemPrompt: String = SystemPrompts.DEFAULT,
    val temperature: Float = 0.6f,
    val maxTokens: Int = 1200,
    val toolsEnabled: Boolean = true,
    val enabledTools: Set<String> = emptySet(),
    val historyTurns: Int = 24,
    val recommendationsEnabled: Boolean = true,
    val recommendationIntervalHours: Int = 12,
    val proactiveNotifications: Boolean = false
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank()

    /** Whether the tool with this name should be advertised to the model. Empty set = all on. */
    fun isToolEnabled(name: String): Boolean =
        toolsEnabled && (enabledTools.isEmpty() || name in enabledTools)
}

data class AppPrefs(
    val use24HourClock: Boolean = false,
    val themeMode: Int = 0, // 0 system, 1 light, 2 dark
    val dynamicColor: Boolean = true,
    val remindersEnabled: Boolean = true,
    val dayStartMinute: Int = 6 * 60,
    val dayEndMinute: Int = 23 * 60,
    val onboarded: Boolean = false,
    val lastRecommendationAt: Long = 0L
)

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private object Keys {
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val BASE_URL = stringPreferencesKey("ai_base_url")
        val API_KEY = stringPreferencesKey("ai_api_key")
        val MODEL = stringPreferencesKey("ai_model")
        val FORMAT = stringPreferencesKey("ai_format")
        val SYSTEM_PROMPT = stringPreferencesKey("ai_system_prompt")
        val TEMPERATURE = floatPreferencesKey("ai_temperature")
        val MAX_TOKENS = intPreferencesKey("ai_max_tokens")
        val TOOLS_ENABLED = booleanPreferencesKey("ai_tools_enabled")
        val ENABLED_TOOLS = stringPreferencesKey("ai_enabled_tools")
        val HISTORY_TURNS = intPreferencesKey("ai_history_turns")
        val RECOMMENDATIONS = booleanPreferencesKey("ai_recommendations")
        val RECOMMENDATION_INTERVAL = intPreferencesKey("ai_recommendation_interval")
        val PROACTIVE_NOTIFICATIONS = booleanPreferencesKey("ai_proactive_notifications")

        val CLOCK_24H = booleanPreferencesKey("use_24h")
        val THEME_MODE = intPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val REMINDERS = booleanPreferencesKey("reminders_enabled")
        val DAY_START = intPreferencesKey("day_start")
        val DAY_END = intPreferencesKey("day_end")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val LAST_RECOMMENDATION = longPreferencesKey("last_recommendation_at")
    }

    val aiConfig: Flow<AiConfig> = dataStore.data.map { prefs ->
        AiConfig(
            enabled = prefs[Keys.AI_ENABLED] ?: true,
            baseUrl = prefs[Keys.BASE_URL] ?: "",
            apiKey = prefs[Keys.API_KEY] ?: "",
            model = prefs[Keys.MODEL] ?: "",
            format = ProviderFormat.fromKey(prefs[Keys.FORMAT]),
            systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: SystemPrompts.DEFAULT,
            temperature = prefs[Keys.TEMPERATURE] ?: 0.6f,
            maxTokens = prefs[Keys.MAX_TOKENS] ?: 1200,
            toolsEnabled = prefs[Keys.TOOLS_ENABLED] ?: true,
            enabledTools = prefs[Keys.ENABLED_TOOLS]
                ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
                ?: emptySet(),
            historyTurns = prefs[Keys.HISTORY_TURNS] ?: 24,
            recommendationsEnabled = prefs[Keys.RECOMMENDATIONS] ?: true,
            recommendationIntervalHours = prefs[Keys.RECOMMENDATION_INTERVAL] ?: 12,
            proactiveNotifications = prefs[Keys.PROACTIVE_NOTIFICATIONS] ?: false
        )
    }

    val appPrefs: Flow<AppPrefs> = dataStore.data.map { prefs ->
        AppPrefs(
            use24HourClock = prefs[Keys.CLOCK_24H] ?: false,
            themeMode = prefs[Keys.THEME_MODE] ?: 0,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            remindersEnabled = prefs[Keys.REMINDERS] ?: true,
            dayStartMinute = prefs[Keys.DAY_START] ?: (6 * 60),
            dayEndMinute = prefs[Keys.DAY_END] ?: (23 * 60),
            onboarded = prefs[Keys.ONBOARDED] ?: false,
            lastRecommendationAt = prefs[Keys.LAST_RECOMMENDATION] ?: 0L
        )
    }

    suspend fun currentAiConfig(): AiConfig = aiConfig.first()

    suspend fun currentAppPrefs(): AppPrefs = appPrefs.first()

    suspend fun updateAi(transform: (AiConfig) -> AiConfig) {
        val updated = transform(currentAiConfig())
        dataStore.edit { prefs ->
            prefs[Keys.AI_ENABLED] = updated.enabled
            prefs[Keys.BASE_URL] = updated.baseUrl.trim()
            prefs[Keys.API_KEY] = updated.apiKey.trim()
            prefs[Keys.MODEL] = updated.model.trim()
            prefs[Keys.FORMAT] = updated.format.key
            prefs[Keys.SYSTEM_PROMPT] = updated.systemPrompt
            prefs[Keys.TEMPERATURE] = updated.temperature
            prefs[Keys.MAX_TOKENS] = updated.maxTokens
            prefs[Keys.TOOLS_ENABLED] = updated.toolsEnabled
            prefs[Keys.ENABLED_TOOLS] = updated.enabledTools.joinToString(",")
            prefs[Keys.HISTORY_TURNS] = updated.historyTurns
            prefs[Keys.RECOMMENDATIONS] = updated.recommendationsEnabled
            prefs[Keys.RECOMMENDATION_INTERVAL] = updated.recommendationIntervalHours
            prefs[Keys.PROACTIVE_NOTIFICATIONS] = updated.proactiveNotifications
        }
    }

    suspend fun updateApp(transform: (AppPrefs) -> AppPrefs) {
        val updated = transform(currentAppPrefs())
        dataStore.edit { prefs ->
            prefs[Keys.CLOCK_24H] = updated.use24HourClock
            prefs[Keys.THEME_MODE] = updated.themeMode
            prefs[Keys.DYNAMIC_COLOR] = updated.dynamicColor
            prefs[Keys.REMINDERS] = updated.remindersEnabled
            prefs[Keys.DAY_START] = updated.dayStartMinute
            prefs[Keys.DAY_END] = updated.dayEndMinute
            prefs[Keys.ONBOARDED] = updated.onboarded
            prefs[Keys.LAST_RECOMMENDATION] = updated.lastRecommendationAt
        }
    }

    suspend fun markRecommendationShown(at: Long = System.currentTimeMillis()) {
        dataStore.edit { it[Keys.LAST_RECOMMENDATION] = at }
    }

    suspend fun resetSystemPrompt() {
        dataStore.edit { it[Keys.SYSTEM_PROMPT] = SystemPrompts.DEFAULT }
    }
}
