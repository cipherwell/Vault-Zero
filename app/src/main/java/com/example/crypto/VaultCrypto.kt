package com.example.crypto

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.roundToInt
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Key-derivation function used to turn a password or recovery phrase into a
 * key-encryption key (KEK). New envelopes use [ARGON2ID]; [PBKDF2_SHA256] remains
 * supported so vaults and backups made before 1.6.0 still open.
 */
enum class KdfAlgorithm(val id: String) {
    PBKDF2_SHA256("pbkdf2-sha256"),
    ARGON2ID("argon2id");

    companion object {
        fun fromId(raw: String?): KdfAlgorithm? =
            entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}

/**
 * Persisted description of the KDF an envelope was created with. Encoded as a
 * single compact, versioned string so it survives SharedPreferences, backup JSON
 * and log/diagnostic dumps without extra keys:
 *
 * - `pbkdf2-sha256:<iterations>`
 * - `argon2id:<memoryKiB>:<iterations>:<parallelism>`
 */
data class KdfSpec(
    val algorithm: KdfAlgorithm,
    val iterations: Int,
    val memoryKiB: Int = 0,
    val parallelism: Int = 1,
) {
    fun encode(): String = when (algorithm) {
        KdfAlgorithm.PBKDF2_SHA256 -> "${algorithm.id}:$iterations"
        KdfAlgorithm.ARGON2ID -> "${algorithm.id}:$memoryKiB:$iterations:$parallelism"
    }

    /** True when every parameter of this spec is inside the range VaultZero produces. */
    fun isSupported(): Boolean = when (algorithm) {
        KdfAlgorithm.PBKDF2_SHA256 ->
            iterations in VaultCrypto.MIN_ACCEPTED_ITERATIONS..VaultCrypto.MAX_ACCEPTED_ITERATIONS
        KdfAlgorithm.ARGON2ID ->
            memoryKiB in VaultCrypto.ARGON2_MIN_MEMORY_KIB..VaultCrypto.ARGON2_MAX_MEMORY_KIB &&
                iterations in VaultCrypto.ARGON2_MIN_ITERATIONS..VaultCrypto.ARGON2_MAX_ITERATIONS &&
                parallelism in VaultCrypto.ARGON2_MIN_PARALLELISM..VaultCrypto.ARGON2_MAX_PARALLELISM
    }

    companion object {
        /** KDF used for every key envelope this build creates. */
        val CURRENT = KdfSpec(
            algorithm = KdfAlgorithm.ARGON2ID,
            iterations = VaultCrypto.ARGON2_ITERATIONS,
            memoryKiB = VaultCrypto.ARGON2_MEMORY_KIB,
            parallelism = VaultCrypto.ARGON2_PARALLELISM,
        )

        fun pbkdf2(iterations: Int): KdfSpec = KdfSpec(KdfAlgorithm.PBKDF2_SHA256, iterations)

        /**
         * Parse a stored/attacker-supplied spec. Returns null when the string is
         * missing, malformed or outside the supported parameter ranges, so callers
         * must fail closed rather than derive with hostile parameters.
         */
        fun decode(raw: String?): KdfSpec? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.trim().split(':')
            val algorithm = KdfAlgorithm.fromId(parts.firstOrNull()) ?: return null
            val spec = when (algorithm) {
                KdfAlgorithm.PBKDF2_SHA256 -> {
                    if (parts.size != 2) return null
                    val iterations = parts[1].toIntOrNull() ?: return null
                    pbkdf2(iterations)
                }
                KdfAlgorithm.ARGON2ID -> {
                    if (parts.size != 4) return null
                    val memoryKiB = parts[1].toIntOrNull() ?: return null
                    val iterations = parts[2].toIntOrNull() ?: return null
                    val parallelism = parts[3].toIntOrNull() ?: return null
                    KdfSpec(algorithm, iterations, memoryKiB, parallelism)
                }
            }
            return spec.takeIf { it.isSupported() }
        }
    }
}

object VaultCrypto {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val AES_CIPHER = "AES/GCM/NoPadding"
    const val GCM_IV_LENGTH_BYTES = 12
    const val GCM_TAG_LENGTH_BITS = 128
    const val GCM_TAG_LENGTH_BYTES = 16
    /**
     * PBKDF2-HMAC-SHA256 work factor accepted from legacy envelopes and backups.
     * 600_000 follows the OWASP Password Storage Cheat Sheet recommendation for
     * SHA-256 (2023+). New envelopes use [KdfSpec.CURRENT] (Argon2id); this
     * constant only bounds what an old vault or backup may legitimately ask for.
     */
    const val DEFAULT_ITERATIONS = 600_000

    /** Oldest PBKDF2 work factor still accepted when opening an existing vault. */
    const val MIN_ACCEPTED_ITERATIONS = 10_000

    /**
     * Newest PBKDF2 work factor accepted from a stored/imported envelope. An
     * attacker-controlled backup cannot ask for more work than this app ever
     * produced, which keeps a crafted file from turning import into a CPU-DoS.
     */
    const val MAX_ACCEPTED_ITERATIONS = 2_000_000

    // --- Argon2id parameters (RFC 9106) -------------------------------------------------
    /** Current memory cost: 64 MiB. */
    const val ARGON2_MEMORY_KIB = 65_536
    /** Current time cost. */
    const val ARGON2_ITERATIONS = 3
    /** Current parallelism (single lane: derivation runs on one background thread). */
    const val ARGON2_PARALLELISM = 1
    const val ARGON2_MIN_MEMORY_KIB = 8_192
    /** Hard ceiling for imported/stored specs; 128 MiB is bounded work, not a DoS. */
    const val ARGON2_MAX_MEMORY_KIB = 131_072
    const val ARGON2_MIN_ITERATIONS = 1
    const val ARGON2_MAX_ITERATIONS = 10
    const val ARGON2_MIN_PARALLELISM = 1
    const val ARGON2_MAX_PARALLELISM = 4

    /** Smallest TOTP secret accepted for display/audit (80 bits, RFC 4226 floor). */
    const val MIN_TOTP_SECRET_BYTES = 10
    const val KEY_LENGTH_BITS = 256
    const val KEY_LENGTH_BYTES = 32
    const val SALT_LENGTH_BYTES = 16

    private val secureRandom = SecureRandom()

    /**
     * Generate cryptographically secure random salt bytes (16 bytes / 128 bits)
     */
    fun generateSalt(lengthBytes: Int = SALT_LENGTH_BYTES): ByteArray {
        require(lengthBytes >= SALT_LENGTH_BYTES) { "Salt length must be at least $SALT_LENGTH_BYTES bytes" }
        val salt = ByteArray(lengthBytes)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Validate that a SecretKey is a valid 256-bit (32-byte) AES key
     */
    fun validateAes256Key(secretKey: SecretKey) {
        val encoded = secretKey.encoded
            ?: throw IllegalArgumentException("SecretKey does not support raw encoding")
        try {
            if (encoded.size != KEY_LENGTH_BYTES) {
                throw IllegalArgumentException("Invalid AES key length: expected $KEY_LENGTH_BYTES bytes (256 bits), got ${encoded.size} bytes")
            }
        } finally {
            Arrays.fill(encoded, 0.toByte())
        }
    }

    /**
     * Derive a 256-bit AES key-encryption key from a password and salt using the
     * KDF described by [spec]. New envelopes pass [KdfSpec.CURRENT] (Argon2id);
     * legacy vaults and backups pass the PBKDF2 spec they were created with.
     */
    fun deriveKey(
        password: String,
        salt: ByteArray,
        spec: KdfSpec = KdfSpec.CURRENT,
    ): SecretKey {
        require(password.isNotEmpty()) { "Password cannot be empty for key derivation" }
        require(salt.size >= SALT_LENGTH_BYTES) { "Salt must be at least $SALT_LENGTH_BYTES bytes" }
        require(spec.isSupported()) { "Unsupported KDF parameters" }
        return when (spec.algorithm) {
            KdfAlgorithm.PBKDF2_SHA256 -> derivePbkdf2Key(password, salt, spec.iterations)
            KdfAlgorithm.ARGON2ID -> {
                val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
                try {
                    SecretKeySpec(
                        deriveArgon2idBytes(
                            passwordBytes = passwordBytes,
                            salt = salt,
                            memoryKiB = spec.memoryKiB,
                            iterations = spec.iterations,
                            parallelism = spec.parallelism,
                        ),
                        "AES",
                    )
                } finally {
                    Arrays.fill(passwordBytes, 0.toByte())
                }
            }
        }
    }

    /**
     * PBKDF2-HMAC-SHA256 derivation. Kept for opening pre-1.6.0 envelopes, for
     * importing old backups and for the legacy-format tests; new key material
     * must use [deriveKey] with an Argon2id [KdfSpec].
     */
    fun derivePbkdf2Key(
        password: String,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
    ): SecretKey {
        require(password.isNotEmpty()) { "Password cannot be empty for key derivation" }
        require(salt.size >= SALT_LENGTH_BYTES) { "Salt must be at least $SALT_LENGTH_BYTES bytes" }
        require(iterations >= MIN_ACCEPTED_ITERATIONS) {
            "PBKDF2 iterations must be at least $MIN_ACCEPTED_ITERATIONS"
        }

        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, KEY_LENGTH_BITS)
        try {
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            val keyBytes = factory.generateSecret(spec).encoded
            try {
                return SecretKeySpec(keyBytes, "AES")
            } finally {
                Arrays.fill(keyBytes, 0.toByte())
            }
        } finally {
            spec.clearPassword()
            Arrays.fill(chars, '\u0000')
        }
    }

    /**
     * Raw Argon2id (RFC 9106, version 1.3) derivation to a 256-bit key.
     *
     * Exposed so the RFC known-answer vectors can be exercised with byte-array
     * passwords, secret and associated data exactly as the RFC defines them;
     * production callers go through [deriveKey].
     */
    internal fun deriveArgon2idBytes(
        passwordBytes: ByteArray,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int,
    ): ByteArray {
        require(memoryKiB in ARGON2_MIN_MEMORY_KIB..ARGON2_MAX_MEMORY_KIB) {
            "Argon2 memory cost outside supported range"
        }
        require(iterations in ARGON2_MIN_ITERATIONS..ARGON2_MAX_ITERATIONS) {
            "Argon2 time cost outside supported range"
        }
        require(parallelism in ARGON2_MIN_PARALLELISM..ARGON2_MAX_PARALLELISM) {
            "Argon2 parallelism outside supported range"
        }
        return argon2idRaw(passwordBytes, salt, memoryKiB, iterations, parallelism)
    }

    /**
     * Unbounded Argon2id core. Kept separately so the RFC 9106 known-answer
     * vectors (m=32 KiB, p=4) can be exercised; production callers must always
     * go through [deriveArgon2idBytes]/[deriveKey], which enforce the parameter
     * ranges this app produces and accepts.
     */
    internal fun argon2idRaw(
        passwordBytes: ByteArray,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int,
        secret: ByteArray? = null,
        associatedData: ByteArray? = null,
    ): ByteArray {
        require(salt.size >= 8) { "Argon2 salt must be at least 8 bytes" }

        val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(iterations)
            .withMemoryAsKB(memoryKiB)
            .withParallelism(parallelism)
            .withSalt(salt)
        secret?.let { builder.withSecret(it) }
        associatedData?.let { builder.withAdditional(it) }

        val generator = Argon2BytesGenerator()
        generator.init(builder.build())
        val output = ByteArray(KEY_LENGTH_BYTES)
        generator.generateBytes(passwordBytes, output)
        return output
    }

    /**
     * Generate a cryptographically random 256-bit AES Vault Master Key (VMK)
     */
    fun generateRandomKey(): SecretKey {
        val keyBytes = ByteArray(KEY_LENGTH_BYTES)
        secureRandom.nextBytes(keyBytes)
        try {
            return SecretKeySpec(keyBytes, "AES")
        } finally {
            Arrays.fill(keyBytes, 0.toByte())
        }
    }

    /**
     * Derive a 256-bit AES key from a recovery phrase and salt using the KDF
     * described by [spec] (Argon2id for new kits, PBKDF2 for legacy ones).
     */
    fun deriveKeyFromPhrase(
        phrase: String,
        salt: ByteArray,
        spec: KdfSpec = KdfSpec.CURRENT,
    ): SecretKey {
        val normalizedPhrase = normalizeRecoveryPhrase(phrase)
        require(normalizedPhrase.isNotEmpty()) { "Recovery phrase cannot be empty" }
        return deriveKey(normalizedPhrase, salt, spec)
    }

    fun normalizeRecoveryPhrase(phrase: String): String {
        return phrase.trim().lowercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /**
     * Legacy (v1.0.0–v1.2.0) checksum word: SHA-256 over the first 11 words mapped into the old
     * 136-word list. Kept only to validate recovery kits generated by those releases.
     */
    fun computeRecoveryChecksumWord(firstElevenWords: List<String>): String {
        require(firstElevenWords.size == 11) { "Checksum computation requires exactly 11 words" }
        val canonical = firstElevenWords.joinToString(" ") { it.trim().lowercase() }
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest("VaultZero_Recovery_Checksum_v2:$canonical".toByteArray(Charsets.UTF_8))
        val uint16 = ((digest[0].toInt() and 0xFF) shl 8) or (digest[1].toInt() and 0xFF)
        return RECOVERY_WORDLIST[uint16 % RECOVERY_WORDLIST.size]
    }

    private fun hasLegacyRecoveryChecksum(words: List<String>): Boolean {
        return words.all { it in RECOVERY_WORDLIST_SET } &&
            words.size == 12 &&
            words[11] == computeRecoveryChecksumWord(words.subList(0, 11))
    }

    /**
     * Verifies a recovery phrase checksum against either supported scheme:
     *  - new: BIP-39 English (128-bit entropy + 4-bit checksum), and
     *  - legacy: the v1.x 136-word SHA-256 checksum.
     */
    fun verifyRecoveryPhraseChecksum(phrase: String): Boolean {
        val normalized = normalizeRecoveryPhrase(phrase)
        val words = normalized.split(" ")
        if (words.size != 12) return false
        return Bip39Recovery.isValidMnemonic(words) || hasLegacyRecoveryChecksum(words)
    }

    /**
     * Validates that a recovery phrase consists of 12 supported words and (by default) passes
     * either BIP-39 or legacy checksum verification. Existing v1.x kits remain valid.
     */
    fun validateRecoveryPhrase(phrase: String, enforceChecksum: Boolean = true): Boolean {
        val normalized = normalizeRecoveryPhrase(phrase)
        val words = normalized.split(" ")
        if (words.size != 12) return false
        val isBip39Words = words.all { it in Bip39Recovery.WORD_SET }
        val isLegacyWords = words.all { it in RECOVERY_WORDLIST_SET }
        if (!isBip39Words && !isLegacyWords) return false
        if (!enforceChecksum) return true
        if (isBip39Words && Bip39Recovery.isValidMnemonic(words)) return true
        return hasLegacyRecoveryChecksum(words)
    }

    /**
     * Construct Additional Authenticated Data (AAD) binding an encrypted record envelope
     * to its specific SQLite primary key and VaultType. Prevents cross-row ciphertext swapping.
     */
    fun recordAssociatedData(recordId: String, recordType: String): ByteArray {
        require(recordId.isNotBlank() && recordType.isNotBlank()) { "Record ID and type cannot be blank for AAD" }
        return "VaultZero_Record_v2:$recordId:$recordType".toByteArray(Charsets.UTF_8)
    }

    /**
     * Create a domain-separated HMAC-SHA256 verifier from a derived SecretKey.
     * Ensures verifiers cannot be brute-forced without running the full PBKDF2 work factor.
     */
    fun createKeyVerifier(secretKey: SecretKey, domain: String = "AUTH"): String {
        validateAes256Key(secretKey)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        val digest = mac.doFinal("VaultZero_Verifier_v2:$domain".toByteArray(Charsets.UTF_8))
        return bytesToHex(digest)
    }

    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.isNotEmpty() && clean.length % 2 == 0) { "Hex string must have an even, non-zero length" }
        val result = ByteArray(clean.length / 2)
        for (i in result.indices) {
            val index = i * 2
            val byteVal = clean.substring(index, index + 2).toIntOrNull(16)
                ?: throw IllegalArgumentException("Invalid hex character at position $index")
            result[i] = byteVal.toByte()
        }
        return result
    }

    /**
     * Encrypt plaintext string using AES-256-GCM.
     * Returns Base64-encoded string containing [IV (12 bytes) + CipherText + Tag (16 bytes)].
     */
    fun encrypt(
        plainText: String,
        secretKey: SecretKey,
        associatedData: ByteArray? = null
    ): String {
        validateAes256Key(secretKey)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_CIPHER)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }

        val plainBytes = plainText.toByteArray(Charsets.UTF_8)
        val cipherTextWithTag = try {
            cipher.doFinal(plainBytes)
        } finally {
            Arrays.fill(plainBytes, 0.toByte())
        }

        val buffer = ByteBuffer.allocate(iv.size + cipherTextWithTag.size)
        buffer.put(iv)
        buffer.put(cipherTextWithTag)

        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    /**
     * Decrypt Base64-encoded [IV + CipherText + Tag] string using AES-256-GCM.
     */
    fun decrypt(
        cipherTextBase64: String,
        secretKey: SecretKey,
        associatedData: ByteArray? = null
    ): String {
        validateAes256Key(secretKey)
        if (cipherTextBase64.isBlank()) {
            throw IllegalArgumentException("Ciphertext payload cannot be blank")
        }

        val combined = try {
            Base64.decode(cipherTextBase64.trim(), Base64.NO_WRAP)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed Base64 encrypted payload", e)
        }

        if (combined.size < GCM_IV_LENGTH_BYTES + GCM_TAG_LENGTH_BYTES) {
            throw IllegalArgumentException("Invalid encrypted payload length: insufficient bytes for IV and 128-bit GCM authentication tag")
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES)

        val cipherTextSize = combined.size - GCM_IV_LENGTH_BYTES
        val cipherText = ByteArray(cipherTextSize)
        System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherText, 0, cipherTextSize)

        val cipher = Cipher.getInstance(AES_CIPHER)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }

        val decryptedBytes = cipher.doFinal(cipherText)
        try {
            return String(decryptedBytes, Charsets.UTF_8)
        } finally {
            Arrays.fill(decryptedBytes, 0.toByte())
        }
    }

    /**
     * SHA-256 hash helper
     */
    fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Create a human-verifiable key fingerprint (VZ-XXXX-XXXX-XXXX-XXXX)
     * Derived from SHA-256 of the cryptographic material.
     */
    fun generateFingerprint(input: String): String {
        require(input.isNotBlank()) { "Fingerprint input material cannot be blank" }
        val hash = sha256("VaultZero_Key_Fingerprint_v1:${input.trim().lowercase()}")
        val chunk1 = hash.substring(0, 4).uppercase()
        val chunk2 = hash.substring(4, 8).uppercase()
        val chunk3 = hash.substring(8, 12).uppercase()
        val chunk4 = hash.substring(12, 16).uppercase()
        return "VZ-$chunk1-$chunk2-$chunk3-$chunk4"
    }

    /**
     * Generates a 12-word BIP-39 recovery phrase from 128 bits of CSPRNG entropy (4-bit checksum)
     * with unique words. New vaults use this scheme; v1.x 136-word phrases are still accepted by
     * validation and continue to unlock existing vaults/backups.
     */
    fun generateRecoveryPhrase(): String {
        return Bip39Recovery.generateMnemonic(secureRandom).joinToString(" ")
    }

    // -------------------------------------------------------------
    // TOTP Generator (RFC 6238)
    // -------------------------------------------------------------
    data class TotpResult(
        val code: String,
        val remainingSeconds: Int,
        val progress: Float
    )

    fun generateTotp(
        secretKeyBase32: String,
        timeMillis: Long = System.currentTimeMillis(),
        timeStepSeconds: Long = 30L,
        digits: Int = 6
    ): TotpResult? {
        if (timeMillis < 0L || timeStepSeconds <= 0L || digits !in 6..8) return null
        val cleanSecret = secretKeyBase32.trim().replace(" ", "").replace("-", "").uppercase()
        if (cleanSecret.isEmpty()) return null

        return try {
            val decodedKey = base32Decode(cleanSecret)
            // RFC 4226 recommends >= 128-bit keys; 10 bytes is the practical
            // floor (80 bits). Anything shorter is a malformed/truncated secret
            // and must not render a misleading "2FA code".
            if (decodedKey.size < MIN_TOTP_SECRET_BYTES) return null

            val currentSecond = timeMillis / 1000L
            val timeWindow = currentSecond / timeStepSeconds
            val remainingSeconds = (timeStepSeconds - (currentSecond % timeStepSeconds)).toInt()
            val progress = remainingSeconds.toFloat() / timeStepSeconds.toFloat()

            val msg = ByteBuffer.allocate(8).putLong(timeWindow).array()
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(decodedKey, "RAW"))
            val hash = mac.doFinal(msg)

            val offset = hash[hash.size - 1].toInt() and 0x0f
            val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                    ((hash[offset + 1].toInt() and 0xff) shl 16) or
                    ((hash[offset + 2].toInt() and 0xff) shl 8) or
                    (hash[offset + 3].toInt() and 0xff)

            var mod = 1
            repeat(digits) { mod *= 10 }
            val otp = binary % mod
            val formattedCode = "%0${digits}d".format(otp)
            TotpResult(formattedCode, remainingSeconds, progress)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Strict RFC 4648 Base32 decoder. Rejects invalid non-Base32 characters.
     */
    fun base32Decode(base32: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val out = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0
        var paddingStarted = false

        for (c in base32) {
            if (c == '=') {
                paddingStarted = true
                continue
            }
            if (paddingStarted) {
                throw IllegalArgumentException("Invalid Base32 padding placement")
            }
            val valIndex = alphabet.indexOf(c)
            if (valIndex < 0) {
                throw IllegalArgumentException("Invalid Base32 character: $c")
            }

            buffer = (buffer shl 5) or valIndex
            bitsLeft += 5

            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer shr bitsLeft) and 0xff).toByte())
            }
        }
        return out.toByteArray()
    }

    // -------------------------------------------------------------
    // Password & Passphrase Generator + Entropy Calculator
    // -------------------------------------------------------------
    enum class StrengthGrade(val label: String, val scorePercent: Float) {
        VERY_WEAK("Very Weak", 0.2f),
        WEAK("Weak", 0.4f),
        FAIR("Fair", 0.6f),
        STRONG("Strong", 0.85f),
        FORTIFIED("Excellent", 1.0f)
    }

    data class PasswordStrength(
        val entropyBits: Int,
        val shannonBits: Int,
        val grade: StrengthGrade,
        val suggestions: List<String>
    ) {
        constructor(
            entropyBits: Int,
            grade: StrengthGrade,
            suggestions: List<String>
        ) : this(entropyBits, entropyBits, grade, suggestions)
    }

    private val COMMON_WEAK_ROOTS = listOf(
        "password", "passw0rd", "123456", "12345678", "123456789", "1234567890",
        "qwerty", "qwertyuiop", "asdfgh", "asdfghjkl", "zxcvbn", "admin",
        "welcome", "letmein", "iloveyou", "monkey", "dragon", "master",
        "trustno1", "sunshine", "princess", "football", "baseball", "superman"
    )

    /**
     * Computes true empirical Shannon entropy in bits across the character distribution of [input]:
     * H_total = length * (-sum(p_i * log2(p_i)))
     */
    fun calculateShannonEntropyBits(input: String): Int {
        if (input.isEmpty()) return 0
        val counts = mutableMapOf<Char, Int>()
        for (c in input) {
            counts[c] = (counts[c] ?: 0) + 1
        }
        val len = input.length.toDouble()
        var hPerChar = 0.0
        for (count in counts.values) {
            val p = count / len
            if (p > 0.0) {
                hPerChar -= p * log2(p)
            }
        }
        return (hPerChar * len).roundToInt()
    }

    /**
     * Evaluates password/passphrase strength combining character-pool capacity, empirical Shannon
     * distribution, wordlist passphrase detection, and pattern/dictionary penalties.
     */
    fun calculateStrength(password: String): PasswordStrength {
        if (password.isEmpty()) {
            return PasswordStrength(0, 0, StrengthGrade.VERY_WEAK, listOf("Password cannot be empty"))
        }

        val shannonBits = calculateShannonEntropyBits(password)
        val suggestions = mutableListOf<String>()

        val hasLower = password.any { it.isLowerCase() }
        val hasUpper = password.any { it.isUpperCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() }

        var poolSize = 0
        if (hasLower) poolSize += 26
        if (hasUpper) poolSize += 26
        if (hasDigit) poolSize += 10
        if (hasSymbol) poolSize += 33
        if (poolSize == 0) poolSize = 26

        // Check if this is a multi-word passphrase
        val hyphenOrSpaceTokens = password.trim().split(Regex("[-\\s]+")).filter { it.isNotEmpty() }
        val isWordlistPassphrase = hyphenOrSpaceTokens.size >= 3 &&
                hyphenOrSpaceTokens.all { it.lowercase() in PASSPHRASE_WORDLIST_SET || it.lowercase() in RECOVERY_WORDLIST_SET }

        // Calculate effective unique length and pattern penalties
        val uniqueRatio = password.toSet().size.toDouble() / password.length.toDouble()
        val hasRepeatedRun = Regex("(.)\\1{3,}").containsMatchIn(password)
        val hasRepeatedSubstring = hasRepeatedBlock(password)
        val hasSequentialRun = hasSequentialCharacters(password, 4)
        val lowerPass = password.lowercase()
        val matchesCommonWeak = COMMON_WEAK_ROOTS.any { lowerPass == it || lowerPass.contains(it) }

        val rawPoolBits = (password.length * log2(poolSize.toDouble())).roundToInt()

        var effectiveBits = if (isWordlistPassphrase) {
            // Idealized selection entropy H = k * log2(N) plus separator/length bonus
            val wordCount = hyphenOrSpaceTokens.size
            val distinctWords = hyphenOrSpaceTokens.map { it.lowercase() }.distinct().size
            // Using 16 bits per independent memorable word in a full passphrase context
            (distinctWords * 16) + ((wordCount - distinctWords) * 4)
        } else {
            // Bound raw pool estimate so low-diversity strings (e.g. "aaaaaaaaaa") cannot score high
            val diversityFactor = when {
                uniqueRatio < 0.25 -> 0.20
                uniqueRatio < 0.45 -> 0.50
                uniqueRatio < 0.65 -> 0.80
                else -> 1.0
            }
            (rawPoolBits * diversityFactor).roundToInt()
        }

        if (hasRepeatedRun) {
            effectiveBits = min(effectiveBits, (effectiveBits * 0.45).roundToInt())
            suggestions.add("Avoid repeated character runs (e.g. 'aaaa')")
        }
        if (hasRepeatedSubstring) {
            effectiveBits = min(effectiveBits, (effectiveBits * 0.50).roundToInt())
            suggestions.add("Avoid repeating character patterns")
        }
        if (hasSequentialRun && !isWordlistPassphrase) {
            effectiveBits = min(effectiveBits, (effectiveBits * 0.55).roundToInt())
            suggestions.add("Avoid sequential patterns (e.g. '1234', 'abcd')")
        }
        if (matchesCommonWeak && !isWordlistPassphrase) {
            effectiveBits = min(effectiveBits, 28)
            suggestions.add("Avoid common dictionary words or keyboard walks")
        }

        if (password.length < 12) suggestions.add("Increase length to at least 14 characters")
        if (!isWordlistPassphrase) {
            if (!hasUpper) suggestions.add("Add uppercase characters (A-Z)")
            if (!hasDigit) suggestions.add("Add numeric digits (0-9)")
            if (!hasSymbol) suggestions.add("Add special symbols (!@#$)")
        }

        val grade = when {
            effectiveBits < 36 || password.length < 8 -> StrengthGrade.VERY_WEAK
            effectiveBits < 50 || (!isWordlistPassphrase && password.length < 12) -> StrengthGrade.WEAK
            effectiveBits < 65 -> StrengthGrade.FAIR
            effectiveBits < 85 -> StrengthGrade.STRONG
            else -> StrengthGrade.FORTIFIED
        }

        return PasswordStrength(
            entropyBits = effectiveBits.coerceAtLeast(0),
            shannonBits = shannonBits,
            grade = grade,
            suggestions = suggestions.distinct()
        )
    }

    private fun hasRepeatedBlock(s: String): Boolean {
        if (s.length < 4) return false
        for (blockLen in 1..(s.length / 2)) {
            if (s.length % blockLen == 0) {
                val block = s.substring(0, blockLen)
                if (block.repeat(s.length / blockLen) == s) return true
            }
        }
        return false
    }

    private fun hasSequentialCharacters(s: String, minRun: Int): Boolean {
        if (s.length < minRun) return false
        var ascRun = 1
        var descRun = 1
        for (i in 1 until s.length) {
            val diff = s[i].code - s[i - 1].code
            if (diff == 1) {
                ascRun++
                descRun = 1
                if (ascRun >= minRun) return true
            } else if (diff == -1) {
                descRun++
                ascRun = 1
                if (descRun >= minRun) return true
            } else {
                ascRun = 1
                descRun = 1
            }
        }
        return false
    }

    fun generatePassword(
        length: Int = 18,
        includeUpper: Boolean = true,
        includeLower: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true,
        avoidAmbiguous: Boolean = true
    ): String {
        require(length in 4..128) { "Password length must be between 4 and 128 characters" }
        require(includeUpper || includeLower || includeDigits || includeSymbols) {
            "At least one character set must be enabled"
        }

        val lowerChars = if (avoidAmbiguous) "abcdefghjkmnpqrstuvwxyz" else "abcdefghijklmnopqrstuvwxyz"
        val upperChars = if (avoidAmbiguous) "ABCDEFGHJKLMNPQRSTUVWXYZ" else "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val digitChars = if (avoidAmbiguous) "23456789" else "0123456789"
        val symbolChars = "!@#$%^&*()-_=+[]{}|;:,.<>?"

        val charPool = StringBuilder()
        val guaranteed = mutableListOf<Char>()

        if (includeLower) {
            charPool.append(lowerChars)
            guaranteed.add(lowerChars[secureRandom.nextInt(lowerChars.length)])
        }
        if (includeUpper) {
            charPool.append(upperChars)
            guaranteed.add(upperChars[secureRandom.nextInt(upperChars.length)])
        }
        if (includeDigits) {
            charPool.append(digitChars)
            guaranteed.add(digitChars[secureRandom.nextInt(digitChars.length)])
        }
        if (includeSymbols) {
            charPool.append(symbolChars)
            guaranteed.add(symbolChars[secureRandom.nextInt(symbolChars.length)])
        }

        val poolString = charPool.toString()
        val result = mutableListOf<Char>()
        result.addAll(guaranteed.take(length))

        for (i in result.size until length) {
            result.add(poolString[secureRandom.nextInt(poolString.length)])
        }

        // Fisher-Yates shuffle using CSPRNG
        for (i in result.size - 1 downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }
        return result.joinToString("")
    }

    fun generatePassphrase(
        wordCount: Int = 4,
        separator: String = "-",
        capitalise: Boolean = false,
    ): String {
        require(wordCount in 3..12) { "Passphrase word count must be between 3 and 12" }
        val words = PASSPHRASE_WORDLIST
        val selected = ArrayList<String>(wordCount)
        for (i in 0 until wordCount) {
            val word = words[secureRandom.nextInt(words.size)]
            selected.add(if (capitalise) word.replaceFirstChar { it.uppercaseChar() } else word)
        }
        return selected.joinToString(separator)
    }

    // -------------------------------------------------------------
    // Legacy 136-word recovery list (v1.0.0–v1.2.0) and passphrase words
    // -------------------------------------------------------------
    // Generation for new vaults now uses BIP-39 (128-bit, 2048 words). This list is
    // retained so existing v1.x recovery phrases and their checksums keep working.
    val RECOVERY_WORDLIST = listOf(
        "abstract", "access", "account", "active", "adapt", "agent", "algorithm", "alpha",
        "anchor", "ancient", "apex", "apollon", "arcane", "archive", "armored", "array",
        "aspect", "atomic", "audit", "axiom", "bastion", "beacon", "binary", "biometric",
        "cipher", "circuit", "clarity", "cluster", "cobalt", "cosmos", "crystal", "current",
        "delta", "digital", "dynasty", "eclipse", "element", "emerald", "engine", "entropy",
        "essence", "eternal", "falcon", "filter", "finite", "flame", "flux", "forge",
        "formula", "fractal", "frontier", "galaxy", "gateway", "genesis", "glacier", "gravity",
        "harbor", "haven", "helix", "horizon", "hybrid", "hyper", "ignite", "infinity",
        "ion", "island", "kinetic", "laser", "legacy", "logic", "lunar", "matrix",
        "mercury", "mirage", "monolith", "nebula", "nexus", "nova", "nucleus", "obelisk",
        "omega", "onyx", "optimum", "orbit", "origin", "particle", "phantom", "phoenix",
        "pixel", "plasma", "polaris", "prism", "protocol", "quantum", "quasar", "radar",
        "radiant", "relay", "remote", "resonance", "satellite", "sector", "sentinel", "shadow",
        "shield", "signal", "silver", "solaris", "spectrum", "spiral", "stellar", "strata",
        "stream", "summit", "synapse", "synergy", "syntax", "tesseract", "titan", "torrent",
        "tracer", "transit", "tribunal", "trident", "ultra", "unity", "uranium", "vacuum",
        "valiant", "vanguard", "vector", "velocity", "vortex", "zenith", "zephyr", "zero"
    )

    private val RECOVERY_WORDLIST_SET = RECOVERY_WORDLIST.toSet()

    val PASSPHRASE_WORDLIST = listOf(
        "anchor", "apollo", "arctic", "argon", "armor", "astral", "atlas", "atomic",
        "aurora", "avalon", "axiom", "bastion", "beacon", "binary", "blazer", "breeze",
        "brisk", "bronze", "canyon", "carbon", "cascade", "cavern", "celestial", "chronicle",
        "cipher", "citadel", "clarity", "clover", "cobalt", "comet", "compass", "condor",
        "copper", "coral", "cosmos", "crimson", "crystal", "cyber", "cyclone", "dagger",
        "dawn", "delta", "diamond", "drift", "dynamo", "eclipse", "echo", "emerald",
        "ember", "empire", "enigma", "equinox", "falcon", "fathom", "fjord", "flint",
        "flux", "forged", "fortress", "fossil", "fractal", "frontier", "frost", "fusion",
        "galaxy", "garnet", "genesis", "geyser", "glacier", "glider", "granite", "gravity",
        "griffin", "harbor", "harvest", "haven", "avali", "helium", "helix", "herald",
        "horizon", "hunter", "ignite", "indigo", "infinity", "impulse", "iron", "island",
        "ivory", "jasper", "javelin", "juniper", "kestrel", "kinetic", "krypton", "lagoon",
        "lantern", "laser", "Aegis", "legend", "lightning", "lithium", "lotus", "lunar",
        "lumen", "magnet", "mantle", "marble", "matrix", "meadow", "mercury", "meteor",
        "mirage", "monarch", "monolith", "mystic", "nebula", "nectar", "neon", "neptune",
        "nexus", "nickel", "nimbus", "noble", "nomad", "nova", "nucleus", "oak",
        "oasis", "obelisk", "obsidian", "ocean", "odyssey", "omega", "onyx", "opal",
        "orbit", "orchid", "origin", "orion", "osprey", "outpost", "oxygen", "paladin",
        "panther", "paragon", "paradox", "particle", "pegasus", "pendulum", "phantom", "phoenix",
        "photon", "pioneer", "pinnacle", "pixel", "planet", "plasma", "platinum", "polaris",
        "portal", "prairie", "prelude", "prism", "prodigy", "prophet", "proton", "pulsar",
        "pulse", "pyramid", "quantum", "quartz", "quasar", "quest", "quiver", "radar",
        "radiant", "raptor", "ravine", "reactor", "redwood", "reflex", "relic", "remedy",
        "resolve", "rhythm", "ridge", "ripple", "rocket", "rubicon", "ruby", "runic",
        "saber", "saffron", "sanctuary", "sapphire", "satellite", "scepter", "scout", "sector",
        "seismic", "sentinel", "sequoia", "seraph", "shadow", "shield", "shimmer", "sierra",
        "signal", "silicon", "silver", "sirius", "solace", "solaris", "solstice", "sonar",
        "sonic", "sovereign", "spark", "specter", "spectrum", "sphere", "sphinx", "spiral",
        "spruce", "starlight", "stealth", "stellar", "sterling", "stone", "storm", "strata",
        "stream", "stride", "summit", "sundial", "supernova", "surge", "Switch", "sylvan",
        "synapse", "synergy", "talisman", "tangent", "tempest", "tensor", "terra", "tesseract",
        "thermal", "thistle", "thunder", "timber", "titan", "titanium", "topaz", "torrent",
        "tracker", "zenith", "zephyr", "zircon", "zodiac", "vortex", "voyager", "vulcan"
    ).map { it.lowercase() }.distinct()

    private val PASSPHRASE_WORDLIST_SET = PASSPHRASE_WORDLIST.toSet()
}
