package com.example.data

/**
 * Outcome of a verified backup import, at record granularity.
 *
 * A single file can contain records that already exist (skipped), records whose
 * content changed (updated in place, same record id), and genuinely new records
 * (imported). Nothing is written unless the whole file passed verification.
 */
data class BackupImportReport(
    val imported: Int,
    val updated: Int,
    val duplicatesSkipped: Int,
) {
    val totalNew: Int get() = imported + updated
    val totalCandidates: Int get() = imported + updated + duplicatesSkipped
}
