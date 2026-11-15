package com.example.data

/**
 * Result of restoring a complete vault during first-run setup (phone-to-phone migration).
 *
 * The source vault's recovery phrase is intentionally preserved: no new recovery kit is
 * generated. Standard backups also keep their original master password unless the caller
 * supplied a new one for this device.
 */
data class SetupRestoreReport(
    val itemCount: Int,
    val keptOriginalPassword: Boolean,
    val phraseOnlyBackup: Boolean,
)
