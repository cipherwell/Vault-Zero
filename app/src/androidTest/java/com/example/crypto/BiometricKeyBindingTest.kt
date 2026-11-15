package com.example.crypto

import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

/**
 * On real hardware, prove the biometric wrap is auth-gated: the Keystore key
 * refuses to encrypt/decrypt unless a BiometricPrompt has authorised that exact
 * Cipher, and no enrolment material exists without one.
 */
@RunWith(AndroidJUnit4::class)
class BiometricKeyBindingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() = BiometricKeyManager.clearBiometricKey(context)

    @After
    fun tearDown() = BiometricKeyManager.clearBiometricKey(context)

    /** True when the throwable (or a cause) is Keystore denying use without auth. */
    private fun Throwable.isAuthenticationRejection(): Boolean =
        generateSequence<Throwable>(this) { it.cause }.any { t ->
            t is UserNotAuthenticatedException ||
                t is android.security.KeyStoreException &&
                t.message.orEmpty().contains("not authenticated", ignoreCase = true) ||
                t.message.orEmpty().contains("not authenticated", ignoreCase = true)
        }

    @Test
    fun keyIsConfiguredForPerUseBiometricAuthentication() {
        val cipher = BiometricKeyManager.getEncryptCipher(context)
        assertNotNull("ENCRYPT_MODE cipher must be created", cipher)

        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey("VaultZero_Biometric_Key_v2", null) as SecretKey
        val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo

        assertTrue("key must require user authentication", info.isUserAuthenticationRequired)
        assertEquals(
            "key must be per-use (validity 0), not a timed unlock window",
            0,
            info.userAuthenticationValidityDurationSeconds,
        )
        assertTrue(
            "key must be gated on biometric strong",
            info.userAuthenticationType and KeyProperties.AUTH_BIOMETRIC_STRONG != 0,
        )
        assertTrue("key must be invalidated by biometric re-enrolment", info.isInvalidatedByBiometricEnrollment)
    }

    @Test
    fun authRequiredKeyRefusesDoFinalWithoutPrompt() {
        val cipher = BiometricKeyManager.getEncryptCipher(context)
        assertNotNull("ENCRYPT_MODE cipher must be created", cipher)

        var rejected = false
        var thrown: Throwable? = null
        try {
            cipher!!.doFinal("no-auth".toByteArray(Charsets.UTF_8))
        } catch (expected: Exception) {
            thrown = expected
            rejected = expected.isAuthenticationRejection()
        }
        assertTrue(
            "auth-required Keystore key must reject doFinal without a BiometricPrompt; threw: " +
                (thrown?.let { "${it::class.java.simpleName} cause=${it.cause?.message}" } ?: "nothing"),
            rejected,
        )
    }

    @Test
    fun noDecryptPathWithoutEnrolment() {
        assertFalse(BiometricKeyManager.hasCurrentEnrollment(context))
        assertNull(BiometricKeyManager.getDecryptCipher(context))
        assertNull(BiometricKeyManager.decryptVmkWithCipher(context, null))
    }

    @Test
    fun cannotPersistVmkWithoutAuthenticatedCipher() {
        assertFalse(BiometricKeyManager.storeVmkWithCipher(context, "deadbeef", null))
        assertFalse(BiometricKeyManager.hasCurrentEnrollment(context))
    }
}
