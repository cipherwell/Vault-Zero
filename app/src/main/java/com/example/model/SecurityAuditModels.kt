package com.example.model

enum class VulnerabilityType(val title: String) {
    WEAK_PASSWORD("Weak Password"),
    REUSED_PASSWORD("Reused Password"),
    STALE_PASSWORD("Stale Password (>90d)"),
    MISSING_2FA("Invalid 2FA / TOTP")
}

enum class Severity {
    CRITICAL,
    WARNING,
    INFO
}

data class Vulnerability(
    val itemId: String,
    val itemTitle: String,
    val type: VulnerabilityType,
    val severity: Severity,
    /** English description, kept for logs/tests; the UI renders a localized template instead. */
    val description: String,
    val recommendation: String,
    /** Values for the localized description template, in template order. */
    val descriptionArgs: List<Any> = emptyList(),
)

data class AuditReport(
    val score: Int, // 0 - 100
    val grade: String, // "A+", "A", "B", "C", "F"
    val totalChecked: Int,
    val weakCount: Int,
    val reusedCount: Int,
    val staleCount: Int,
    val missing2faCount: Int,
    val vulnerabilities: List<Vulnerability>
)
