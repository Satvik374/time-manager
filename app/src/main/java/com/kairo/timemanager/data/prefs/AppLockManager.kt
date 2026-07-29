package com.kairo.timemanager.data.prefs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime lock state, kept in memory rather than persisted: a cold start is always locked when a
 * lock is configured, and the grace period only survives while the process does. Persisting
 * "unlocked" would mean a killed process comes back unlocked, which defeats the point.
 */
@Singleton
class AppLockManager @Inject constructor(
    private val authRepository: AuthRepository
) {

    private val scope = CoroutineScope(SupervisorJob())

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: Long = 0L
    private var initialised = false

    /** Called once on process start, before the first frame decides what to show. */
    fun initialise() {
        if (initialised) return
        initialised = true
        scope.launch {
            _locked.value = authRepository.current().lockEnabled
        }
    }

    fun unlock() {
        _locked.value = false
        backgroundedAt = 0L
    }

    fun lockNow() {
        scope.launch {
            if (authRepository.current().lockEnabled) _locked.value = true
        }
    }

    fun onStopped() {
        backgroundedAt = System.currentTimeMillis()
    }

    /**
     * Re-locks when the app has been away longer than the user's grace period.
     * A zero-minute setting means lock the moment the app leaves the foreground.
     */
    fun onStarted() {
        val leftAt = backgroundedAt
        backgroundedAt = 0L
        scope.launch {
            val state = authRepository.current()
            if (!state.lockEnabled) {
                _locked.value = false
                return@launch
            }
            if (_locked.value) return@launch
            if (leftAt == 0L) return@launch
            val elapsedMinutes = (System.currentTimeMillis() - leftAt) / 60_000.0
            if (elapsedMinutes >= state.autoLockMinutes) _locked.value = true
        }
    }

    /** After the lock is turned on or off in Settings, keep the runtime state consistent. */
    fun onLockConfigurationChanged(lockEnabled: Boolean) {
        if (!lockEnabled) _locked.value = false
    }
}
