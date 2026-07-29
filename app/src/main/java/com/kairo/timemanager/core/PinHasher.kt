package com.kairo.timemanager.core

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** A stored PIN: never the PIN itself, only a salted PBKDF2 digest of it. */
data class PinDigest(
    val algorithm: String,
    val iterations: Int,
    val saltBase64: String,
    val hashBase64: String
) {
    fun serialize(): String = listOf(algorithm, iterations.toString(), saltBase64, hashBase64)
        .joinToString(SEPARATOR)

    companion object {
        private const val SEPARATOR = "$"

        fun parse(raw: String?): PinDigest? {
            val parts = raw?.split(SEPARATOR) ?: return null
            if (parts.size != 4) return null
            val iterations = parts[1].toIntOrNull() ?: return null
            return PinDigest(parts[0], iterations, parts[2], parts[3])
        }
    }
}

/**
 * PBKDF2 over the PIN with a per-user random salt.
 *
 * A 4–8 digit PIN has a tiny keyspace, so the iteration count is the only thing standing between a
 * dumped preferences file and the PIN. It is set high enough to make offline guessing slow while
 * staying under ~200ms on a mid-range phone. This protects the PIN value itself; it is not
 * encryption of the app's data, which is covered by Android's own app sandbox.
 */
object PinHasher {

    const val ALGORITHM = "PBKDF2WithHmacSHA256"
    const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    private val random = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getEncoder()
    private val decoder: Base64.Decoder = Base64.getDecoder()

    fun isValidPin(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }

    fun hash(pin: String): PinDigest {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return PinDigest(
            algorithm = ALGORITHM,
            iterations = ITERATIONS,
            saltBase64 = encoder.encodeToString(salt),
            hashBase64 = encoder.encodeToString(derive(pin, salt, ITERATIONS))
        )
    }

    fun verify(pin: String, digest: PinDigest): Boolean {
        val salt = runCatching { decoder.decode(digest.saltBase64) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(digest.hashBase64) }.getOrNull() ?: return false
        val actual = runCatching { derive(pin, salt, digest.iterations) }.getOrNull() ?: return false
        return constantTimeEquals(expected, actual)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Compares without an early exit, so timing can't leak how much of the digest matched. */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var difference = 0
        for (index in a.indices) difference = difference or (a[index].toInt() xor b[index].toInt())
        return difference == 0
    }
}

/**
 * Escalating lockout after repeated wrong PINs, so a stolen phone can't be brute-forced by hand.
 * Deliberately capped — this is a nuisance barrier, not a wipe policy.
 */
object LockoutPolicy {

    const val ATTEMPTS_BEFORE_LOCKOUT = 5

    fun lockoutMillisFor(failedAttempts: Int): Long = when {
        failedAttempts < ATTEMPTS_BEFORE_LOCKOUT -> 0L
        failedAttempts < 7 -> 30_000L
        failedAttempts < 9 -> 2 * 60_000L
        failedAttempts < 12 -> 5 * 60_000L
        else -> 15 * 60_000L
    }

    fun attemptsLeft(failedAttempts: Int): Int =
        (ATTEMPTS_BEFORE_LOCKOUT - failedAttempts).coerceAtLeast(0)
}
