package com.example.crypto

import com.example.model.LoginPayload
import com.example.model.VaultItem
import com.example.model.VaultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Direct tests for the local security audit rules. The audit is pure logic, so
 * it runs as a plain JVM test: strength, reuse, staleness and malformed TOTP all
 * have to be reported for the right records.
 */
class SecurityAuditEngineTest {

    private val day = 24L * 60 * 60 * 1000

    private fun login(
        id: String,
        title: String,
        password: String,
        totp: String = "",
        updatedDaysAgo: Long = 0,
    ) = VaultItem(
        id = id,
        title = title,
        type = VaultType.LOGIN,
        createdAt = System.currentTimeMillis() - updatedDaysAgo * day,
        updatedAt = System.currentTimeMillis() - updatedDaysAgo * day,
        payload = LoginPayload(username = "$id@example.com", password = password, totpSecret = totp),
    )

    private val validTotp = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun `reports weak, reused, stale and malformed totp findings`() {
        val now = System.currentTimeMillis()
        val items = listOf(
            login("clean", "Clean", "Vx9#mQ2!pL7\$nR4", validTotp),
            login("weak-a", "Weak A", "password123", "NOT-A-VALID-SECRET", updatedDaysAgo = 120),
            login("weak-b", "Weak B", "password123", validTotp),
        )

        val report = SecurityAuditEngine.analyzeVault(items, now)

        assertEquals("three logins checked", 3, report.totalChecked)
        assertEquals("weak count", 2, report.weakCount)
        assertEquals("reused count", 2, report.reusedCount)
        assertEquals("stale count (only the 120-day record)", 1, report.staleCount)
        assertEquals("malformed TOTP count (only weak-a)", 1, report.missing2faCount)
        assertTrue("action items listed", report.vulnerabilities.size >= 6)
        assertTrue("score drops below a clean vault", report.score < 100)
        assertEquals("no finding for the clean login", 0, report.vulnerabilities.count { it.itemId == "clean" })
    }

    @Test
    fun `clean vault scores 100 and reports no findings`() {
        val items = listOf(login("only", "Only", "Vx9#mQ2!pL7\$nR4", validTotp))
        val report = SecurityAuditEngine.analyzeVault(items, System.currentTimeMillis())
        assertEquals(100, report.score)
        assertEquals(0, report.vulnerabilities.size)
        assertEquals(0, report.weakCount + report.reusedCount + report.staleCount + report.missing2faCount)
    }

    @Test
    fun `empty login set is treated as a clean audit`() {
        val report = SecurityAuditEngine.analyzeVault(emptyList(), System.currentTimeMillis())
        assertEquals(100, report.score)
        assertEquals(0, report.totalChecked)
    }
}
