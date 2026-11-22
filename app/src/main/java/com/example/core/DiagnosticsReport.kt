package com.example.core

/**
 * Builds the support/diagnostics blob testers can copy from Settings.
 *
 * Hard rule: this report may only contain build, device and non-secret state. It must never
 * include item titles, usernames, passwords, recovery phrases, key material or ciphertext.
 */
object DiagnosticsReport {

    data class Input(
        val appVersionName: String,
        val appVersionCode: Int,
        val packageName: String,
        val androidRelease: String,
        val sdkInt: Int,
        val manufacturer: String,
        val model: String,
        val languageTag: String,
        val selectedLanguage: String,
        val vaultInitialized: Boolean,
        val vaultUnlocked: Boolean,
        val itemCount: Int,
        val biometricsEnabled: Boolean,
        val biometricsAvailable: String,
        val keyFingerprint: String,
        val lastError: String?,
    )

    fun build(input: Input): String = buildString {
        appendLine("VaultZero diagnostics")
        appendLine("App: ${input.appVersionName} (build ${input.appVersionCode}) · ${input.packageName}")
        appendLine(
            "Android: ${input.androidRelease} (API ${input.sdkInt}) · " +
                "${input.manufacturer} ${input.model}",
        )
        appendLine("Language: ${input.languageTag} (selected: ${input.selectedLanguage})")
        appendLine(
            "Vault: initialized=${yesNo(input.vaultInitialized)}, " +
                "state=${if (input.vaultUnlocked) "unlocked" else "locked"}, " +
                "items=${input.itemCount}",
        )
        appendLine(
            "Biometrics: enabled=${yesNo(input.biometricsEnabled)} · ${input.biometricsAvailable}",
        )
        if (input.keyFingerprint.isNotBlank()) {
            appendLine("Key fingerprint: ${input.keyFingerprint}")
        }
        append("Last error: ")
        append(if (input.lastError.isNullOrBlank()) "none" else input.lastError)
    }

    private fun yesNo(value: Boolean): String = if (value) "yes" else "no"
}
