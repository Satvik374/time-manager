package com.kairo.timemanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.timemanager.data.prefs.AppLockManager
import com.kairo.timemanager.data.prefs.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What the app should show before any tab is reachable. */
sealed interface GateState {
    data object Loading : GateState
    data object Setup : GateState
    data object Locked : GateState
    data object Unlocked : GateState
}

@HiltViewModel
class AppGateViewModel @Inject constructor(
    authRepository: AuthRepository,
    appLockManager: AppLockManager
) : ViewModel() {

    init {
        appLockManager.initialise()
    }

    val gate: StateFlow<GateState> = combine(
        authRepository.state,
        appLockManager.locked
    ) { auth, locked ->
        when {
            auth.needsOnboarding -> GateState.Setup
            locked && auth.lockEnabled -> GateState.Locked
            else -> GateState.Unlocked
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GateState.Loading)
}
