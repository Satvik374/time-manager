package com.kairo.timemanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `accepts pins of the documented length and shape`() {
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("12345678"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("123456789"))
        assertFalse(PinHasher.isValidPin("12a4"))
        assertFalse(PinHasher.isValidPin(""))
        assertFalse(PinHasher.isValidPin("12 34"))
    }

    @Test
    fun `a correct pin verifies and a wrong one does not`() {
        val digest = PinHasher.hash("4821")
        assertTrue(PinHasher.verify("4821", digest))
        assertFalse(PinHasher.verify("4822", digest))
        assertFalse(PinHasher.verify("482", digest))
        assertFalse(PinHasher.verify("", digest))
    }

    @Test
    fun `the pin itself never appears in the stored digest`() {
        val pin = "738291"
        val serialized = PinHasher.hash(pin).serialize()
        assertFalse(serialized.contains(pin))
    }

    @Test
    fun `the same pin hashes differently each time thanks to the salt`() {
        val first = PinHasher.hash("1234")
        val second = PinHasher.hash("1234")
        assertNotEquals(first.saltBase64, second.saltBase64)
        assertNotEquals(first.hashBase64, second.hashBase64)
        // Both must still verify — the salt travels with the digest.
        assertTrue(PinHasher.verify("1234", first))
        assertTrue(PinHasher.verify("1234", second))
    }

    @Test
    fun `digests round-trip through serialization`() {
        val digest = PinHasher.hash("9090")
        val parsed = PinDigest.parse(digest.serialize())
        assertEquals(digest, parsed)
        assertTrue(PinHasher.verify("9090", parsed!!))
    }

    @Test
    fun `malformed stored digests are rejected rather than crashing`() {
        assertNull(PinDigest.parse(null))
        assertNull(PinDigest.parse(""))
        assertNull(PinDigest.parse("nonsense"))
        assertNull(PinDigest.parse("alg\$notanumber\$salt\$hash"))

        val broken = PinDigest(PinHasher.ALGORITHM, PinHasher.ITERATIONS, "!!!not base64!!!", "!!!")
        assertFalse(PinHasher.verify("1234", broken))
    }

    @Test
    fun `the iteration count is high enough to slow offline guessing`() {
        // A four-digit PIN has 10k possibilities; the work factor is the only real defence.
        assertTrue(PinHasher.ITERATIONS >= 100_000)
    }
}

class LockoutPolicyTest {

    @Test
    fun `early mistakes cost nothing`() {
        for (attempt in 0 until LockoutPolicy.ATTEMPTS_BEFORE_LOCKOUT) {
            assertEquals(0L, LockoutPolicy.lockoutMillisFor(attempt))
        }
    }

    @Test
    fun `the cooldown starts at the threshold and never shrinks`() {
        assertTrue(LockoutPolicy.lockoutMillisFor(LockoutPolicy.ATTEMPTS_BEFORE_LOCKOUT) > 0L)
        var previous = 0L
        for (attempt in 0..20) {
            val cooldown = LockoutPolicy.lockoutMillisFor(attempt)
            assertTrue("cooldown shrank at attempt $attempt", cooldown >= previous)
            previous = cooldown
        }
    }

    @Test
    fun `the cooldown is capped so a forgotten tap sequence is not a lockout forever`() {
        assertEquals(LockoutPolicy.lockoutMillisFor(50), LockoutPolicy.lockoutMillisFor(500))
        assertTrue(LockoutPolicy.lockoutMillisFor(500) <= 15 * 60_000L)
    }

    @Test
    fun `attempts left counts down and floors at zero`() {
        assertEquals(LockoutPolicy.ATTEMPTS_BEFORE_LOCKOUT, LockoutPolicy.attemptsLeft(0))
        assertEquals(1, LockoutPolicy.attemptsLeft(LockoutPolicy.ATTEMPTS_BEFORE_LOCKOUT - 1))
        assertEquals(0, LockoutPolicy.attemptsLeft(LockoutPolicy.ATTEMPTS_BEFORE_LOCKOUT))
        assertEquals(0, LockoutPolicy.attemptsLeft(99))
    }
}
