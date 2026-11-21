package com.example.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.crypto.KdfSpec
import com.example.crypto.VaultCrypto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dev/QA utility: creates a clean vault (master password `test1234`, no demo
 * items) on the target device so UI and biometric flows can be exercised. Used
 * for the physical-device biometric verification; leaves a working vault behind.
 */
@RunWith(AndroidJUnit4::class)
class VaultSetupOnlyDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = VaultPreferences(context)

    @After
    fun closeDb() = Unit

    @Test
    fun createCleanVaultOnDevice() = runBlocking {
        prefs.resetAll()
        val db = VaultDatabase.getInstance(context)
        db.vaultDao().clearAll()
        val repository = VaultRepository(db.vaultDao(), prefs)

        val (phrase, vmkHex) = repository.setupMasterPassword("test1234")
        assertTrue(prefs.isInitialized)
        assertEquals(KdfSpec.CURRENT, prefs.passwordKdf)
        assertTrue(VaultCrypto.validateRecoveryPhrase(phrase))
        assertEquals(64, vmkHex.length)
        repository.lock()
        assertTrue(repository.unlock("test1234"))
    }
}
