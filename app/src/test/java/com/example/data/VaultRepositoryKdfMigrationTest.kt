package com.example.data

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.KdfSpec
import com.example.crypto.VaultCrypto
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * KDF migration: a vault created by the previous release (100k PBKDF2
 * iterations) must open, then silently re-wrap its key envelopes at the current
 * work factor - without re-keying the VMK, without touching encrypted records,
 * and without weakening any failure path.
 *
 * The envelope is the real on-disk shape: VMK wrapped by a password KEK and a
 * recovery KEK, both PBKDF2-HMAC-SHA256, iteration count stored in prefs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.16 has no android-all jar for SDK 36
class VaultRepositoryKdfMigrationTest {

    private lateinit var db: VaultDatabase
    private lateinit var prefs: VaultPreferences
    private lateinit var repository: VaultRepository

    private val legacyIterations = 100_000
    private val password = "test1234"
    private lateinit var recoveryPhrase: String
    private lateinit var vmkHex: String

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        prefs = VaultPreferences(context)
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

    /** Build the envelope exactly as the 100k-iteration release did. */
    private fun createVaultAtLegacyWorkFactor() {
        val vmk = VaultCrypto.generateRandomKey()
        vmkHex = VaultCrypto.bytesToHex(vmk.encoded)

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
    fun `unlock upgrades a legacy envelope to the current work factor`() = runTest {
        val oldSalt = prefs.saltBase64
        val oldRecoverySalt = prefs.recoverySaltBase64
        val oldWrapped = prefs.encryptedVmkByPassword

        assertTrue("legacy vault must still unlock", repository.unlock(password))

        assertEquals("unlock must migrate the password envelope to Argon2id", KdfSpec.CURRENT, prefs.passwordKdf)
        assertEquals("unlock must migrate the recovery envelope too", KdfSpec.CURRENT, prefs.recoveryKdf)
        assertNotEquals("password salt must rotate", oldSalt, prefs.saltBase64)
        assertNotEquals("recovery salt must rotate", oldRecoverySalt, prefs.recoverySaltBase64)
        assertNotEquals("VMK must be re-wrapped under the new KEK", oldWrapped, prefs.encryptedVmkByPassword)
        assertEquals("the VMK itself must not change", vmkHex, repository.getActiveVmkHex())
    }

    @Test
    fun `migrated vault unlocks again with the same password`() = runTest {
        assertTrue(repository.unlock(password))
        assertEquals(KdfSpec.CURRENT, prefs.passwordKdf)

        repository.lock()
        assertTrue("password must still unlock after re-wrap", repository.unlock(password))
        assertEquals(vmkHex, repository.getActiveVmkHex())
    }

    @Test
    fun `recovery phrase still restores the same vmk after migration`() = runTest {
        assertTrue(repository.unlock(password))
        assertEquals(KdfSpec.CURRENT, prefs.passwordKdf)
        repository.lock()

        assertTrue(
            "recovery envelope must be re-derived at the new work factor",
            repository.recoverWithPhrase(recoveryPhrase, "newpassword123"),
        )
        assertEquals(vmkHex, repository.getActiveVmkHex())
    }

    @Test
    fun `wrong password neither unlocks nor rewrites the envelope`() = runTest {
        val oldSalt = prefs.saltBase64
        val oldWrapped = prefs.encryptedVmkByPassword

        assertFalse(repository.unlock("wrongpass1"))

        assertEquals(
            "a failed unlock must not strengthen or rotate anything",
            KdfSpec.pbkdf2(legacyIterations),
            prefs.passwordKdf,
        )
        assertEquals(oldSalt, prefs.saltBase64)
        assertEquals(oldWrapped, prefs.encryptedVmkByPassword)
    }

    @Test
    fun `upgrade is skipped and the vault still opens when the recovery copy is missing`() = runTest {
        prefs.encryptedRecoveryPhrase = ""
        val oldSalt = prefs.saltBase64

        assertTrue("password unlock must not depend on the recovery copy", repository.unlock(password))

        assertEquals(
            "without a readable recovery phrase the legacy KDF must stay untouched",
            KdfSpec.pbkdf2(legacyIterations),
            prefs.passwordKdf,
        )
        assertEquals(
            "the recovery spec must not drift away from the password spec",
            KdfSpec.pbkdf2(legacyIterations),
            prefs.recoveryKdf,
        )
        assertEquals(oldSalt, prefs.saltBase64)
        assertEquals(vmkHex, repository.getActiveVmkHex())
    }
}
