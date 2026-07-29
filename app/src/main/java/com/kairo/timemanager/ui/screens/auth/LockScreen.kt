package com.kairo.timemanager.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LockScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val auth by viewModel.authState.collectAsStateWithLifecycle()
    val state by viewModel.unlock.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val canUseBiometrics = auth.biometricEnabled && biometricAvailable(context)

    val promptBiometric = rememberBiometricPrompt(
        title = "Unlock Kairo",
        subtitle = "Use your fingerprint or face instead of your PIN",
        negativeLabel = "Use PIN",
        onSuccess = viewModel::onBiometricSuccess,
        onError = viewModel::onBiometricError
    )

    // Offer the sheet straight away, so the common case is one tap and done.
    LaunchedEffect(canUseBiometrics) {
        if (canUseBiometrics && !auth.isLockedOut) promptBiometric()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(27.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            if (auth.profileName.isBlank()) "Welcome back" else "Welcome back, ${auth.profileName}",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Enter your PIN to open your timetable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(26.dp))
        PinDots(length = state.entered.length, error = state.isError)

        Spacer(Modifier.height(10.dp))
        AuthMessage(
            text = state.message ?: if (auth.isLockedOut) {
                "Locked for ${auth.secondsUntilRetry}s after too many attempts."
            } else null,
            isError = state.isError || auth.isLockedOut
        )

        Spacer(Modifier.height(18.dp))
        PinPad(
            onDigit = viewModel::onUnlockDigit,
            onBackspace = viewModel::onUnlockBackspace,
            enabled = !state.busy && !auth.isLockedOut,
            showBiometric = canUseBiometrics,
            onBiometric = promptBiometric
        )

        Spacer(Modifier.height(22.dp))
        AuthActionButton(
            text = "Unlock",
            enabled = state.canSubmit && !auth.isLockedOut,
            onClick = viewModel::submitUnlock,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.weight(1f))
        Text(
            "Your PIN unlocks this device only. Kairo has no account and no server — " +
                "a forgotten PIN can only be cleared by reinstalling, which erases your data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 18.dp)
        )
    }
}
