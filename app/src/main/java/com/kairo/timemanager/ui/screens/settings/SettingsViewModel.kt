package com.kairo.timemanager.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.timemanager.ai.AiProviderClient
import com.kairo.timemanager.ai.AiTools
import com.kairo.timemanager.ai.ProviderFormat
import com.kairo.timemanager.ai.asAiError
import com.kairo.timemanager.data.prefs.AiConfig
import com.kairo.timemanager.data.prefs.AppLockManager
import com.kairo.timemanager.data.prefs.AuthRepository
import com.kairo.timemanager.data.prefs.AuthState
import com.kairo.timemanager.data.prefs.PinResult
import com.kairo.timemanager.data.prefs.AppPrefs
import com.kairo.timemanager.data.prefs.SettingsRepository
import com.kairo.timemanager.work.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
    private val reminderScheduler: ReminderScheduler,
    private val authRepository: AuthRepository,
    private val appLockManager: AppLockManager
) : ViewModel() {

    private val messages = Channel<String>(Channel.BUFFERED)
    val messageFlow: Flow<String> = messages.receiveAsFlow()

    val authState: StateFlow<AuthState> = authRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState())

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

    // ---- security --------------------------------------------------------

    fun setProfileName(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { authRepository.setProfileName(name) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            authRepository.setBiometricEnabled(enabled)
            if (enabled && !authRepository.hasPin()) {
                messages.send("Set a PIN first — biometrics unlock on top of it.")
            }
        }
    }

    fun setAutoLockMinutes(minutes: Int) {
        viewModelScope.launch { authRepository.setAutoLockMinutes(minutes) }
    }

    /** Creates the first PIN, or replaces an existing one when [currentPin] is supplied. */
    fun savePin(currentPin: String?, newPin: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = if (currentPin == null) authRepository.setPin(newPin)
            else authRepository.changePin(currentPin, newPin)
            onDone(result == PinResult.Success)
            messages.send(describe(result, success = "App lock updated."))
        }
    }

    fun removeLock(currentPin: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.disableLock(currentPin)
            val ok = result == PinResult.Success
            if (ok) appLockManager.onLockConfigurationChanged(lockEnabled = false)
            onDone(ok)
            messages.send(describe(result, success = "App lock removed."))
        }
    }

    fun lockNow() {
        appLockManager.lockNow()
    }

    private fun describe(result: PinResult, success: String): String = when (result) {
        PinResult.Success -> success
        is PinResult.Wrong -> "That PIN is wrong. ${result.attemptsLeft} attempt(s) left."
        is PinResult.LockedOut -> "Too many attempts. Try again in ${result.seconds}s."
        PinResult.NoPinSet -> "No PIN is set on this device."
        is PinResult.Invalid -> result.reason
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
