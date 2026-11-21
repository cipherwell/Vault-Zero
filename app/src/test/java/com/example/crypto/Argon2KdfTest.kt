package com.example.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Arrays

/**
 * Argon2id is the password/recovery KDF as of 1.6.0. These tests pin the
 * implementation to the RFC 9106 known-answer vector, prove the spec encoding
 * round-trips, and prove that hostile specs are rejected instead of silently
 * driving the derivation.
 */
class Argon2KdfTest {

    // ---- RFC 9106 §5.3 Argon2id test vector ----

    @Test
    fun `Argon2id matches the RFC 9106 known-answer vector`() {
        val tag = VaultCrypto.argon2idRaw(
            passwordBytes = ByteArray(32) { 0x01 },
            salt = ByteArray(16) { 0x02 },
            memoryKiB = 32,
            iterations = 3,
            parallelism = 4,
            secret = ByteArray(8) { 0x03 },
            associatedData = ByteArray(12) { 0x04 },
        )
        assertEquals(
            "0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659",
            VaultCrypto.bytesToHex(tag),
        )
    }

    @Test
    fun `Argon2id without secret or associated data still derives deterministically`() {
        val salt = ByteArray(16) { 0x02 }
        val first = VaultCrypto.argon2idRaw(ByteArray(32) { 0x01 }, salt, 32, 3, 4)
        val second = VaultCrypto.argon2idRaw(ByteArray(32) { 0x01 }, salt, 32, 3, 4)
        assertArrayEquals(first, second)
        assertEquals(VaultCrypto.KEY_LENGTH_BYTES, first.size)
    }

    // ---- production parameters ----

    @Test
    fun `current KDF is Argon2id at the documented strength`() {
        assertEquals(KdfAlgorithm.ARGON2ID, KdfSpec.CURRENT.algorithm)
        assertEquals(65_536, KdfSpec.CURRENT.memoryKiB)
        assertEquals(3, KdfSpec.CURRENT.iterations)
        assertEquals(1, KdfSpec.CURRENT.parallelism)
        assertTrue(KdfSpec.CURRENT.isSupported())
    }

    @Test
    fun `deriveKey with the current KDF is deterministic and salt-bound`() {
        val salt = ByteArray(16) { 9 }
        val a = VaultCrypto.deriveKey("correct horse battery staple", salt).encoded
        val b = VaultCrypto.deriveKey("correct horse battery staple", salt).encoded
        val c = VaultCrypto.deriveKey("correct horse battery staple", ByteArray(16) { 10 }).encoded
        try {
            assertArrayEquals(a, b)
            assertFalse(a.contentEquals(c))
            assertEquals(VaultCrypto.KEY_LENGTH_BYTES, a.size)
        } finally {
            Arrays.fill(a, 0)
            Arrays.fill(b, 0)
            Arrays.fill(c, 0)
        }
    }

    @Test
    fun `Argon2id and PBKDF2 never produce the same key for the same input`() {
        val salt = ByteArray(16) { 4 }
        val argon = VaultCrypto.deriveKey("same password", salt).encoded
        val pbkdf2 = VaultCrypto.derivePbkdf2Key("same password", salt).encoded
        try {
            assertFalse(
                "changing the KDF must change the key or old envelopes would open with new specs",
                argon.contentEquals(pbkdf2),
            )
        } finally {
            Arrays.fill(argon, 0)
            Arrays.fill(pbkdf2, 0)
        }
    }

    @Test
    fun `a recovery phrase derives through the same spec dispatcher`() {
        val phrase = "abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon about"
        val salt = ByteArray(16) { 5 }
        val fromPhrase = VaultCrypto.deriveKeyFromPhrase(phrase, salt, KdfSpec.CURRENT).encoded
        val fromPassword = VaultCrypto.deriveKey(phrase, salt, KdfSpec.CURRENT).encoded
        try {
            assertArrayEquals(fromPhrase, fromPassword)
        } finally {
            Arrays.fill(fromPhrase, 0)
            Arrays.fill(fromPassword, 0)
        }
    }

    // ---- spec encoding ----

    @Test
    fun `KdfSpec encode and decode round-trip for both algorithms`() {
        val argon = KdfSpec(KdfAlgorithm.ARGON2ID, 3, 65_536, 1)
        val pbkdf2 = KdfSpec.pbkdf2(600_000)
        assertEquals("argon2id:65536:3:1", argon.encode())
        assertEquals("pbkdf2-sha256:600000", pbkdf2.encode())
        assertEquals(argon, KdfSpec.decode(argon.encode()))
        assertEquals(pbkdf2, KdfSpec.decode(pbkdf2.encode()))
        assertEquals(argon, KdfSpec.decode("  ARGON2ID:65536:3:1  "))
    }

    @Test
    fun `KdfSpec decode rejects malformed and unsupported values`() {
        val rejected = listOf(
            null, "", "   ",
            "argon2",                       // no parameters
            "argon2id:65536:3",             // missing parallelism
            "argon2id:65536:3:1:1",         // extra field
            "argon2id:x:3:1",               // non-numeric
            "pbkdf2-sha256",                // missing iterations
            "pbkdf2-sha256:1000",           // below the historical floor
            "pbkdf2-sha256:5000000",        // above the accepted ceiling
            "argon2id:1024:3:1",            // memory below the floor
            "argon2id:1048576:3:1",         // 1 GiB would be a memory DoS
            "argon2id:65536:99:1",          // time cost above the ceiling
            "argon2id:65536:3:64",          // parallelism above the ceiling
            "scrypt:65536:3:1",             // unknown algorithm
        )
        for (raw in rejected) {
            assertNull("must reject [$raw]", KdfSpec.decode(raw))
        }
    }

    @Test
    fun `deriveKey refuses an unsupported spec instead of clamping it`() {
        val hostile = KdfSpec(KdfAlgorithm.ARGON2ID, iterations = 3, memoryKiB = 1_048_576, parallelism = 1)
        var threw = false
        try {
            VaultCrypto.deriveKey("pw", ByteArray(16) { 1 }, hostile)
        } catch (expected: IllegalArgumentException) {
            threw = true
        }
        assertTrue("a 1 GiB memory cost must be refused, not silently clamped", threw)
    }

    @Test
    fun `legacy PBKDF2 envelopes still derive through the dispatcher`() {
        val salt = ByteArray(16) { 6 }
        val legacy = KdfSpec.pbkdf2(10_000)
        val viaSpec = VaultCrypto.deriveKey("legacy password", salt, legacy).encoded
        val viaLegacyApi = VaultCrypto.derivePbkdf2Key("legacy password", salt, 10_000).encoded
        try {
            assertArrayEquals(
                "a pre-1.6.0 envelope must open with the spec form of the same PBKDF2 parameters",
                viaLegacyApi,
                viaSpec,
            )
        } finally {
            Arrays.fill(viaSpec, 0)
            Arrays.fill(viaLegacyApi, 0)
        }
    }

    @Test
    fun `different KDF algorithms are distinguishable from their spec string`() {
        assertNotEquals(
            KdfSpec.pbkdf2(600_000).encode(),
            KdfSpec.CURRENT.encode(),
        )
    }
}
