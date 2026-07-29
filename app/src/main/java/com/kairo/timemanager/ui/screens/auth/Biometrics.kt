package com.kairo.timemanager.ui.screens.auth

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK

/** Whether this device can actually do a biometric check right now. */
fun biometricAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS

/** True when hardware exists but the user hasn't enrolled a fingerprint or face yet. */
fun biometricNeedsEnrolment(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

/**
 * Shows the system biometric sheet. Returns a launcher that no-ops when biometrics are
 * unavailable, so callers never have to guard the call site.
 */
@Composable
fun rememberBiometricPrompt(
    title: String,
    subtitle: String,
    negativeLabel: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }

    return remember(activity, title, subtitle, negativeLabel, onSuccess, onError) {
        launcher@{
            val host = activity ?: run {
                onError("Biometric unlock isn't available here.")
                return@launcher
            }
            if (!biometricAvailable(host)) {
                onError(
                    if (biometricNeedsEnrolment(host)) "No fingerprint or face is enrolled on this device."
                    else "This device can't do a biometric check."
                )
                return@launcher
            }
            val prompt = BiometricPrompt(
                host,
                ContextCompat.getMainExecutor(host),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // Cancelling isn't a failure worth shouting about — the PIN pad is right there.
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                            errorCode != BiometricPrompt.ERROR_CANCELED
                        ) {
                            onError(errString.toString())
                        }
                    }
                }
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButtonText(negativeLabel)
                    .setAllowedAuthenticators(AUTHENTICATORS)
                    .setConfirmationRequired(false)
                    .build()
            )
        }
    }
}
