package com.kairo.timemanager.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.timemanager.ai.CoachEngine
import com.kairo.timemanager.ai.CoachProgress
import com.kairo.timemanager.data.local.entity.ChatMessageEntity
import com.kairo.timemanager.data.local.entity.ChatRole
import com.kairo.timemanager.data.prefs.AiConfig
import com.kairo.timemanager.data.prefs.SettingsRepository
import com.kairo.timemanager.data.repo.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A chat bubble as the screen wants it: tool traffic collapsed into one activity line. */
sealed interface ChatItem {
    data class User(val id: Long, val text: String) : ChatItem
    data class Assistant(val id: Long, val text: String, val isError: Boolean) : ChatItem
    data class ToolActivity(val id: Long, val labels: List<String>, val anyFailed: Boolean) : ChatItem
}

data class CoachUiState(
    val items: List<ChatItem> = emptyList(),
    val config: AiConfig = AiConfig(),
    val busy: Boolean = false,
    val statusLine: String? = null,
    val scheduleChanged: Boolean = false
) {
    val configured: Boolean get() = config.isConfigured && config.enabled
}

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val coachEngine: CoachEngine,
    private val chatRepository: ChatRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val status = MutableStateFlow<String?>(null)
    private val scheduleChanged = MutableStateFlow(false)

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    val state: StateFlow<CoachUiState> = combine(
        chatRepository.observeMessages().map(::toItems),
        settingsRepository.aiConfig,
        busy,
        status,
        scheduleChanged
    ) { items, config, isBusy, statusLine, changed ->
        CoachUiState(items, config, isBusy, statusLine, changed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoachUiState())

    fun updateDraft(value: String) {
        _draft.value = value
    }

    fun send(text: String = _draft.value) {
        val message = text.trim()
        if (message.isEmpty() || busy.value) return
        _draft.value = ""
        scheduleChanged.value = false
        viewModelScope.launch {
            busy.value = true
            status.value = "Thinking…"
            coachEngine.send(message) { progress -> handle(progress) }
            busy.value = false
            status.value = null
        }
    }

    fun retry() {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            status.value = "Retrying…"
            coachEngine.retry { progress -> handle(progress) }
            busy.value = false
            status.value = null
        }
    }

    fun clearConversation() {
        viewModelScope.launch { chatRepository.clear() }
    }

    private fun handle(progress: CoachProgress) {
        when (progress) {
            CoachProgress.Thinking -> status.value = "Thinking…"
            is CoachProgress.RunningTool -> status.value = "${progress.label}…"
            is CoachProgress.ToolFinished -> {
                status.value = progress.outcome.summary
                if (progress.outcome.mutatedSchedule) scheduleChanged.value = true
            }
            is CoachProgress.Replied -> {
                status.value = null
                if (progress.scheduleChanged) scheduleChanged.value = true
            }
            is CoachProgress.Failed -> status.value = null
        }
    }

    /**
     * Collapses each assistant tool-call turn plus its results into a single activity chip,
     * because the raw JSON is noise for the user but must still stay in the model's history.
     */
    private fun toItems(messages: List<ChatMessageEntity>): List<ChatItem> {
        val items = mutableListOf<ChatItem>()
        var pendingLabels = mutableListOf<String>()
        var pendingFailed = false
        var pendingId = 0L

        fun flushTools() {
            if (pendingLabels.isNotEmpty()) {
                items += ChatItem.ToolActivity(pendingId, pendingLabels.toList(), pendingFailed)
                pendingLabels = mutableListOf()
                pendingFailed = false
            }
        }

        messages.forEach { message ->
            when (message.role) {
                ChatRole.USER -> {
                    flushTools()
                    items += ChatItem.User(message.id, message.content)
                }
                ChatRole.ASSISTANT -> {
                    val hasCalls = message.toolCallsJson != null
                    if (hasCalls) {
                        val names = chatRepository.decodeToolCalls(message.toolCallsJson!!)
                            .map { com.kairo.timemanager.ai.AiTools.byName[it.name]?.humanLabel ?: it.name }
                        if (pendingLabels.isEmpty()) pendingId = message.id
                        pendingLabels += names
                    }
                    if (message.content.isNotBlank()) {
                        flushTools()
                        items += ChatItem.Assistant(message.id, message.content, message.isError)
                    }
                }
                ChatRole.TOOL -> {
                    if (message.isError) pendingFailed = true
                }
            }
        }
        flushTools()
        return items
    }
}
