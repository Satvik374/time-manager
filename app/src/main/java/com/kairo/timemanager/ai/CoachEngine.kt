package com.kairo.timemanager.ai

import com.kairo.timemanager.core.Leveling
import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.prefs.AiConfig
import com.kairo.timemanager.data.prefs.SettingsRepository
import com.kairo.timemanager.data.repo.AnalyticsRepository
import com.kairo.timemanager.data.repo.ChatRepository
import com.kairo.timemanager.data.repo.ProgressRepository
import com.kairo.timemanager.data.repo.RecommendationRepository
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Progress emitted while a turn runs, so the chat UI can show what the coach is doing. */
sealed interface CoachProgress {
    data object Thinking : CoachProgress
    data class RunningTool(val label: String) : CoachProgress
    data class ToolFinished(val outcome: ToolOutcome) : CoachProgress
    data class Replied(val text: String, val scheduleChanged: Boolean) : CoachProgress
    data class Failed(val message: String) : CoachProgress
}

/**
 * Runs one user turn end to end: builds the grounded system prompt, calls the provider, executes
 * whatever tools the model asks for, feeds the results back, and repeats until the model answers
 * in plain text or hits [MAX_TOOL_ROUNDS].
 */
@Singleton
class CoachEngine @Inject constructor(
    private val client: AiProviderClient,
    private val executor: AiToolExecutor,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val progressRepository: ProgressRepository,
    private val recommendationRepository: RecommendationRepository
) {

    suspend fun send(userText: String, onProgress: suspend (CoachProgress) -> Unit) {
        val config = settingsRepository.currentAiConfig()
        if (!config.enabled) {
            onProgress(CoachProgress.Failed("The AI coach is switched off in Settings."))
            return
        }
        if (!config.isConfigured) {
            onProgress(CoachProgress.Failed(AiError.NotConfigured.readable()))
            return
        }

        chatRepository.appendUser(userText)
        progressRepository.onCoachConversation()
        runTurn(config, onProgress)
    }

    /** Re-runs the loop without adding a user message — used by "Retry" after a network failure. */
    suspend fun retry(onProgress: suspend (CoachProgress) -> Unit) {
        val config = settingsRepository.currentAiConfig()
        if (!config.isConfigured) {
            onProgress(CoachProgress.Failed(AiError.NotConfigured.readable()))
            return
        }
        runTurn(config, onProgress)
    }

    private suspend fun runTurn(config: AiConfig, onProgress: suspend (CoachProgress) -> Unit) {
        val tools = if (config.toolsEnabled) {
            AiTools.all.filter { config.isToolEnabled(it.name) }
        } else {
            emptyList()
        }
        val grounded = config.copy(systemPrompt = buildSystemPrompt(config.systemPrompt))
        var scheduleChanged = false

        repeat(MAX_TOOL_ROUNDS) { round ->
            onProgress(CoachProgress.Thinking)
            val history = chatRepository.buildHistory(config.historyTurns * 3)
            val result = client.complete(grounded, history, tools)

            val turn = result.getOrElse { throwable ->
                val message = throwable.asAiError().readable()
                chatRepository.appendError(message)
                onProgress(CoachProgress.Failed(message))
                return
            }

            chatRepository.appendAssistant(turn.text, turn.toolCalls)

            if (!turn.wantsTools) {
                val text = turn.text.ifBlank {
                    "I didn't get a usable reply from the provider. Try rephrasing, or check the model name in Settings."
                }
                onProgress(CoachProgress.Replied(text, scheduleChanged))
                return
            }

            for (call in turn.toolCalls) {
                val label = AiTools.byName[call.name]?.humanLabel ?: call.name
                onProgress(CoachProgress.RunningTool(label))
                val outcome = executor.execute(call)
                if (outcome.mutatedSchedule) scheduleChanged = true
                chatRepository.appendToolResult(call, outcome.resultJson, outcome.succeeded)
                onProgress(CoachProgress.ToolFinished(outcome))
            }

            if (round == MAX_TOOL_ROUNDS - 1) {
                val message = "I ran out of tool steps for this request. Ask me to continue and I'll pick up where I left off."
                chatRepository.appendError(message)
                onProgress(CoachProgress.Failed(message))
            }
        }
    }

    /** The user's editable prompt plus live device state, so the model never has to guess. */
    private suspend fun buildSystemPrompt(userPrompt: String): String {
        val today = TimeUtils.todayEpochDay()
        val stats = progressRepository.stats()
        val digest = runCatching { analyticsRepository.textDigest(today) }
            .getOrDefault("No history yet.")
        val context = SystemPrompts.runtimeContext(
            todayIso = TimeUtils.formatIso(today),
            weekday = TimeUtils.dayOfWeek(today).getDisplayName(TextStyle.FULL, Locale.getDefault()),
            nowTime = TimeUtils.formatTime(TimeUtils.nowMinuteOfDay(), use24h = true),
            streak = stats.currentStreak,
            level = Leveling.levelForXp(stats.xp),
            coins = stats.coins,
            digest = digest
        )
        return "${userPrompt.trim()}\n\n$context"
    }

    // ---- proactive recommendations ---------------------------------------

    /**
     * Generates at most one recommendation card, respecting the user's interval setting.
     * Returns true when a new card was posted. Safe to call from a worker or on app resume.
     */
    suspend fun maybeGenerateRecommendation(force: Boolean = false): Boolean {
        val config = settingsRepository.currentAiConfig()
        if (!config.enabled || !config.recommendationsEnabled || !config.isConfigured) return false
        if (!config.isToolEnabled(AiTools.SUGGEST_RECOMMENDATION)) return false

        val prefs = settingsRepository.currentAppPrefs()
        val elapsed = System.currentTimeMillis() - prefs.lastRecommendationAt
        val intervalMillis = config.recommendationIntervalHours.coerceAtLeast(1) * 60L * 60L * 1000L
        if (!force && elapsed < intervalMillis) return false

        // Nothing to reason about yet — don't burn a call on an empty schedule.
        val today = TimeUtils.todayEpochDay()
        val digest = runCatching { analyticsRepository.textDigest(today) }.getOrNull() ?: return false

        val grounded = config.copy(
            systemPrompt = buildSystemPrompt(config.systemPrompt),
            maxTokens = 600
        )
        val prompt = AiMessage(
            role = "user",
            content = RECOMMENDATION_PROMPT.trimIndent() + "\n\nSummary you already have:\n" + digest
        )
        val tools = AiTools.all.filter {
            it.name in RECOMMENDATION_TOOLS && config.isToolEnabled(it.name)
        }

        var posted = false
        var conversation = listOf(prompt)
        repeat(3) {
            val turn = client.complete(grounded, conversation, tools).getOrNull() ?: return posted
            if (!turn.wantsTools) return posted
            conversation = conversation + AiMessage(
                role = "assistant",
                content = turn.text,
                toolCalls = turn.toolCalls
            )
            for (call in turn.toolCalls) {
                val outcome = executor.execute(call)
                if (call.name == AiTools.SUGGEST_RECOMMENDATION && outcome.succeeded) posted = true
                conversation = conversation + AiMessage(
                    role = "tool",
                    content = outcome.resultJson,
                    toolCallId = call.id,
                    toolName = call.name
                )
            }
            if (posted) {
                settingsRepository.markRecommendationShown()
                recommendationRepository.prune()
                return true
            }
        }
        return posted
    }

    private companion object {
        const val MAX_TOOL_ROUNDS = 6

        val RECOMMENDATION_TOOLS = setOf(
            AiTools.GET_ANALYTICS,
            AiTools.GET_SCHEDULE,
            AiTools.GET_PROGRESS,
            AiTools.SUGGEST_RECOMMENDATION
        )

        const val RECOMMENDATION_PROMPT = """
            Review the user's recent performance and post exactly one optional suggestion card
            using the suggest_recommendation tool. Look at the real data first with get_analytics
            and, if useful, get_schedule. The suggestion must name a specific block, day or time —
            never generic advice. If the data is too thin to say anything specific, or the user is
            already doing well and needs no change, reply with the single word SKIP and call no tools.
        """
    }
}
