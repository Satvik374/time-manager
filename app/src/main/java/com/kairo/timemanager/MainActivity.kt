package com.kairo.timemanager

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.timemanager.data.prefs.AppPrefs
import com.kairo.timemanager.data.prefs.SettingsRepository
import com.kairo.timemanager.ui.KairoRoot
import com.kairo.timemanager.ui.theme.KairoTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {
    val prefs: StateFlow<AppPrefs> = settingsRepository.appPrefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPrefs())
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Reminders simply stay silent if the user declines. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val startRoute = intent?.getStringExtra(EXTRA_ROUTE)

        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val prefs by themeViewModel.prefs.collectAsStateWithLifecycle()
            KairoTheme(themeMode = prefs.themeMode, dynamicColor = prefs.dynamicColor) {
                KairoRoot(startRoute = startRoute)
            }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "kairo_route"
    }
}
