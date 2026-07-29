package com.kairo.timemanager.ui.screens.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * First-run setup: a name, then an optional PIN. Kept to one screen with three steps because a
 * long onboarding flow for a local-only app would be ceremony without payoff.
 */
@Composable
fun SetupScreen(
    onFinished: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.setup.collectAsStateWithLifecycle()

    LaunchedEffect(state.finished) {
        if (state.finished) onFinished()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (state.step != SetupStep.PROFILE) {
                IconButton(onClick = viewModel::backSetupStep) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back a step")
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.weight(1f))
            StepDots(current = state.step.ordinal, total = SetupStep.entries.size)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "setupStep"
        ) { step ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (step) {
                    SetupStep.PROFILE -> ProfileStep(state, viewModel)
                    SetupStep.CHOOSE_PIN -> PinStep(
                        title = "Set a PIN",
                        body = "Kairo will ask for this when you open it. " +
                            "4 to 8 digits.",
                        state = state,
                        viewModel = viewModel
                    )
                    SetupStep.CONFIRM_PIN -> PinStep(
                        title = "Confirm your PIN",
                        body = "Enter it once more so a typo can't lock you out.",
                        state = state,
                        viewModel = viewModel
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        AuthActionButton(
            text = when (state.step) {
                SetupStep.PROFILE -> "Continue"
                SetupStep.CHOOSE_PIN -> "Next"
                SetupStep.CONFIRM_PIN -> "Finish setup"
            },
            enabled = state.canContinue,
            onClick = viewModel::advanceSetup
        )

        if (state.step != SetupStep.PROFILE) {
            Spacer(Modifier.height(14.dp))
            AuthTextLink("Skip — don't lock the app", onClick = viewModel::skipLock)
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "Everything stays on this phone. There's no account to create and no server to sign " +
                "in to — the PIN simply keeps your timetable private on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun ProfileStep(state: SetupUiState, viewModel: AuthViewModel) {
    Text(
        "Welcome to Kairo",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "What should the coach call you?",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(22.dp))
    OutlinedTextField(
        value = state.name,
        onValueChange = viewModel::setSetupName,
        label = { Text("Your name") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    AuthMessage(state.message, state.isError)
}

@Composable
private fun PinStep(
    title: String,
    body: String,
    state: SetupUiState,
    viewModel: AuthViewModel
) {
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(6.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(22.dp))
    PinDots(length = state.activePin.length, error = state.isError)
    Spacer(Modifier.height(10.dp))
    AuthMessage(state.message, state.isError)
    Spacer(Modifier.height(16.dp))
    PinPad(
        onDigit = viewModel::onSetupDigit,
        onBackspace = viewModel::onSetupBackspace
    )
}

@Composable
private fun StepDots(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            Box(
                Modifier
                    .height(5.dp)
                    .width(if (index == current) 20.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index <= current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}
