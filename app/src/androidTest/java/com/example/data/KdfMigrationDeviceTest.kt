package com.example.data

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.crypto.KdfSpec
import com.example.crypto.VaultCrypto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device proof of the KDF migration: a vault created by the previous release
 * (100k PBKDF2 iterations) must unlock on a real Android device with `test1234`,
 * be re-wrapped at 600k, and stay unlockable by both the password and the
 * recovery phrase. Also records how long the stronger KDF takes on this device.
 */
@RunWith(AndroidJUnit4::class)
class KdfMigrationDeviceTest {

    private lateinit var context: Context
    private lateinit var prefs: VaultPreferences
    private lateinit var db: VaultDatabase
    private lateinit var repository: VaultRepository

    private val legacyIterations = 100_000
    private val password = "test1234"
    private lateinit var recoveryPhrase: String

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = VaultPreferences(context, VaultPreferences.TEST_PREFS_NAME)
        prefs.resetAll()
        db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = VaultRepository(db.vaultDao(), prefs)
        createVaultAtLegacyWorkFactor()
    }

    @After
    fun tearDown() {
        db.close()
        prefs.resetAll()
    }

    private fun createVaultAtLegacyWorkFactor() {
        val vmk = VaultCrypto.generateRandomKey()
        val vmkHex = VaultCrypto.bytesToHex(vmk.encoded)
        val passwordSalt = VaultCrypto.generateSalt()
        val passwordKey = VaultCrypto.derivePbkdf2Key(password, passwordSalt, legacyIterations)
        recoveryPhrase = VaultCrypto.generateRecoveryPhrase()
        val recoverySalt = VaultCrypto.generateSalt()
        val recoveryKey = VaultCrypto.deriveKeyFromPhrase(
            recoveryPhrase,
            recoverySalt,
            KdfSpec.pbkdf2(legacyIterations),
        )
        prefs.saveEnvelopeCredentials(
            passwordHash = VaultCrypto.createKeyVerifier(passwordKey, "PASSWORD"),
            saltBase64 = Base64.encodeToString(passwordSalt, Base64.NO_WRAP),
            recoverySaltBase64 = Base64.encodeToString(recoverySalt, Base64.NO_WRAP),
            encryptedVmkByPassword = VaultCrypto.encrypt(vmkHex, passwordKey),
            encryptedVmkByRecovery = VaultCrypto.encrypt(vmkHex, recoveryKey),
            encryptedRecoveryPhrase = VaultCrypto.encrypt(recoveryPhrase, vmk),
            recoveryPhraseHash = VaultCrypto.createKeyVerifier(recoveryKey, "RECOVERY"),
            keyFingerprint = VaultCrypto.generateFingerprint(vmkHex),
            passwordKdf = KdfSpec.pbkdf2(legacyIterations),
            recoveryKdf = KdfSpec.pbkdf2(legacyIterations),
        )
    }

    @Test
    fun legacyVaultUnlocksAndIsUpgradedOnDevice() = runBlocking {
        val startedAt = System.currentTimeMillis()
        assertTrue(repository.unlock(password))
        val unlockMs = System.currentTimeMillis() - startedAt

        assertEquals("on-device unlock must migrate to Argon2id", KdfSpec.CURRENT, prefs.passwordKdf)
        assertTrue("migrated vault must still hold a usable VMK", repository.getActiveVmkHex()?.length == 64)
        assertTrue("PBKDF2 unlock + Argon2id migration must stay usable on device (took ${unlockMs}ms)", unlockMs < 20_000)

        repository.lock()
        assertTrue("password must open the migrated vault", repository.unlock(password))
        repository.lock()

        assertTrue("recovery phrase must open the migrated vault", repository.recoverWithPhrase(recoveryPhrase, "newpassword123"))
    }

    @Test
    fun envelopeRotatesAndFailedPasswordChangesNothing() = runBlocking {
        val beforeSalt = prefs.saltBase64
        val beforeWrapped = prefs.encryptedVmkByPassword
        val wrongStarted = System.currentTimeMillis()
        assertTrue(!repository.unlock("wrongpass1"))
        val wrongMs = System.currentTimeMillis() - wrongStarted
        assertEquals(KdfSpec.pbkdf2(legacyIterations), prefs.passwordKdf)
        assertEquals(beforeSalt, prefs.saltBase64)
        assertEquals(beforeWrapped, prefs.encryptedVmkByPassword)
        assertTrue("one rejected 100k derivation should be fast (${wrongMs}ms)", wrongMs < 10_000)

        assertTrue(repository.unlock(password))
        assertNotEquals(beforeSalt, prefs.saltBase64)
    }
}
