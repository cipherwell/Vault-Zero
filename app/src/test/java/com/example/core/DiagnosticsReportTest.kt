package com.example.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The diagnostics blob is pasted into public bug reports, so it must carry build/device state and
 * nothing that could be a secret.
 */
class DiagnosticsReportTest {

    private val input = DiagnosticsReport.Input(
        appVersionName = "1.7.1",
        appVersionCode = 10,
        packageName = "com.cipherwell.vaultzero",
        androidRelease = "17",
        sdkInt = 37,
        manufacturer = "Google",
        model = "Pixel 9 Pro XL",
        languageTag = "es-US",
        selectedLanguage = "SPANISH_US",
        vaultInitialized = true,
        vaultUnlocked = true,
        itemCount = 3,
        biometricsEnabled = true,
        biometricsAvailable = "available",
        keyFingerprint = "VZ-1234-5678-90AB-CDEF",
        lastError = "Incorrect Master Password. Please check and try again.",
    )

    @Test
    fun `report contains the fields support needs`() {
        val report = DiagnosticsReport.build(input)
        listOf(
            "VaultZero diagnostics",
            "1.7.1 (build 10)",
            "com.cipherwell.vaultzero",
            "Android: 17 (API 37)",
            "Google Pixel 9 Pro XL",
            "Language: es-US (selected: SPANISH_US)",
            "initialized=yes",
            "state=unlocked",
            "items=3",
            "enabled=yes",
            "available",
            "VZ-1234-5678-90AB-CDEF",
            "Last error: Incorrect Master Password",
        ).forEach { expected ->
            assertTrue("missing [$expected] in:\n$report", report.contains(expected))
        }
    }

    @Test
    fun `report never embeds absent optional data`() {
        val minimal = input.copy(lastError = null, keyFingerprint = "", itemCount = 0)
        val report = DiagnosticsReport.build(minimal)
        assertTrue(report.contains("Last error: none"))
        assertFalse("no empty fingerprint line", report.contains("Key fingerprint: "))
        assertTrue(report.contains("items=0"))
    }

    @Test
    fun `report is a single copyable block without blank trailing lines`() {
        val report = DiagnosticsReport.build(input)
        assertFalse(report.endsWith("\n"))
        assertTrue("kept short enough to paste in a chat", report.length < 800)
    }
}
