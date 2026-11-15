package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.model.LoginPayload
import com.example.model.NotePayload
import com.example.model.VaultItem
import com.example.model.VaultType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
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
 * Security-critical repository behaviour that must never regress:
 * rate limiting, AAD row binding (SQLite row swap must not decrypt), and
 * authenticated backups (tampering must not import).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.16 has no android-all jar for SDK 36
class VaultRepositorySecurityTest {

    private lateinit var db: VaultDatabase
    private lateinit var prefs: VaultPreferences
    private lateinit var repository: VaultRepository
    private lateinit var recoveryPhrase: String

    private val password = "test1234"

    @Before
    fun setUp() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        prefs = VaultPreferences(context, VaultPreferences.TEST_PREFS_NAME)
        prefs.resetAll()
        db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = VaultRepository(db.vaultDao(), prefs)
        recoveryPhrase = repository.setupMasterPassword(password).first
    }

    @After
    fun tearDown() {
        db.close()
        prefs.resetAll()
    }

    private fun login(id: String, title: String, secret: String) = VaultItem(
        id = id,
        title = title,
        type = VaultType.LOGIN,
        payload = LoginPayload(username = "user@example.com", password = secret),
    )

    // ---- rate limiting ----

    @Test
    fun `five failed unlocks lock out even a correct password until the cooldown passes`() = runTest {
        val t0 = 1_000_000L
        repeat(5) { attempt ->
            assertFalse("wrong guess $attempt must fail", repository.unlock("wrongpass$attempt", nowElapsedRealtime = t0))
        }
        assertTrue("cooldown must be active", repository.getRemainingLockoutSeconds(t0) > 0)
        assertFalse(
            "a correct password must not bypass the cooldown",
            repository.unlock(password, nowElapsedRealtime = t0),
        )
        assertTrue(
            "after the cooldown the correct password unlocks",
            repository.unlock(password, nowElapsedRealtime = t0 + VaultRepository.COOLDOWN_DURATION_MS + 1_000),
        )
    }

    @Test
    fun `saveItem preserves caller-provided timestamps (restore and audit paths)`() = runTest {
        val old = System.currentTimeMillis() - 200L * 24 * 60 * 60 * 1000
        repository.saveItem(
            login("row-old", "Old Record", "Strong-unique-Passw0rd!")
                .copy(createdAt = old, updatedAt = old),
        )
        val entity = db.vaultDao().getAllList().single { it.id == "row-old" }
        assertEquals("createdAt must be preserved", old, entity.createdAt)
        assertEquals("updatedAt drives the stale audit and must be preserved", old, entity.updatedAt)
    }

    // ---- AAD row binding ----

    @Test
    fun `swapping ciphertext between rows is rejected by the AAD binding`() = runTest {
        repository.saveItem(login("row-a", "Alpha", "secret-alpha"))
        repository.saveItem(login("row-b", "Beta", "secret-beta"))

        val entities = db.vaultDao().getAllList()
        assertEquals(2, entities.size)
        val a = entities.first { it.id == "row-a" }
        val b = entities.first { it.id == "row-b" }
        assertNotEquals(a.encryptedPayload, b.encryptedPayload)

        // Attacker swaps the opaque payloads between two rows in SQLite.
        db.vaultDao().insertOrUpdate(a.copy(encryptedPayload = b.encryptedPayload))
        db.vaultDao().insertOrUpdate(b.copy(encryptedPayload = a.encryptedPayload))

        val decrypted = repository.getVaultItemsFlow().first()
        assertTrue(
            "both swapped records must be dropped, not shown under the wrong identity",
            decrypted.isEmpty(),
        )
    }

    @Test
    fun `tampering with a stored payload is rejected`() = runTest {
        repository.saveItem(login("row-c", "Gamma", "secret-gamma"))
        val entity = db.vaultDao().getAllList().single()
        val payload = entity.encryptedPayload
        val tampered = payload.dropLast(1) + if (payload.last() == 'A') 'B' else 'A'
        db.vaultDao().insertOrUpdate(entity.copy(encryptedPayload = tampered))

        assertTrue("a record whose GCM tag fails must not decrypt", repository.getVaultItemsFlow().first().isEmpty())
    }

    // ---- authenticated backups ----

    @Test
    fun `tampered backup does not import`() = runTest {
        repository.saveItem(login("row-d", "Delta", "secret-delta"))
        val backup = JSONObject(repository.exportEncryptedBackup())
        val items = backup.getJSONArray("items")
        val item = items.getJSONObject(0)

        val payload = item.getString("encryptedPayload")
        val tampered = payload.dropLast(2) + if (payload.takeLast(2) == "A=") "B=" else "A="
        item.put("encryptedPayload", tampered)

        // The backup is still decryptable with the active key for untampered rows;
        // the tampered row must be skipped instead of imported with bad data.
        val imported = repository.importEncryptedBackup(backup.toString())
        assertEquals("tampered record must be skipped", 0, imported)
        assertEquals("original record is untouched", 1, db.vaultDao().getAllList().size)
    }

    @Test
    fun `backup round trip restores a deleted record`() = runTest {
        repository.saveItem(login("row-e", "Epsilon", "secret-epsilon"))
        val backup = repository.exportEncryptedBackup()
        repository.deleteItem("row-e")
        assertEquals(0, db.vaultDao().getAllList().size)

        assertEquals("authentic backup row imports", 1, repository.importEncryptedBackup(backup))
        assertEquals(1, db.vaultDao().getAllList().size)
    }

    @Test
    fun `exported backup envelope leaks no plaintext secrets or metadata`() = runTest {
        repository.saveItem(login("row-f", "Zeta Bank", "super-secret-value-42"))
        val backup = repository.exportEncryptedBackup()

        assertFalse("secret payload must never appear in the clear", backup.contains("super-secret-value-42"))
        assertFalse("record titles are redacted", backup.contains("Zeta Bank"))
        assertTrue("envelope format field", backup.contains("VaultZero_Encrypted_Envelope"))
        assertTrue("kdf metadata travels with the envelope", backup.contains("\"kdfIterations\": 600000"))
    }

    // ---- recovery ----

    @Test
    fun `wrong recovery phrase cannot open the vault but the real one can`() = runTest {
        val words = recoveryPhrase.split(" ").toMutableList()
        // Flip one word - the 12th-word SHA-256 checksum must catch it.
        words[0] = if (words[0] == "abandon") "ability" else "abandon"
        val forged = words.joinToString(" ")

        repository.lock()
        assertFalse(repository.recoverWithPhrase(forged, "newpassword123"))

        assertTrue(repository.recoverWithPhrase(recoveryPhrase, "newpassword123"))
        assertEquals("recovered VMK must be the original one", 64, repository.getActiveVmkHex()?.length)
    }
}
