package com.example.data

import androidx.room.Room
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
    fun closeDb() {
        if (::db.isInitialized) db.close()
    }

    @Test
    fun setupProducesAnUnlockableVaultAtTheCurrentWorkFactor() = runBlocking {
        prefs.resetAll()
        db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
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
    }
}
