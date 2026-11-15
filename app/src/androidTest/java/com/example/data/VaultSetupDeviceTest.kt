package com.example.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.crypto.VaultCrypto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device end-to-end check of vault creation against the REAL app prefs.
 *
 * Runs on the VaultZero-Live emulator and deliberately leaves a working vault
 * behind (password `test1234`) so the UI can be inspected afterwards. This is
 * the equivalent of a human completing the Initialize screen, executed through
 * the same repository code the UI calls.
 *
 * Note: `./gradlew connectedDebugAndroidTest` uninstalls the app when it
 * finishes, which also deletes this vault. Run it with
 * `adb shell am instrument` (see BUILDING.md) when the device state matters.
 */
@RunWith(AndroidJUnit4::class)
class VaultSetupDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = VaultPreferences(context) // production prefs on purpose
    private lateinit var db: VaultDatabase

    @After
    fun tearDown() {
        // Do not close the Room singleton: it outlives this test by design.
    }

    @Test
    fun setupProducesAnUnlockableVaultAtTheCurrentWorkFactor() = runBlocking {
        prefs.resetAll()
        // Persistent DB on purpose: this test seeds the device for UI/backup
        // verification, so the demo entry must survive the test process.
        db = VaultDatabase.getInstance(context)
        val repository = VaultRepository(db.vaultDao(), prefs)

        val (recoveryPhrase, vmkHex) = repository.setupMasterPassword("test1234")

        assertTrue("vault must be initialized", prefs.isInitialized)
        assertEquals("new vaults use the 600k work factor", 600_000, prefs.kdfIterations)
        assertTrue(VaultCrypto.validateRecoveryPhrase(recoveryPhrase))
        assertEquals(64, vmkHex.length)
        assertEquals("a wrapped VMK must be persisted", 1, prefs.encryptedVmkByPassword.isNotEmpty().compareTo(false))

        repository.lock()
        assertFalse("wrong password must be rejected", repository.unlock("wrongpass1"))
        assertTrue("the setup password must unlock the vault", repository.unlock("test1234"))
        assertEquals("unlock must recover the same VMK", vmkHex, repository.getActiveVmkHex())

        // One known entry so backup export/import can be exercised from the UI.
        repository.saveItem(
            com.example.model.VaultItem(
                id = "demo-entry-1",
                title = "Demo Login",
                type = com.example.model.VaultType.LOGIN,
                payload = com.example.model.LoginPayload(
                    username = "demo@example.com",
                    password = "super-secret-value-42",
                ),
            )
        )
        assertEquals(1, db.vaultDao().getAllList().size)
    }
}
