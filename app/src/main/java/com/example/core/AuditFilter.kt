package com.example.core

import com.example.model.AuditReport
import com.example.model.VulnerabilityType

/**
 * Vault-list filters backed by the local security audit, so the audit findings can
 * be sliced in the list itself: `is:weak`, `is:reused`, `is:stale`,
 * `is:no2fa` and `is:clean`.
 */
enum class AuditFilter(val label: String) {
    WEAK("Weak"),
    REUSED("Reused"),
    STALE("Stale"),
    NO_2FA("No/invalid 2FA"),
    CLEAN("Clean");

    companion object {
        fun fromToken(value: String): AuditFilter? = when (value.lowercase()) {
            "weak" -> WEAK
            "reused", "reuse" -> REUSED
            "stale", "old" -> STALE
            "no2fa", "missing2fa", "2faerror", "2fa-error", "no-2fa" -> NO_2FA
            "clean", "healthy", "ok" -> CLEAN
            else -> null
        }
    }
}

/** Precomputed sets for fast item-wise filtering. */
data class AuditIndex(
    val weak: Set<String>,
    val reused: Set<String>,
    val stale: Set<String>,
    val missing2fa: Set<String>,
) {
    private val flagged: Set<String> get() = weak + reused + stale + missing2fa

    fun matches(itemId: String, filters: Set<AuditFilter>): Boolean =
        filters.all { filter ->
            when (filter) {
                AuditFilter.WEAK -> itemId in weak
                AuditFilter.REUSED -> itemId in reused
                AuditFilter.STALE -> itemId in stale
                AuditFilter.NO_2FA -> itemId in missing2fa
                AuditFilter.CLEAN -> itemId !in flagged
            }
        }

    companion object {
        fun from(report: AuditReport): AuditIndex {
            fun ids(type: VulnerabilityType) = report.vulnerabilities
                .asSequence()
                .filter { it.type == type }
                .map { it.itemId }
                .toSet()
            return AuditIndex(
                weak = ids(VulnerabilityType.WEAK_PASSWORD),
                reused = ids(VulnerabilityType.REUSED_PASSWORD),
                stale = ids(VulnerabilityType.STALE_PASSWORD),
                missing2fa = ids(VulnerabilityType.MISSING_2FA),
            )
        }
    }
}
