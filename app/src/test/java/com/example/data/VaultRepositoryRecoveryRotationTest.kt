package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.model.LoginPayload
import com.example.model.VaultItem
import com.example.model.VaultType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Recovery-kit rotation must replace only the recovery envelope: the VMK, records, fingerprint
 * and master password survive; the old phrase retires for this vault; old backups still open with
 * their embedded old phrase, and new backups open with the new phrase.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultRepositoryRecoveryRotationTest {

    private lateinit var context: Context
    private lateinit var prefs: VaultPreferences
    private lateinit var db: VaultDatabase
    private lateinit var repository: VaultRepository

    private val password = "rotation-pass-123"

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        prefs = VaultPreferences(context, VaultPreferences.TEST_PREFS_NAME)
        prefs.resetAll()
        db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = VaultRepository(db.vaultDao(), prefs)
        repository.setupMasterPassword(password)
        repository.saveItem(
            VaultItem(
                id = "rotation-record",
                title = "Rotation Record",
                type = VaultType.LOGIN,
                payload = LoginPayload(username = "user@example.com", password = "s3cret-Value!"),
            )
        )
    }

    @After
    fun tearDown() {
        runCatching { db.close() }
        prefs.resetAll()
    }

    private suspend fun newPhone(): Pair<VaultDatabase, VaultRepository> {
        repository.lock()
        runCatching { db.close() }
        prefs.resetAll()
        val newDb = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return newDb to VaultRepository(newDb.vaultDao(), prefs)
    }

    private suspend fun expectIllegal(block: suspend () -> Unit): String {
        try { block(); fail("expected IllegalArgumentException") } catch (e: IllegalArgumentException) {
            return e.message.orEmpty()
        }
        error("unreachable")
    }

    @Test
    fun `rotation keeps vmK records fingerprint and password but retires the old phrase`() = runTest {
        val oldPhrase = requireNotNull(repository.getDecryptedRecoveryPhrase().ifEmpty { null })
        val fingerprint = repository.keyFingerprint

        val newPhrase = repository.regenerateRecoveryKit()

        assertNotEquals(oldPhrase, newPhrase)
        assertEquals(12, newPhrase.split(" ").size)
        assertTrue(VaultCryptoBridge.validate(newPhrase))
        assertEquals(fingerprint, repository.keyFingerprint)
        assertEquals(newPhrase, repository.getDecryptedRecoveryPhrase())
        assertEquals(1, repository.getVaultItemsFlow().first().size)

        repository.lock()
        // Old phrase no longer recovers this vault; new phrase does.
        assertFalse(repository.recoverWithPhrase(oldPhrase, "recovered-pass-123"))
        assertTrue(repository.recoverWithPhrase(newPhrase, "recovered-pass-123"))
    }

    @Test
    fun `rotation requires an unlocked vault`() = runTest {
        repository.lock()
        val error = try {
            repository.regenerateRecoveryKit()
            fail("expected IllegalStateException")
            ""
        } catch (e: IllegalStateException) {
            e.message.orEmpty()
        }
        assertTrue(error.contains("locked", ignoreCase = true))
    }

    @Test
    fun `old backups still restore with their old phrase and new backups use the new phrase`() = runTest {
        val oldPhrase = requireNotNull(repository.getDecryptedRecoveryPhrase().ifEmpty { null })
        val oldBackup = repository.exportEncryptedBackup(BackupProtection.STANDARD)
        val newPhrase = repository.regenerateRecoveryKit()
        val newBackup = repository.exportEncryptedBackup(BackupProtection.STANDARD)

        suspend fun freshPhone(block: suspend (VaultRepository) -> Unit) {
            val (phoneDb, phone) = newPhone()
            try {
                block(phone)
            } finally {
                phoneDb.close()
            }
        }

        // Old backup: old phrase works, new phrase fails.
        freshPhone { phone ->
            phone.restoreBackupAtSetup(oldBackup, oldPhrase)
            assertEquals(1, phone.getVaultItemsFlow().first().size)
        }
        freshPhone { phone ->
            val error = expectIllegal { phone.restoreBackupAtSetup(oldBackup, newPhrase) }
            assertTrue(error.contains("phrase", ignoreCase = true))
            assertFalse(phone.isInitialized)
        }

        // New backup: new phrase works, old phrase fails.
        freshPhone { phone ->
            phone.restoreBackupAtSetup(newBackup, newPhrase)
            assertEquals(1, phone.getVaultItemsFlow().first().size)
        }
        freshPhone { phone ->
            expectIllegal { phone.restoreBackupAtSetup(newBackup, oldPhrase) }
            assertFalse(phone.isInitialized)
        }
    }

    /** Small bridge so this test does not duplicate crypto imports beyond validation. */
    private object VaultCryptoBridge {
        fun validate(phrase: String) = com.example.crypto.VaultCrypto.validateRecoveryPhrase(phrase)
    }
}
