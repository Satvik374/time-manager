package com.kairo.timemanager.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.timemanager.core.PinHasher
import com.kairo.timemanager.data.prefs.AppLockManager
import com.kairo.timemanager.data.prefs.AuthRepository
import com.kairo.timemanager.data.prefs.AuthState
import com.kairo.timemanager.data.prefs.PinResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UnlockUiState(
    val entered: String = "",
    val message: String? = null,
    val isError: Boolean = false,
    val busy: Boolean = false
) {
    val canSubmit: Boolean get() = entered.length >= PinHasher.MIN_LENGTH && !busy
}

/** Which step of first-run setup the user is on. */
enum class SetupStep { PROFILE, CHOOSE_PIN, CONFIRM_PIN }

data class SetupUiState(
    val step: SetupStep = SetupStep.PROFILE,
    val name: String = "",
    val pin: String = "",
    val confirmPin: String = "",
    val message: String? = null,
    val isError: Boolean = false,
    val finished: Boolean = false
) {
    val activePin: String get() = if (step == SetupStep.CONFIRM_PIN) confirmPin else pin
    val canContinue: Boolean
        get() = when (step) {
            SetupStep.PROFILE -> name.isNotBlank()
            SetupStep.CHOOSE_PIN -> PinHasher.isValidPin(pin)
            SetupStep.CONFIRM_PIN -> confirmPin.length >= PinHasher.MIN_LENGTH
        }
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appLockManager: AppLockManager
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState())

    private val _unlock = MutableStateFlow(UnlockUiState())
    val unlock: StateFlow<UnlockUiState> = _unlock.asStateFlow()

    private val _setup = MutableStateFlow(SetupUiState())
    val setup: StateFlow<SetupUiState> = _setup.asStateFlow()

    // ---- unlocking -------------------------------------------------------

    fun onUnlockDigit(digit: Char) {
        val current = _unlock.value
        if (current.busy || current.entered.length >= PinHasher.MAX_LENGTH) return
        _unlock.value = current.copy(entered = current.entered + digit, message = null, isError = false)
    }

    fun onUnlockBackspace() {
        val current = _unlock.value
        if (current.entered.isEmpty()) return
        _unlock.value = current.copy(entered = current.entered.dropLast(1), message = null, isError = false)
    }

    fun submitUnlock() {
        val entered = _unlock.value.entered
        if (entered.length < PinHasher.MIN_LENGTH || _unlock.value.busy) return
        viewModelScope.launch {
            _unlock.value = _unlock.value.copy(busy = true, message = null)
            when (val result = authRepository.verifyPin(entered)) {
                PinResult.Success -> {
                    _unlock.value = UnlockUiState()
                    appLockManager.unlock()
                }
                is PinResult.Wrong -> _unlock.value = UnlockUiState(
                    message = if (result.attemptsLeft > 0)
                        "Wrong PIN. ${result.attemptsLeft} attempt(s) before a cooldown."
                    else "Wrong PIN.",
                    isError = true
                )
                is PinResult.LockedOut -> _unlock.value = UnlockUiState(
                    message = "Too many attempts. Try again in ${result.seconds}s.",
                    isError = true
                )
                PinResult.NoPinSet -> {
                    // The lock was removed on another screen; don't strand the user behind it.
                    _unlock.value = UnlockUiState()
                    appLockManager.unlock()
                }
                is PinResult.Invalid -> _unlock.value = UnlockUiState(
                    message = result.reason,
                    isError = true
                )
            }
        }
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            authRepository.onBiometricSuccess()
            _unlock.value = UnlockUiState()
            appLockManager.unlock()
        }
    }

    fun onBiometricError(message: String) {
        _unlock.value = _unlock.value.copy(message = message, isError = true)
    }

    // ---- first-run setup -------------------------------------------------

    fun setSetupName(value: String) {
        _setup.value = _setup.value.copy(name = value, message = null, isError = false)
    }

    fun onSetupDigit(digit: Char) {
        val current = _setup.value
        when (current.step) {
            SetupStep.CHOOSE_PIN -> if (current.pin.length < PinHasher.MAX_LENGTH) {
                _setup.value = current.copy(pin = current.pin + digit, message = null, isError = false)
            }
            SetupStep.CONFIRM_PIN -> if (current.confirmPin.length < PinHasher.MAX_LENGTH) {
                _setup.value = current.copy(
                    confirmPin = current.confirmPin + digit,
                    message = null,
                    isError = false
                )
            }
            else -> Unit
        }
    }

    fun onSetupBackspace() {
        val current = _setup.value
        when (current.step) {
            SetupStep.CHOOSE_PIN -> _setup.value = current.copy(pin = current.pin.dropLast(1))
            SetupStep.CONFIRM_PIN -> _setup.value = current.copy(confirmPin = current.confirmPin.dropLast(1))
            else -> Unit
        }
    }

    fun advanceSetup() {
        val current = _setup.value
        when (current.step) {
            SetupStep.PROFILE -> {
                if (current.name.isBlank()) return
                _setup.value = current.copy(step = SetupStep.CHOOSE_PIN, message = null)
            }
            SetupStep.CHOOSE_PIN -> {
                if (!PinHasher.isValidPin(current.pin)) {
                    _setup.value = current.copy(
                        message = "Use ${PinHasher.MIN_LENGTH}–${PinHasher.MAX_LENGTH} digits.",
                        isError = true
                    )
                    return
                }
                _setup.value = current.copy(step = SetupStep.CONFIRM_PIN, message = null, isError = false)
            }
            SetupStep.CONFIRM_PIN -> {
                if (current.pin != current.confirmPin) {
                    _setup.value = current.copy(
                        confirmPin = "",
                        message = "Those didn't match. Try again.",
                        isError = true
                    )
                    return
                }
                finishSetup(withPin = current.pin)
            }
        }
    }

    fun backSetupStep() {
        val current = _setup.value
        _setup.value = when (current.step) {
            SetupStep.CONFIRM_PIN -> current.copy(step = SetupStep.CHOOSE_PIN, confirmPin = "", message = null)
            SetupStep.CHOOSE_PIN -> current.copy(step = SetupStep.PROFILE, pin = "", message = null)
            SetupStep.PROFILE -> current
        }
    }

    /** "Skip for now" — a profile is created, but the app stays unlocked. */
    fun skipLock() {
        finishSetup(withPin = null)
    }

    private fun finishSetup(withPin: String?) {
        viewModelScope.launch {
            authRepository.createProfile(_setup.value.name)
            if (withPin != null) {
                when (val result = authRepository.setPin(withPin)) {
                    PinResult.Success -> Unit
                    is PinResult.Invalid -> {
                        _setup.value = _setup.value.copy(message = result.reason, isError = true)
                        return@launch
                    }
                    else -> {
                        _setup.value = _setup.value.copy(
                            message = "Couldn't save that PIN. Try again.",
                            isError = true
                        )
                        return@launch
                    }
                }
            }
            appLockManager.unlock()
            _setup.value = _setup.value.copy(finished = true, message = null, isError = false)
        }
    }
}
