package com.kairo.timemanager.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kairo.timemanager.core.LockoutPolicy
import com.kairo.timemanager.core.PinDigest
import com.kairo.timemanager.core.PinHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Who the user is on this device, and how the app should be unlocked. */
data class AuthState(
    val profileName: String = "",
    val hasProfile: Boolean = false,
    val lockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 5,
    val failedAttempts: Int = 0,
    val lockedUntil: Long = 0L
) {
    val isLockedOut: Boolean get() = System.currentTimeMillis() < lockedUntil
    val secondsUntilRetry: Int
        get() = ((lockedUntil - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
    val attemptsLeft: Int get() = LockoutPolicy.attemptsLeft(failedAttempts)

    /** Setup is complete once a profile exists; a lock is optional on top of that. */
    val needsOnboarding: Boolean get() = !hasProfile
}

sealed interface PinResult {
    data object Success : PinResult
    data class Wrong(val attemptsLeft: Int) : PinResult
    data class LockedOut(val seconds: Int) : PinResult
    data object NoPinSet : PinResult
    data class Invalid(val reason: String) : PinResult
}

/**
 * Device-local authentication. There is no account server: this gates access to the app on this
 * phone, and nothing more. Wording throughout the UI says so rather than implying a cloud login.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private object Keys {
        val PROFILE_NAME = stringPreferencesKey("auth_profile_name")
        val HAS_PROFILE = booleanPreferencesKey("auth_has_profile")
        val PIN_DIGEST = stringPreferencesKey("auth_pin_digest")
        val LOCK_ENABLED = booleanPreferencesKey("auth_lock_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("auth_biometric_enabled")
        val AUTO_LOCK_MINUTES = intPreferencesKey("auth_auto_lock_minutes")
        val FAILED_ATTEMPTS = intPreferencesKey("auth_failed_attempts")
        val LOCKED_UNTIL = longPreferencesKey("auth_locked_until")
    }

    val state: Flow<AuthState> = dataStore.data.map { prefs ->
        AuthState(
            profileName = prefs[Keys.PROFILE_NAME].orEmpty(),
            hasProfile = prefs[Keys.HAS_PROFILE] ?: false,
            lockEnabled = (prefs[Keys.LOCK_ENABLED] ?: false) && prefs[Keys.PIN_DIGEST] != null,
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            autoLockMinutes = prefs[Keys.AUTO_LOCK_MINUTES] ?: 5,
            failedAttempts = prefs[Keys.FAILED_ATTEMPTS] ?: 0,
            lockedUntil = prefs[Keys.LOCKED_UNTIL] ?: 0L
        )
    }

    suspend fun current(): AuthState = state.first()

    suspend fun hasPin(): Boolean = dataStore.data.first()[Keys.PIN_DIGEST] != null

    suspend fun createProfile(name: String) {
        dataStore.edit { prefs ->
            prefs[Keys.PROFILE_NAME] = name.trim().take(40)
            prefs[Keys.HAS_PROFILE] = true
        }
    }

    suspend fun setProfileName(name: String) {
        dataStore.edit { it[Keys.PROFILE_NAME] = name.trim().take(40) }
    }

    /** Sets or replaces the PIN. Hashing runs off the main thread — PBKDF2 is deliberately slow. */
    suspend fun setPin(pin: String): PinResult = withContext(Dispatchers.Default) {
        if (!PinHasher.isValidPin(pin)) {
            return@withContext PinResult.Invalid(
                "Use ${PinHasher.MIN_LENGTH}–${PinHasher.MAX_LENGTH} digits."
            )
        }
        val digest = PinHasher.hash(pin)
        dataStore.edit { prefs ->
            prefs[Keys.PIN_DIGEST] = digest.serialize()
            prefs[Keys.LOCK_ENABLED] = true
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKED_UNTIL] = 0L
        }
        PinResult.Success
    }

    /** Replaces the PIN only if the current one checks out. */
    suspend fun changePin(currentPin: String, newPin: String): PinResult {
        val verified = verifyPin(currentPin)
        if (verified != PinResult.Success) return verified
        return setPin(newPin)
    }

    suspend fun verifyPin(pin: String): PinResult = withContext(Dispatchers.Default) {
        val prefs = dataStore.data.first()
        val digest = PinDigest.parse(prefs[Keys.PIN_DIGEST]) ?: return@withContext PinResult.NoPinSet

        val lockedUntil = prefs[Keys.LOCKED_UNTIL] ?: 0L
        val now = System.currentTimeMillis()
        if (now < lockedUntil) {
            return@withContext PinResult.LockedOut(((lockedUntil - now) / 1000).toInt() + 1)
        }

        if (PinHasher.verify(pin, digest)) {
            dataStore.edit { edit ->
                edit[Keys.FAILED_ATTEMPTS] = 0
                edit[Keys.LOCKED_UNTIL] = 0L
            }
            return@withContext PinResult.Success
        }

        val attempts = (prefs[Keys.FAILED_ATTEMPTS] ?: 0) + 1
        val cooldown = LockoutPolicy.lockoutMillisFor(attempts)
        dataStore.edit { edit ->
            edit[Keys.FAILED_ATTEMPTS] = attempts
            edit[Keys.LOCKED_UNTIL] = if (cooldown > 0) now + cooldown else 0L
        }
        if (cooldown > 0) PinResult.LockedOut((cooldown / 1000).toInt())
        else PinResult.Wrong(LockoutPolicy.attemptsLeft(attempts))
    }

    /** Clears the failure counter after a successful biometric unlock. */
    suspend fun onBiometricSuccess() {
        dataStore.edit { prefs ->
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKED_UNTIL] = 0L
        }
    }

    suspend fun disableLock(currentPin: String): PinResult {
        val verified = verifyPin(currentPin)
        if (verified != PinResult.Success) return verified
        dataStore.edit { prefs ->
            prefs.remove(Keys.PIN_DIGEST)
            prefs[Keys.LOCK_ENABLED] = false
            prefs[Keys.BIOMETRIC_ENABLED] = false
            prefs[Keys.FAILED_ATTEMPTS] = 0
            prefs[Keys.LOCKED_UNTIL] = 0L
        }
        return PinResult.Success
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        // Biometric unlock is a shortcut past the PIN, so it can't exist without one.
        if (enabled && !hasPin()) return
        dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setAutoLockMinutes(minutes: Int) {
        dataStore.edit { it[Keys.AUTO_LOCK_MINUTES] = minutes.coerceIn(0, 120) }
    }
}
