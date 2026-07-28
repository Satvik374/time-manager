package com.kairo.timemanager.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.timemanager.ai.AiProviderClient
import com.kairo.timemanager.ai.AiTools
import com.kairo.timemanager.ai.ProviderFormat
import com.kairo.timemanager.ai.asAiError
import com.kairo.timemanager.data.prefs.AiConfig
import com.kairo.timemanager.data.prefs.AppPrefs
import com.kairo.timemanager.data.prefs.SettingsRepository
import com.kairo.timemanager.work.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Running : ConnectionTest
    data class Success(val reply: String) : ConnectionTest
    data class Failure(val message: String) : ConnectionTest
}

data class SettingsUiState(
    val ai: AiConfig = AiConfig(),
    val app: AppPrefs = AppPrefs()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val providerClient: AiProviderClient,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val _test = MutableStateFlow<ConnectionTest>(ConnectionTest.Idle)
    val test: StateFlow<ConnectionTest> = _test.asStateFlow()

    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.aiConfig,
        settingsRepository.appPrefs
    ) { ai, app -> SettingsUiState(ai, app) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun updateAi(transform: (AiConfig) -> AiConfig) {
        viewModelScope.launch {
            settingsRepository.updateAi(transform)
            _test.value = ConnectionTest.Idle
        }
    }

    fun updateApp(transform: (AppPrefs) -> AppPrefs) {
        viewModelScope.launch {
            settingsRepository.updateApp(transform)
            // Reminder timing and the master switch both live here, so re-arm after any change.
            reminderScheduler.rescheduleAll()
        }
    }

    fun applyPreset(format: ProviderFormat, baseUrl: String, model: String) {
        updateAi { it.copy(format = format, baseUrl = baseUrl, model = model) }
    }

    fun setSystemPrompt(prompt: String) {
        updateAi { it.copy(systemPrompt = prompt) }
    }

    fun resetSystemPrompt() {
        viewModelScope.launch { settingsRepository.resetSystemPrompt() }
    }

    fun toggleTool(name: String) {
        updateAi { config ->
            // An empty set means "everything on", so materialise the full set before removing one.
            val current = config.enabledTools.ifEmpty { AiTools.defaultEnabled }
            val next = if (name in current) current - name else current + name
            config.copy(enabledTools = next)
        }
    }

    fun setAllTools(enabled: Boolean) {
        // An empty set already means "all on", so switching everything off needs a non-empty set
        // that matches no real tool name rather than an empty one.
        updateAi { it.copy(enabledTools = if (enabled) AiTools.defaultEnabled else setOf(NO_TOOLS)) }
    }

    private companion object {
        const val NO_TOOLS = "__none__"
    }

    fun testConnection() {
        if (_test.value == ConnectionTest.Running) return
        viewModelScope.launch {
            _test.value = ConnectionTest.Running
            val config = settingsRepository.currentAiConfig()
            _test.value = providerClient.testConnection(config).fold(
                onSuccess = { ConnectionTest.Success(it.take(200)) },
                onFailure = { ConnectionTest.Failure(it.asAiError().readable()) }
            )
        }
    }
}
