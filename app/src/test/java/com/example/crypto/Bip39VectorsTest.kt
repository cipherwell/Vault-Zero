package com.example.crypto

import cash.z.ecc.android.bip39.Mnemonics
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Independent cross-check of VaultZero's BIP-39 implementation against:
 *  - the official Trezor BIP-39 vectors (all 24 English vectors, 128–256 bit), and
 *  - the maintained Zcash `kotlin-bip39` implementation (test-only oracle; not shipped).
 *
 * VaultZero only generates 12-word/128-bit phrases, but validating all official vectors
 * catches encoder/bit-order mistakes that a single vector can miss.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Bip39VectorsTest {

    private fun loadVectors(): JSONArray {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("bip39_vectors.json")) {
            "bip39_vectors.json missing from test resources"
        }
        return JSONArray(stream.bufferedReader().use { it.readText() })
    }

    @Test
    fun `all official BIP39 vectors validate against the reference library`() {
        val vectors = loadVectors()
        assertEquals(24, vectors.length())
        var oursChecked = 0
        for (i in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(i)
            val entropy = hexToBytes(vector.getString("entropy"))
            val mnemonic = vector.getString("mnemonic")
            val words = mnemonic.split(" ")

            // VaultZero supports 12-word/128-bit phrases; the reference implementation
            // validates all official lengths (128–256 bit). Cross-check every vector there.
            val reference = Mnemonics.MnemonicCode(mnemonic)
            reference.validate()
            assertEquals("vector $i entropy", vector.getString("entropy"), bytesToHex(reference.toEntropy()))
            reference.clear()

            val referenceFromEntropy = Mnemonics.MnemonicCode(entropy)
            assertEquals("vector $i reference encode", mnemonic, referenceFromEntropy.chars.concatToString())
            referenceFromEntropy.clear()

            if (entropy.size == 16) {
                oursChecked++
                assertEquals("vector $i", words, Bip39Recovery.entropyToMnemonic(entropy))
                assertTrue("vector $i checksum", Bip39Recovery.isValidMnemonic(words))
                assertTrue("vector $i app validation", VaultCrypto.validateRecoveryPhrase(mnemonic))
            } else {
                assertFalse("only 12-word phrases are supported by VaultZero", Bip39Recovery.isValidMnemonic(words))
            }
        }
        assertTrue("official 128-bit vectors must be exercised", oursChecked >= 5)
    }

    @Test
    fun `tampered checksum is rejected by both implementations`() {
        val vectors = loadVectors()
        for (i in 0 until vectors.length()) {
            val vector = vectors.getJSONObject(i)
            if (hexToBytes(vector.getString("entropy")).size != 16) continue
            val mnemonic = vector.getString("mnemonic")
            val words = mnemonic.split(" ").toMutableList()
            val replacement = Bip39Recovery.WORDLIST.first { it != words[11] }
            words[11] = replacement
            val tampered = words.joinToString(" ")

            assertFalse("vector $i must fail our checksum", Bip39Recovery.isValidMnemonic(words))
            assertThrows(Mnemonics.ChecksumException::class.java) {
                Mnemonics.MnemonicCode(tampered).validate()
            }
        }
    }

    @Test
    fun `random 128-bit entropies agree with the reference library`() {
        val random = java.security.SecureRandom()
        repeat(100) {
            val entropy = ByteArray(16).also(random::nextBytes)
            val ours = Bip39Recovery.entropyToMnemonic(entropy)
            val reference = Mnemonics.MnemonicCode(entropy)
            assertEquals(ours.joinToString(" "), reference.chars.concatToString())
            assertEquals(ours, Mnemonics.MnemonicCode(ours.joinToString(" ")).let { ref ->
                ref.validate()
                ref.toEntropy().let { recovered -> Bip39Recovery.entropyToMnemonic(recovered) }
            })
            reference.clear()
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0)
        return ByteArray(hex.length / 2) { i ->
            ((Character.digit(hex[i * 2], 16) shl 4) or Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
    }

    private fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
