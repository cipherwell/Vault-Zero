package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.util.Base64
import com.example.crypto.KdfSpec
import com.example.crypto.VaultCrypto
import javax.crypto.spec.SecretKeySpec
import com.example.model.LoginPayload
import com.example.model.VaultItem
import com.example.model.VaultType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * First-run phone-to-phone migration: a fresh install restores a complete vault envelope
 * using the existing recovery phrase, keeps that phrase, and never creates a second kit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultRepositorySetupRestoreTest {

    private lateinit var context: Context
    private lateinit var prefs: VaultPreferences
    private lateinit var sourceDb: VaultDatabase
    private lateinit var sourceRepo: VaultRepository

    private lateinit var recoveryPhrase: String
    private lateinit var fingerprint: String
    private lateinit var standardBackup: String
    private lateinit var phraseOnlyBackup: String

    private val originalPassword = "original-pass-123"
    private val newPassword = "migrated-pass-456"

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        prefs = VaultPreferences(context, VaultPreferences.TEST_PREFS_NAME)
        prefs.resetAll()
        sourceDb = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sourceRepo = VaultRepository(sourceDb.vaultDao(), prefs)

        recoveryPhrase = sourceRepo.setupMasterPassword(originalPassword).first
        fingerprint = sourceRepo.keyFingerprint

        sourceRepo.saveItem(login("record-a", "Alpha Login", "Alpha-Secret-1!"))
        sourceRepo.saveItem(
            login("record-b", "Beta Login", "Beta-Secret-2!").copy(
                isFavorite = true,
                createdAt = 1_700_000_000_000L,
                updatedAt = 1_700_000_100_000L,
                lastUsedAt = 1_700_000_200_000L,
            )
        )
        standardBackup = sourceRepo.exportEncryptedBackup(BackupProtection.STANDARD)
        phraseOnlyBackup = sourceRepo.exportEncryptedBackup(BackupProtection.RECOVERY_PHRASE_ONLY)
    }

    @After
    fun tearDown() {
        runCatching { sourceDb.close() }
        prefs.resetAll()
    }

    @Test
    fun `setup keeps the kit pending until the user confirms it`() = runTest {
        assertTrue("a fresh setup must be marked pending", sourceRepo.isSetupKitPending)
        assertEquals(
            "the pending kit must be decryptable with the active VMK",
            recoveryPhrase,
            sourceRepo.getDecryptedRecoveryPhrase(),
        )
        sourceRepo.markSetupKitConfirmed()
        assertFalse("confirming the kit clears the pending flag", sourceRepo.isSetupKitPending)
    }

    private fun login(id: String, title: String, secret: String) = VaultItem(
        id = id,
        title = title,
        type = VaultType.LOGIN,
        payload = LoginPayload(username = "user@example.com", password = secret),
    )

    /** Simulates a new phone: source DB/prefs are discarded and an empty DB/repo is returned. */
    private fun newPhone(): Pair<VaultDatabase, VaultRepository> {
        sourceRepo.lock()
        runCatching { sourceDb.close() }
        prefs.resetAll()
        val db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return db to VaultRepository(db.vaultDao(), prefs)
    }

    private suspend fun expectIllegal(block: suspend () -> Unit): String {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            return e.message.orEmpty()
        }
        error("unreachable")
    }


    /**
     * Build a genuine 3.2-era backup: both envelopes derived with PBKDF2 (600k), no
     * per-envelope spec fields, only the shared `kdfIterations`. The source repo stays
     * unlocked so the records (wrapped by the unchanged VMK) can be exported as-is.
     */
    private suspend fun buildLegacyPbkdf2Backup(): String {
        val vmkHex = sourceRepo.getActiveVmkHex()
            ?: error("source vault must be unlocked to build the legacy backup")
        val vmk = SecretKeySpec(VaultCrypto.hexToBytes(vmkHex), "AES")
        val legacy = KdfSpec.pbkdf2(600_000)

        val passwordSalt = VaultCrypto.generateSalt()
        val passwordKey = VaultCrypto.deriveKey(originalPassword, passwordSalt, legacy)
        val recoverySalt = VaultCrypto.generateSalt()
        val recoveryKey = VaultCrypto.deriveKeyFromPhrase(recoveryPhrase, recoverySalt, legacy)

        prefs.saveEnvelopeCredentials(
            passwordHash = VaultCrypto.createKeyVerifier(passwordKey, "PASSWORD"),
            saltBase64 = Base64.encodeToString(passwordSalt, Base64.NO_WRAP),
            recoverySaltBase64 = Base64.encodeToString(recoverySalt, Base64.NO_WRAP),
            encryptedVmkByPassword = VaultCrypto.encrypt(vmkHex, passwordKey),
            encryptedVmkByRecovery = VaultCrypto.encrypt(vmkHex, recoveryKey),
            encryptedRecoveryPhrase = VaultCrypto.encrypt(recoveryPhrase, vmk),
            recoveryPhraseHash = VaultCrypto.createKeyVerifier(recoveryKey, "RECOVERY"),
            keyFingerprint = prefs.keyFingerprint,
            passwordKdf = legacy,
            recoveryKdf = legacy,
        )

        val json = JSONObject(sourceRepo.exportEncryptedBackup(BackupProtection.STANDARD))
        json.remove("kdfPasswordSpec")
        json.remove("kdfRecoverySpec")
        json.put("kdfIterations", 600_000)
        json.put("version", "3.2")
        return json.toString()
    }

    @Test
    fun `standard backup restores records and keeps the original password and seed phrase`() = runTest {
        val (db, repo) = newPhone()
        try {
            val report = repo.restoreBackupAtSetup(standardBackup, recoveryPhrase)

            assertEquals(2, report.itemCount)
            assertTrue(report.keptOriginalPassword)
            assertFalse(report.phraseOnlyBackup)
            assertTrue(repo.isInitialized)
            assertTrue(repo.isUnlocked)
            assertEquals(fingerprint, repo.keyFingerprint)
            assertEquals(recoveryPhrase, repo.getDecryptedRecoveryPhrase())

            val restored = repo.getVaultItemsFlow().first()
            assertEquals(2, restored.size)
            val beta = restored.single { it.id == "record-b" }
            assertEquals("Beta Login", beta.title)
            assertTrue(beta.isFavorite)
            assertEquals(1_700_000_000_000L, beta.createdAt)
            assertEquals(1_700_000_100_000L, beta.updatedAt)
            assertEquals(1_700_000_200_000L, beta.lastUsedAt)

            repo.lock()
            assertTrue("the original master password must still unlock", repo.unlock(originalPassword))
        } finally {
            db.close()
        }
    }

    @Test
    fun `standard backup can rotate to a new password while keeping the seed phrase`() = runTest {
        val (db, repo) = newPhone()
        try {
            val report = repo.restoreBackupAtSetup(
                jsonString = standardBackup,
                recoveryPhrase = recoveryPhrase,
                newMasterPassword = newPassword,
            )
            assertFalse(report.keptOriginalPassword)
            assertEquals(fingerprint, repo.keyFingerprint)
            assertEquals(recoveryPhrase, repo.getDecryptedRecoveryPhrase())

            repo.lock()
            assertFalse("old password must be replaced", repo.unlock(originalPassword))
            assertTrue("new password must unlock", repo.unlock(newPassword))
        } finally {
            db.close()
        }
    }


    @Test
    fun `3_2 PBKDF2 backup restores then migrates to Argon2id on the first unlock`() = runTest {
        val legacyBackup = buildLegacyPbkdf2Backup()
        val (db, repo) = newPhone()
        try {
            val report = repo.restoreBackupAtSetup(legacyBackup, recoveryPhrase)
            assertEquals(2, report.itemCount)
            assertTrue("reusing the backup password keeps the backup's envelope", report.keptOriginalPassword)
            assertEquals(
                "a 3.2 backup declares no specs, so the restored envelope is PBKDF2",
                KdfSpec.pbkdf2(600_000),
                prefs.passwordKdf,
            )

            repo.lock()
            assertTrue("the legacy backup password must open the restored vault", repo.unlock(originalPassword))
            assertEquals("unlock must migrate the password envelope", KdfSpec.CURRENT, prefs.passwordKdf)
            assertEquals("unlock must migrate the recovery envelope", KdfSpec.CURRENT, prefs.recoveryKdf)
            assertEquals(2, repo.getVaultItemsFlow().first().size)
            assertEquals(recoveryPhrase, repo.getDecryptedRecoveryPhrase())
        } finally {
            db.close()
        }
    }

    @Test
    fun `phrase-only backup requires a new password and keeps the seed phrase`() = runTest {
        val (db, repo) = newPhone()
        try {
            val error = expectIllegal {
                repo.restoreBackupAtSetup(phraseOnlyBackup, recoveryPhrase)
            }
            assertTrue(
                "must explain that a phrase-only backup needs a new password: $error",
                error.contains("phrase-only", ignoreCase = true) &&
                    error.contains("master password", ignoreCase = true),
            )
            assertFalse(repo.isInitialized)

            val report = repo.restoreBackupAtSetup(
                jsonString = phraseOnlyBackup,
                recoveryPhrase = recoveryPhrase,
                newMasterPassword = newPassword,
            )
            assertTrue(report.phraseOnlyBackup)
            assertEquals(2, report.itemCount)
            assertEquals(recoveryPhrase, repo.getDecryptedRecoveryPhrase())

            repo.lock()
            assertTrue(repo.unlock(newPassword))
            assertFalse(repo.unlock(originalPassword))
        } finally {
            db.close()
        }
    }

    @Test
    fun `wrong recovery phrase is rejected without initializing the vault`() = runTest {
        val wrongPhrase = VaultCrypto.generateRecoveryPhrase()
        val (db, repo) = newPhone()
        try {
            val error = expectIllegal { repo.restoreBackupAtSetup(standardBackup, wrongPhrase) }
            assertTrue(
                "error should mention the phrase not matching the backup: $error",
                error.contains("phrase", ignoreCase = true),
            )
            assertFalse(repo.isInitialized)
            assertTrue(db.vaultDao().getAllList().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `tampered record in the backup is rejected before any write`() = runTest {
        val tampered = JSONObject(standardBackup)
        val items = tampered.getJSONArray("items")
        val first = items.getJSONObject(0)
        val payload = first.getString("encryptedPayload")
        val replacement = if (payload.first() != 'A') "A" + payload.drop(1) else "B" + payload.drop(1)
        first.put("encryptedPayload", replacement)
        items.put(0, first)
        tampered.put("items", items)

        val (db, repo) = newPhone()
        try {
            val error = expectIllegal {
                repo.restoreBackupAtSetup(tampered.toString(), recoveryPhrase)
            }
            assertTrue(
                "tamper must fail authentication: $error",
                error.contains("authentication", ignoreCase = true),
            )
            assertFalse(repo.isInitialized)
            assertTrue(db.vaultDao().getAllList().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `restore never overwrites an existing vault`() = runTest {
        val (db, repo) = newPhone()
        try {
            repo.setupMasterPassword("existing-pass-789")
            val error = expectIllegal {
                repo.restoreBackupAtSetup(standardBackup, recoveryPhrase)
            }
            assertTrue(
                "must refuse to overwrite an initialized vault: $error",
                error.contains("already exists", ignoreCase = true),
            )
            assertTrue(repo.isInitialized)
        } finally {
            db.close()
        }
    }
}
