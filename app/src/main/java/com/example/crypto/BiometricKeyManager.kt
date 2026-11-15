package com.example.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed wrapping of the Vault Master Key with a per-use biometric gate.
 *
 * The Keystore key is created with `setUserAuthenticationRequired(true)` and a
 * per-operation validity (`AUTH_BIOMETRIC_STRONG` on API 30+, 0-second validity
 * below that), and is invalidated when biometrics are re-enrolled. That means
 * `Cipher.doFinal` fails with `UserNotAuthenticatedException` until a fresh
 * `BiometricPrompt` has authorised that exact `Cipher` through a `CryptoObject`.
 *
 * Consequences, all deliberate:
 *  - enrolling requires a biometric prompt (encrypt is authenticated too)
 *  - unlocking requires a biometric prompt every time (no silent decrypt path)
 *  - clearing the app data is not enough to recover the wrapped VMK: the wrapping
 *    key lives in the TEE/StrongBox and dies with the key alias
 *  - re-enrolling a fingerprint invalidates the key, so the user must re-enable
 *    biometric unlock with the master password once
 */
object BiometricKeyManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "VaultZero_Biometric_Key_v2"
    private const val LEGACY_KEY_ALIAS = "VaultZero_Biometric_Key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFS_NAME = "vaultzero_biometric_secure_prefs"
    private const val PREF_KEY_ENCRYPTED_VMK = "encrypted_vault_master_key_v2"
    private const val PREF_KEY_LEGACY_ENCRYPTED_VMK = "encrypted_vault_master_key"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** One builder for both TEE and StrongBox paths; per-API auth params applied conditionally. */
    private fun buildSpec(strongBox: Boolean): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Per-use authentication: the key works only for the cipher a fresh
            // biometric prompt has authorised.
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUnlockedDeviceRequired(true)
            if (strongBox) builder.setIsStrongBoxBacked(true)
        }
        return builder.build()
    }

    /** Create the auth-required key, preferring StrongBox and falling back to the TEE. */
    private fun getOrCreateSecretKey(): SecretKey {
        val store = keyStore()
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        // Drop any legacy (non-auth-gated) key so it can never be used again.
        if (store.containsAlias(LEGACY_KEY_ALIAS)) {
            runCatching { store.deleteEntry(LEGACY_KEY_ALIAS) }
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                generator.init(buildSpec(strongBox = true))
                return generator.generateKey()
            }
        }
        generator.init(buildSpec(strongBox = false))
        return generator.generateKey()
    }

    /**
     * ENCRYPT_MODE cipher for enrolling: pass it to BiometricPrompt as a
     * CryptoObject, then hand the authenticated cipher back to
     * [storeVmkWithCipher].
     */
    fun getEncryptCipher(context: Context): Cipher? = try {
        val key = getOrCreateSecretKey()
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    } catch (e: Exception) {
        null
    }

    /**
     * DECRYPT_MODE cipher bound to the stored IV. Returns null when there is no
     * enrolment or the key was invalidated (biometric re-enrolment / lock-screen
     * change), in which case the user must re-enable biometric unlock.
     */
    fun getDecryptCipher(context: Context): Cipher? {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val encoded = prefs.getString(PREF_KEY_ENCRYPTED_VMK, null) ?: return null
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH_BYTES + 16) return null

            val iv = ByteArray(IV_LENGTH_BYTES)
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES)

            val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: return null
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            }
        } catch (invalidated: KeyPermanentlyInvalidatedException) {
            clearBiometricKey(context)
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Persist the VMK wrapped by an already-authenticated ENCRYPT_MODE cipher. */
    fun storeVmkWithCipher(context: Context, vmkHex: String, authenticatedCipher: Cipher?): Boolean {
        if (authenticatedCipher == null) return false
        return try {
            val iv = authenticatedCipher.iv
            val cipherText = authenticatedCipher.doFinal(vmkHex.toByteArray(Charsets.UTF_8))
            val buffer = ByteBuffer.allocate(iv.size + cipherText.size)
            buffer.put(iv)
            buffer.put(cipherText)
            val encoded = Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit(commit = true) {
                    putString(PREF_KEY_ENCRYPTED_VMK, encoded)
                    remove(PREF_KEY_LEGACY_ENCRYPTED_VMK)
                }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Decrypt the wrapped VMK with the authenticated DECRYPT_MODE cipher. There is
     * deliberately no fallback path: without a biometric-authorised cipher the
     * key cannot be used at all.
     */
    fun decryptVmkWithCipher(context: Context, authenticatedCipher: Cipher?): String? {
        if (authenticatedCipher == null) return null
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val encoded = prefs.getString(PREF_KEY_ENCRYPTED_VMK, null) ?: return null
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH_BYTES + 16) return null

            val cipherText = ByteArray(combined.size - IV_LENGTH_BYTES)
            System.arraycopy(combined, IV_LENGTH_BYTES, cipherText, 0, cipherText.size)
            String(authenticatedCipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /** True only when a current (v2, auth-gated) enrolment exists. */
    fun hasCurrentEnrollment(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(PREF_KEY_ENCRYPTED_VMK, null).isNullOrEmpty()
    }

    /** Public shim kept for the existing call sites. */
    fun hasBiometricKey(context: Context): Boolean = hasCurrentEnrollment(context)

    /** Drop the enrolment material and any legacy key so a stale wrap can't linger. */
    fun clearBiometricKey(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit(commit = true) {
                    remove(PREF_KEY_ENCRYPTED_VMK)
                    remove(PREF_KEY_LEGACY_ENCRYPTED_VMK)
                }
            val store = keyStore()
            listOf(KEY_ALIAS, LEGACY_KEY_ALIAS).forEach { alias ->
                if (store.containsAlias(alias)) store.deleteEntry(alias)
            }
        }
    }
}
