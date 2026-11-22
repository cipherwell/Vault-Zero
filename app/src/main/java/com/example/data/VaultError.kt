package com.example.data

/**
 * User-reachable failure reasons. The English `message` on [VaultException] stays the same as
 * before (logs, crash reports, unit-test assertions); [VaultError] lets the UI render the same
 * failure in the selected app language instead of leaking English into a Spanish session.
 */
enum class VaultError {
    PASSWORD_TOO_SHORT,
    NEW_PASSWORD_TOO_SHORT,
    NEW_PASSWORD_UNCHANGED,
    CURRENT_PASSWORD_INCORRECT,
    VAULT_ALREADY_EXISTS,
    RECOVERY_PHRASE_INVALID,
    RECOVERY_PHRASE_CHECKSUM_MISMATCH,
    RECOVERY_PHRASE_MISMATCH,
    INVALID_JSON,
    NOT_A_VAULT_BACKUP,
    BACKUP_NO_ITEMS,
    BACKUP_EMPTY,
    BACKUP_NO_RECOVERY_KEY,
    BACKUP_NO_PASSWORD_KEY,
    BACKUP_PASSWORD_WRONG,
    BACKUP_VERIFIER_MISSING,
    BACKUP_PHRASE_ONLY_NEEDS_PHRASE,
    BACKUP_PROTECTED_BY_PHRASE,
    BACKUP_PHRASE_ONLY_NEEDS_NEW_PASSWORD,
    BACKUP_ENTRY_NOT_OBJECT,
    BACKUP_ENTRY_NO_ID,
    BACKUP_ENTRY_DUPLICATE_ID,
    BACKUP_ENTRY_UNKNOWN_TYPE,
    BACKUP_ENTRY_NO_PAYLOAD,
    BACKUP_ENTRY_FAILED_AUTH,
    BACKUP_ENTRY_FOREIGN_KEY,
    BACKUP_ENTRY_FOREIGN_KEY_FAILED,
    BACKUP_SALT_TOO_LARGE,
    BACKUP_SALT_LENGTH,
    BACKUP_ENVELOPE_TOO_LARGE,
    BACKUP_KDF_UNSUPPORTED,
    INVALID_VAULT_KEY,
    VAULT_LOCKED,
    RECORD_AAD_MISMATCH,
}

/** An [IllegalArgumentException] that also carries a localizable [error] code and its format args. */
class VaultException(
    val error: VaultError,
    message: String,
    /** Values for the localized template (entry index, salt label, ...). */
    val args: List<Any> = emptyList(),
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/**
 * A vault-state failure (currently "vault is locked"). Kept as [IllegalStateException] so callers
 * that guard on state still behave exactly as before, while carrying a localizable code.
 */
class VaultStateException(
    val error: VaultError,
    message: String,
    val args: List<Any> = emptyList(),
) : IllegalStateException(message)
