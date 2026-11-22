package com.example.ui.i18n

import androidx.annotation.StringRes
import com.example.R
import com.example.data.VaultError

/**
 * Maps a data-layer failure code to a localized message. Everything the user can trigger has an
 * explicit entry; unknown codes fall back to the English exception message in [failureText].
 */
@StringRes
fun VaultError.messageRes(): Int = when (this) {
    VaultError.PASSWORD_TOO_SHORT -> R.string.err_password_too_short
    VaultError.NEW_PASSWORD_TOO_SHORT -> R.string.err_new_password_too_short
    VaultError.NEW_PASSWORD_UNCHANGED -> R.string.err_new_password_unchanged
    VaultError.CURRENT_PASSWORD_INCORRECT -> R.string.err_current_password_incorrect
    VaultError.VAULT_ALREADY_EXISTS -> R.string.err_vault_already_exists
    VaultError.RECOVERY_PHRASE_INVALID -> R.string.err_recovery_phrase_invalid
    VaultError.RECOVERY_PHRASE_CHECKSUM_MISMATCH -> R.string.err_recovery_phrase_checksum
    VaultError.RECOVERY_PHRASE_MISMATCH -> R.string.err_recovery_phrase_mismatch
    VaultError.INVALID_JSON -> R.string.err_invalid_json
    VaultError.NOT_A_VAULT_BACKUP -> R.string.err_not_a_vault_backup
    VaultError.BACKUP_NO_ITEMS -> R.string.err_backup_no_items
    VaultError.BACKUP_EMPTY -> R.string.err_backup_empty
    VaultError.BACKUP_NO_RECOVERY_KEY -> R.string.err_backup_no_recovery_key
    VaultError.BACKUP_NO_PASSWORD_KEY -> R.string.err_backup_no_password_key
    VaultError.BACKUP_PASSWORD_WRONG -> R.string.err_backup_password_wrong
    VaultError.BACKUP_VERIFIER_MISSING -> R.string.err_backup_verifier_missing
    VaultError.BACKUP_PHRASE_ONLY_NEEDS_PHRASE -> R.string.err_backup_phrase_only_needs_phrase
    VaultError.BACKUP_PROTECTED_BY_PHRASE -> R.string.err_backup_needs_recovery_phrase
    VaultError.BACKUP_PHRASE_ONLY_NEEDS_NEW_PASSWORD -> R.string.err_backup_phrase_only_needs_password
    VaultError.BACKUP_ENTRY_NOT_OBJECT -> R.string.err_backup_entry_not_object
    VaultError.BACKUP_ENTRY_NO_ID -> R.string.err_backup_entry_no_id
    VaultError.BACKUP_ENTRY_DUPLICATE_ID -> R.string.err_backup_entry_duplicate_id
    VaultError.BACKUP_ENTRY_UNKNOWN_TYPE -> R.string.err_backup_entry_unknown_type
    VaultError.BACKUP_ENTRY_NO_PAYLOAD -> R.string.err_backup_entry_no_payload
    VaultError.BACKUP_ENTRY_FAILED_AUTH -> R.string.err_backup_entry_failed_auth
    VaultError.BACKUP_ENTRY_FOREIGN_KEY -> R.string.err_backup_entry_foreign_key
    VaultError.BACKUP_ENTRY_FOREIGN_KEY_FAILED -> R.string.err_backup_entry_foreign_key_failed
    VaultError.BACKUP_SALT_TOO_LARGE -> R.string.err_backup_salt_too_large
    VaultError.BACKUP_SALT_LENGTH -> R.string.err_backup_salt_length
    VaultError.BACKUP_ENVELOPE_TOO_LARGE -> R.string.err_backup_envelope_too_large
    VaultError.BACKUP_KDF_UNSUPPORTED -> R.string.err_backup_kdf_unsupported
    VaultError.INVALID_VAULT_KEY -> R.string.err_invalid_vault_key
    VaultError.VAULT_LOCKED -> R.string.err_vault_locked
    VaultError.RECORD_AAD_MISMATCH -> R.string.err_record_aad_mismatch
}
