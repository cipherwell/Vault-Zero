package com.example.data

/**
 * A backup that is structurally valid but contains records that could not be
 * authenticated/decrypted. Nothing was written; the caller can offer a partial
 * import of the readable records.
 */
class BackupVerificationException(
    val unreadableRecords: Int,
    message: String,
) : IllegalArgumentException(message)
