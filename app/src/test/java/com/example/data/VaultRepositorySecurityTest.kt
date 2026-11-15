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

    @Test
    fun `bulk delete removes exactly the selected records`() = runTest {
        repository.saveItem(login("bulk-a", "Alpha", "pw-a"))
        repository.saveItem(login("bulk-b", "Beta", "pw-b"))
        repository.saveItem(login("bulk-c", "Gamma", "pw-c"))

        repository.deleteItems(listOf("bulk-a", "bulk-c"))

        val remaining = db.vaultDao().getAllList().map { it.id }
        assertEquals(listOf("bulk-b"), remaining)
    }

    @Test
    fun `bulk delete ignores an empty selection`() = runTest {
        repository.saveItem(login("keep", "Keep", "pw"))
        repository.deleteItems(emptyList())
        assertEquals(1, db.vaultDao().getAllList().size)
    }

    // ---- authenticated backups ----

    @Test
    fun `tampered backup is rejected before anything is written`() = runTest {
        repository.saveItem(login("row-d", "Delta", "secret-delta"))
        val backup = JSONObject(repository.exportEncryptedBackup())
        val items = backup.getJSONArray("items")
        val item = items.getJSONObject(0)

        val payload = item.getString("encryptedPayload")
        val tampered = payload.dropLast(2) + if (payload.takeLast(2) == "A=") "B=" else "A="
        item.put("encryptedPayload", tampered)

        var rejected = false
        try {
            repository.importEncryptedBackup(backup.toString())
        } catch (e: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("tampered backup must fail verification", rejected)
        assertEquals("verification failure must not write anything", 1, db.vaultDao().getAllList().size)
        assertEquals("original record stays decryptable", "Delta", repository.getVaultItemsFlow().first().single().title)
    }

    @Test
    fun `backup round trip restores a deleted record`() = runTest {
        repository.saveItem(login("row-e", "Epsilon", "secret-epsilon"))
        val backup = repository.exportEncryptedBackup()
        repository.deleteItem("row-e")
        assertEquals(0, db.vaultDao().getAllList().size)

        val report = repository.importEncryptedBackup(backup)
        assertEquals("authentic backup row imports", 1, report.imported)
        assertEquals("no duplicates in a fresh import", 0, report.duplicatesSkipped)
        assertEquals(1, db.vaultDao().getAllList().size)
    }

    @Test
    fun `importing the same backup twice writes no duplicates`() = runTest {
        repository.saveItem(login("row-g", "Eta", "secret-eta"))
        val backup = repository.exportEncryptedBackup()
        // Simulate "this record is not in the vault yet" before re-importing it.
        repository.deleteItem("row-g")

        val first = repository.importEncryptedBackup(backup)
        assertEquals("first import restores the record", 1, first.imported)
        assertEquals(0, first.duplicatesSkipped)

        val second = repository.importEncryptedBackup(backup)
        assertEquals("second import must not write", 0, second.imported)
        assertEquals("second import is a duplicate", 1, second.duplicatesSkipped)
        assertEquals("vault still holds exactly one record", 1, db.vaultDao().getAllList().size)
    }

    @Test
    fun `same content under a different record id is treated as a duplicate`() = runTest {
        repository.saveItem(login("row-h", "Theta", "secret-theta"))
        val backup = repository.exportEncryptedBackup()
        repository.deleteItem("row-h")

        // Same title/type/secret re-created locally with a fresh record id: importing
        // the old backup must recognise it by content, not only by id.
        repository.saveItem(login("row-h-recreated", "Theta", "secret-theta"))
        val report = repository.importEncryptedBackup(backup)
        assertEquals("content duplicate must not be written", 0, report.imported)
        assertEquals(1, report.duplicatesSkipped)
        assertEquals(1, db.vaultDao().getAllList().size)
    }

    @Test
    fun `backup with repeated and new records imports only the new ones`() = runTest {
        repository.saveItem(login("row-l", "Mu", "secret-mu"))
        repository.saveItem(login("row-m", "Nu", "secret-nu"))
        val backup = repository.exportEncryptedBackup()

        // Nu is still present locally (a repeat); Mu was removed (new to the vault).
        repository.deleteItem("row-l")
        val report = repository.importEncryptedBackup(backup)
        assertEquals("only the missing record is new", 1, report.imported)
        assertEquals("unchanged existing record is a duplicate", 1, report.duplicatesSkipped)
        assertEquals(0, report.updated)
        assertEquals(2, db.vaultDao().getAllList().size)
        assertEquals(
            "both records are present exactly once",
            setOf("Mu", "Nu"),
            repository.getVaultItemsFlow().first().map { it.title }.toSet(),
        )
    }

    @Test
    fun `repeated record inside one backup file is imported once`() = runTest {
        repository.saveItem(login("row-n", "Xi", "secret-xi"))
        val backup = JSONObject(repository.exportEncryptedBackup())
        val items = backup.getJSONArray("items")
        items.put(items.getJSONObject(0))
        repository.deleteItem("row-n")

        val report = repository.importEncryptedBackup(backup.toString())
        assertEquals(1, report.imported)
        assertEquals("the twin entry in the same file is skipped", 1, report.duplicatesSkipped)
        assertEquals(1, db.vaultDao().getAllList().size)
    }

    @Test
    fun `same record id with changed content is updated instead of duplicated`() = runTest {
        repository.saveItem(login("row-o", "Omicron", "secret-old"))
        val backup = repository.exportEncryptedBackup()
        repository.saveItem(login("row-o", "Omicron", "secret-new"))

        val report = repository.importEncryptedBackup(backup)
        assertEquals("changed record is not new", 0, report.imported)
        assertEquals("changed record is updated in place", 1, report.updated)
        assertEquals(0, report.duplicatesSkipped)
        assertEquals("no second copy is created", 1, db.vaultDao().getAllList().size)

        val restored = repository.getVaultItemsFlow().first().single().payload as LoginPayload
        assertEquals("backup content replaces local content for that record", "secret-old", restored.password)
    }

    @Test
    fun `one bad record rejects the whole backup with no partial import`() = runTest {
        repository.saveItem(login("row-i", "Iota", "secret-iota"))
        repository.saveItem(login("row-j", "Kappa", "secret-kappa"))
        val backup = JSONObject(repository.exportEncryptedBackup())
        val items = backup.getJSONArray("items")
        // Corrupt the second record's authentication tag.
        val second = items.getJSONObject(1)
        val payload = second.getString("encryptedPayload")
        second.put("encryptedPayload", payload.dropLast(2) + if (payload.takeLast(2) == "A=") "B=" else "A=")

        var rejected = false
        try {
            repository.importEncryptedBackup(backup.toString())
        } catch (e: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("a partially invalid backup must be rejected", rejected)
        assertEquals("no partial import may be written", 2, db.vaultDao().getAllList().size)
    }

    @Test
    fun `unknown envelope format is rejected without writing`() = runTest {
        repository.saveItem(login("row-k", "Lambda", "secret-lambda"))
        val backup = JSONObject(repository.exportEncryptedBackup())
        backup.put("format", "NotVaultZero")
        var rejected = false
        try {
            repository.importEncryptedBackup(backup.toString())
        } catch (e: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
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
