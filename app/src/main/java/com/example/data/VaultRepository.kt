package com.example.data

import android.os.SystemClock
import android.util.Base64
import com.example.crypto.KdfAlgorithm
import com.example.crypto.KdfSpec
import com.example.crypto.VaultCrypto
import com.example.model.ApiSecretPayload
import com.example.model.CardPayload
import com.example.model.CryptoPayload
import com.example.model.IdentityPayload
import com.example.model.ItemPayload
import com.example.model.LoginPayload
import com.example.model.NotePayload
import com.example.model.VaultItem
import com.example.model.VaultType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class VaultRepository(
    private val dao: VaultDao,
    private val prefs: VaultPreferences
) {
    @Volatile
    private var activeSecretKey: SecretKey? = null

    @Volatile
    private var failedUnlockAttempts: Int = 0

    @Volatile
    private var lockoutUntilElapsedRealtime: Long = 0L

    companion object {
        const val MAX_FAILED_ATTEMPTS_BEFORE_COOLDOWN = 5
        private const val BACKUP_FORMAT = "VaultZero_Encrypted_Envelope"
        private const val MAX_BACKUP_SALT_BASE64 = 256
        private const val MAX_BACKUP_KEY_ENVELOPE_CHARS = 4096
        const val COOLDOWN_DURATION_MS = 30_000L
        const val REDACTED_TITLE = "[ENCRYPTED]"
        const val REDACTED_CATEGORY = "Encrypted"
        const val REDACTED_TAGS_JSON = "[]"
    }

    val isInitialized: Boolean
        get() = prefs.isInitialized

    val isUnlocked: Boolean
        get() = activeSecretKey != null

    val keyFingerprint: String
        get() = prefs.keyFingerprint

    /**
     * Decrypts and returns the 12-word recovery phrase ONLY when the vault is currently unlocked.
     * Never exposes or stores the recovery phrase in plaintext on disk.
     */
    fun getDecryptedRecoveryPhrase(): String {
        val key = activeSecretKey ?: return ""
        val enc = prefs.encryptedRecoveryPhrase
        if (enc.isNotEmpty()) {
            return try {
                VaultCrypto.decrypt(enc, key)
            } catch (e: Exception) {
                ""
            }
        }
        val legacy = prefs.legacyPlaintextRecoveryPhrase
        if (legacy.isNotEmpty()) {
            return try {
                prefs.encryptedRecoveryPhrase = VaultCrypto.encrypt(legacy, key)
                prefs.clearLegacyPlaintextRecoveryPhrase()
                legacy
            } catch (e: Exception) {
                legacy
            }
        }
        return ""
    }

    fun getRemainingLockoutSeconds(nowElapsedRealtime: Long = SystemClock.elapsedRealtime()): Int {
        val remainingMs = lockoutUntilElapsedRealtime - nowElapsedRealtime
        return if (remainingMs > 0L) ((remainingMs + 999L) / 1000L).toInt() else 0
    }

    fun resetRateLimiter() {
        failedUnlockAttempts = 0
        lockoutUntilElapsedRealtime = 0L
    }

    val autoLockSeconds: Int
        get() = prefs.autoLockSeconds

    fun setAutoLockSeconds(seconds: Int) {
        prefs.autoLockSeconds = seconds
    }

    val backgroundLockSeconds: Int
        get() = prefs.backgroundLockSeconds

    fun setBackgroundLockSeconds(seconds: Int) {
        prefs.backgroundLockSeconds = seconds
    }

    val clipboardTimeoutSeconds: Int
        get() = prefs.clipboardTimeoutSeconds

    fun setClipboardTimeoutSeconds(seconds: Int) {
        prefs.clipboardTimeoutSeconds = seconds
    }

    val sortOption: String
        get() = prefs.sortOption

    fun setSortOption(option: String) {
        prefs.sortOption = option
    }

    val groupByType: Boolean
        get() = prefs.groupByType

    fun setGroupByType(enabled: Boolean) {
        prefs.groupByType = enabled
    }

    fun themeMode(): String = prefs.themeMode

    fun setThemeMode(value: String) {
        prefs.themeMode = value
    }

    fun isDarkTheme(): Boolean = prefs.isDarkTheme

    fun setDarkTheme(enabled: Boolean) {
        prefs.isDarkTheme = enabled
    }

    fun isBiometricEnabled(): Boolean = prefs.isBiometricEnabled

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.isBiometricEnabled = enabled
    }

    /**
     * Initializes the Vault with a Master Password and creates a recovery kit using envelope encryption.
     * Returns Pair(recoveryPhrase, vmkHex)
     */
    suspend fun setupMasterPassword(
        password: String
    ): Pair<String, String> = withContext(Dispatchers.Default) {
        val cleanPassword = password.trim()
        if (cleanPassword.length < 8) {
            throw VaultException(VaultError.PASSWORD_TOO_SHORT, "Master Password must be at least 8 characters")
        }

        val kdf = KdfSpec.CURRENT

        // 1. Generate random 256-bit Vault Master Key (VMK)
        val vmk = VaultCrypto.generateRandomKey()
        val vmkHex = VaultCrypto.bytesToHex(vmk.encoded)

        // 2. Derive Password Key via PBKDF2-HMAC-SHA256 and encrypt VMK
        val passwordSalt = VaultCrypto.generateSalt()
        val passwordSaltB64 = Base64.encodeToString(passwordSalt, Base64.NO_WRAP)
        val passwordKey = VaultCrypto.deriveKey(cleanPassword, passwordSalt, kdf)
        val encVmkPassword = VaultCrypto.encrypt(vmkHex, passwordKey)
        val passwordVerifier = VaultCrypto.createKeyVerifier(passwordKey, "PASSWORD")

        // 3. Generate Checksummed 12-Word Recovery Phrase, derive Recovery Key, and encrypt VMK
        val recoveryPhrase = VaultCrypto.generateRecoveryPhrase()
        val recoverySalt = VaultCrypto.generateSalt()
        val recoverySaltB64 = Base64.encodeToString(recoverySalt, Base64.NO_WRAP)
        val recoveryKey = VaultCrypto.deriveKeyFromPhrase(recoveryPhrase, recoverySalt, kdf)
        val encVmkRecovery = VaultCrypto.encrypt(vmkHex, recoveryKey)
        val recoveryVerifier = VaultCrypto.createKeyVerifier(recoveryKey, "RECOVERY")

        // 4. Encrypt recovery phrase using VMK so plaintext phrase is never stored on disk
        val encRecoveryPhrase = VaultCrypto.encrypt(recoveryPhrase, vmk)

        // 5. Generate key fingerprint from VMK
        val fingerprint = VaultCrypto.generateFingerprint(vmkHex)

        // 6. Atomic persistence of envelope credentials
        prefs.saveEnvelopeCredentials(
            passwordHash = passwordVerifier,
            saltBase64 = passwordSaltB64,
            recoverySaltBase64 = recoverySaltB64,
            encryptedVmkByPassword = encVmkPassword,
            encryptedVmkByRecovery = encVmkRecovery,
            encryptedRecoveryPhrase = encRecoveryPhrase,
            recoveryPhraseHash = recoveryVerifier,
            keyFingerprint = fingerprint,
            passwordKdf = kdf,
            recoveryKdf = kdf,
        )

        activeSecretKey = vmk
        resetRateLimiter()

        Pair(recoveryPhrase, vmkHex)
    }

    /**
     * Restores a complete vault during first-run setup, for phone-to-phone migration.
     *
     * The backup's VMK is unwrapped with the user's existing 12-word recovery phrase, every
     * record is authenticated before any write, and the records are installed with their
     * original ciphertext. The recovery phrase is preserved (no new kit is generated); the
     * original master password is preserved for standard backups unless [newMasterPassword]
     * is supplied. A phrase-only backup has no password envelope, so a new local master
     * password is required in that case.
     *
     * Existing vaults are never overwritten: the caller must be in the uninitialized setup
     * state, and migration between already-created vaults uses the normal import path.
     */
    suspend fun restoreBackupAtSetup(
        jsonString: String,
        recoveryPhrase: String,
        newMasterPassword: String? = null,
    ): SetupRestoreReport = withContext(Dispatchers.Default) {
        if (prefs.isInitialized) {
            throw VaultException(
                VaultError.VAULT_ALREADY_EXISTS,
                "A vault already exists on this device. Use Settings → Restore / Import Encrypted Backup instead.",
            )
        }

        val cleanPhrase = VaultCrypto.normalizeRecoveryPhrase(recoveryPhrase)
        if (!VaultCrypto.validateRecoveryPhrase(cleanPhrase, enforceChecksum = false)) {
            throw VaultException(
                VaultError.RECOVERY_PHRASE_INVALID,
                "The recovery phrase must contain 12 valid words.",
            )
        }
        if (!VaultCrypto.verifyRecoveryPhraseChecksum(cleanPhrase)) {
            throw VaultException(
                VaultError.RECOVERY_PHRASE_CHECKSUM_MISMATCH,
                "Recovery phrase checksum does not match — check for a mistyped or reordered word.",
            )
        }

        val backup = BackupJson.parse(jsonString)
        if (backup.optString("format", "") != BACKUP_FORMAT) {
            throw VaultException(VaultError.NOT_A_VAULT_BACKUP, "not a VaultZero encrypted backup envelope")
        }
        val itemsArray = backup.optJSONArray("items")
            ?: throw VaultException(VaultError.BACKUP_NO_ITEMS, "backup has no items array")
        if (itemsArray.length() == 0) {
            throw VaultException(VaultError.BACKUP_EMPTY, "backup contains no entries")
        }

        // The KDF specs travel inside an untrusted file. decode() rejects malformed,
        // out-of-range and DoS-sized parameters. A spec above the hard minimum but below
        // the current default (e.g. argon2id:8192:1:1) is still accepted for restoring old
        // backups: the envelope keeps that cost until the first successful unlock migrates
        // both envelopes to KdfSpec.CURRENT.
        val recoveryKdf = parseBackupKdf(backup, "kdfRecoverySpec", "recovery-phrase")
        val sourcePasswordKdf = parseBackupKdf(backup, "kdfPasswordSpec", "password")

        val recoverySaltB64 = backup.optString("recoverySalt", "")
        val encryptedVmkByRecovery = backup.optString("encryptedVmkByRecovery", "")
        if (recoverySaltB64.isEmpty() || encryptedVmkByRecovery.isEmpty()) {
            throw VaultException(VaultError.BACKUP_NO_RECOVERY_KEY, "this backup has no recovery-phrase key material")
        }
        validateBackupKeyEnvelope(encryptedVmkByRecovery)
        val recoverySalt = decodeBackupSalt(recoverySaltB64, "recovery")

        val vmk: SecretKey
        try {
            val recoveryKey = VaultCrypto.deriveKeyFromPhrase(cleanPhrase, recoverySalt, recoveryKdf)
            val vmkHex = VaultCrypto.decrypt(encryptedVmkByRecovery, recoveryKey)
            val vmkBytes = VaultCrypto.hexToBytes(vmkHex)
            if (vmkBytes.size != VaultCrypto.KEY_LENGTH_BYTES) {
                throw VaultException(
                    VaultError.INVALID_VAULT_KEY,
                    "recovered vault key has an invalid length",
                )
            }
            vmk = SecretKeySpec(vmkBytes, "AES")
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw VaultException(VaultError.RECOVERY_PHRASE_MISMATCH, "the recovery phrase does not match this backup")
        }
        val vmkHex = VaultCrypto.bytesToHex(vmk.encoded)

        // Authenticate every record before writing anything. The original ciphertext is
        // preserved; no re-encryption or metadata downgrade occurs during migration.
        val seenIds = HashSet<String>()
        val entities = ArrayList<VaultItemEntity>(itemsArray.length())
        for (i in 0 until itemsArray.length()) {
            try {
                val itemObj = itemsArray.optJSONObject(i)
                    ?: throw VaultException(
                        VaultError.BACKUP_ENTRY_NOT_OBJECT,
                        "entry ${i + 1} is not an object",
                        args = listOf(i + 1),
                    )
                val id = itemObj.optString("id", "")
                if (id.isBlank()) {
                    throw VaultException(
                        VaultError.BACKUP_ENTRY_NO_ID,
                        "entry ${i + 1} has no record id",
                        args = listOf(i + 1),
                    )
                }
                if (!seenIds.add(id)) {
                    throw VaultException(
                        VaultError.BACKUP_ENTRY_DUPLICATE_ID,
                        "entry ${i + 1} has a duplicate record id",
                        args = listOf(i + 1),
                    )
                }
                val typeStr = itemObj.optString("type", "")
                val validType = runCatching { VaultType.valueOf(typeStr) }.getOrNull()
                    ?: throw VaultException(
                        VaultError.BACKUP_ENTRY_UNKNOWN_TYPE,
                        "entry ${i + 1} has an unknown item type",
                        args = listOf(i + 1),
                    )
                val encPayload = itemObj.optString("encryptedPayload", "")
                if (encPayload.isEmpty()) {
                    throw VaultException(
                        VaultError.BACKUP_ENTRY_NO_PAYLOAD,
                        "entry ${i + 1} has no encrypted payload",
                        args = listOf(i + 1),
                    )
                }

                val entity = VaultItemEntity(
                    id = id,
                    title = REDACTED_TITLE,
                    type = validType.name,
                    category = REDACTED_CATEGORY,
                    encryptedPayload = encPayload,
                    isFavorite = itemObj.optBoolean("isFavorite", false),
                    createdAt = itemObj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = itemObj.optLong("updatedAt", System.currentTimeMillis()),
                    lastUsedAt = itemObj.optLong("lastUsedAt", 0L),
                    tagsJson = REDACTED_TAGS_JSON,
                )
                decryptRecordEntity(entity, vmk)
                entities.add(entity)
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: Exception) {
                throw VaultException(
                    VaultError.BACKUP_ENTRY_FAILED_AUTH,
                    "entry ${i + 1} failed authentication (wrong recovery phrase or corrupted file)",
                    args = listOf(i + 1),
                )
            }
        }

        val protection = BackupProtection.fromStorage(backup.optString("protection", null))
        val sourceSaltB64 = backup.optString("salt", "")
        val sourceEncVmk = backup.optString("encryptedVmkByPassword", "")
        val sourceVerifier = backup.optString("verifier", "")
        val hasOriginalPassword = protection == BackupProtection.STANDARD &&
            sourceSaltB64.isNotEmpty() && sourceEncVmk.isNotEmpty() && sourceVerifier.isNotEmpty()

        val cleanNewPassword = newMasterPassword?.trim().orEmpty()
        // A new password is always wrapped with the current KDF. Reusing the backup's own
        // password envelope keeps that envelope's declared KDF, which may still be PBKDF2;
        // the first successful unlock then migrates both envelopes to Argon2id.
        val passwordKdf = if (cleanNewPassword.isNotEmpty()) KdfSpec.CURRENT else sourcePasswordKdf
        val passwordEnvelope: Triple<String, String, String> = if (cleanNewPassword.isNotEmpty()) {
            if (cleanNewPassword.length < 8) {
                throw VaultException(
                    VaultError.NEW_PASSWORD_TOO_SHORT,
                    "New master password must be at least 8 characters",
                )
            }
            val salt = VaultCrypto.generateSalt()
            val key = VaultCrypto.deriveKey(cleanNewPassword, salt, passwordKdf)
            Triple(
                Base64.encodeToString(salt, Base64.NO_WRAP),
                VaultCrypto.encrypt(vmkHex, key),
                VaultCrypto.createKeyVerifier(key, "PASSWORD"),
            )
        } else {
            if (!hasOriginalPassword) {
                throw VaultException(
                    VaultError.BACKUP_PHRASE_ONLY_NEEDS_NEW_PASSWORD,
                    "This is a phrase-only backup with no master password. " +
                        "Enter a new master password for this device.",
                )
            }
            validateBackupKeyEnvelope(sourceEncVmk)
            decodeBackupSalt(sourceSaltB64, "password")
            if (sourceVerifier.isEmpty()) {
                throw VaultException(VaultError.BACKUP_VERIFIER_MISSING, "backup password verifier is missing")
            }
            Triple(sourceSaltB64, sourceEncVmk, sourceVerifier)
        }

        // Keep the existing recovery kit: reuse its salt/envelope and only refresh the
        // VMK-encrypted copy of the phrase plus the verifier derived from the supplied phrase.
        val recoveryVerifier = VaultCrypto.createKeyVerifier(
            VaultCrypto.deriveKeyFromPhrase(cleanPhrase, recoverySalt, recoveryKdf),
            "RECOVERY",
        )
        val encryptedRecoveryPhrase = VaultCrypto.encrypt(cleanPhrase, vmk)
        val fingerprint = backup.optString("fingerprint", "")
            .takeIf { it.length in 4..64 }
            ?: VaultCrypto.generateFingerprint(vmkHex)

        // Records first, then the atomic credential commit: if preferences fail, the app
        // remains uninitialized and the restore can simply be retried (the DB is replaced).
        dao.replaceAllForRestore(entities)
        prefs.saveEnvelopeCredentials(
            passwordHash = passwordEnvelope.third,
            saltBase64 = passwordEnvelope.first,
            recoverySaltBase64 = recoverySaltB64,
            encryptedVmkByPassword = passwordEnvelope.second,
            encryptedVmkByRecovery = encryptedVmkByRecovery,
            encryptedRecoveryPhrase = encryptedRecoveryPhrase,
            recoveryPhraseHash = recoveryVerifier,
            keyFingerprint = fingerprint,
            passwordKdf = passwordKdf,
            recoveryKdf = recoveryKdf,
        )

        activeSecretKey = vmk
        resetRateLimiter()
        SetupRestoreReport(
            itemCount = entities.size,
            keptOriginalPassword = cleanNewPassword.isEmpty(),
            phraseOnlyBackup = !hasOriginalPassword,
        )
    }

    fun getActiveVmkHex(): String? {
        val key = activeSecretKey ?: return null
        return VaultCrypto.bytesToHex(key.encoded)
    }

    /**
     * Unlocks the vault directly with hardware-decrypted VMK (from Biometric Keystore)
     */
    suspend fun unlockWithVmkHex(vmkHex: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val keyBytes = VaultCrypto.hexToBytes(vmkHex)
            if (keyBytes.size == VaultCrypto.KEY_LENGTH_BYTES) {
                val vmk = SecretKeySpec(keyBytes, "AES")
                activeSecretKey = vmk
                resetRateLimiter()
                purgeLegacyDemoItemsIfPresent(vmk)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Unlocks the vault with Master Password by decrypting the Vault Master Key (VMK).
     * Enforces rate-limiting cooldown after repeated failed guesses.
     */
    suspend fun unlock(
        password: String,
        nowElapsedRealtime: Long = SystemClock.elapsedRealtime()
    ): Boolean = withContext(Dispatchers.Default) {
        if (!prefs.isInitialized) return@withContext false
        if (getRemainingLockoutSeconds(nowElapsedRealtime) > 0) {
            return@withContext false
        }

        val saltB64 = prefs.saltBase64
        val expectedVerifier = prefs.masterPasswordHash
        if (saltB64.isEmpty() || expectedVerifier.isEmpty()) return@withContext false

        val salt = try {
            Base64.decode(saltB64, Base64.NO_WRAP)
        } catch (e: Exception) {
            return@withContext false
        }
        if (salt.size < VaultCrypto.SALT_LENGTH_BYTES) return@withContext false

        val passwordKdf = prefs.passwordKdf

        // Check both exact input and trimmed input for robustness
        val candidates = listOf(password, password.trim()).distinct()
        for (candidate in candidates) {
            if (candidate.isEmpty()) continue
            val passwordKey = VaultCrypto.deriveKey(candidate, salt, passwordKdf)
            val v2Verifier = VaultCrypto.createKeyVerifier(passwordKey, "PASSWORD")
            val legacyV1Verifier = VaultCrypto.sha256(candidate + saltB64)

            if (v2Verifier == expectedVerifier || legacyV1Verifier == expectedVerifier) {
                if (prefs.encryptedVmkByPassword.isNotEmpty()) {
                    try {
                        val vmkHex = VaultCrypto.decrypt(prefs.encryptedVmkByPassword, passwordKey)
                        val vmkBytes = VaultCrypto.hexToBytes(vmkHex)
                        if (vmkBytes.size == VaultCrypto.KEY_LENGTH_BYTES) {
                            val vmk = SecretKeySpec(vmkBytes, "AES")
                            activeSecretKey = vmk
                            resetRateLimiter()

                            // Transparently upgrade legacy v1 verifier or plaintext recovery phrase if present
                            if (legacyV1Verifier == expectedVerifier) {
                                prefs.masterPasswordHash = v2Verifier
                            }
                            val legacyPhrase = prefs.legacyPlaintextRecoveryPhrase
                            if (legacyPhrase.isNotEmpty()) {
                                prefs.encryptedRecoveryPhrase = VaultCrypto.encrypt(legacyPhrase, vmk)
                                prefs.clearLegacyPlaintextRecoveryPhrase()
                            }
                            // Transparently strengthen the KDF envelope when this
                            // vault predates the current PBKDF2 work factor.
                            migrateKdfIfNeeded(candidate.trim(), vmkHex, passwordKdf)
                            purgeLegacyDemoItemsIfPresent(vmk)
                            return@withContext true
                        }
                    } catch (e: Exception) {
                        // Authenticated decryption failed, continue to next candidate
                    }
                } else {
                    activeSecretKey = passwordKey
                    resetRateLimiter()
                    purgeLegacyDemoItemsIfPresent(passwordKey)
                    return@withContext true
                }
            }
        }

        failedUnlockAttempts++
        if (failedUnlockAttempts >= MAX_FAILED_ATTEMPTS_BEFORE_COOLDOWN) {
            lockoutUntilElapsedRealtime = nowElapsedRealtime + COOLDOWN_DURATION_MS
        }
        false
    }

    /**
     * Recovers vault using the 12-word recovery phrase, restoring the exact Vault Master Key (VMK).
     */
    suspend fun recoverWithPhrase(phrase: String, newPassword: String): Boolean = withContext(Dispatchers.Default) {
        if (!prefs.isInitialized) return@withContext false
        val cleanPhrase = VaultCrypto.normalizeRecoveryPhrase(phrase)
        val cleanNewPassword = newPassword.trim()
        if (cleanNewPassword.length < 8) return@withContext false
        // Must be 12 valid words from the recovery wordlist
        if (!VaultCrypto.validateRecoveryPhrase(cleanPhrase, enforceChecksum = false)) return@withContext false
        if (prefs.encryptedVmkByRecovery.isEmpty() || prefs.recoverySaltBase64.isEmpty()) return@withContext false

        try {
            val recoveryKdf = prefs.recoveryKdf
            val recoverySalt = Base64.decode(prefs.recoverySaltBase64, Base64.NO_WRAP)
            if (recoverySalt.size < VaultCrypto.SALT_LENGTH_BYTES) return@withContext false

            val recoveryKey = VaultCrypto.deriveKeyFromPhrase(cleanPhrase, recoverySalt, recoveryKdf)
            val v2RecoveryVerifier = VaultCrypto.createKeyVerifier(recoveryKey, "RECOVERY")
            val legacyV1RecoveryVerifier = VaultCrypto.sha256(cleanPhrase)

            if (prefs.recoveryPhraseHash.isNotEmpty() &&
                v2RecoveryVerifier != prefs.recoveryPhraseHash &&
                legacyV1RecoveryVerifier != prefs.recoveryPhraseHash
            ) {
                return@withContext false
            }

            // 1. Recover original VMK using recovery key (authenticated AES-256-GCM decryption)
            val vmkHex = VaultCrypto.decrypt(prefs.encryptedVmkByRecovery, recoveryKey)
            val vmkBytes = VaultCrypto.hexToBytes(vmkHex)
            if (vmkBytes.size != VaultCrypto.KEY_LENGTH_BYTES) return@withContext false
            val vmk = SecretKeySpec(vmkBytes, "AES")

            // 2. Re-wrap VMK with new password
            val newSalt = VaultCrypto.generateSalt()
            val newSaltB64 = Base64.encodeToString(newSalt, Base64.NO_WRAP)
            val newPasswordKey = VaultCrypto.deriveKey(cleanNewPassword, newSalt, KdfSpec.CURRENT)
            val newEncVmkPassword = VaultCrypto.encrypt(vmkHex, newPasswordKey)
            val newPasswordVerifier = VaultCrypto.createKeyVerifier(newPasswordKey, "PASSWORD")
            val encRecoveryPhrase = VaultCrypto.encrypt(cleanPhrase, vmk)

            // 3. Atomically update preferences with new password envelope
            prefs.saveEnvelopeCredentials(
                passwordHash = newPasswordVerifier,
                saltBase64 = newSaltB64,
                recoverySaltBase64 = prefs.recoverySaltBase64,
                encryptedVmkByPassword = newEncVmkPassword,
                encryptedVmkByRecovery = prefs.encryptedVmkByRecovery,
                encryptedRecoveryPhrase = encRecoveryPhrase,
                recoveryPhraseHash = v2RecoveryVerifier,
                keyFingerprint = prefs.keyFingerprint,
                passwordKdf = KdfSpec.CURRENT,
                recoveryKdf = recoveryKdf,
            )

            activeSecretKey = vmk
            resetRateLimiter()
            purgeLegacyDemoItemsIfPresent(vmk)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Locks vault by purging the active secret key reference from RAM
     */
    /**
     * Rotates the recovery kit while the vault is unlocked: generates a new 128-bit BIP-39
     * phrase, derives a fresh recovery KEK/salt, wraps the SAME Vault Master Key, and replaces
     * the recovery envelope in one atomic preference write. Records, the master password and the
     * key fingerprint are unchanged; old backups still carry their own old recovery envelope.
     */
    suspend fun regenerateRecoveryKit(): String = withContext(Dispatchers.Default) {
        val key = activeSecretKey ?: throw VaultStateException(VaultError.VAULT_LOCKED, "Vault is locked")
        val vmkHex = VaultCrypto.bytesToHex(key.encoded)

        val phrase = VaultCrypto.generateRecoveryPhrase()
        val recoverySalt = VaultCrypto.generateSalt()
        val recoveryKey = VaultCrypto.deriveKeyFromPhrase(phrase, recoverySalt, KdfSpec.CURRENT)
        val encryptedVmk = VaultCrypto.encrypt(vmkHex, recoveryKey)
        val encryptedPhrase = VaultCrypto.encrypt(phrase, key)
        val verifier = VaultCrypto.createKeyVerifier(recoveryKey, "RECOVERY")

        prefs.rotateRecoveryEnvelope(
            recoverySaltBase64 = Base64.encodeToString(recoverySalt, Base64.NO_WRAP),
            encryptedVmkByRecovery = encryptedVmk,
            encryptedRecoveryPhrase = encryptedPhrase,
            recoveryPhraseHash = verifier,
            kdf = KdfSpec.CURRENT,
        )
        phrase
    }

    fun lock() {
        activeSecretKey = null
    }

    /**
     * Encrypts full record metadata (title, category, tags) + secret payload into an AAD-bound
     * AES-256-GCM envelope so SQLite columns store only redacted placeholders.
     */
    private fun encryptRecordEnvelope(item: VaultItem, recordId: String, key: SecretKey): String {
        val tagsArray = JSONArray()
        item.tags.forEach { tagsArray.put(it) }

        val envelopeJson = JSONObject().apply {
            put("_vz_meta_v2", true)
            put("id", recordId)
            put("type", item.type.name)
            put("title", item.title)
            put("category", item.category)
            put("tags", tagsArray)
            put("payload", JSONObject(item.payloadToJsonString()))
        }
        val aad = VaultCrypto.recordAssociatedData(recordId, item.type.name)
        return VaultCrypto.encrypt(envelopeJson.toString(), key, aad)
    }

    /**
     * Decrypts a VaultItemEntity using v2 AAD-bound metadata envelope first, with seamless
     * fallback to v1 legacy format.
     */
    private fun decryptRecordEntity(entity: VaultItemEntity, key: SecretKey): VaultItem {
        val type = VaultType.valueOf(entity.type)
        val aad = VaultCrypto.recordAssociatedData(entity.id, entity.type)

        // Try v2 AAD-bound full-metadata envelope first
        try {
            val decryptedEnvelopeStr = VaultCrypto.decrypt(entity.encryptedPayload, key, aad)
            val json = JSONObject(decryptedEnvelopeStr)
            if (json.optBoolean("_vz_meta_v2", false)) {
                val envId = json.optString("id", entity.id)
                val envType = json.optString("type", entity.type)
                if (envId != entity.id || envType != entity.type) {
                    throw VaultException(VaultError.RECORD_AAD_MISMATCH, "Record AAD envelope mismatch")
                }
                val decryptedTitle = json.optString("title", entity.title)
                val decryptedCategory = json.optString("category", entity.category)
                val decryptedTags = mutableListOf<String>()
                val tagsArr = json.optJSONArray("tags")
                if (tagsArr != null) {
                    for (i in 0 until tagsArr.length()) {
                        decryptedTags.add(tagsArr.getString(i))
                    }
                }
                val payloadObj = json.optJSONObject("payload") ?: JSONObject()
                val payload = VaultItem.parsePayload(type, payloadObj.toString())

                return VaultItem(
                    id = entity.id,
                    title = decryptedTitle,
                    type = type,
                    category = decryptedCategory,
                    isFavorite = entity.isFavorite,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    lastUsedAt = entity.lastUsedAt,
                    tags = decryptedTags,
                    payload = payload
                )
            }
        } catch (e: Exception) {
            // Fall through to legacy v1 decryption (no AAD)
        }

        val decryptedPayloadJson = VaultCrypto.decrypt(entity.encryptedPayload, key)
        val payload = VaultItem.parsePayload(type, decryptedPayloadJson)
        val tags = parseTags(entity.tagsJson)

        return VaultItem(
            id = entity.id,
            title = entity.title,
            type = type,
            category = entity.category,
            isFavorite = entity.isFavorite,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            tags = tags,
            payload = payload
        )
    }

    /**
     * Flow of all decrypted vault items
     */
    fun getVaultItemsFlow(): Flow<List<VaultItem>> {
        return dao.getAllFlow().map { entities ->
            val key = activeSecretKey
            if (key == null) {
                emptyList()
            } else {
                entities.mapNotNull { entity ->
                    try {
                        decryptRecordEntity(entity, key)
                    } catch (e: Exception) {
                        null
                    }
                }
                    // Titles are stored redacted, so ordering has to happen after
                    // decryption: favorites first, then alphabetical (stable and
                    // predictable, independent of insertion order).
                    .sortedWith(
                        compareByDescending<VaultItem> { it.isFavorite }
                            .thenBy { it.title.lowercase() }
                    )
            }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun saveItem(item: VaultItem) = withContext(Dispatchers.Default) {
        val key = activeSecretKey ?: throw VaultStateException(VaultError.VAULT_LOCKED, "Vault is locked")
        val recordId = item.id.ifEmpty { UUID.randomUUID().toString() }
        val encryptedEnvelope = encryptRecordEnvelope(item, recordId, key)
        val now = System.currentTimeMillis()

        val entity = VaultItemEntity(
            id = recordId,
            title = REDACTED_TITLE,
            type = item.type.name,
            category = REDACTED_CATEGORY,
            encryptedPayload = encryptedEnvelope,
            isFavorite = item.isFavorite,
            createdAt = if (item.createdAt == 0L) now else item.createdAt,
            updatedAt = if (item.updatedAt > 0L) item.updatedAt else now,
            lastUsedAt = item.lastUsedAt,
            tagsJson = REDACTED_TAGS_JSON
        )
        dao.insertOrUpdate(entity)
    }

    suspend fun toggleFavorite(id: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        if (!isUnlocked) throw VaultStateException(VaultError.VAULT_LOCKED, "Vault is locked")
        dao.updateFavorite(id, isFavorite)
    }

    suspend fun setFavorite(ids: Collection<String>, favorite: Boolean) = withContext(Dispatchers.IO) {
        if (!isUnlocked) throw VaultStateException(VaultError.VAULT_LOCKED, "Vault is locked")
        if (ids.isNotEmpty()) dao.updateFavoriteAll(ids.toList(), favorite)
    }

    /** Record when a secret was last viewed or copied (drives the "Recently used" sort). */
    suspend fun markUsed(id: String) = withContext(Dispatchers.IO) {
        if (!isUnlocked) return@withContext
        runCatching { dao.updateLastUsed(id, System.currentTimeMillis()) }
    }

    suspend fun deleteItem(id: String) = withContext(Dispatchers.IO) {
        if (!isUnlocked) throw VaultStateException(VaultError.VAULT_LOCKED, "Vault is locked")
        dao.deleteById(id)
    }

    /** Delete several records in one transaction (bulk selection action). */
    suspend fun deleteItems(ids: Collection<String>) = withContext(Dispatchers.IO) {
        if (!isUnlocked) throw VaultStateException(VaultError.VAULT_LOCKED, "Vault is locked")
        if (ids.isEmpty()) return@withContext
        dao.deleteByIds(ids.toList())
    }

    suspend fun clearVault() = withContext(Dispatchers.IO) {
        dao.clearAll()
        prefs.resetAll()
        activeSecretKey = null
        resetRateLimiter()
    }

    /**
     * Export all items as an authenticated encrypted JSON backup envelope.
     * Outer SQLite/JSON metadata fields remain redacted ("[ENCRYPTED]") while full record metadata
     * and secret payloads are inside the AAD-bound AES-256-GCM ciphertext.
     */
    suspend fun exportEncryptedBackup(
        protection: BackupProtection = BackupProtection.STANDARD,
    ): String = withContext(Dispatchers.Default) {
        val key = activeSecretKey ?: throw VaultStateException(VaultError.VAULT_LOCKED, "Vault is locked")
        VaultCrypto.validateAes256Key(key)
        val entities = dao.getAllList()
        val backupJson = JSONObject()
        backupJson.put("version", "3.3")
        backupJson.put("format", BACKUP_FORMAT)
        backupJson.put("protection", protection.storage)
        backupJson.put("fingerprint", prefs.keyFingerprint)
        backupJson.put("exportedAt", System.currentTimeMillis())
        backupJson.put("kdfPasswordSpec", prefs.passwordKdf.encode())
        backupJson.put("kdfRecoverySpec", prefs.recoveryKdf.encode())
        // Legacy field for 3.2-era readers: only meaningful while both envelopes are PBKDF2.
        if (prefs.passwordKdf.algorithm == KdfAlgorithm.PBKDF2_SHA256 &&
            prefs.recoveryKdf.algorithm == KdfAlgorithm.PBKDF2_SHA256
        ) {
            backupJson.put("kdfIterations", prefs.passwordKdf.iterations)
        }
        backupJson.put("recoverySalt", prefs.recoverySaltBase64)
        backupJson.put("encryptedVmkByRecovery", prefs.encryptedVmkByRecovery)
        if (protection == BackupProtection.STANDARD) {
            backupJson.put("salt", prefs.saltBase64)
            backupJson.put("encryptedVmkByPassword", prefs.encryptedVmkByPassword)
            backupJson.put("verifier", prefs.masterPasswordHash)
        }

        val itemsArray = JSONArray()
        for (entity in entities) {
            val itemObj = JSONObject().apply {
                put("id", entity.id)
                put("title", REDACTED_TITLE)
                put("type", entity.type)
                put("category", REDACTED_CATEGORY)
                put("encryptedPayload", entity.encryptedPayload)
                put("isFavorite", entity.isFavorite)
                put("createdAt", entity.createdAt)
                put("updatedAt", entity.updatedAt)
                put("lastUsedAt", entity.lastUsedAt)
                put("tagsJson", REDACTED_TAGS_JSON)
            }
            itemsArray.put(itemObj)
        }
        backupJson.put("items", itemsArray)
        backupJson.toString(2)
    }

    /**
     * Import encrypted backup into the currently unlocked vault.
     * If the backup was encrypted under a different vault key and [backupPassword] is provided,
     * unwraps the backup's VMK using [backupPassword], decrypts each item (supporting both v2
     * AAD-bound metadata envelopes and v1 legacy envelopes), and re-encrypts it under the active
     * vault's VMK with full metadata redaction.
     */
    /**
     * Verified, duplicate-safe backup import.
     *
     * Verification happens entirely before any write:
     *  1. the envelope is JSON, has the expected format marker and a non-empty
     *     items array
     *  2. every record parses, has a usable id/type/payload and decrypts with an
     *     authenticated key (active vault key, or a key unwrapped from the supplied
     *     source master password / 12-word recovery phrase). A phrase-only backup
     *     must always be given its recovery phrase;
     *     one bad record fails the whole import instead of writing a partial vault
     *  3. duplicates are detected against both the vault contents (record id, and
     *     a SHA-256 content fingerprint of type + normalised title + payload) and
     *     earlier records in the same file
     *
     * Only then are the surviving records re-encrypted under the active vault key
     * and written in a single Room transaction, so an interrupted import can never
     * leave half a backup behind.
     */
    suspend fun importEncryptedBackup(
        jsonString: String,
        backupPassword: String? = null,
        skipInvalidRecords: Boolean = false,
    ): BackupImportReport = withContext(Dispatchers.Default) {
        val activeKey = activeSecretKey ?: throw VaultStateException(VaultError.VAULT_LOCKED, "Vault is locked")
        val backupJson = BackupJson.parse(jsonString)
        val format = backupJson.optString("format", "")
        if (format != BACKUP_FORMAT) {
            throw VaultException(VaultError.NOT_A_VAULT_BACKUP, "not a VaultZero encrypted backup envelope")
        }
        val itemsArray = backupJson.optJSONArray("items")
            ?: throw VaultException(VaultError.BACKUP_NO_ITEMS, "backup has no items array")
        if (itemsArray.length() == 0) {
            throw VaultException(VaultError.BACKUP_EMPTY, "backup contains no entries")
        }

        // ---- key needed to authenticate the records ----
        // The secret may be the source vault's master password OR its 12-word
        // recovery phrase: every backup already carries the VMK wrapped under the
        // recovery-phrase key, so the phrase alone is a complete restore credential.
        val protection = BackupProtection.fromStorage(backupJson.optString("protection", null))
        val secretText = backupPassword?.trim().orEmpty()
        if (protection == BackupProtection.RECOVERY_PHRASE_ONLY && secretText.isEmpty()) {
            throw VaultException(
                VaultError.BACKUP_PROTECTED_BY_PHRASE,
                "this backup is protected by its 12-word recovery phrase; enter that phrase to import it",
            )
        }
        var foreignBackupKey: SecretKey? = null
        if (secretText.isNotEmpty()) {
            // The KDF specs travel inside an untrusted file: parseBackupKdf rejects
            // malformed or out-of-range parameters before any KDF work happens, so a
            // crafted backup cannot CPU/memory-DoS the import or weaken the KDF.
            val backedUpRecoveryKdf = parseBackupKdf(backupJson, "kdfRecoverySpec", "recovery-phrase")
            val backedUpPasswordKdf = parseBackupKdf(backupJson, "kdfPasswordSpec", "password")
            val phraseCandidate = VaultCrypto.normalizeRecoveryPhrase(secretText)
            val looksLikePhrase = VaultCrypto.validateRecoveryPhrase(
                phraseCandidate,
                enforceChecksum = false,
            )
            foreignBackupKey = if (looksLikePhrase) {
                if (!VaultCrypto.verifyRecoveryPhraseChecksum(phraseCandidate)) {
                    throw VaultException(
                        VaultError.RECOVERY_PHRASE_CHECKSUM_MISMATCH,
                        "recovery phrase checksum does not match — check for a mistyped or reordered word",
                    )
                }
                val recoverySaltB64 = backupJson.optString("recoverySalt", "")
                val recoveryEncVmk = backupJson.optString("encryptedVmkByRecovery", "")
                if (recoverySaltB64.isEmpty() || recoveryEncVmk.isEmpty()) {
                    throw VaultException(VaultError.BACKUP_NO_RECOVERY_KEY, "this backup has no recovery-phrase key material")
                }
                validateBackupKeyEnvelope(recoveryEncVmk)
                try {
                    val salt = decodeBackupSalt(recoverySaltB64, "recovery")
                    val recoveryKey = VaultCrypto.deriveKeyFromPhrase(phraseCandidate, salt, backedUpRecoveryKdf)
                    val vmkHex = VaultCrypto.decrypt(recoveryEncVmk, recoveryKey)
                    SecretKeySpec(VaultCrypto.hexToBytes(vmkHex), "AES")
                } catch (e: Exception) {
                    throw VaultException(VaultError.RECOVERY_PHRASE_MISMATCH, "the recovery phrase does not match this backup")
                }
            } else {
                if (protection == BackupProtection.RECOVERY_PHRASE_ONLY) {
                    throw VaultException(
                        VaultError.BACKUP_PHRASE_ONLY_NEEDS_PHRASE,
                        "this backup can only be opened with its 12-word recovery phrase, not a password",
                    )
                }
                val backupSaltB64 = backupJson.optString("salt", "")
                val backupEncVmk = backupJson.optString("encryptedVmkByPassword", "")
                if (backupSaltB64.isEmpty() || backupEncVmk.isEmpty()) {
                    throw VaultException(VaultError.BACKUP_NO_PASSWORD_KEY, "backup has no password envelope to unlock")
                }
                validateBackupKeyEnvelope(backupEncVmk)
                try {
                    val salt = decodeBackupSalt(backupSaltB64, "password")
                    val passKey = VaultCrypto.deriveKey(secretText, salt, backedUpPasswordKdf)
                    val unwrappedVmkHex = VaultCrypto.decrypt(backupEncVmk, passKey)
                    SecretKeySpec(VaultCrypto.hexToBytes(unwrappedVmkHex), "AES")
                } catch (e: Exception) {
                    throw VaultException(VaultError.BACKUP_PASSWORD_WRONG, "backup password is wrong")
                }
            }
        }

        // ---- phase 1: parse + decrypt + validate every record (no writes) ----
        val candidates = ArrayList<VaultItem>(itemsArray.length())
        var unreadable = 0
        var firstUnreadableReason: String? = null
        for (i in 0 until itemsArray.length()) {
            try {
                val itemObj = itemsArray.optJSONObject(i)
                    ?: throw VaultException(
                        VaultError.BACKUP_ENTRY_NOT_OBJECT,
                        "entry ${i + 1} is not an object",
                        args = listOf(i + 1),
                    )
                val id = itemObj.optString("id", "")
                if (id.isBlank()) {
                    throw VaultException(
                        VaultError.BACKUP_ENTRY_NO_ID,
                        "entry ${i + 1} has no record id",
                        args = listOf(i + 1),
                    )
                }
                val typeStr = itemObj.optString("type", "")
                val validType = runCatching { VaultType.valueOf(typeStr) }.getOrNull()
                    ?: throw VaultException(
                        VaultError.BACKUP_ENTRY_UNKNOWN_TYPE,
                        "entry ${i + 1} has an unknown item type",
                        args = listOf(i + 1),
                    )
                val encPayload = itemObj.optString("encryptedPayload", "")
                if (encPayload.isEmpty()) {
                    throw VaultException(
                        VaultError.BACKUP_ENTRY_NO_PAYLOAD,
                        "entry ${i + 1} has no encrypted payload",
                        args = listOf(i + 1),
                    )
                }

                val tempEntity = VaultItemEntity(
                    id = id,
                    title = itemObj.optString("title", REDACTED_TITLE),
                    type = validType.name,
                    category = itemObj.optString("category", REDACTED_CATEGORY),
                    encryptedPayload = encPayload,
                    isFavorite = itemObj.optBoolean("isFavorite", false),
                    createdAt = itemObj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = itemObj.optLong("updatedAt", System.currentTimeMillis()),
                    lastUsedAt = itemObj.optLong("lastUsedAt", 0L),
                    tagsJson = itemObj.optString("tagsJson", REDACTED_TAGS_JSON)
                )

                val decrypted = try {
                    decryptRecordEntity(tempEntity, activeKey)
                } catch (activeFailure: Exception) {
                    val altKey = foreignBackupKey
                        ?: throw VaultException(
                            VaultError.BACKUP_ENTRY_FOREIGN_KEY,
                            "entry ${i + 1} failed authentication with this vault's key " +
                                "(backup of a different vault? provide its master password or 12-word recovery phrase)",
                            args = listOf(i + 1),
                        )
                    try {
                        decryptRecordEntity(tempEntity, altKey)
                    } catch (foreignFailure: Exception) {
                        throw VaultException(
                            VaultError.BACKUP_ENTRY_FOREIGN_KEY_FAILED,
                            "entry ${i + 1} failed authentication (wrong backup password or corrupted file)",
                            args = listOf(i + 1),
                        )
                    }
                }
                candidates.add(decrypted)
            } catch (e: IllegalArgumentException) {
                unreadable++
                if (firstUnreadableReason == null) firstUnreadableReason = e.message
            }
        }
        if (unreadable > 0 && !skipInvalidRecords) {
            throw BackupVerificationException(
                unreadableRecords = unreadable,
                message = "Backup failed verification: $unreadable " +
                    (if (unreadable == 1) "record is" else "records are") +
                    " unreadable (${firstUnreadableReason ?: "authentication failed"}). " +
                    "Nothing has been written.",
            )
        }

        // ---- phase 2: per-record classification (still no writes) ----
        val existingEntities = dao.getAllList()
        val existingById = HashMap<String, VaultItem?>(existingEntities.size)
        val existingFingerprints = HashSet<String>(existingEntities.size)
        for (entity in existingEntities) {
            val decrypted = runCatching { decryptRecordEntity(entity, activeKey) }.getOrNull()
            existingById[entity.id] = decrypted
            if (decrypted != null) existingFingerprints.add(contentFingerprint(decrypted))
        }

        val batchIds = HashSet<String>()
        val batchFingerprints = HashSet<String>()
        var imported = 0
        var updated = 0
        var duplicates = 0
        val entitiesToWrite = ArrayList<VaultItemEntity>(candidates.size)
        for (item in candidates) {
            val fingerprint = contentFingerprint(item)
            val existingForId = existingById[item.id]
            val sameId = item.id in existingById
            val sameContent = when {
                item.id in batchIds -> true // repeated record inside the file
                sameId -> existingForId?.let { contentFingerprint(it) == fingerprint } ?: true
                fingerprint in existingFingerprints -> true // same content, different id
                fingerprint in batchFingerprints -> true
                else -> false
            }

            if (sameContent) {
                duplicates++
                continue
            }
            if (sameId) {
                // Same record id but the backup's content differs: accept the
                // backup as an update of that record instead of a duplicate.
                updated++
            } else {
                imported++
            }
            batchIds.add(item.id)
            batchFingerprints.add(fingerprint)
            val envelope = encryptRecordEnvelope(item, item.id, activeKey)
            entitiesToWrite.add(
                VaultItemEntity(
                    id = item.id,
                    title = REDACTED_TITLE,
                    type = item.type.name,
                    category = REDACTED_CATEGORY,
                    encryptedPayload = envelope,
                    isFavorite = item.isFavorite,
                    createdAt = item.createdAt,
                    updatedAt = item.updatedAt,
                    lastUsedAt = item.lastUsedAt,
                    tagsJson = REDACTED_TAGS_JSON
                )
            )
        }

        // ---- phase 3: one atomic write ----
        if (entitiesToWrite.isNotEmpty()) {
            dao.insertOrUpdateAll(entitiesToWrite)
        }
        BackupImportReport(
            imported = imported,
            updated = updated,
            duplicatesSkipped = duplicates,
            skippedInvalid = unreadable,
        )
    }

    /**
     * Stable content identity used to stop re-imports from duplicating records.
     * Type + normalised title + category + sorted tags + the whole secret payload:
     * a record differing only in its favourite flag or timestamps counts as the
     * same content (local favourite is preserved), while any real field change is
     * recognised as an update rather than a duplicate.
     */
    private fun decodeBackupSalt(raw: String, label: String): ByteArray {
        if (raw.length > MAX_BACKUP_SALT_BASE64) {
            throw VaultException(
                VaultError.BACKUP_SALT_TOO_LARGE,
                "$label salt in the backup is too large",
                args = listOf(label),
            )
        }
        val salt = Base64.decode(raw, Base64.NO_WRAP)
        if (salt.size < VaultCrypto.SALT_LENGTH_BYTES || salt.size > 64) {
            throw VaultException(
                VaultError.BACKUP_SALT_LENGTH,
                "$label salt in the backup has an unsupported length",
                args = listOf(label),
            )
        }
        return salt
    }

    private fun validateBackupKeyEnvelope(raw: String) {
        if (raw.length > MAX_BACKUP_KEY_ENVELOPE_CHARS) {
            throw VaultException(VaultError.BACKUP_ENVELOPE_TOO_LARGE, "backup key envelope is unreasonably large")
        }
    }

    /**
     * Read the KDF spec a backup declares for one envelope. Backup 3.3+ carries
     * explicit specs; 3.2 and older carry only the shared PBKDF2 iteration count,
     * which is still bounded here because the file is untrusted.
     */
    private fun parseBackupKdf(backup: JSONObject, key: String, label: String): KdfSpec {
        val raw = backup.optString(key, "")
        if (raw.isNotEmpty()) {
            return KdfSpec.decode(raw)
                ?: throw VaultException(
                    VaultError.BACKUP_KDF_UNSUPPORTED,
                    "backup declares unsupported $label KDF parameters",
                )
        }
        val iterations = backup.optInt("kdfIterations", VaultCrypto.DEFAULT_ITERATIONS)
        if (iterations < VaultCrypto.MIN_ACCEPTED_ITERATIONS ||
            iterations > VaultCrypto.MAX_ACCEPTED_ITERATIONS
        ) {
            throw VaultException(VaultError.BACKUP_KDF_UNSUPPORTED, "backup uses an unsupported KDF iteration count")
        }
        return KdfSpec.pbkdf2(iterations)
    }

    private fun contentFingerprint(item: VaultItem): String {
        val payloadJson = item.payload.toJson()
        val canonicalPayload = payloadJson.keys().asSequence().sorted()
            .joinToString("|") { key -> "$key=${payloadJson.opt(key).toString()}" }
        val canonicalTags = item.tags.sorted().joinToString(",")
        return VaultCrypto.sha256(
            listOf(
                item.type.name,
                item.title.trim().lowercase(),
                item.category.trim(),
                canonicalTags,
                canonicalPayload,
            ).joinToString("\u0000")
        )
    }


    /**
     * Rotate the master password without changing the VMK: verify the current
     * password against the stored password envelope, derive a new KEK with a fresh
     * salt at the current PBKDF2 strength, and atomically replace only the
     * password-derived envelope. Records are untouched (they are wrapped by the
     * VMK), and the recovery kit keeps working because its envelope is derived
     * from the VMK too.
     */
    suspend fun changeMasterPassword(
        currentPassword: String,
        newPassword: String,
    ) = withContext(Dispatchers.Default) {
        val key = activeSecretKey ?: throw VaultStateException(VaultError.VAULT_LOCKED, "Vault is locked")
        val cleanCurrent = currentPassword.trim()
        val cleanNew = newPassword.trim()
        if (cleanNew.length < 8) {
            throw VaultException(VaultError.NEW_PASSWORD_TOO_SHORT, "New password must be at least 8 characters")
        }
        if (cleanNew == cleanCurrent) {
            throw VaultException(VaultError.NEW_PASSWORD_UNCHANGED, "New password must differ from the current one")
        }

        val salt = Base64.decode(prefs.saltBase64, Base64.NO_WRAP)
        val currentKey = VaultCrypto.deriveKey(cleanCurrent, salt, prefs.passwordKdf)
        val currentVerifier = VaultCrypto.createKeyVerifier(currentKey, "PASSWORD")
        if (currentVerifier != prefs.masterPasswordHash) {
            throw VaultException(VaultError.CURRENT_PASSWORD_INCORRECT, "Current master password is incorrect")
        }

        val vmkHex = VaultCrypto.bytesToHex(key.encoded)
        val newSalt = VaultCrypto.generateSalt()
        val newKey = VaultCrypto.deriveKey(cleanNew, newSalt, KdfSpec.CURRENT)
        val newEncVmk = VaultCrypto.encrypt(vmkHex, newKey)
        val newVerifier = VaultCrypto.createKeyVerifier(newKey, "PASSWORD")

        prefs.rotatePasswordEnvelope(
            passwordVerifier = newVerifier,
            saltBase64 = Base64.encodeToString(newSalt, Base64.NO_WRAP),
            encryptedVmkByPassword = newEncVmk,
            kdf = KdfSpec.CURRENT,
        )
    }

    /**
     * Re-wrap both key envelopes with [KdfSpec.CURRENT] (Argon2id) after a
     * successful password unlock of a vault still using a weaker or legacy KDF
     * (PBKDF2 at 600k or below, e.g. a vault restored from an old backup).
     *
     * The VMK itself is never re-keyed, so every encrypted record stays valid;
     * only the password and recovery KEKs (and their salts) rotate. Both
     * envelopes rotate together because a single unlock cannot know how the
     * other envelope's KDF will drift: if the recovery phrase cannot be read,
     * the whole migration is skipped rather than leaving a recovery path that
     * no longer derives.
     *
     * The write is a single SharedPreferences commit; any failure leaves the
     * previous envelopes, KDF specs and salts untouched.
     *
     * @return true when the envelopes were migrated
     */
    private fun migrateKdfIfNeeded(
        cleanPassword: String,
        vmkHex: String,
        currentPasswordKdf: KdfSpec,
    ): Boolean {
        if (cleanPassword.isEmpty()) return false
        if (currentPasswordKdf == KdfSpec.CURRENT && prefs.recoveryKdf == KdfSpec.CURRENT) return false
        return try {
            val vmk = SecretKeySpec(VaultCrypto.hexToBytes(vmkHex), "AES")
            if (prefs.encryptedRecoveryPhrase.isEmpty()) return false
            val recoveryPhrase = VaultCrypto.decrypt(prefs.encryptedRecoveryPhrase, vmk)
            if (!VaultCrypto.validateRecoveryPhrase(recoveryPhrase, enforceChecksum = false)) return false

            val newPasswordSalt = VaultCrypto.generateSalt()
            val newPasswordKey = VaultCrypto.deriveKey(
                cleanPassword,
                newPasswordSalt,
                KdfSpec.CURRENT,
            )
            val newPasswordVerifier = VaultCrypto.createKeyVerifier(newPasswordKey, "PASSWORD")

            val newRecoverySalt = VaultCrypto.generateSalt()
            val newRecoveryKey = VaultCrypto.deriveKeyFromPhrase(
                recoveryPhrase,
                newRecoverySalt,
                KdfSpec.CURRENT,
            )
            val newRecoveryVerifier = VaultCrypto.createKeyVerifier(newRecoveryKey, "RECOVERY")

            prefs.saveEnvelopeCredentials(
                passwordHash = newPasswordVerifier,
                saltBase64 = Base64.encodeToString(newPasswordSalt, Base64.NO_WRAP),
                recoverySaltBase64 = Base64.encodeToString(newRecoverySalt, Base64.NO_WRAP),
                encryptedVmkByPassword = VaultCrypto.encrypt(vmkHex, newPasswordKey),
                encryptedVmkByRecovery = VaultCrypto.encrypt(vmkHex, newRecoveryKey),
                // Still encrypted under the unchanged VMK.
                encryptedRecoveryPhrase = prefs.encryptedRecoveryPhrase,
                recoveryPhraseHash = newRecoveryVerifier,
                keyFingerprint = prefs.keyFingerprint,
                passwordKdf = KdfSpec.CURRENT,
                recoveryKdf = KdfSpec.CURRENT,
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun purgeLegacyDemoItemsIfPresent(key: SecretKey) {
        try {
            val entities = dao.getAllList()
            for (entity in entities) {
                val item = try {
                    decryptRecordEntity(entity, key)
                } catch (e: Exception) {
                    null
                } ?: continue

                val isLegacyDemo = when (val p = item.payload) {
                    is LoginPayload -> item.title == "GitHub Developer Account" && p.password == "g!t_V@ultZ3r0#2026_SecureKey"
                    is ApiSecretPayload -> item.title == "AWS Production Root API" && p.apiKey == "AKIAIOSFODNN7EXAMPLE"
                    is CryptoPayload -> item.title == "Ethereum Cold Storage Wallet" && p.publicAddress == "0x71C...8494"
                    is CardPayload -> item.title == "Titanium Corporate Visa" && p.cardNumber == "4532 8812 9014 3829"
                    is NotePayload -> item.title == "Post-Quantum Security Protocol Notes" && p.content.startsWith("VaultZero Architecture:")
                    else -> false
                }
                if (isLegacyDemo) {
                    dao.deleteById(entity.id)
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    private fun parseTags(json: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
        } catch (e: Exception) {
            // ignore malformed tag JSON
        }
        return list
    }
}
