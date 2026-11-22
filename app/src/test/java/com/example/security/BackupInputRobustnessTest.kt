package com.example.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.BackupJson
import com.example.data.VaultDatabase
import com.example.data.VaultError
import com.example.data.VaultException
import com.example.data.VaultPreferences
import com.example.data.VaultRepository
import kotlinx.coroutines.test.runTest
import com.example.ui.screens.BackupFileIo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * Regression tests for the backup import attack surface:
 *  - a document that streams past the size ceiling is rejected without buffering it all,
 *  - deeply nested JSON is rejected by the depth guard instead of throwing StackOverflowError
 *    (an Error that `catch (Exception)` would not stop).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupInputRobustnessTest {

    @Test
    fun `bounded read rejects oversized streams without consuming them all`() {
        val oversize = ByteArrayInputStream(ByteArray(4096) { 'a'.code.toByte() })
        assertNull(BackupFileIo.readBounded(oversize, maxBytes = 1024))
    }

    @Test
    fun `bounded read returns content at or below the limit`() {
        val payload = "{\"format\":\"x\"}".toByteArray()
        val bytes = BackupFileIo.readBounded(ByteArrayInputStream(payload), maxBytes = payload.size.toLong())
        assertNotNull(bytes)
        assertEquals(String(payload), String(bytes!!))
    }

    @Test
    fun `depth guard rejects 20k nesting without a StackOverflowError`() {
        val deep = "{\"x\":" + "[".repeat(20_000) + "]".repeat(20_000) + "}"
        val error = runCatching { BackupJson.parse(deep) }.exceptionOrNull()
        assertTrue("expected VaultException, got $error", error is VaultException)
        assertEquals(VaultError.INVALID_JSON, (error as VaultException).error)
    }

    @Test
    fun `depth guard accepts a normal envelope`() {
        val parsed: JSONObject = BackupJson.parse("{\"format\":\"VaultZero_Encrypted_Envelope\",\"items\":[]}")
        assertEquals("VaultZero_Encrypted_Envelope", parsed.getString("format"))
    }

    @Test
    fun `depth counter ignores brackets inside strings`() {
        val json = "{\"note\":\"" + "[".repeat(500) + "\"}"
        assertTrue(BackupJson.exceedsDepth(json, MAX_ALLOWED_DEPTH) == false)
    }

    @Test
    fun `repository import rejects deep nesting instead of crashing`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = VaultPreferences(context, VaultPreferences.TEST_PREFS_NAME)
        prefs.resetAll()
        val db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = VaultRepository(db.vaultDao(), prefs)
            repository.setupMasterPassword("AuditPass2026")
            val deep = "{\"x\":" + "[".repeat(20_000) + "]".repeat(20_000) + "}"
            val error = runCatching { repository.importEncryptedBackup(deep) }.exceptionOrNull()
            assertTrue("expected VaultException, got $error", error is VaultException)
            assertEquals(VaultError.INVALID_JSON, (error as VaultException).error)
        } finally {
            db.close()
            prefs.resetAll()
        }
    }

    private companion object {
        const val MAX_ALLOWED_DEPTH = 64
    }
}
