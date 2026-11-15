package com.example.data

/**
 * How much key material travels in an exported backup.
 *
 * STANDARD: both the password-wrapped and the recovery-phrase-wrapped VMK travel
 * with the file, so it can be restored with the source master password OR the
 * source 12-word recovery phrase.
 *
 * RECOVERY_PHRASE_ONLY: the password-wrapped envelope, password salt and password
 * verifier are stripped. The only key material left is the VMK wrapped under the
 * recovery-phrase key, so the file can only be opened with the 12-word phrase.
 * (Your own vault, while unlocked, can still import it after verifying the phrase.)
 */
enum class BackupProtection(val storage: String) {
    STANDARD("standard"),
    RECOVERY_PHRASE_ONLY("recovery_phrase_only");

    companion object {
        fun fromStorage(raw: String?): BackupProtection =
            entries.firstOrNull { it.storage == raw } ?: STANDARD
    }
}
