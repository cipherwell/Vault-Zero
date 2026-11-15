package com.example.crypto

import android.security.keystore.KeyInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/**
 * On real hardware, proves the "fingerprint decryption" key for saved backups is
 * per-use auth-gated: no entry can exist without an authenticated cipher, the key
 * refuses use without a biometric prompt, and decryption needs a real cipher.
 */
@RunWith(AndroidJUnit4::class)
class BiometricBackupKeyBindingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() = BiometricBackupKeyManager.clearAll(context)

    @After
    fun tearDown() = BiometricBackupKeyManager.clearAll(context)

    private fun Throwable.isAuthenticationRejection(): Boolean =
        generateSequence<Throwable>(this) { it.cause }.any { t ->
            (t is android.security.keystore.UserNotAuthenticatedException) ||
                t.message.orEmpty().contains("not authenticated", ignoreCase = true)
        }

    @Test
    fun keyIsPerUseBiometricGated() {
        val cipher = BiometricBackupKeyManager.getEncryptCipher()
        assertNotNull("ENCRYPT_MODE cipher must be created", cipher)

        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey("VaultZero_Backup_Key_v1", null) as SecretKey
        val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo
        assertTrue("backup key must require user authentication", info.isUserAuthenticationRequired)
        assertTrue("must be per-use", info.userAuthenticationValidityDurationSeconds == 0)
        assertTrue("must be invalidated by biometric re-enrolment", info.isInvalidatedByBiometricEnrollment)
    }

    @Test
    fun keyRefusesUseWithoutPrompt() {
        val cipher = BiometricBackupKeyManager.getEncryptCipher()
        assertNotNull(cipher)
        var rejected = false
        try {
            cipher!!.doFinal("x".toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            rejected = e.isAuthenticationRejection()
        }
        assertTrue("backup key must reject doFinal without a BiometricPrompt", rejected)
    }

    @Test
    fun noEntriesAndNoDecryptionWithoutAuthenticatedCipher() {
        assertFalse(BiometricBackupKeyManager.hasEntry(context, "fingerprint"))
        assertNull(BiometricBackupKeyManager.getDecryptCipher(context, "fingerprint"))
        assertNull(BiometricBackupKeyManager.decryptEntry(context, "fingerprint", null))
        assertFalse(
            BiometricBackupKeyManager.storeEntry(context, "fingerprint", "deadbeef", null)
        )
        assertFalse(BiometricBackupKeyManager.hasEntry(context, "fingerprint"))
    }
}
