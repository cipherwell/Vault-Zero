package com.example.core

import com.example.model.AuditReport
import com.example.model.Severity
import com.example.model.Vulnerability
import com.example.model.VulnerabilityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditFilterTest {

    private fun vulnerability(id: String, type: VulnerabilityType) = Vulnerability(
        itemId = id,
        itemTitle = id,
        type = type,
        severity = Severity.CRITICAL,
        description = "d",
        recommendation = "r",
    )

    private val report = AuditReport(
        score = 40,
        grade = "D",
        totalChecked = 5,
        weakCount = 1,
        reusedCount = 2,
        staleCount = 1,
        missing2faCount = 1,
        vulnerabilities = listOf(
            vulnerability("weak-1", VulnerabilityType.WEAK_PASSWORD),
            vulnerability("reused-1", VulnerabilityType.REUSED_PASSWORD),
            vulnerability("reused-2", VulnerabilityType.REUSED_PASSWORD),
            vulnerability("stale-1", VulnerabilityType.STALE_PASSWORD),
            vulnerability("totp-1", VulnerabilityType.MISSING_2FA),
        ),
    )

    @Test
    fun `tokens map to audit filters and unknown tokens do not`() {
        assertEquals(AuditFilter.WEAK, AuditFilter.fromToken("weak"))
        assertEquals(AuditFilter.REUSED, AuditFilter.fromToken("reused"))
        assertEquals(AuditFilter.STALE, AuditFilter.fromToken("stale"))
        assertEquals(AuditFilter.NO_2FA, AuditFilter.fromToken("no2fa"))
        assertEquals(AuditFilter.NO_2FA, AuditFilter.fromToken("2fa-error"))
        assertEquals(AuditFilter.CLEAN, AuditFilter.fromToken("clean"))
        assertNull(AuditFilter.fromToken("banana"))
    }

    @Test
    fun `search spec parses audit operators and keeps favorites working`() {
        val spec = SearchQuery.parse("is:weak is:fav is:clean")
        assertEquals(setOf(AuditFilter.WEAK, AuditFilter.CLEAN), spec.audit)
        assertEquals(true, spec.favoritesOnly)
        assertFalse(spec.isEmpty)
    }

    @Test
    fun `audit index classifies each finding and clean items`() {
        val index = AuditIndex.from(report)
        assertTrue(index.matches("weak-1", setOf(AuditFilter.WEAK)))
        assertFalse(index.matches("reused-1", setOf(AuditFilter.WEAK)))
        assertTrue(index.matches("reused-2", setOf(AuditFilter.REUSED)))
        assertTrue(index.matches("stale-1", setOf(AuditFilter.STALE)))
        assertTrue(index.matches("totp-1", setOf(AuditFilter.NO_2FA)))
        assertTrue(index.matches("clean-item", setOf(AuditFilter.CLEAN)))
        assertFalse(index.matches("weak-1", setOf(AuditFilter.CLEAN)))
    }

    @Test
    fun `multiple audit filters are ANDed`() {
        val index = AuditIndex.from(report)
        assertFalse(index.matches("weak-1", setOf(AuditFilter.WEAK, AuditFilter.REUSED)))
        assertTrue(index.matches("reused-1", setOf(AuditFilter.REUSED)))
    }
}
