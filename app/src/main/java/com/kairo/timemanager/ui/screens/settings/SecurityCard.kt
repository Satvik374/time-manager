package com.kairo.timemanager.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kairo.timemanager.core.PinHasher
import com.kairo.timemanager.data.prefs.AuthState
import com.kairo.timemanager.ui.components.KairoCard
import com.kairo.timemanager.ui.components.KairoChip
import com.kairo.timemanager.ui.screens.auth.AuthMessage
import com.kairo.timemanager.ui.screens.auth.PinDots
import com.kairo.timemanager.ui.screens.auth.PinPad
import com.kairo.timemanager.ui.screens.auth.biometricAvailable
import com.kairo.timemanager.ui.screens.auth.biometricNeedsEnrolment

private enum class PinFlow { SET, CHANGE, REMOVE }

private enum class PinFlowStep { CURRENT, NEW, CONFIRM }

@Composable
fun SecurityCard(
    auth: AuthState,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    var flow by remember { mutableStateOf<PinFlow?>(null) }
    var editingName by remember { mutableStateOf(false) }

    KairoCard {
        Text(
            "Kairo has no account and no server. This is a lock on this device: it keeps your " +
                "timetable, coach conversation and API key behind a PIN if the phone is unlocked " +
                "and handed to someone else.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Profile",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    auth.profileName.ifBlank { "No name set" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            KairoChip(text = "Rename", onClick = { editingName = true })
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "App lock",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (auth.lockEnabled) "PIN required when Kairo opens"
                    else "Off — Kairo opens straight to your day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            KairoChip(
                text = if (auth.lockEnabled) "Change PIN" else "Set a PIN",
                selected = !auth.lockEnabled,
                onClick = { flow = if (auth.lockEnabled) PinFlow.CHANGE else PinFlow.SET }
            )
        }

        if (auth.lockEnabled) {
            Spacer(Modifier.height(14.dp))
            val canUseBiometrics = biometricAvailable(context)
            SettingSwitchRow(
                title = "Unlock with biometrics",
                subtitle = when {
                    canUseBiometrics -> "Use your fingerprint or face instead of typing the PIN."
                    biometricNeedsEnrolment(context) -> "Enrol a fingerprint or face in system settings first."
                    else -> "This device has no biometric hardware Kairo can use."
                },
                checked = auth.biometricEnabled && canUseBiometrics,
                enabled = canUseBiometrics,
                onCheckedChange = viewModel::setBiometricEnabled
            )

            Spacer(Modifier.height(14.dp))
            Text(
                "Lock again after",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Instantly", 1 to "1 min", 5 to "5 min", 15 to "15 min", 60 to "1 hour")
                    .forEach { (minutes, label) ->
                        KairoChip(
                            text = label,
                            selected = auth.autoLockMinutes == minutes,
                            onClick = { viewModel.setAutoLockMinutes(minutes) }
                        )
                    }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KairoChip(text = "Lock now", selected = true, onClick = viewModel::lockNow)
                KairoChip(text = "Remove lock", onClick = { flow = PinFlow.REMOVE })
            }
        }
    }

    if (editingName) {
        RenameDialog(
            current = auth.profileName,
            onDismiss = { editingName = false },
            onConfirm = { name ->
                viewModel.setProfileName(name)
                editingName = false
            }
        )
    }

    flow?.let { active ->
        PinFlowDialog(
            flow = active,
            onDismiss = { flow = null },
            onSubmitPin = { current, new ->
                if (active == PinFlow.REMOVE) {
                    viewModel.removeLock(current.orEmpty()) { ok -> if (ok) flow = null }
                } else {
                    viewModel.savePin(current, new.orEmpty()) { ok -> if (ok) flow = null }
                }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun RenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What should the coach call you?") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * One dialog covering set / change / remove, because all three are the same keypad with a
 * different number of steps. Passes the current PIN up only when the flow actually needs it.
 */
@Composable
private fun PinFlowDialog(
    flow: PinFlow,
    onDismiss: () -> Unit,
    onSubmitPin: (current: String?, new: String?) -> Unit
) {
    var step by remember(flow) {
        mutableStateOf(if (flow == PinFlow.SET) PinFlowStep.NEW else PinFlowStep.CURRENT)
    }
    var currentPin by remember(flow) { mutableStateOf("") }
    var newPin by remember(flow) { mutableStateOf("") }
    var confirmPin by remember(flow) { mutableStateOf("") }
    var error by remember(flow) { mutableStateOf<String?>(null) }

    val active = when (step) {
        PinFlowStep.CURRENT -> currentPin
        PinFlowStep.NEW -> newPin
        PinFlowStep.CONFIRM -> confirmPin
    }

    fun setActive(value: String) {
        when (step) {
            PinFlowStep.CURRENT -> currentPin = value
            PinFlowStep.NEW -> newPin = value
            PinFlowStep.CONFIRM -> confirmPin = value
        }
        error = null
    }

    val title = when (flow) {
        PinFlow.SET -> if (step == PinFlowStep.CONFIRM) "Confirm your PIN" else "Choose a PIN"
        PinFlow.CHANGE -> when (step) {
            PinFlowStep.CURRENT -> "Enter your current PIN"
            PinFlowStep.NEW -> "Choose a new PIN"
            PinFlowStep.CONFIRM -> "Confirm your new PIN"
        }
        PinFlow.REMOVE -> "Enter your PIN to remove the lock"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (flow == PinFlow.REMOVE) {
                    Text(
                        "Kairo will open without asking for anything. Your data stays on the device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                }
                PinDots(length = active.length, error = error != null)
                Spacer(Modifier.height(8.dp))
                AuthMessage(error, isError = true)
                Spacer(Modifier.height(10.dp))
                PinPad(
                    onDigit = { digit ->
                        if (active.length < PinHasher.MAX_LENGTH) setActive(active + digit)
                    },
                    onBackspace = { if (active.isNotEmpty()) setActive(active.dropLast(1)) }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = active.length >= PinHasher.MIN_LENGTH,
                onClick = {
                    when (step) {
                        PinFlowStep.CURRENT ->
                            if (flow == PinFlow.REMOVE) onSubmitPin(currentPin, null)
                            else step = PinFlowStep.NEW

                        PinFlowStep.NEW -> {
                            if (!PinHasher.isValidPin(newPin)) {
                                error = "Use ${PinHasher.MIN_LENGTH}–${PinHasher.MAX_LENGTH} digits."
                            } else {
                                step = PinFlowStep.CONFIRM
                            }
                        }

                        PinFlowStep.CONFIRM -> {
                            if (newPin != confirmPin) {
                                confirmPin = ""
                                error = "Those didn't match."
                            } else {
                                onSubmitPin(currentPin.takeIf { flow == PinFlow.CHANGE }, newPin)
                            }
                        }
                    }
                }
            ) {
                Text(
                    when {
                        flow == PinFlow.REMOVE -> "Remove lock"
                        step == PinFlowStep.CONFIRM -> "Save"
                        else -> "Next"
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
