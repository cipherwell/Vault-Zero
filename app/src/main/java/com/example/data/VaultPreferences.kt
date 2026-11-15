package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.crypto.VaultCrypto

/**
 * Typed wrapper around the vault's SharedPreferences file.
 *
 * Vault-critical writes use [edit] with `commit = true` on purpose (crash-safe,
 * synchronous). User-interface preferences (auto-lock, clipboard timeout, theme)
 * use the default asynchronous `apply`: they are re-read on next launch and a lost
 * last change on a process crash is harmless, while a blocking disk write on every
 * chip tap is exactly the jank the settings screen showed. The default (`apply`)
 * defers the disk write to a background thread, which is wrong for key material:
 * if the process dies right after [saveEnvelopeCredentials] or a KDF re-wrap
 * reports success, the envelope (and the only copy of the wrapped VMK) must
 * already be on disk. The cost is one synchronous write per security-state
 * change, not per keystroke.
 */
class VaultPreferences(
    context: Context,
    prefsName: String = SECURE_PREFS_NAME,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    companion object {
        /** Production prefs file. */
        const val SECURE_PREFS_NAME = "vaultzero_secure_prefs"

        /** Isolated file used by instrumentation tests, never the real vault. */
        const val TEST_PREFS_NAME = "vaultzero_test_prefs"
    }

    var isInitialized: Boolean
        get() = prefs.getBoolean("is_initialized", false)
        set(value) = prefs.edit(commit = true) { putBoolean("is_initialized", value) }

    var masterPasswordHash: String
        get() = prefs.getString("master_password_hash", "") ?: ""
        set(value) = prefs.edit(commit = true) { putString("master_password_hash", value) }

    var saltBase64: String
        get() = prefs.getString("master_salt_b64", "") ?: ""
        set(value) = prefs.edit(commit = true) { putString("master_salt_b64", value) }

    var recoverySaltBase64: String
        get() = prefs.getString("recovery_salt_b64", "") ?: ""
        set(value) = prefs.edit(commit = true) { putString("recovery_salt_b64", value) }

    var kdfIterations: Int
        get() = prefs.getInt("kdf_iterations", VaultCrypto.DEFAULT_ITERATIONS)
        set(value) = prefs.edit(commit = true) { putInt("kdf_iterations", value) }

    var encryptedVmkByPassword: String
        get() = prefs.getString("encrypted_vmk_by_password", "") ?: ""
        set(value) = prefs.edit(commit = true) { putString("encrypted_vmk_by_password", value) }

    var encryptedVmkByRecovery: String
        get() = prefs.getString("encrypted_vmk_by_recovery", "") ?: ""
        set(value) = prefs.edit(commit = true) { putString("encrypted_vmk_by_recovery", value) }

    var encryptedRecoveryPhrase: String
        get() = prefs.getString("encrypted_recovery_phrase", "") ?: ""
        set(value) = prefs.edit(commit = true) {
            putString("encrypted_recovery_phrase", value)
            // One-time migration away from the legacy plaintext copy.
            remove("recovery_phrase")
        }

    /**
     * Read-only access to legacy plaintext recovery_phrase for one-time encrypted migration.
     */
    val legacyPlaintextRecoveryPhrase: String
        get() = prefs.getString("recovery_phrase", "") ?: ""

    fun clearLegacyPlaintextRecoveryPhrase() {
        prefs.edit(commit = true) { remove("recovery_phrase") }
    }

    var recoveryPhraseHash: String
        get() = prefs.getString("recovery_phrase_hash", "") ?: ""
        set(value) = prefs.edit(commit = true) { putString("recovery_phrase_hash", value) }

    var keyFingerprint: String
        get() = prefs.getString("key_fingerprint", "VZ-0000-0000-0000-0000") ?: "VZ-0000-0000-0000-0000"
        set(value) = prefs.edit(commit = true) { putString("key_fingerprint", value) }

    var autoLockSeconds: Int
        get() = prefs.getInt("auto_lock_seconds", 60)
        set(value) = prefs.edit { putInt("auto_lock_seconds", value) }

    /** Lock when the app leaves the foreground/ screen off after N seconds (0 = off). */
    var backgroundLockSeconds: Int
        get() = prefs.getInt("background_lock_seconds", 30)
        set(value) = prefs.edit { putInt("background_lock_seconds", value) }

    var clipboardTimeoutSeconds: Int
        get() = prefs.getInt("clipboard_timeout_seconds", 30)
        set(value) = prefs.edit { putInt("clipboard_timeout_seconds", value) }

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean("is_biometric_enabled", false)
        set(value) = prefs.edit(commit = true) { putBoolean("is_biometric_enabled", value) }

    /** Vault list sort order, persisted across sessions. */
    var sortOption: String
        get() = prefs.getString("vault_sort_option", "FAVORITES_FIRST") ?: "FAVORITES_FIRST"
        set(value) = prefs.edit { putString("vault_sort_option", value) }

    /** Whether the vault list groups by item type, persisted across sessions. */
    var groupByType: Boolean
        get() = prefs.getBoolean("vault_group_by_type", false)
        set(value) = prefs.edit { putBoolean("vault_group_by_type", value) }

    /** One UI appearance: "system" (default), "light" or "dark". */
    var themeMode: String
        get() = prefs.getString("theme_mode", "system") ?: "system"
        set(value) = prefs.edit { putString("theme_mode", value) }

    var isDarkTheme: Boolean
        get() = prefs.getBoolean("is_dark_theme", true)
        set(value) = prefs.edit { putBoolean("is_dark_theme", value) }

    fun saveEnvelopeCredentials(
        passwordHash: String,
        saltBase64: String,
        recoverySaltBase64: String,
        encryptedVmkByPassword: String,
        encryptedVmkByRecovery: String,
        encryptedRecoveryPhrase: String,
        recoveryPhraseHash: String,
        keyFingerprint: String,
        kdfIterations: Int = VaultCrypto.DEFAULT_ITERATIONS
    ) {
        prefs.edit(commit = true) {
            putString("master_password_hash", passwordHash)
            putString("master_salt_b64", saltBase64)
            putString("recovery_salt_b64", recoverySaltBase64)
            putInt("kdf_iterations", kdfIterations)
            putString("encrypted_vmk_by_password", encryptedVmkByPassword)
            putString("encrypted_vmk_by_recovery", encryptedVmkByRecovery)
            putString("encrypted_recovery_phrase", encryptedRecoveryPhrase)
            // The recovery phrase is only ever stored encrypted under the VMK.
            remove("recovery_phrase")
            putString("recovery_phrase_hash", recoveryPhraseHash)
            putString("key_fingerprint", keyFingerprint)
            putBoolean("is_initialized", true)
        }
    }

    /**
     * Atomically replace the password-derived envelope after a master password
     * change. The recovery envelope and the encrypted recovery phrase are derived
     * from the VMK and stay untouched, so the emergency kit keeps working.
     */
    fun rotatePasswordEnvelope(
        passwordVerifier: String,
        saltBase64: String,
        encryptedVmkByPassword: String,
        kdfIterations: Int = VaultCrypto.DEFAULT_ITERATIONS,
    ) {
        prefs.edit(commit = true) {
            putString("master_password_hash", passwordVerifier)
            putString("master_salt_b64", saltBase64)
            putString("encrypted_vmk_by_password", encryptedVmkByPassword)
            putInt("kdf_iterations", kdfIterations)
        }
    }

    /**
     * Atomically replace only the recovery-kit envelope after the user rotates their kit.
     * The password envelope, settings, records and VMK are untouched; old kits stop opening
     * this vault, while old backups keep their own embedded (old) recovery envelope.
     */
    fun rotateRecoveryEnvelope(
        recoverySaltBase64: String,
        encryptedVmkByRecovery: String,
        encryptedRecoveryPhrase: String,
        recoveryPhraseHash: String,
    ) {
        prefs.edit(commit = true) {
            putString("recovery_salt_b64", recoverySaltBase64)
            putString("encrypted_vmk_by_recovery", encryptedVmkByRecovery)
            putString("encrypted_recovery_phrase", encryptedRecoveryPhrase)
            remove("recovery_phrase")
            putString("recovery_phrase_hash", recoveryPhraseHash)
        }
    }

    fun resetAll() {
        prefs.edit(commit = true) { clear() }
    }
}
