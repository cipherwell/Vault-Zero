package com.example.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Arrays

/**
 * Behavioural tests for the vault's cryptographic primitives.
 *
 * These are the invariants an offline password manager must never break:
 * authenticated encryption round-trips, any tampering is rejected, the same
 * password+salt yields the same key, different salts do not, and the recovery
 * phrase checksum catches transcription errors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // match the suite: Robolectric 4.16 does not ship an android-all jar for SDK 36
class VaultCryptoTest {

    private val key = VaultCrypto.deriveKey(
        password = "correct horse battery staple",
        salt = ByteArray(16) { (it + 1).toByte() },
        iterations = 10_000,
    )

    // ---- PBKDF2 ----

    @Test
    fun `deriveKey is deterministic for same password salt and iterations`() {
        val salt = ByteArray(16) { 7 }
        val a = VaultCrypto.deriveKey("hunter2-test", salt, 10_000)
        val b = VaultCrypto.deriveKey("hunter2-test", salt, 10_000)
        assertTrue(a.encoded.contentEquals(b.encoded))
        assertEquals(VaultCrypto.KEY_LENGTH_BYTES, a.encoded.size)
    }

    @Test
    fun `deriveKey changes with salt and with iterations`() {
        val a = VaultCrypto.deriveKey("hunter2-test", ByteArray(16) { 1 }, 10_000)
        val b = VaultCrypto.deriveKey("hunter2-test", ByteArray(16) { 2 }, 10_000)
        val c = VaultCrypto.deriveKey("hunter2-test", ByteArray(16) { 1 }, 20_000)
        assertFalse(a.encoded.contentEquals(b.encoded))
        assertFalse(a.encoded.contentEquals(c.encoded))
    }

    @Test
    fun `deriveKey rejects a short salt`() {
        var threw = false
        try {
            VaultCrypto.deriveKey("pw", ByteArray(8), 10_000)
        } catch (expected: IllegalArgumentException) {
            threw = true
        }
        assertTrue("salt shorter than 16 bytes must be rejected", threw)
    }

    // ---- AES-256-GCM ----

    @Test
    fun `encrypt and decrypt round trip with associated data`() {
        val aad = VaultCrypto.recordAssociatedData("record-1", "LOGIN")
        val sealed = VaultCrypto.encrypt("s3cr3t value", key, aad)
        assertEquals("s3cr3t value", VaultCrypto.decrypt(sealed, key, aad))
    }

    @Test
    fun `each encryption uses a fresh IV`() {
        val first = VaultCrypto.encrypt("same plaintext", key)
        val second = VaultCrypto.encrypt("same plaintext", key)
        assertNotEquals(first, second)
    }

    @Test
    fun `tampered ciphertext is rejected by the GCM tag`() {
        val sealed = VaultCrypto.encrypt("tamper me", key)
        val raw = java.util.Base64.getDecoder().decode(sealed)
        raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 0xFF).toByte()
        val tampered = java.util.Base64.getEncoder().encodeToString(raw)
        var threw = false
        try {
            VaultCrypto.decrypt(tampered, key)
        } catch (expected: Exception) {
            threw = true
        }
        assertTrue("modified tag/ciphertext must not decrypt", threw)
    }

    @Test
    fun `wrong key cannot decrypt`() {
        val sealed = VaultCrypto.encrypt("secret", key)
        val otherKey = VaultCrypto.deriveKey("another password", ByteArray(16) { 9 }, 10_000)
        var threw = false
        try {
            VaultCrypto.decrypt(sealed, otherKey)
        } catch (expected: Exception) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `associated data mismatch cannot decrypt`() {
        val sealed = VaultCrypto.encrypt("secret", key, VaultCrypto.recordAssociatedData("a", "LOGIN"))
        var threw = false
        try {
            VaultCrypto.decrypt(sealed, key, VaultCrypto.recordAssociatedData("b", "LOGIN"))
        } catch (expected: Exception) {
            threw = true
        }
        assertTrue("row-swap protection: different AAD must fail", threw)
    }

    @Test
    fun `short or malformed payloads are rejected`() {
        var threw = false
        try {
            VaultCrypto.decrypt("AAAA", key)
        } catch (expected: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `validateAes256Key rejects a 128-bit key`() {
        var threw = false
        try {
            VaultCrypto.validateAes256Key(javax.crypto.spec.SecretKeySpec(ByteArray(16), "AES"))
        } catch (expected: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    // ---- key verifier / fingerprints ----

    @Test
    fun `key verifier is deterministic and key dependent`() {
        val verifier = VaultCrypto.createKeyVerifier(key, "AUTH")
        assertEquals(verifier, VaultCrypto.createKeyVerifier(key, "AUTH"))
        assertNotEquals(verifier, VaultCrypto.createKeyVerifier(key, "RECOVERY"))
        assertNotEquals(verifier, VaultCrypto.createKeyVerifier(VaultCrypto.generateRandomKey(), "AUTH"))
    }

    // ---- recovery phrase ----

    @Test
    fun `generated recovery phrase validates and detects transcription errors`() {
        val phrase = VaultCrypto.generateRecoveryPhrase()
        assertEquals(12, phrase.trim().split(Regex("\\s+")).size)
        assertTrue(VaultCrypto.validateRecoveryPhrase(phrase))

        val words = phrase.trim().split(Regex("\\s+")).toMutableList()
        words[3] = if (words[3] == "abandon") "ability" else "abandon"
        val tampered = words.joinToString(" ")
        assertFalse(
            "a single wrong word must fail the 12th-word checksum",
            VaultCrypto.validateRecoveryPhrase(tampered, enforceChecksum = true),
        )
    }

    @Test
    fun `recovery phrase normalisation ignores case and extra whitespace`() {
        val phrase = VaultCrypto.generateRecoveryPhrase()
        val messy = "  " + phrase.uppercase().replace(" ", "   ") + "  "
        assertEquals(VaultCrypto.normalizeRecoveryPhrase(phrase), VaultCrypto.normalizeRecoveryPhrase(messy))
        assertTrue(VaultCrypto.validateRecoveryPhrase(messy))
    }

    @Test
    fun `recovery phrase keys differ from password keys for the same salt`() {
        val salt = ByteArray(16) { 3 }
        val phrase = VaultCrypto.generateRecoveryPhrase()
        val passwordKey = VaultCrypto.deriveKey("master password", salt, 10_000)
        val phraseKey = VaultCrypto.deriveKeyFromPhrase(phrase, salt, 10_000)
        assertFalse(passwordKey.encoded.contentEquals(phraseKey.encoded))
        Arrays.fill(phraseKey.encoded, 0)
    }

    // ---- helpers ----

    @Test
    fun `hex helpers round trip`() {
        val bytes = byteArrayOf(0x00, 0x0F, 0x7F, -0x01, 0x42)
        assertTrue(bytes.contentEquals(VaultCrypto.hexToBytes(VaultCrypto.bytesToHex(bytes))))
    }

    @Test
    fun `random key is 32 bytes and unique`() {
        val first = VaultCrypto.generateRandomKey().encoded
        val second = VaultCrypto.generateRandomKey().encoded
        assertEquals(32, first.size)
        assertFalse(first.contentEquals(second))
    }
}
