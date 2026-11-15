package com.example.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.crypto.VaultCrypto
import com.example.model.ApiSecretPayload
import com.example.model.CardPayload
import com.example.model.CryptoPayload
import com.example.model.IdentityPayload
import com.example.model.LoginPayload
import com.example.model.NotePayload
import com.example.model.VaultItem
import com.example.model.VaultType
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
        // Seeding is idempotent: drop whatever a previous run left behind.
        db.vaultDao().clearAll()
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

        // Rich demo data so every screen/type and the audit engine can be
        // exercised from the UI, including weak/reused/stale password findings.
        val now = System.currentTimeMillis()
        val old = now - 120L * 24 * 60 * 60 * 1000
        val items = listOf(
            VaultItem(
                id = "seed-login-github", title = "GitHub", type = VaultType.LOGIN,
                isFavorite = true, createdAt = now, updatedAt = now,
                payload = LoginPayload(
                    username = "demo@example.com",
                    password = "Vx9#mQ2!pL7\$nR4",
                    url = "https://github.com",
                    totpSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",
                ),
            ),
            VaultItem(
                id = "seed-login-netflix", title = "Netflix", type = VaultType.LOGIN,
                createdAt = old, updatedAt = old,
                payload = LoginPayload(
                    username = "demo@example.com", password = "password123",
                    url = "https://netflix.com",
                ),
            ),
            VaultItem(
                id = "seed-login-bank", title = "Bank Portal", type = VaultType.LOGIN,
                createdAt = now, updatedAt = now,
                payload = LoginPayload(
                    username = "demo@example.com", password = "password123",
                    url = "https://bank.example", totpSecret = "NOT-A-VALID-SECRET",
                ),
            ),
            VaultItem(
                id = "seed-card-chase", title = "Chase Sapphire", type = VaultType.CARD,
                createdAt = now, updatedAt = now,
                payload = CardPayload(
                    cardholderName = "CIPHERWELL", cardNumber = "4111111111111111",
                    expMonth = "12", expYear = "2029", cvv = "123", brand = "Visa",
                ),
            ),
            VaultItem(
                id = "seed-note-wifi", title = "Wi-Fi & Router", type = VaultType.NOTE,
                createdAt = now, updatedAt = now,
                payload = NotePayload(content = "SSID: VaultNet\nRouter admin: 192.168.1.1\nPSK: 5up3r-S3cret-WiFi!"),
            ),
            VaultItem(
                id = "seed-api-stripe", title = "Stripe Production", type = VaultType.API_SECRET,
                createdAt = now, updatedAt = now,
                payload = ApiSecretPayload(
                    serviceName = "Stripe", apiKey = "pk_live_51Demo0000000000",
                    secretToken = "sk_live_51Demo0000000000Secret", environment = "Production",
                    scopes = "charges:read, payouts:write", endpointUrl = "https://api.stripe.com",
                ),
            ),
            VaultItem(
                id = "seed-wallet-ledger", title = "Ledger Backup", type = VaultType.CRYPTO_WALLET,
                createdAt = now, updatedAt = now,
                payload = CryptoPayload(
                    walletName = "Ledger Nano X", network = "Bitcoin",
                    publicAddress = "bc1qdemo0address0for0ui0only0not0real",
                    seedPhrase = "abandon ability able about above absent absorb abstract absurd abuse access accident",
                ),
            ),
            VaultItem(
                id = "seed-identity-passport", title = "Passport", type = VaultType.IDENTITY,
                createdAt = now, updatedAt = now,
                payload = IdentityPayload(
                    fullName = "Cipherwell", idType = "Passport",
                    idNumber = "P123456789", birthDate = "1990-01-01", expiryDate = "2031-06-30",
                ),
            ),
        )
        items.forEach { repository.saveItem(it) }
        assertEquals(8, db.vaultDao().getAllList().size)
    }
}
