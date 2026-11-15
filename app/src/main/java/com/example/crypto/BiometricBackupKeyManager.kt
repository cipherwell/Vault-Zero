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
 * Hardware-gated "fingerprint decryption" for backups.
 *
 * When the user opts in for a backup, the backup's vault key (never the 12-word
 * phrase) is wrapped by a dedicated Keystore key that requires a fresh biometric
 * authentication for every use (`AUTH_BIOMETRIC_STRONG`, 0-second validity,
 * invalidated when biometrics are re-enrolled). Later imports of a backup from the
 * same source vault can then be decrypted after a fingerprint prompt, with no
 * phrase or password typed.
 *
 * Keys are stored per backup fingerprint (the source vault's key fingerprint that
 * already travels inside every export). Clearing the entries or re-enrolling
 * biometrics makes the saved backups forgettable/unreadable by fingerprint.
 */
object BiometricBackupKeyManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "VaultZero_Backup_Key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFS_NAME = "vaultzero_backup_bio_prefs"
    private const val ENTRY_PREFIX = "backup_vmk_"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

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

    private fun getOrCreateKey(): SecretKey {
        val store = keyStore()
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
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

    /** ENCRYPT_MODE cipher for enrolling an entry; pass it through BiometricPrompt. */
    fun getEncryptCipher(): Cipher? = try {
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
    } catch (e: Exception) {
        null
    }

    /** DECRYPT_MODE cipher for one saved backup; requires a fresh biometric prompt. */
    fun getDecryptCipher(context: Context, fingerprint: String): Cipher? {
        return try {
            val entry = readEntry(context, fingerprint) ?: return null
            val iv = ByteArray(IV_LENGTH_BYTES)
            System.arraycopy(entry, 0, iv, 0, IV_LENGTH_BYTES)
            val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: return null
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            }
        } catch (invalidated: KeyPermanentlyInvalidatedException) {
            clearAll(context)
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Persist `fingerprint -> wrapped backup VMK` with an authenticated cipher. */
    fun storeEntry(
        context: Context,
        fingerprint: String,
        vmkHex: String,
        authenticatedCipher: Cipher?,
    ): Boolean {
        if (authenticatedCipher == null || fingerprint.isBlank() || vmkHex.isBlank()) return false
        return try {
            val iv = authenticatedCipher.iv
            val ct = authenticatedCipher.doFinal(vmkHex.toByteArray(Charsets.UTF_8))
            val buffer = ByteBuffer.allocate(iv.size + ct.size)
            buffer.put(iv)
            buffer.put(ct)
            val encoded = Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit(commit = true) { putString(ENTRY_PREFIX + fingerprint, encoded) }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Unwrap the saved backup key with the authenticated cipher. */
    fun decryptEntry(
        context: Context,
        fingerprint: String,
        authenticatedCipher: Cipher?,
    ): String? {
        if (authenticatedCipher == null) return null
        return try {
            val entry = readEntry(context, fingerprint) ?: return null
            val ct = ByteArray(entry.size - IV_LENGTH_BYTES)
            System.arraycopy(entry, IV_LENGTH_BYTES, ct, 0, ct.size)
            String(authenticatedCipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun hasEntry(context: Context, fingerprint: String): Boolean =
        readEntry(context, fingerprint) != null

    fun entryCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.all.keys.count { it.startsWith(ENTRY_PREFIX) }
    }

    fun forgetEntry(context: Context, fingerprint: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit(commit = true) { remove(ENTRY_PREFIX + fingerprint) }
    }

    fun clearAll(context: Context) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit(commit = true) { clear() }
            val store = keyStore()
            if (store.containsAlias(KEY_ALIAS)) store.deleteEntry(KEY_ALIAS)
        }
    }

    private fun readEntry(context: Context, fingerprint: String): ByteArray? {
        if (fingerprint.isBlank()) return null
        val encoded = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(ENTRY_PREFIX + fingerprint, null) ?: return null
        return runCatching { Base64.decode(encoded, Base64.NO_WRAP) }
            .getOrNull()
            ?.takeIf { it.size > IV_LENGTH_BYTES }
    }
}
